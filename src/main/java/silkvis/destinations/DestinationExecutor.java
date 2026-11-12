package silkvis.destinations;

import silkvis.core.Clock;
import silkvis.core.ErrorCode;
import silkvis.core.SilkError;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Manual destination execution state machine (DEST-05, DEST-06, DEST-07).
 *
 * <p>Deliberately pessimistic: silence after sending is not success, retries default to
 * zero for ambiguous commands, and the exact command text is shown before sending.</p>
 */
public final class DestinationExecutor {
    /** Sink for the client network API; never a shell, never evaluated. */
    public interface CommandSink {
        void send(String normalizedCommand);
    }

    public enum State {
        READY,
        SENT,
        WAITING,
        VERIFIED,
        REJECTED,
        TIMED_OUT,
        UNCONFIRMED,
        CANCELLED
    }

    /** One execution attempt; {@code reason} feeds localization, never raw command text. */
    public record Result(State state, Optional<ErrorCode> error, String reason) {
        public boolean terminal() {
            return state == State.VERIFIED || state == State.REJECTED || state == State.TIMED_OUT
                    || state == State.UNCONFIRMED || state == State.CANCELLED;
        }
    }

    private final Clock clock;
    private final CommandSink sink;
    private final Map<String, Long> cooldownUntilMs = new HashMap<>();

    private Destination active;
    private State state = State.READY;
    private long sentAtMs;
    private PlayerSnapshot sentFrom;
    private boolean chatSignalReceived;
    private String reason = "";

    public DestinationExecutor(Clock clock, CommandSink sink) {
        this.clock = clock;
        this.sink = sink;
    }

    public State state() {
        return state;
    }

    public Optional<Destination> active() {
        return Optional.ofNullable(active);
    }

    /** Clears cooldowns when the world/profile changes. */
    public void resetCooldowns() {
        cooldownUntilMs.clear();
    }

    /**
     * @return result of the send attempt; only ACCEPTED-like states proceed past SENT.
     */
    public Result start(Destination destination, PlayerSnapshot player) {
        if (destination == null || destination.target() == null) {
            return new Result(State.REJECTED, Optional.of(ErrorCode.CONFIG_INVALID), "destination_not_configured");
        }
        DestinationTarget target = destination.target();
        if (!(target instanceof DestinationTarget.ServerCommand command)) {
            // Coordinates are a marker/route in P1; no hidden teleport (DEST-08).
            return new Result(State.REJECTED, Optional.of(ErrorCode.CAPABILITY_UNSUPPORTED), "walk_only");
        }
        if (!CommandTextValidator.isValid(command.commandText())) {
            return new Result(State.REJECTED, Optional.of(ErrorCode.CONFIG_INVALID), "invalid_command");
        }
        long now = clock.epochMillis();
        Long until = cooldownUntilMs.get(destination.id());
        if (until != null && now < until) {
            return new Result(State.REJECTED, Optional.of(ErrorCode.PERMISSION_DENIED), "cooldown");
        }
        if (command.stationaryRequired() && (player.moving() || player.damagedRecently())) {
            return new Result(State.REJECTED, Optional.of(ErrorCode.PERMISSION_DENIED), "not_stationary");
        }
        try {
            sink.send(command.commandText());
        } catch (RuntimeException e) {
            return new Result(State.REJECTED, Optional.of(ErrorCode.PERMISSION_DENIED), "send_failed");
        }
        active = destination;
        sentFrom = player;
        sentAtMs = now;
        chatSignalReceived = false;
        state = State.SENT;
        reason = "";
        cooldownUntilMs.put(destination.id(), now + command.cooldownSeconds() * 1000L);
        return new Result(State.SENT, Optional.empty(), "sent");
    }

    /** External chat signal (optional, never treated as universal proof by itself). */
    public void onChatSignal(boolean matchedExpectedSignal) {
        if (state == State.SENT || state == State.WAITING) {
            chatSignalReceived = matchedExpectedSignal;
        }
    }

    public void onDamage() {
        if (state == State.SENT || state == State.WAITING) {
            state = State.CANCELLED;
            reason = "damage";
        }
    }

    /**
     * Advances the state machine. Must be called from the client tick; does no I/O.
     */
    public Result update(PlayerSnapshot player) {
        if (active == null || state == State.READY) {
            return new Result(state, Optional.empty(), reason);
        }
        if (state != State.SENT && state != State.WAITING) {
            return new Result(state, Optional.empty(), reason);
        }
        DestinationTarget target = active.target();
        if (!(target instanceof DestinationTarget.ServerCommand command)) {
            state = State.REJECTED;
            return new Result(state, Optional.of(ErrorCode.CAPABILITY_UNSUPPORTED), "walk_only");
        }
        long elapsed = clock.epochMillis() - sentAtMs;
        if (elapsed < command.warmupSeconds() * 1000L) {
            state = State.WAITING;
            return new Result(state, Optional.empty(), "warmup");
        }
        state = State.WAITING;

        DestinationTarget.Coordinates expected = command.expectedPosition();
        boolean observed = expected != null
                && expected.dimensionId().equals(player.dimensionId())
                && player.distanceTo(expected) <= expected.arrivalRadius();
        switch (command.confirmationPolicy()) {
            case "OBSERVED_POSITION" -> {
                if (observed) {
                    state = State.VERIFIED;
                    return new Result(state, Optional.empty(), "position_observed");
                }
            }
            case "CHAT_SIGNAL" -> {
                if (chatSignalReceived) {
                    state = State.UNCONFIRMED;
                    return new Result(state, Optional.empty(), "chat_signal_only");
                }
            }
            default -> {
                if (player.moving() && elapsed > command.timeoutSeconds() * 1000L) {
                    state = State.UNCONFIRMED;
                    return new Result(state, Optional.empty(), "user_confirmation_required");
                }
            }
        }
        if (elapsed > command.timeoutSeconds() * 1000L) {
            state = State.TIMED_OUT;
            // Never auto-retry an ambiguous teleport (DEST-07).
            return new Result(state, Optional.of(ErrorCode.TIMEOUT), "timeout");
        }
        return new Result(state, Optional.empty(), reason);
    }

    /** User dismisses a pending execution; no retry is scheduled. */
    public void cancel() {
        if (state == State.SENT || state == State.WAITING) {
            state = State.CANCELLED;
            reason = "user";
        }
        active = null;
    }

    /** Explicit user confirmation for policies that require it (DEST-06). */
    public Result confirmByUser() {
        if (state == State.WAITING || state == State.UNCONFIRMED) {
            state = State.VERIFIED;
            reason = "user_confirmed";
            return new Result(state, Optional.empty(), reason);
        }
        return new Result(state, Optional.empty(), reason);
    }

    public Optional<SilkError> lastError() {
        return state == State.REJECTED ? Optional.of(SilkError.of(ErrorCode.PERMISSION_DENIED)) : Optional.empty();
    }
}
