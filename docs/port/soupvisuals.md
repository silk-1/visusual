# SoupVisuals port (SoupVisuals 1.21.11 -> SilkVis / Minecraft 1.21.10)

This package is a port of particle modules from **SoupVisuals / soupapi** by **Padej_**
(`SoupVisuals-1.21.11-3.2.0.jar`). It is not a re-implementation: settings, their names,
their order, their defaults, the animation maths and the render structure come from the
original classes and are kept as close as the target version allows.

## Attribution and licence

* Original: **SoupVisuals / soupapi**, author **Padej_**, version `1.21.11-3.2.0`.
* Original licence: **SOUP-1.0** ("Source-Only User Permission"), (c) 2025 Padej_. It allows
  viewing only; use, modification and distribution require written permission.
* This fork is made **with the written permission of the author** (fork and adaptation to
  Minecraft 1.21.10). The permission covers this project only; the material is **not**
  re-licensed, the author keeps the copyright and every ported file says so in its header.
* Textures and the lang strings under `assets/silkvis/textures/soup/` come from the same
  jar and stay under SOUP-1.0. See `THIRD_PARTY_NOTICES.md` in the repository root.

Ported classes, one line each (original -> this package):

| Original (`padej.soup.*`) | Port |
|---|---|
| `api.feature.module.setting.Setting` | `setting.Setting` |
| `...setting.implement.BooleanSetting` | `setting.BooleanSetting` |
| `...setting.implement.SelectSetting` | `setting.SelectSetting` |
| `...setting.implement.ValueSetting` | `setting.ValueSetting` |
| `...setting.implement.MultiSelectSetting` | `setting.MultiSelectSetting` |
| `...setting.implement.MultiColorSetting` | `setting.MultiColorSetting` |
| `...setting.implement.ColorSetting` | `setting.ColorSetting` |
| `...setting.implement.GroupSetting` | `setting.GroupSetting` |
| `...setting.implement.BindSetting` / `ButtonSetting` / `TextSetting` | same names |
| `api.feature.module.Module` | `module.SoupModule` |
| `api.feature.module.IParticleModule` | `module.ParticleModule` |
| `base.util.animation.Interpolation(s)` | `animation.Interpolation(s)` |
| `base.util.animation.ThreeStageAnimation` | `animation.ThreeStageAnimation` |
| `base.util.color.ColorUtil` (subset) | `util.ColorUtil` |
| `base.util.particle.ParticleColorUtil` | `util.ParticleColorUtil` |
| `base.util.particle.AbstractParticle` | `particles.AbstractParticle` |
| `base.util.particle.ParticleUpdateExecutor` | `particles.ParticleUpdateExecutor` |
| `base.util.spatial.SpatialGrid3D` | `util.SpatialGrid3D` |
| `base.util.render.Render3DUtil` (batch part) | `render.SoupRender` |
| `...particles.types.WorldParticle` | `particles.WorldParticle` |
| `...particles.render.ParticleRenderer` | `render.ParticleRenderer` |
| `...particles.ParticleData` | `particles.ParticleData` |
| `...particles.HitBubbles` | `modules.HitBubbles` |
| `...particles.TotemParticles` | `modules.TotemParticles` |
| `...particles.AmbientParticles` | `modules.AmbientParticles` |
| `...visuals.Hitboxes` | `modules.Hitboxes` |
| `...visuals.JumpCircles` (+ `$Circle`, `JumpCircleRenderer`) | `modules.JumpCircles` |
| `...visuals.Trails` (+ `$Trail`, `TrailRenderer`) | `modules.Trails` |
| `...visuals.FriendsTargetRender` (+ `TargetRenderer`, `VisibleUtils`) | `modules.FriendsTargetRender` |
| `...other.KillEffect` (+ `$WorldParticle`, `$1`) | `modules.KillEffect` |
| `...other.HitSounds` (+ `SoundManager`) | `modules.HitSounds` |
| `...misc.HandTweaks` | `modules.HandTweaks` |
| `...api.util.render.Render3DUtil` | `render.SoupRender` (extended) |
| `...api.util.FriendUtils` | `util.FriendList` |
| `...api.util.render.EntityRenderState` (снимок) | `util.EntitySnapshot` |
| `...api.util.ColorUtil` (новые хелперы) | `util.ColorUtil` (extended) |

## Batch 2: the seven modules with their own triggers

