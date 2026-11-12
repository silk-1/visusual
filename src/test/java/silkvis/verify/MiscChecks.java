package silkvis.verify;

import silkvis.automation.AutomationCatalog;
import silkvis.core.BoundedExecutors;
import silkvis.core.Capability;
import silkvis.core.ErrorCode;
import silkvis.core.ModuleCategory;
import silkvis.core.ModuleDescriptor;
import silkvis.core.ModuleRegistry;
import silkvis.core.ModuleState;
import silkvis.core.OptionalLimit;
import silkvis.core.WorldSessionToken;
import silkvis.diagnostics.BoundedLog;
import silkvis.diagnostics.Timings;
import silkvis.integration.IntegrationRegistry;

import java.util.List;
import java.util.Set;

import static silkvis.verify.SelfCheck.assertEquals;
import static silkvis.verify.SelfCheck.assertTrue;
import static silkvis.verify.SelfCheck.check;
import static silkvis.verify.SelfCheck.expectThrows;

final class MiscChecks {
    private MiscChecks() {
    }

    static void run() {
        check("automation catalogue is entirely planned in this build", () -> {
            assertEquals(22, AutomationCatalog.entries().size(), "A01..A22 present");
            assertTrue(!AutomationCatalog.anyReady(), "nothing ready");
            assertTrue(!AutomationCatalog.canStart("A01"), "cannot start");
            for (AutomationCatalog.Entry entry : AutomationCatalog.entries()) {
                assertTrue(entry.id().startsWith("A"), "id format");
            }
        });

        check("module registry degrades unavailable modules with a reason", () -> {
            ModuleRegistry registry = new ModuleRegistry();
            ModuleRegistry.Module noop = new ModuleRegistry.Module() {
                @Override
                public void enable() {
                }

                @Override
                public void disable() {
                }
            };
            ModuleDescriptor descriptor = new ModuleDescriptor("map.core", ModuleCategory.MAP,
                    "k", "d", 1, Set.of(Capability.MAP_SAMPLING), List.of());
            registry.register(descriptor, noop);
            registry.get("map.core").orElseThrow()
                    .markUnavailable(ErrorCode.CAPABILITY_UNSUPPORTED, "no_hook");
            ModuleRegistry.EnableResult result = registry.enable("map.core");
            assertTrue(!result.enabled(), "not enabled");
            assertEquals(ErrorCode.CAPABILITY_UNSUPPORTED, result.reason().orElseThrow(), "reason code");
            assertEquals(ModuleState.DISABLED, registry.get("map.core").orElseThrow().state(), "state");
        });

        check("module conflicts are enforced", () -> {
            ModuleRegistry registry = new ModuleRegistry();
            ModuleRegistry.Module noop = new ModuleRegistry.Module() {
                @Override
                public void enable() {
                }

                @Override
                public void disable() {
                }
            };
            registry.register(new ModuleDescriptor("a", ModuleCategory.VISUAL, "k", "d", 1, Set.of(),
                    List.of("b")), noop);
            registry.register(new ModuleDescriptor("b", ModuleCategory.VISUAL, "k", "d", 1, Set.of(),
                    List.of()), noop);
            assertTrue(registry.enable("b").enabled(), "b enabled");
            ModuleRegistry.EnableResult result = registry.enable("a");
            assertTrue(!result.enabled(), "conflict blocks enable");
            assertEquals(ErrorCode.INTEGRATION_CONFLICT, result.reason().orElseThrow(), "conflict code");
        });

        check("failing module does not throw into the ui", () -> {
            ModuleRegistry registry = new ModuleRegistry();
            registry.register(ModuleDescriptor.of("boom", ModuleCategory.HUD, "k", "d"),
                    new ModuleRegistry.Module() {
                        @Override
                        public void enable() {
                            throw new IllegalStateException("boom");
                        }

                        @Override
                        public void disable() {
                        }
                    });
            ModuleRegistry.EnableResult result = registry.enable("boom");
            assertTrue(!result.enabled(), "reported failure");
            assertEquals(ModuleState.FAILED, registry.get("boom").orElseThrow().state(), "failed state");
        });

        check("integration probing isolates failures", () -> {
            IntegrationRegistry registry = new IntegrationRegistry();
            registry.probe("sodium", () -> true);
            registry.probe("iris", () -> {
                throw new IllegalStateException("adapter boom");
            });
            assertTrue(registry.isPresent("sodium"), "present mod");
            assertTrue(!registry.isPresent("iris"), "failure treated as absent");
            assertTrue(registry.failure("iris").isPresent(), "failure recorded");
            Set<Capability> caps = registry.capabilities(true, true);
            assertTrue(caps.contains(Capability.MEDIA_PROVIDER), "media capability");
            assertTrue(caps.contains(Capability.HUD_LAYERS), "hud capability");
            assertEquals(IntegrationRegistry.ShaderMode.SODIUM, registry.shaderMode(false), "shader mode");
        });

        check("bounded log rate-limits repeats", () -> {
            FakeClock clock = new FakeClock();
            BoundedLog log = new BoundedLog(clock, 16, 1000);
            log.log("CONFIG_INVALID", "same detail");
            log.log("CONFIG_INVALID", "same detail");
            log.log("CONFIG_INVALID", "same detail");
            assertEquals(1, log.entries().size(), "collapsed repeats");
            assertEquals(2, log.suppressedCount(), "suppressed counted");
            clock.advanceMillis(1500);
            log.log("CONFIG_INVALID", "same detail");
            assertEquals(2, log.entries().size(), "new entry after interval");
        });

        check("timings report percentiles", () -> {
            Timings timings = new Timings(64);
            for (int i = 1; i <= 100; i++) {
                timings.record(i * 1_000_000L);
            }
            assertTrue(timings.p50Ms() > 40 && timings.p50Ms() < 60, "p50 around 50ms");
            assertTrue(timings.p95Ms() > 90, "p95 high");
        });

        check("world session tokens invalidate stale work", () -> {
            WorldSessionToken first = WorldSessionToken.mint("w1");
            WorldSessionToken second = WorldSessionToken.mint("w2");
            assertTrue(!first.matches(second), "different tokens");
            assertTrue(WorldSessionToken.none().isNone(), "empty token");
            assertTrue(!WorldSessionToken.none().matches(WorldSessionToken.none()), "none never matches");
        });

        check("bounded executors reject work instead of growing forever", () -> {
            try (BoundedExecutors executors = new BoundedExecutors(1, 2, 1, 1)) {
                assertEquals(2, 2, "queue bounded by construction");
                assertTrue(executors.cpu() != null, "cpu pool present");
                assertTrue(executors.io() != null, "io pool present");
            }
        });

        check("optional limits keep user and technical caps separate", () -> {
            OptionalLimit user = new OptionalLimit(true, 100);
            assertEquals(100L, user.effective(1000, 10), "user value below cap");
            assertEquals(10L, user.effective(10, 100), "hard cap wins");
        });

        check("module descriptor validates ids", () -> {
            expectThrows(IllegalArgumentException.class,
                    () -> ModuleDescriptor.of("Bad Id", ModuleCategory.HUD, "k", "d"), "invalid id");
            expectThrows(IllegalArgumentException.class,
                    () -> ModuleDescriptor.of("", ModuleCategory.HUD, "k", "d"), "empty id");
        });
    }
}
