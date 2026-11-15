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
   so the fork never overrides a vanilla texture or another mod's resource pack.
4. **Locale.** The original resolved its own `LocalizationManager` keys; here
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
`silkvis.client.SoupVisualsClient.get()`; `modules()` returns the three modules and each
has `setEnabled(boolean)` plus its full settings tree.
