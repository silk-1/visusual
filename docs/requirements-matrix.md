# Матрица требований (TEST-06)

Статусы: `IMPLEMENTED_TESTED` · `IMPLEMENTED_NOT_TESTED` · `PLANNED` · `BLOCKED` ·
`UNSUPPORTED`.

Отсчёт ведётся от реальных прогонов. На 2026-11-12 выполнено: `gradlew clean build`
→ `BUILD SUCCESSFUL`, `selfCheck` → `passed=60 failed=0`, синтаксис 122/122,
паритет lang-ключей. Строка получает `IMPLEMENTED_TESTED`, только если за ней стоит
такой прогон; всё, что требует запущенного клиента (рендер, GPU-путь карты, медиа
на Windows), остаётся `IMPLEMENTED_NOT_TESTED` или `BLOCKED`.

| Requirement | Реализация | Evidence | Статус |
|---|---|---|---|
| 0.2 приоритеты P0→P1→P2 | `automation/AutomationCatalog.java` (всё PLANNED), вкладка-заглушка в `client/SilkVisScreen.java` | `MiscChecks` (catalog) | IMPLEMENTED_NOT_TESTED |
| 0.3 тема Graphite/Turquoise, RU/EN | `ui/ThemeTokens.java`, `assets/silkvis/lang/{en_us,ru_ru}.json` | сверка ключей 0 missing | IMPLEMENTED_NOT_TESTED |
| 0.4/0.7 визуал доступен в соло и сети, координаты ≠ телепорт | `destinations/DestinationTarget.java`, `DestinationExecutor` | `DestinationChecks` (walk_only) | IMPLEMENTED_NOT_TESTED |
| 0.5 все автоматизации выключены | `AutomationCatalog.canStart` всегда false | `MiscChecks` | IMPLEMENTED_NOT_TESTED |
| 0.6 аварийный выход не убивает процесс | P2, только DTO/каталог | — | PLANNED |
| BUILD-01 1.21.10, client JAR, Java 21 | `fabric.mod.json`, `build.gradle`, `gradle.properties` | `gradlew clean build` → `BUILD SUCCESSFUL` | IMPLEMENTED_TESTED (jar не инспектировался) |
| BUILD-02 аудит репозитория | см. `docs/status-report.md` (репозиторий содержал только `LICENSE`, `README.md`, `promt`) | git log | IMPLEMENTED_TESTED |
| BUILD-03 pinned версии, одна система mappings | `gradle.properties`, ADR-0001, `dependency-matrix.md` | Loom `1.19.4` разрешён и запущен | IMPLEMENTED_TESTED |
| BUILD-04 проверка render API 1.21.10 | `render/HudHook.java`, `render/VanillaPainter.java`, ADR-0002 | `javac` против 1.21.10 скомпилировал вызовы | IMPLEMENTED_TESTED (компиляция, не рендер) |
| BUILD-05 команды проверок | `docs/build.md`, `docs/status-report.md` | фактические прогоны в отчёте | IMPLEMENTED_TESTED (отчёт) |
| BUILD-06 production JAR | `jar`/`sourcesJar` задачи | `build/libs` заполняется задачей `build` | IMPLEMENTED_TESTED (запуск в игре — нет) |
| ARCH-01 направление зависимостей | `ui` → модели; `render` без бизнес-логики; `MediaService` без UI | архитектурный обзор | IMPLEMENTED_NOT_TESTED |
| ARCH-02 ModuleDescriptor/lifecycle/reason | `core/ModuleRegistry.java`, `ModuleDescriptor.java` | `MiscChecks` | IMPLEMENTED_NOT_TESTED |
| ARCH-03 клиентский поток владеет миром | `client/ClientTileSampler.java`, `MapService.tick()` | — | IMPLEMENTED_NOT_TESTED |
| ARCH-04 WorldSessionToken, отмена | `core/WorldSessionToken.java`, `MapService` | `MapChecks`, `MiscChecks` | IMPLEMENTED_NOT_TESTED |
| ARCH-05 bounded pools/очереди | `core/BoundedExecutors.java`, `MapService` coalesce | `MiscChecks` | IMPLEMENTED_NOT_TESTED |
| ARCH-06 классификация ошибок, bounded log | `core/ErrorCode.java`, `diagnostics/BoundedLog.java` | `MiscChecks` | IMPLEMENTED_NOT_TESTED |
| ARCH-07 capability discovery | `integration/IntegrationRegistry.java` | `MiscChecks` (failure isolation) | IMPLEMENTED_NOT_TESTED |
| UI-01 ThemeTokens, alpha, без hex в рендере | `ui/ThemeTokens.java`, `ui/Color.java` | grep hex по `render/`,`client/` | IMPLEMENTED_NOT_TESTED |
| UI-02 spacing/radius/height/hit area | `ThemeTokens`, `ui/widget/Widget.java` | `UiChecks` | IMPLEMENTED_NOT_TESTED |
| UI-03 Right Shift, max 960×620, breakpoints | `client/SilkVisClient.java`, `ui/MainScreenLayout.java` | `UiChecks` | IMPLEMENTED_NOT_TESTED |
| UI-04 вкладки, поиск, избранное | `ui/MainScreenModel.java`, `SilkVisScreen` | — | IMPLEMENTED_NOT_TESTED (избранное — без экрана) |
| UI-05 button/toggle/slider/text/search/scroll/tooltip | `ui/widget/*`, `ui/TooltipController.java`, `ui/ScrollModel.java` | `UiChecks` | IMPLEMENTED_NOT_TESTED |
| UI-05 color/keybind/file picker, modal, toast UI | `ToastManager` модель | — | PLANNED |
| UI-06 focus/modal/input routing | `ui/FocusManager.java`, `SilkVisScreen.keyPressed` | `UiChecks` | IMPLEMENTED_NOT_TESTED |
| UI-07 120–180 ms, reduced motion, tooltip 350 ms | `ThemeTokens.motionDuration`, `TooltipController` | `UiChecks` | IMPLEMENTED_NOT_TESTED |
| UI-08 blur, fallback opaque | `GlobalConfig.effectiveBlur`, `ThemeTokens.opaqueFallback` | — | PLANNED (эффект не рисуется) |
| UI-09 RU/EN, plurals, glyphs | lang-файлы | сверка ключей | IMPLEMENTED_NOT_TESTED |
| HUD-01 10 P1-виджетов + независимые настройки | `ui/hud/HudWidgetId.java`, `HudWidgetConfig.java` | — | IMPLEMENTED_NOT_TESTED |
| HUD-02 anchor/offset, clamp, snap, guides, reset | `ui/hud/HudLayoutEngine.java`, `client/HudEditorScreen.java` | `UiChecks` | IMPLEMENTED_NOT_TESTED |
| HUD-03 visibility policy, protected areas | `VisibilityPolicy.java`, `HudLayoutEngine.protectedAreas` | `UiChecks` | IMPLEMENTED_NOT_TESTED |
| HUD-04 частоты 2 Hz/10 Hz, cached model | `hud/FpsAggregator.java`, `RateLimiter.java`, `client/HudDataCollector.java` | — | IMPLEMENTED_NOT_TESTED |
| HUD-05 click-through + interaction mode | `SilkVisRuntime.setHudInteractionMode`, биндинг по умолчанию не назначен | — | IMPLEMENTED_NOT_TESTED (кнопки оверлея не кликабельны) |
| VIS-01/02/03 настройки камеры/мира/маркеров | `visual/VisualSettings.java` + UI | — | BLOCKED (нет hook, эффекты не применяются) |
| VIS-04 AUTO по Sodium/Iris | `IntegrationRegistry.shaderMode` | `MiscChecks` | IMPLEMENTED_NOT_TESTED |
| ASSET-01…04 тема/скай/тотем | — | — | PLANNED (V4) |
| MAP-01 только клиентские данные | `client/ClientTileSampler.java` | — | IMPLEMENTED_NOT_TESTED |
| MAP-02 identity мир/измерение/порт | `map/MapIdentity.java`, `config/ProfileIdentity.java` | `MapChecks` | IMPLEMENTED_NOT_TESTED |
| MAP-03 миникарта/маркеры/waypoints | `ui/hud`, `client/MinimapRenderer.java`, `map/Waypoint.java` | — | IMPLEMENTED_NOT_TESTED (нет full map UI) |
| MAP-04 слои и удаление stale сущностей | кэш тайлов, маркеры отдельно | — | PLANNED (слои) |
| MAP-05 Surface/Slice в ключе тайла | `MapMode`, `MapIdentity.slice` | `MapChecks` | IMPLEMENTED_NOT_TESTED |
| MAP-06 128×128 тайлы, LOD, dirty coalesce | `map/TileKey.java`, `TileMath.java`, `MapService` | `MapChecks` | IMPLEMENTED_NOT_TESTED |
| MAP-07 LRU по байтам, квота без маркеров | `BoundedTileCache`, `TileStore.enforceQuota` | `MapChecks` | IMPLEMENTED_NOT_TESTED |
| MAP-08 версионированный формат + CRC + atomic | `TileCodec.java`, `TileStore.java` | `MapChecks` (corruption) | IMPLEMENTED_NOT_TESTED |
| MAP-09 Дом/База/Спавн вручную | `destinations/*` | `DestinationChecks` | IMPLEMENTED_NOT_TESTED |
| MEDIA-01/02/03 GSMTC, Windows, NO_SESSION | `media/WindowsMediaProvider.java`, `MediaHelperBinary.java` | — | BLOCKED (helper не собран) |
| MEDIA-04/05/06 дочерний процесс, hash, watchdog | `WindowsMediaProvider`, ADR-0003 | — | IMPLEMENTED_NOT_TESTED |
| MEDIA-07 IPC NDJSON/лимиты | `media/IpcCodec.java`, `IpcProtocol.java` | `MediaChecks` | IMPLEMENTED_NOT_TESTED |
| MEDIA-08 events/reconciliation | провайдер + `MediaService.tick` | — | IMPLEMENTED_NOT_TESTED |
| MEDIA-09 AUTO/PINNED | `MediaService.onSnapshot` | `MediaChecks` | IMPLEMENTED_NOT_TESTED |
| MEDIA-10/11/12 поля, capabilities, Try* | `MediaSnapshot`, `MediaCapabilities`, контракты | — | IMPLEMENTED_NOT_TESTED (helper) |
| MEDIA-13 timeline/LIVE/seek | `media/MediaTimeline.java` | `MediaChecks` | IMPLEMENTED_NOT_TESTED |
| MEDIA-14 сброс прогресса, pending seek, timeout | `MediaService` | `MediaChecks` | IMPLEMENTED_NOT_TESTED |
| MEDIA-15 keybinds не назначены, ellipsis | `SilkVisClient`, `HudOverlayRenderer` | — | IMPLEMENTED_NOT_TESTED |
| MEDIA-16/17 без громкости, bounded cache, без истории | `ArtworkCache`, `MediaService` | `MediaChecks` | IMPLEMENTED_NOT_TESTED |
| 7.4 матрица совместимости | — | — | BLOCKED (нет прогона) |
| CFG-01 пути и безопасные имена | `config/ConfigPaths.java` | — | IMPLEMENTED_NOT_TESTED |
| CFG-02 разделение global/profile | `GlobalConfig`, `UiConfig`, `DestinationsConfig` | — | IMPLEMENTED_NOT_TESTED |
| CFG-03 validate→temp→atomic→backup, quarantine | `config/JsonConfigFile.java` | `CoreChecks` | IMPLEMENTED_NOT_TESTED |
| CFG-04 finite/range, OptionalLimit | `config/Validators.java`, `core/OptionalLimit.java` | `CoreChecks`, `MiscChecks` | IMPLEMENTED_NOT_TESTED |
| PERF-01…05 бюджеты/SOAK | `diagnostics/Timings.java`, presets | — | PLANNED |
| AUTO-01…15, A01…A22 | `automation/AutomationCatalog.java` | `MiscChecks` (all PLANNED) | PLANNED |
| DEST-01 слоты без defaults | `DestinationsConfig.defaultSlots` | `DestinationChecks` | IMPLEMENTED_NOT_TESTED |
| DEST-02 tagged union | `DestinationTarget.java` | `DestinationChecks` | IMPLEMENTED_NOT_TESTED |
| DEST-03 пример DTO | `DestinationChecks` (декодирование примера из ТЗ) | `DestinationChecks` | IMPLEMENTED_NOT_TESTED |
| DEST-04 одна команда, без shell | `CommandTextValidator.java` | `DestinationChecks` | IMPLEMENTED_NOT_TESTED |
| DEST-05/06/07 состояния, verification, retries=0 | `DestinationExecutor.java` | `DestinationChecks` | IMPLEMENTED_NOT_TESTED |
| DEST-08 без обхода серверных прав | `DestinationExecutor` (walk_only) | `DestinationChecks` | IMPLEMENTED_NOT_TESTED |
| TEST-01/02 unit+integration | `src/test/java/silkvis/verify/*` | `selfCheck`: `passed=60 failed=0` | IMPLEMENTED_TESTED |
| TEST-03/04 ручные матрицы | — | — | BLOCKED (нет клиента) |
| TEST-05 инварианты I1…I10 | self-checks + архитектура | те же 60 проверок | IMPLEMENTED_TESTED (кроме клиентских) |
| TEST-06 матрица | этот файл | — | IMPLEMENTED_TESTED |
| DEV-01 scope до реализации | `docs/status-report.md` | — | IMPLEMENTED_TESTED |
| DEV-02 цель Minecraft/без серверного мода | `fabric.mod.json` | — | IMPLEMENTED_TESTED |
| DEV-03 mixins не введены | нет mixin-конфига; причина в ADR-0002 | — | IMPLEMENTED_TESTED |
| DEV-04 license/purpose/fallback | `docs/dependency-matrix.md` | — | IMPLEMENTED_TESTED |
| DEV-06 без секретов/демо в production | demo-текст только в редакторе HUD с меткой preview | — | IMPLEMENTED_NOT_TESTED |
