package silkvis.verify;

import silkvis.config.json.JsonObject;
import silkvis.config.json.JsonValue;
import silkvis.media.ArtworkCache;
import silkvis.media.IpcCodec;
import silkvis.media.IpcEnvelope;
import silkvis.media.IpcProtocol;
import silkvis.media.MediaAction;
import silkvis.media.MediaCapabilities;
import silkvis.media.MediaCommand;
import silkvis.media.MediaCommandResult;
import silkvis.media.MediaProvider;
import silkvis.media.MediaProviderListener;
import silkvis.media.MediaService;
import silkvis.media.MediaSessionRef;
import silkvis.media.MediaSnapshot;
import silkvis.media.MediaTimeline;
import silkvis.media.PlaybackState;
import silkvis.media.ProviderStatus;
import silkvis.media.SessionSelection;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import static silkvis.verify.SelfCheck.assertEquals;
import static silkvis.verify.SelfCheck.assertTrue;
import static silkvis.verify.SelfCheck.check;
import static silkvis.verify.SelfCheck.expectThrows;

final class MediaChecks {
    private MediaChecks() {
    }

    private static MediaSnapshot snapshot(String sessionId, String appId, long generation, long revision,
                                          PlaybackState state, Long position, Long end) {
        return new MediaSnapshot("test", sessionId, generation, revision, appId, "Test App",
                "Title", null, null, null, state, position, 0L, end, 0L, end, 1.0, 0L,
                MediaCapabilities.of(MediaAction.PLAY, MediaAction.PAUSE, MediaAction.NEXT), null);
    }

    private static final class FakeProvider implements MediaProvider {
        final List<MediaCommand> commands = new ArrayList<>();
        MediaProviderListener listener;
        ProviderStatus status = ProviderStatus.DISABLED;
        int started;
        boolean closed;

        @Override
        public String id() {
            return "fake";
        }

        @Override
        public void start(MediaProviderListener newListener) {
            this.listener = newListener;
            this.started++;
            status = ProviderStatus.READY;
            newListener.onStatus(ProviderStatus.READY, "");
        }

        @Override
        public ProviderStatus status() {
            return status;
        }

        @Override
        public List<MediaSessionRef> listSessions() {
            return List.of(new MediaSessionRef("s1", "spotify", "Spotify", true));
        }

        @Override
        public void selectSession(String sessionId) {
        }

        @Override
        public void execute(MediaCommand command) {
            commands.add(command);
        }

        @Override
        public void requestArtwork(String sessionId, long sessionGeneration, String artworkRef) {
        }

        @Override
        public void close() {
            closed = true;
            status = ProviderStatus.DISABLED;
        }
    }

