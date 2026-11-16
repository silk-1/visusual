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
| 0.2 приоритеты P0→P1→P2 | `automation/AutomationCatalog.java`: 7 целей READY (A01, A03, A04, A09, A19, A21, A22), остальные PLANNED; вкладка AUTOMATION в `client/SilkVisScreen.java` | `AutomationChecks` (catalogMatchesRegistry), `MiscChecks` | IMPLEMENTED_TESTED (selfCheck) |
| 0.3 тема Graphite/Turquoise, RU/EN | `ui/ThemeTokens.java`, `ui/LangTable.java`, `assets/silkvis/lang/{en_us,ru_ru}.json` | сверка ключей 0 missing; **раунд 15**: RU по умолчанию (Авто) | IMPLEMENTED_NOT_TESTED |
| 0.4/0.7 визуал доступен в соло и сети, координаты ≠ телепорт | `destinations/DestinationTarget.java`, `DestinationExecutor` | `DestinationChecks` (walk_only) | IMPLEMENTED_NOT_TESTED |
| 0.5 все автоматизации выключены | `AutomationSettings.defaults()` = `OFF`, файл `automation.json` не создаётся до первого изменения; движок не тикает в `OFF` | `AutomationChecks` (settingsRoundTrip, engine…), `MiscChecks` | IMPLEMENTED_TESTED (selfCheck) |
| 0.6 аварийный выход не убивает процесс | `automation/goals/SafetyGoal.java` (A22, режим MOVE, без копания), кнопка «Стоп» на вкладке | `AutomationChecks` (safetyGoalLeavesThePit) | IMPLEMENTED_NOT_TESTED (в игре не проверено) |
| BUILD-01 1.21.10, client JAR, Java 21 | `fabric.mod.json`, `build.gradle`, `gradle.properties` | `gradlew clean build` → `BUILD SUCCESSFUL`; зависимости объявлены как `fabricloader >=0.19.0`, `fabric-api >=0.116.0` (правка раунда 4 — первая попытка запуска в игре отклонена Loader-ом, разбор в `docs/status-report.md`) | IMPLEMENTED_TESTED (запуск в игре — нет) |
| BUILD-02 аудит репозитория | см. `docs/status-report.md` (репозиторий содержал только `LICENSE`, `README.md`, `promt`) | git log | IMPLEMENTED_TESTED |
| BUILD-03 pinned версии, одна система mappings | `gradle.properties`, ADR-0001, `dependency-matrix.md` | Loom `1.19.4` разрешён и запущен | IMPLEMENTED_TESTED |
| BUILD-04 проверка render API 1.21.10 | `render/HudHook.java`, `render/VanillaPainter.java`, ADR-0002 | `javac` против 1.21.10 скомпилировал вызовы | IMPLEMENTED_TESTED (компиляция, не рендер) |
| BUILD-05 команды проверок | `docs/build.md`, `docs/status-report.md` | фактические прогоны в отчёте | IMPLEMENTED_TESTED (отчёт) |
| BUILD-06 production JAR | `jar`/`sourcesJar` задачи | `build/libs` заполняется задачей `build`; `0.1.1-p0` содержит исправленные `depends` | IMPLEMENTED_TESTED (запуск в игре — нет) |
| ARCH-01 направление зависимостей | `ui` → модели; `render` без бизнес-логики; `MediaService` без UI | архитектурный обзор | IMPLEMENTED_NOT_TESTED |
| ARCH-02 ModuleDescriptor/lifecycle/reason | `core/ModuleRegistry.java`, `ModuleDescriptor.java` | `MiscChecks` | IMPLEMENTED_NOT_TESTED |
| ARCH-03 клиентский поток владеет миром | `client/ClientTileSampler.java`, `MapService.tick()` | — | IMPLEMENTED_NOT_TESTED |
| ARCH-04 WorldSessionToken, отмена | `core/WorldSessionToken.java`, `MapService` | `MapChecks`, `MiscChecks` | IMPLEMENTED_NOT_TESTED |
| ARCH-05 bounded pools/очереди | `core/BoundedExecutors.java`, `MapService` coalesce | `MiscChecks` | IMPLEMENTED_NOT_TESTED |
| ARCH-06 классификация ошибок, bounded log | `core/ErrorCode.java`, `diagnostics/BoundedLog.java` | `MiscChecks` | IMPLEMENTED_NOT_TESTED |
| ARCH-07 capability discovery | `integration/IntegrationRegistry.java` | `MiscChecks` (failure isolation) | IMPLEMENTED_NOT_TESTED |
| UI-01 ThemeTokens, alpha, без hex в рендере | `ui/ThemeTokens.java`, `ui/Color.java` | grep hex по `render/`,`client/`; **первый запуск в игре 2026-11-12**: тема Graphite/Turquoise, состояния подписаны текстом | IMPLEMENTED_TESTED |
| UI-02 spacing/radius/height/hit area | `ThemeTokens`, `ui/widget/Widget.java`, `ui/Painter.roundRect` | `UiChecks`; скриншоты запуска: кнопки/тумблеры/слайдер читаемы, кириллица рендерится; **раунд 6**: `radiusPanel`/`radiusControl` наконец рисуются (до этого токены были без применения) | IMPLEMENTED_TESTED (радиус — без скриншота после правки) |
| UI-03 Right Shift, max 960×620, breakpoints, прокрутка | `client/SilkVisClient.java`, `ui/MainScreenLayout.java`, `ui/ScrollModel.java` | **раунд 12**: прокрутка не работала вовсе (`ScrollModel` с viewport 0 и без `setContentHeight`) — исправлено; `UiChecks`; **запуск 2026-11-12**: окно открылось по Right Shift, окно ≤ 960×620, компактный режим (< 700) включился сам, footer/close на месте | IMPLEMENTED_TESTED |
| UI-04 вкладки, поиск, избранное | `ui/MainScreenModel.java`, `SilkVisScreen` | **запуск 2026-11-12**: 7 вкладок P1 переключаются, «Автоматизация» явно помечена как P2; поле поиска рисовалось пустым ящиком и не получало фокус по клику — исправлено после запуска | IMPLEMENTED_TESTED (поиск: фильтрация не видна в компактном режиме — список модулей по UI-03 замещён настройками; избранное — без экрана) |
| UI-05 button/toggle/slider/text/search/scroll/tooltip | `ui/widget/*`, `ui/TooltipController.java`, `ui/ScrollModel.java` | `UiChecks`; **запуск 2026-11-12**: кнопки, тумблеры, слайдер (перетаскивание, значение меняется 4000↔5100) | IMPLEMENTED_TESTED (scroll/tooltip в игре не проверялись) |
| UI-05 color/keybind/file picker, modal, toast UI | `ToastManager` модель | — | PLANNED |
| UI-06 focus/modal/input routing | `ui/FocusManager.java`, `SilkVisScreen.keyPressed` | `UiChecks` | IMPLEMENTED_NOT_TESTED |
| UI-07 120–180 ms, reduced motion, tooltip 350 ms | `ui/Animation.java`, `ThemeTokens.motionMs`, `TooltipController` | `UiChecks`; **раунд 11**: hover-фейд кнопок и тумблера, слайд ручки, fade-in подложки; при «Меньше движения» `motionMs = 0` и всё переключение мгновенное | IMPLEMENTED_NOT_TESTED |
| UI-08 blur, fallback opaque | `GlobalConfig.effectiveBlur`, `ThemeTokens.opaqueFallback` | — | PLANNED (эффект не рисуется) |
| UI-09 RU/EN, plurals, glyphs | `ui/LangTable.java`, lang-файлы, переключатель на вкладке «Настройки» | **раунд 15**: язык выбирается в UI и не зависит от языка игры; сверка ключей 0 missing, `ru_ru` полностью переведён | IMPLEMENTED_NOT_TESTED |
| HUD-01 10 P1-виджетов + независимые настройки | `ui/hud/HudWidgetId.java`, `HudWidgetConfig.java` | **запуск 2026-11-12**: в мире рисуются панель FPS и миникарта-заглушка; FPS показывал `0 FPS 0.0 ms` (агрегатор не вызывался) — исправлено | IMPLEMENTED_TESTED (рендер виджетов; карта — заглушка) |
| HUD-02 anchor/offset, clamp, snap, guides, reset | `ui/hud/HudLayoutEngine.java`, `client/HudEditorScreen.java` | `UiChecks` | IMPLEMENTED_NOT_TESTED |
| HUD-03 visibility policy, protected areas | `VisibilityPolicy.java`, `HudLayoutEngine.protectedAreas` | `UiChecks`; **F1 в игре ещё не проверялся** | IMPLEMENTED_NOT_TESTED |
| HUD-04 частоты 2 Hz/10 Hz, cached model | `hud/FpsAggregator.java`, `RateLimiter.java`, `client/HudDataCollector.java` | **запуск 2026-11-12** показал `0 FPS 0.0 ms`: `HudDataCollector.onFrame()` не вызывался ниоткуда; вызов добавлен в HUD-хук, `RateLimiter`/`FpsAggregator` больше не полагаются на нулевое начало часов. Повторного замера не было | IMPLEMENTED_NOT_TESTED (исправлено, ждёт проверки) |
| HUD-05 click-through + interaction mode | `SilkVisRuntime.setHudInteractionMode`, биндинг по умолчанию не назначен | — | IMPLEMENTED_NOT_TESTED (кнопки оверлея не кликабельны) |
| VIS-01/02/03 настройки камеры/мира/маркеров | `visual/VisualSettings.java` + UI | — | BLOCKED (нет hook, эффекты не применяются) |
| VIS-04 AUTO по Sodium/Iris | `IntegrationRegistry.shaderMode` | `MiscChecks` | IMPLEMENTED_NOT_TESTED |
| ASSET-01…04 тема/скай/тотем | `assets/TotemPackBuilder.java`, `assets/TotemLibrary.java`, `assets/TotemDownloader.java`, `client/SilkVisScreen` (вкладка «Тотемы»), `client/SilkVisRuntime.applyTotem`/`downloadTotem`, `client/TotemPackApplier` | **0.1.16-p0**: ASSET-04 реализован — картинка из `silkvis-data/totems` превращается в набор ресурсов `silkvis-totem` (текстура `totem_of_undying.png` 16/32/64/128 px по настройке, по умолчанию 128, + `pack.mcmeta` с `supported_formats`), без миксинов и инъекций; с 0.1.17 картинку можно и скачать по ссылке (только http/https, лимиты размера и времени); ASSET-01…03 (тема/скай) остаются V4 | ASSET-04 IMPLEMENTED_NOT_TESTED (набор включается программно с 0.1.18) / ASSET-01…03 PLANNED |
| MAP-01 только клиентские данные | `client/ClientTileSampler.java` | семплер читает только загруженные чанки, сервер не опрашивается; тайлы запрашиваются (`MapPrefetcher`) | IMPLEMENTED_NOT_TESTED (в игре не проверено) |
| MAP-02 identity мир/измерение/порт | `map/MapIdentity.java`, `config/ProfileIdentity.java` | `MapChecks` | IMPLEMENTED_NOT_TESTED |
| MAP-03 миникарта/маркеры/waypoints (закрывается внешней картой, ADR-0007) | `ui/hud`, `client/MinimapRenderer.java`, `client/MapScreen.java`, `client/MapPrefetcher.java`, `map/Waypoint.java` | **раунд 9**: миникарта строит и рисует тайлы; **раунд 10**: полноэкранная карта (pan/zoom/к игроку), waypoint создание/удаление/список, ПКМ по карте. Нет: поиска по точкам, слоёв Slice. **Раунд 12**: при установленном AtlasMap своя карта и миникарта отключаются автоматически. **Раунд 17 (0.1.14)**: своя миникарта удалена полностью — `MinimapRenderer` нет, `HudWidgetId.MINIMAP` только для чтения старых конфигов; миникарта — AtlasMap | IMPLEMENTED_NOT_TESTED |
| MAP-04 слои и удаление stale сущностей | кэш тайлов, маркеры отдельно | — | PLANNED (слои) |
| MAP-05 Surface/Slice в ключе тайла | `MapMode`, `MapIdentity.slice` | `MapChecks` | IMPLEMENTED_NOT_TESTED |
| MAP-06 128×128 тайлы, LOD, dirty coalesce | `map/TileKey.java`, `TileMath.java`, `MapService`, `client/MapPrefetcher.java` | `MapChecks`; живая диагностика `MapService.describe()` выведена на вкладку «Карта» | IMPLEMENTED_NOT_TESTED |
| MAP-07 LRU по байтам, квота без маркеров | `BoundedTileCache`, `TileStore.enforceQuota` | `MapChecks` | IMPLEMENTED_NOT_TESTED |
| MAP-08 версионированный формат + CRC + atomic | `TileCodec.java`, `TileStore.java`, `map/MarkerSet.java` | `MapChecks` (corruption); маркеры — отдельный файл, кэш их не вытесняет (раунд 10: UI создаёт/удаляет точки) | IMPLEMENTED_NOT_TESTED |
| MAP-09 Дом/База/Спавн вручную | `destinations/*` | `DestinationChecks` | IMPLEMENTED_NOT_TESTED |
| MEDIA-01/02/03 GSMTC, Windows, NO_SESSION | `media/WindowsMediaProvider.java`, `MediaHelperBinary.java` | — | BLOCKED (helper не собран) |
| MEDIA-04/05/06 дочерний процесс, hash, watchdog | `WindowsMediaProvider`, `MediaHelperLauncher`, `ExeHelperLauncher`, `PowerShellHelperLauncher`, ADR-0003/0008 | **раунд 13**: появился бэкенд (PowerShell-мост), до этого `helper_missing` при любой конфигурации; watchdog/рестарты общие | IMPLEMENTED_NOT_TESTED |
| MEDIA-07 IPC NDJSON/лимиты | `media/IpcCodec.java`, `IpcProtocol.java` | `MediaChecks`; **раунд 13**: добавлен `SNAPSHOT_REQUEST` для ленивого моста | IMPLEMENTED_NOT_TESTED |
| MEDIA-08 events/reconciliation | провайдер + `MediaService.tick` | — | IMPLEMENTED_NOT_TESTED |
| MEDIA-09 AUTO/PINNED | `MediaService.onSnapshot` | `MediaChecks` | IMPLEMENTED_NOT_TESTED |
| MEDIA-10/11/12 поля, capabilities, Try* | `MediaSnapshot`, `MediaCapabilities`, `silkvis-media-helper.ps1` | **раунд 13**: capabilities из `Controls`, команды `Try*` реализованы в скрипте | IMPLEMENTED_NOT_TESTED (helper) |
| MEDIA-13 timeline/LIVE/seek | `media/MediaTimeline.java` | `MediaChecks` | IMPLEMENTED_NOT_TESTED |
| MEDIA-14 сброс прогресса, pending seek, timeout | `MediaService`, `HudOverlayRenderer.renderMediaHint` | **раунд 16**: пустое состояние по-прежнему молчит, но отказ провайдера (`ERROR`/`UNSUPPORTED`) рисует на HUD причину, а не тишину | IMPLEMENTED_NOT_TESTED |
| MEDIA-10 обложка | `media/MediaService.requestArtworkIfNeeded`, `ArtworkCache.looksLikeImage`, `client/ArtworkPixels` | **раунд 18**: запрос миниатюры не вызывался ниоткуда (обложка всегда пустая), JPEG отбрасывался PNG-проверкой; теперь запрос с ретраем 2 с и сбросом на смену трека, принимаются PNG/JPEG/GIF/BMP | IMPLEMENTED_NOT_TESTED |
| HUD-02 редактор | `client/HudEditorScreen`, `HudOverlayRenderer.lastRects`, `ui/hud/HudLayoutEngine.nudge` | **раунд 18**: перетаскивание реальных прямоугольников, живая запись, debounce 500 мс, общий профиль `defaults`. **0.1.19**: выбор элемента, масштаб/прозрачность, скрыть/показать, сброс одного/всех, Ctrl+Z (20 шагов), стрелки 1/10 px, сетка 4/8/выкл; исправлен якорь по центру (смещение умножалось на 0) | IMPLEMENTED_NOT_TESTED |
| UI-06 пресеты | `config/VisualPreset`, `ui/ThemeTokens.withDensity/withMotion`, `client/SilkVisRuntime.applyTheme` | **раунд 18**: пресет хранился и ни на что не влиял; теперь меняет плотность темы | IMPLEMENTED_NOT_TESTED |
| MEDIA-05 проверка манифеста | `media/PowerShellHelperLauncher.java`, `.gitattributes` | **раунд 17**: на Windows-клоне с `core.autocrlf` скрипт выходил с CRLF и хеш не сходился (`hash_mismatch`, отчёт владельца); теперь концы строк нормализуются перед хешированием и записью, упакованный скрипт проверяется до записи | IMPLEMENTED_NOT_TESTED |
| MEDIA-02 запуск по требованию | `SilkVisRuntime.tryStartMedia/setMediaEnabled/stopMedia`, `SilkVisClient.setMediaFactory`, `SilkVisScreen` (кнопка «Перезапустить мост») | **раунд 16**: включение музыки раньше не запускало провайдер (создавался только на старте клиента при уже включённом флаге); теперь старт/стоп/перезапуск по требованию, ретрай с троттлингом 15 с | IMPLEMENTED_NOT_TESTED |
| HUD-01 иконки брони | `client/ArmorSnapshot.java`, `client/HudDataCollector.armor`, `HudOverlayRenderer.renderArmor` | **раунд 16**: по просьбе владельца — иконка предмета вместо названия, прочность `320/528` с цветом и полосой, до двух зачарований; `drawItem` сверен по yarn 1.21.10, фолбэк на рамку при несовместимости | IMPLEMENTED_NOT_TESTED |
| MEDIA-15 keybinds не назначены, ellipsis | `SilkVisClient`, `HudOverlayRenderer`, `HudInteractionScreen` | **раунд 14**: управление через экран взаимодействия, медиа-клавиши не перехватываются; ellipsis общий | IMPLEMENTED_NOT_TESTED |
| MEDIA-16/17 без громкости, bounded cache, без истории | `ArtworkCache`, `ArtworkPixels`, `MediaService` | `MediaChecks`; **раунд 14**: обложка рисуется сеткой 26×26 из RAM-кэша, декод вне кадра, на диск ничего | IMPLEMENTED_NOT_TESTED |
| VIS-11 HitBubbles (порт SoupVisuals) | `visual/soup/modules/HitBubbles.java`, `visual/soup/render/SoupRender.java`, `client/SoupVisualsClient.java` | `SoupVisualsChecks` (9 проверок: крит-правило, дефолты настроек, ключи конфига, анимация, цвета); компиляция портированного пакета компилятором Eclipse JDT (из npm-пакета ctxo-jdt-analyzer) против API-стабов 1.21.10 — без ошибок | IMPLEMENTED_NOT_TESTED |
| VIS-12 TotemParticles (порт SoupVisuals) | `visual/soup/modules/TotemParticles.java`, `client/SoupVisualsClient.detectTotemPops` | `SoupVisualsChecks`; логика эмиттера/цвета «Vanilla» перенесена 1:1 | IMPLEMENTED_NOT_TESTED |
| VIS-13 AmbientParticles (порт SoupVisuals) | `visual/soup/modules/AmbientParticles.java`, `visual/soup/particles/*`, `visual/soup/util/SpatialGrid3D.java` | `SoupVisualsChecks` (сетка соседей, физика через `AbstractParticle`) | IMPLEMENTED_NOT_TESTED |
| VIS-14 каркас настроек SoupVisuals | `visual/soup/setting/*` (Setting, Boolean/Select/Value/MultiSelect/MultiColor/Color/Group/Bind/Button/Text, SettingRepository) | `SoupVisualsChecks` (значения, видимость, reset, стабильные ключи конфига) | IMPLEMENTED_TESTED |
| VIS-15 Hitboxes (порт SoupVisuals) | `visual/soup/modules/Hitboxes.java` | `SoupVisualsChecks2` (дефолты, режимы, градиент по `hurtTime`); JDT-компиляция без ошибок | IMPLEMENTED_NOT_TESTED |
| VIS-16 JumpCircles (порт SoupVisuals) | `visual/soup/modules/JumpCircles.java`, `client/SoupVisualsClient.detectJump` | `SoupVisualsChecks2` (дефолты, четыре текстуры, длительности, `Bright` = число кругов); триггер — смена `onGround`, ADR-0009 | IMPLEMENTED_NOT_TESTED |
| VIS-17 Trails (порт SoupVisuals) | `visual/soup/modules/Trails.java` | `SoupVisualsChecks2` (дефолты, прогресс/альфа точки, удаление по времени) | IMPLEMENTED_NOT_TESTED |
| VIS-18 HitSounds (порт SoupVisuals) | `visual/soup/modules/HitSounds.java`, `visual/soup/util/SoupSounds.java`, `assets/silkvis/sounds.json` | `SoupVisualsChecks2` (крит-правило по кулдауну/падению/эффектам, паритет `sounds.json` и `ALL_PATHS`, наличие всех 23 `.ogg`) | IMPLEMENTED_NOT_TESTED |
| VIS-19 KillEffect (порт SoupVisuals) | `visual/soup/modules/KillEffect.java` | `SoupVisualsChecks2` (18 режимов, дефолты, трекинг атаки/подтверждения/отложенного килла, карта звуков) | IMPLEMENTED_NOT_TESTED |
| VIS-20 FriendsTargetRender (порт SoupVisuals) | `visual/soup/modules/FriendsTargetRender.java`, `assets/silkvis/textures/soup/legacy/*` | `SoupVisualsChecks2` (26 настроек, 9 легаси-текстур, формат пути); Ghosts/Crystals — заглушка на Legacy | IMPLEMENTED_NOT_TESTED |
| VIS-21 HandTweaks (порт SoupVisuals) | `visual/soup/modules/HandTweaks.java` | `SoupVisualsChecks2` (26 настроек, `applySwing`/`handScale`/`swingDuration`); **вызова нет** — ADR-0010 | IMPLEMENTED_NOT_TESTED |
| VIS-22 триггеры без миксинов | `client/SoupVisualsClient.java` (`detectJump`, `detectTotemPops`, `onAttack`) | ADR-0009; JDT-компиляция клиентского слоя против стабов без ошибок | IMPLEMENTED_NOT_TESTED |
| 7.4 матрица совместимости | — | — | BLOCKED (нет прогона) |
| CFG-01 пути и безопасные имена | `config/ConfigPaths.java` | — | IMPLEMENTED_NOT_TESTED |
| CFG-02 разделение global/profile | `GlobalConfig`, `UiConfig`, `DestinationsConfig` | — | IMPLEMENTED_NOT_TESTED |
| CFG-03 validate→temp→atomic→backup, quarantine | `config/JsonConfigFile.java` | `CoreChecks` | IMPLEMENTED_NOT_TESTED |
| CFG-04 finite/range, OptionalLimit | `config/Validators.java`, `core/OptionalLimit.java` | `CoreChecks`, `MiscChecks` | IMPLEMENTED_NOT_TESTED |
| PERF-01…05 бюджеты/SOAK | `diagnostics/Timings.java`, presets | — | PLANNED |
| AUTO-01…15, A01…A22 | `automation/` (движок, цели, A*), `client/AutomationExecutor.java`, `client/AutomationSnapshot.java` | `AutomationChecks` — 31 чек; ADR-0012 | IMPLEMENTED_TESTED (ядро) / IMPLEMENTED_NOT_TESTED (клиентский адаптер) |
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