| Module | Settings | Trigger | Deviation |
|---|---|---|---|
| `Hitboxes` | 5 | none (world render pass) | drawn in `WorldRenderEvents.END_MAIN`, not a mixin; self only in third person |
| `JumpCircles` | 18 | ground-state transition (ADR-0009) | none |
| `Trails` | 16 | `END_CLIENT_TICK` sampling | `min move` distance is merged into the tick pass |
| `HitSounds` | 9 | `AttackEntityCallback` | sweep/knockback variants play the same sound (the original only has the volumes) |
| `KillEffect` | 22 | `AttackEntityCallback` + tick | `TR Sync` falls back to `Particles` until TargetRender exists |
| `FriendsTargetRender` | 26 | world render pass | Ghosts/Crystals fall back to Legacy (needs the original's particle batch) |
| `HandTweaks` | 26 | **none** (see ADR-0010) | settings-only: no first-person hook in Fabric API 1.21.10 |

Everything else (names, order, defaults, option lists, animation maths, colour maths,
lifetimes, thresholds) follows the original 1:1; `SoupVisualsChecks2` asserts the parts that
can be asserted without the game.

### Colour animations

The original has two colour animations per module (`Wave` and `Vertex`/`Vertexes`, the label
differs per module -- `Trails` uses the singular form). Both are now real:

* `Wave` picks one colour for the whole quad.
* `Vertexes` assigns one colour per corner; `SoupRender.drawTexture(..., Vector4i, ...)` carries
  them, so the corner gradient of the original survives the move to `VertexConsumer`.

`HitBubbles` had a label typo (`Vertex` instead of `Vertexes`) that silently disabled its
vertex mode; the labels are now constants and asserted by `SoupVisualsChecks2.optionLabels`.

## Where the version work is

1. **Rendering.** The original streams quads into a `BufferBuilder` with
   `BufferRenderer.drawWithGlobalProgram` and manages blend/depth state itself. 1.21.10
   exposes world rendering through `WorldRenderEvents` (Fabric API) with a
   `VertexConsumerProvider`; `render.SoupRender` keeps the "queue, then flush once per frame"
   model and draws through `RenderLayer.getEntityTranslucentEmissive(...)` (textures) and
   `RenderLayer.getLines()` (network links). No raw GL calls, no mixins.
2. **Events.** `EventAttack`, `TickEvent`, `WorldRenderEvent` and the totem-pop mixin of the
   original do not exist here. `silkvis.client.SoupVisualsClient` feeds the same data from
   `AttackEntityCallback`, `ClientTickEvents.END_CLIENT_TICK`, `WorldRenderEvents.END_MAIN`
   and a totem-loss detector.
3. **Textures.** `assets/minecraft/textures/...` became `assets/silkvis/textures/soup/...`
   so the fork never overrides a vanilla texture or another mod's resource pack. The nine
   legacy target textures live in `textures/soup/legacy/` (`rockfly.png` is not used by any
   batch-2 module and is kept for the ghost renderer later).
4. **Sounds.** The original registered its sounds into `assets/minecraft/sounds`; the port
   uses `assets/silkvis/sounds.json` with 23 entries (`silkvis:trinket_hit` ... and
   `silkvis:kill_sounds/*`), so nothing collides with vanilla. `SoupSounds.ALL_PATHS` is the
   single list the client registers from, and the self-check compares it with `sounds.json`.
5. **Locale.** The original resolved its own `LocalizationManager` keys; here
   `Setting.setTextSink(...)` is pointed at SilkVis' language table, so `module.*`,
   `group.*`, `setting.*` keys work and RU/EN parity is checked.

## Not ported yet (and why the code says so)

* `Cube` / `Pyramid` / `Text` particle modes: they need `ParticleBatchRenderer`
  (own VBO batch + bloom pass). Selecting them renders nothing, as in the original while
  its bloom texture is still loading.
* `Firefly` particle pool of AmbientParticles: needs `FireFlyParticle` with its trail
  buffer; the settings are present and wired, the pool stays empty.
* The settings screen for these three modules: the modules, their settings tree and the
  render path are complete, but nothing draws until they are switched on (see below).

## Switching the modules on

They stay off on purpose (`AGENTS.md` rule 7). The instance is available from
`silkvis.client.SoupVisualsClient.get()`; `modules()` returns all ten modules and each
has `setEnabled(boolean)` plus its full settings tree.

The client-side wiring is one class: `silkvis.client.SoupVisualsClient`.

| Method | Called from | Feeds |
|---|---|---|
| `register()` | mod initialiser | sound registration, `END_CLIENT_TICK`, `END_MAIN`, `AttackEntityCallback` |
| `tick(client)` | every client tick | jump/totem detectors, Trails sampling, KillEffect bookkeeping |
| `renderWorld(context)` | `END_MAIN` | Hitboxes, JumpCircles, Trails, KillEffect, FriendsTargetRender |
| `onAttack(...)` | `AttackEntityCallback` | HitSounds, KillEffect |
| `snapshot(entity)` | tick pass | `EntitySnapshot` for every module |

`EntitySnapshot` is the only type that crosses the module/game boundary (AGENTS.md rule 10),
so the modules stay testable on a plain JDK and the client glue is the only version-specific
code.

## Not verified

Nothing in this port has been launched in Minecraft. The verification that *was* done is:

* `java-parser` syntax gate over all 187 Java sources -> 0 failures;
* JDT typecheck of `silkvis.visual.soup.**` and `silkvis.client.SoupVisualsClient` against
  hand-written 1.21.10 stubs -> 0 errors;
* `SoupVisualsChecks` (9) + `SoupVisualsChecks2` (16) -> 25 passes, 0 failures on a plain JDK.

That is static verification only: no Gradle build, no game launch (neither is possible in the
authoring environment).