    static void run() {
        check("ipc codec roundtrip and caps", () -> {
            IpcEnvelope envelope = IpcEnvelope.request(7, IpcProtocol.COMMAND, JsonValue.newObject()
                    .put("action", "NEXT").put("sessionId", "s1"));
            String line = IpcCodec.encode(envelope);
            IpcEnvelope decoded = IpcCodec.decodeLine(line);
            assertEquals(IpcProtocol.COMMAND, decoded.type(), "type");
            assertEquals(7L, decoded.requestId(), "request id");
            assertEquals("NEXT", decoded.payload().get("action").string(), "payload");
            expectThrows(IpcCodec.IpcException.class, () -> IpcCodec.decodeLine("not json"), "malformed");
            expectThrows(IpcCodec.IpcException.class, () -> IpcCodec.decodeLine(
                    "{\"schemaVersion\":2,\"type\":\"SNAPSHOT\",\"payload\":{}}"), "future schema");
            StringBuilder longValue = new StringBuilder();
            for (int i = 0; i < 5000; i++) {
                longValue.append('a');
            }
            JsonObject payload = JsonValue.newObject().put("title", longValue.toString());
            String tooLong = IpcCodec.encode(IpcEnvelope.of(IpcProtocol.SNAPSHOT, payload));
            expectThrows(IpcCodec.IpcException.class, () -> IpcCodec.decodeLine(tooLong), "metadata cap");
        });

        check("timeline extrapolates only while playing", () -> {
            FakeClock clock = new FakeClock();
            MediaSnapshot playing = snapshot("s1", "spotify", 1, 1, PlaybackState.PLAYING, 10_000L, 200_000L);
            clock.advanceMillis(5_000);
            OptionalLong projected = MediaTimeline.projectPosition(playing, clock.monotonicNanos(), clock);
            assertEquals(15_000L, projected.orElse(-1), "playing extrapolation");
            MediaSnapshot paused = snapshot("s1", "spotify", 1, 2, PlaybackState.PAUSED, 10_000L, 200_000L);
            assertEquals(10_000L, MediaTimeline.projectPosition(paused, clock.monotonicNanos(), clock)
                    .orElse(-1), "paused stays");
            MediaSnapshot live = snapshot("s1", "spotify", 1, 3, PlaybackState.PLAYING, 1_000L, null);
            assertTrue(MediaTimeline.isLiveOrUnknownLength(live), "live detected");
            assertTrue(!MediaTimeline.canSeek(live), "no seek on live");
            assertEquals("LIVE", MediaTimeline.describeLength(live), "live label");
            assertEquals("1:05", MediaTimeline.formatPosition(65_000L), "formatting");
        });

        check("media service addresses commands to the selected session", () -> {
            FakeClock clock = new FakeClock();
            FakeProvider provider = new FakeProvider();
            MediaService service = new MediaService(provider, clock, s -> { }, r -> { });
            service.start(SessionSelection.AUTO, "");
            assertEquals(ProviderStatus.READY, service.status(), "ready after start");
            service.onSnapshot(snapshot("s1", "spotify", 1, 1, PlaybackState.PLAYING, 0L, 1000L));
            MediaCommandResult result = service.command(MediaAction.NEXT);
            assertEquals(MediaCommandResult.Status.ACCEPTED, result.status(), "next accepted");
            assertEquals(1, provider.commands.size(), "exactly one command dispatched");
            assertEquals("s1", provider.commands.get(0).sessionId(), "addressed to session");
            MediaCommandResult unsupported = service.command(MediaAction.SEEK);
            assertEquals(MediaCommandResult.Status.UNSUPPORTED, unsupported.status(), "seek unsupported");
            assertEquals(1, provider.commands.size(), "unsupported never dispatched");
        });

        check("media service enforces command timeout without retry", () -> {
            FakeClock clock = new FakeClock();
            FakeProvider provider = new FakeProvider();
            List<MediaCommandResult> results = new ArrayList<>();
            MediaService service = new MediaService(provider, clock, s -> { }, results::add);
            service.start(SessionSelection.AUTO, "");
            service.onSnapshot(snapshot("s1", "spotify", 1, 1, PlaybackState.PLAYING, 0L, 1000L));
            service.command(MediaAction.NEXT);
            clock.advanceMillis(2_500);
            service.tick();
            assertEquals(1, results.size(), "timeout reported");
            assertEquals(MediaCommandResult.Status.TIMEOUT, results.get(0).status(), "timeout status");
            assertEquals(1, provider.commands.size(), "no automatic retry");
        });

        check("pinned selection ignores another session", () -> {
            FakeClock clock = new FakeClock();
            FakeProvider provider = new FakeProvider();
            MediaService service = new MediaService(provider, clock, s -> { }, r -> { });
            service.start(SessionSelection.PINNED, "spotify");
            service.onSnapshot(snapshot("s2", "browser", 1, 1, PlaybackState.PLAYING, 0L, 1000L));
            assertTrue(service.snapshot().isEmpty(), "foreign session ignored");
            service.onSnapshot(snapshot("s1", "spotify", 1, 1, PlaybackState.PLAYING, 0L, 1000L));
            assertTrue(service.snapshot().isPresent(), "pinned app accepted");
            service.onSnapshot(snapshot("s3", "spotify", 2, 1, PlaybackState.PLAYING, 0L, 1000L));
            assertTrue(service.snapshot().isEmpty(), "pinned session not switched");
            assertTrue(service.sourceUnavailable(), "source unavailable state");
        });

        check("stale artwork and revision rollback are rejected", () -> {
            FakeClock clock = new FakeClock();
            FakeProvider provider = new FakeProvider();
            MediaService service = new MediaService(provider, clock, s -> { }, r -> { });
            service.start(SessionSelection.AUTO, "");
            MediaSnapshot withArt = new MediaSnapshot("test", "s1", 1, 5, "spotify", "Spotify", "T", null, null,
                    "ref-1", PlaybackState.PLAYING, 0L, 0L, 1000L, 0L, 1000L, 1.0, 0L,
                    MediaCapabilities.of(MediaAction.PLAY), null);
            service.onSnapshot(withArt);
            byte[] png = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
            service.onArtwork("other-ref", png);
            assertTrue(service.currentArtworkHash().isEmpty(), "stale artwork dropped");
            service.onArtwork("ref-1", png);
            assertTrue(service.currentArtworkHash().isPresent(), "matching artwork accepted");
            service.onSnapshot(new MediaSnapshot("test", "s1", 1, 3, "spotify", "Spotify", "T", null, null,
                    "ref-1", PlaybackState.PLAYING, 0L, 0L, 1000L, 0L, 1000L, 1.0, 0L,
                    MediaCapabilities.of(MediaAction.PLAY), null));
            assertEquals(5L, service.snapshot().orElseThrow().revision(), "older revision ignored");
        });

        check("artwork cache is bounded and validates png signature", () -> {
            ArtworkCache cache = new ArtworkCache(1000);
            byte[] png = new byte[200];
            byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
            System.arraycopy(signature, 0, png, 0, signature.length);
            assertTrue(ArtworkCache.looksLikePng(png), "png signature");
            assertTrue(!ArtworkCache.looksLikePng(new byte[]{1, 2, 3}), "non-png rejected");
            for (int i = 0; i < 10; i++) {
                cache.put("hash" + i, png);
            }
            assertTrue(cache.bytes() <= 1000, "bounded bytes");
            assertTrue(cache.size() < 10, "eviction happened");
        });

        check("close releases provider resources", () -> {
            FakeProvider provider = new FakeProvider();
            MediaService service = new MediaService(provider, new FakeClock(), s -> { }, r -> { });
            service.start(SessionSelection.AUTO, "");
            service.close();
            assertTrue(provider.closed, "provider closed");
            assertTrue(service.snapshot().isEmpty(), "session cleared");
        });
    }
}
