# ADR-0002 — Render/HUD adapter для 1.21.9–1.21.10

- Статус: принято с открытым риском (BUILD-04, VIS-04)
- Дата: 2026-11-12

## Контекст

В 1.21.9/1.21.10 менялись rendering и resource-loader API. Старые per-frame callbacks
(в частности прежний HUD render callback) нельзя предполагать существующими. ТЗ прямо
требует проверить сигнатуры закреплённой версии, а не копировать tutorial.

## Решение

1. Весь бизнес-код UI/HUD рисует через собственный порт `silkvis.ui.Painter`.
   Minecraft-классы встречаются только в:
   - `silkvis.render.VanillaPainter` — примитивы `DrawContext`;
   - `silkvis.render.HudHook` — единственная точка регистрации HUD-слоя;
   - `silkvis.client.*Screen` — экраны и ввод.
2. HUD регистрируется как **идентифицированный слой** после vanilla HUD
   (`HudLayerRegistrationCallback` + `IdentifiedLayer.MISC_OVERLAYS`) с
   `Identifier.of("silkvis","hud_overlay")`.
3. Если callback/сигнатура в закреплённой версии иные, меняется только `HudHook`;
   при недоступности hook мод не падает: capability `HUD_LAYERS` помечается
   недоступной, в лог пишется `CAPABILITY_UNSUPPORTED`, остальной UI работает.
4. Никаких mixin'ов на рендер в P0/P1 не добавлено. Mixin вводится только если
   публичного hook реально нет, с документированием target/version/причины (DEV-03).
5. Per-widget `scale` пока не применяется (нет проверенного API стека матриц):
   значение валидируется и хранится, рендер идёт в масштабе 1. Это ограничение
   зафиксировано в `docs/known-limitations.md`, а не замаскировано.

## Последствия

- Один класс (`HudHook`) — точка адаптации при смене версии.
- При первом реальном запуске необходимо проверить: регистрацию слоя, порядок
  относительно chat/bossbar, отсутствие утечки scissor/blend state.
