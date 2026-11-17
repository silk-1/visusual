# Third-party notices

SilkVis itself is licensed under Apache-2.0 (see `LICENSE`). The material listed here keeps
its own licence and is **not** relicensed; the original authors keep their copyright.

## SoupVisuals / soupapi (particle modules)

* Author: **Padej_**
* Original version used for the port: `SoupVisuals-1.21.11-3.2.0.jar`
* Licence: **SOUP-1.0** ("Source-Only User Permission"), (c) 2025 Padej_.
  The licence allows viewing only; use, modification, copying, distribution and derivative
  works require prior written permission of the copyright holder
  (`soupvisuals2@gmail.com`).
* **Permission obtained:** the project owner holds the author's written permission for a
  fork of SoupVisuals and its adaptation to Minecraft 1.21.10. The permission is specific to
  this project; it does not re-license the original code or assets for anybody else.
* What is included here:
  * ported Java code under `src/main/java/silkvis/visual/soup/` - each file names the
    original class it comes from and its author;
  * textures under `src/main/resources/assets/silkvis/textures/soup/` (bubble styles,
    particle glyphs and particle mode textures);
  * lang strings (`module.*`, `group.*`, `setting.*`) in `en_us.json` / `ru_ru.json`.
* What is **not** included: the original shaders, fonts, sounds, the menu, the mixins and
  any module that is not listed in `docs/port/soupvisuals.md`.

If a redistribution of this repository is planned outside the project owner's control, the
permission above has to be confirmed with the author again - the SOUP-1.0 terms apply.

## Baritone (automation engine, not bundled)

* Source: <https://github.com/cabaletta/baritone>, branch `1.21.10`
* Licence: **LGPL-3.0**
* **No code from Baritone is included in this repository.** SilkVis calls its public API at
  runtime through reflection; the player installs Baritone themselves.
* Calling a separately distributed LGPL library does not make this project a derivative work
  of it, so SilkVis keeps its own licence. See `docs/adr/0013-baritone-integration.md`.
* If Baritone is not installed, SilkVis loads and works normally; the BARITONE tab says so.

## Notes on other third-party material

The original jar's `CREDITS.md` mentions `meteor-client` (GPL-3.0) for "Better Glow &
post-process". No code from that project was taken over by this port, so no GPL material is
present here; the post-process/bloom part of SoupVisuals is deliberately not ported.
