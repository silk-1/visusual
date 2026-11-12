# Матрица требований (TEST-06)

Дата: 2026-11-12. Снимок `main@e87b2084089a898844aafe4eeeb86ecdbfca73cb`.

Статусы: `IMPLEMENTED_TESTED` · `IMPLEMENTED_NOT_TESTED` · `PLANNED` ·
`BLOCKED` · `UNSUPPORTED`. `IMPLEMENTED_NOT_TESTED` означает наличие кода/модели
без подтверждённой приёмки; это не обещание полноты реализации.

**Build-гейт заблокирован.** Java runtime 21 доступен, `javac` и PowerShell отсутствуют.
`bash gradlew clean build` на предыдущей базе `0c2d7e6` завершился exit 1 на загрузке
Gradle (`Network is unreachable`); на текущей базе build не повторялся. selfCheck, клиент
и Windows-ПК не проверены. PR #4/#5 слиты, config-пакет восстановлен (18 файлов),
его компиляция не подтверждена. Свежий Python-прогон: 118/118 lang-ключей; ASCII FAIL 10/122.

Классы `CoreChecks`, `UiChecks`, `MapChecks`, `MediaChecks`, `DestinationChecks`,
`MiscChecks` в колонке Evidence — **места предполагаемых проверок, а не результаты запуска**.
Историческое сообщение java-parser 104/104 не воспроизводилось в этом раунде.
Ни одна runtime-возможность не переведена в `IMPLEMENTED_TESTED`.
BUILD-02 отмечает только выполненный статический аудит снимка.

Пути пакетов в таблице относительно `src/main/java/silkvis/`, lang — относительно
`src/main/resources/`. Наличие восстановленных config-файлов не означает успешную приёмку.
Фактические команды/exit codes: [status-report.md](status-report.md);
неустранённые дефекты: [known-limitations.md](known-limitations.md).

| Requirement | Реализация | Evidence | Статус |
|---|---|---|---|
| 0.2 приоритеты P0→P1→P2 | `automation/AutomationCatalog.java` (всё PLANNED), вкладка-заглушка в `client/SilkVisScreen.java` | `MiscChecks` (catalog) | IMPLEMENTED_NOT_TESTED |
| 0.3 тема Graphite/Turquoise, RU/EN | `ui/ThemeTokens.java`, lang-файлы | `python scripts/verify.py`: паритет 118/118 PASS; тема в игре NOT_TESTED | IMPLEMENTED_NOT_TESTED |
| 0.4/0.7 визуал доступен в соло и сети, координаты ≠ телепорт | `destinations/DestinationTarget.java`, `DestinationExecutor` | `DestinationChecks` (walk_only) | IMPLEMENTED_NOT_TESTED |
| 0.5 все автоматизации выключены | `AutomationCatalog.canStart` всегда false | `MiscChecks` | IMPLEMENTED_NOT_TESTED |
| 0.6 аварийный выход не убивает процесс | P2, только DTO/каталог | — | PLANNED |
| BUILD-01 1.21.10, client JAR, Java 21 | `fabric.mod.json`, `build.gradle`, `gradle.properties` | статический обзор | IMPLEMENTED_NOT_TESTED |
| BUILD-02 аудит репозитория | `promt`, `AGENTS.md`, build scripts, дерево main@e87b208 | GitHub-снимок: 151 blob SHA, tree/commit; свежий `source-audit.log` | IMPLEMENTED_TESTED |
| BUILD-03 pinned версии, одна система mappings | `gradle.properties`, Wrapper, ADR-0001 | Версии не изменены; bootstrap на 0c2d7e6 завершился сетевой ошибкой; новый resolve не выполнялся | BLOCKED |
| BUILD-04 проверка render API 1.21.10 | `render/HudHook.java`, `render/VanillaPainter.java`, ADR-0002 | AGENTS.md §5 фиксирует прежнюю сверку API; в этом раунде не повторялась, компиляции нет | IMPLEMENTED_NOT_TESTED |
| BUILD-05 команды проверок | `docs/status-report.md`, `scripts/evidence/` | Build exit 1 на старой базе; на e87b208 build/selfCheck/runClient NOT_RUN; Windows-ПК недоступен | BLOCKED |
| BUILD-06 production JAR | Gradle/Loom build pipeline | Production JAR не получен; проверка в чистом Fabric-профиле не выполнена | BLOCKED |
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
| UI-04 вкладки, поиск, избранное | `ui/MainScreenModel.java`, `SilkVisScreen` | —; избранное — без экрана | IMPLEMENTED_NOT_TESTED |
| UI-05 button/toggle/slider/text/search/scroll/tooltip | `ui/widget/*`, `ui/TooltipController.java`, `ui/ScrollModel.java` | `UiChecks` | IMPLEMENTED_NOT_TESTED |
| UI-05 color/keybind/file picker, modal, toast UI | `ToastManager` модель | — | PLANNED |
| UI-06 focus/modal/input routing | `ui/FocusManager.java`, `SilkVisScreen.keyPressed` | `UiChecks` | IMPLEMENTED_NOT_TESTED |
| UI-07 120–180 ms, reduced motion, tooltip 350 ms | `ThemeTokens.motionDuration`, `TooltipController` | `UiChecks` | IMPLEMENTED_NOT_TESTED |
| UI-08 blur, fallback opaque | `config/GlobalConfig.java`, `ThemeTokens.opaqueFallback` | Config восстановлен; сам эффект не принят | PLANNED |
| UI-09 RU/EN, plurals, glyphs | `assets/silkvis/lang/{en_us,ru_ru}.json` | `python scripts/verify.py`: 118/118, missing=0 в обе стороны; перевод/glyphs/UI NOT_TESTED; hardcoded строки см. ограничения | IMPLEMENTED_NOT_TESTED |
| HUD-01 10 P1-виджетов + независимые настройки | `ui/hud/HudWidgetId.java`, `HudWidgetConfig.java` | — | IMPLEMENTED_NOT_TESTED |
| HUD-02 anchor/offset, clamp, snap, guides, reset | `ui/hud/HudLayoutEngine.java`, `client/HudEditorScreen.java` | `UiChecks` | IMPLEMENTED_NOT_TESTED |
| HUD-03 visibility policy, protected areas | `VisibilityPolicy.java`, `HudLayoutEngine.protectedAreas` | `UiChecks` | IMPLEMENTED_NOT_TESTED |
| HUD-04 частоты 2 Hz/10 Hz, cached model | `hud/FpsAggregator.java`, `RateLimiter.java`, `client/HudDataCollector.java` | — | IMPLEMENTED_NOT_TESTED |
| HUD-05 click-through + interaction mode | `SilkVisRuntime.setHudInteractionMode`, биндинг по умолчанию не назначен | —; кнопки оверлея не кликабельны | IMPLEMENTED_NOT_TESTED |
| VIS-01/02/03 настройки камеры/мира/маркеров | `visual/VisualSettings.java` + UI | —; нет hook, эффекты не применяются | BLOCKED |
| VIS-04 AUTO по Sodium/Iris | `IntegrationRegistry.shaderMode` | `MiscChecks` | IMPLEMENTED_NOT_TESTED |
| ASSET-01…04 тема/скай/тотем | — | —; V4 | PLANNED |
| MAP-01 только клиентские данные | `client/ClientTileSampler.java` | — | IMPLEMENTED_NOT_TESTED |
| MAP-02 identity мир/измерение/порт | `map/MapIdentity.java`, восстановленный `config/ProfileIdentity.java` | Свежий `git ls-files`; `MapChecks` не запускался | IMPLEMENTED_NOT_TESTED |
| MAP-03 миникарта/маркеры/waypoints | `ui/hud`, `client/MinimapRenderer.java`, `map/Waypoint.java` | —; нет full map UI | IMPLEMENTED_NOT_TESTED |
| MAP-04 слои и удаление stale сущностей | кэш тайлов, маркеры отдельно | —; слои | PLANNED |
| MAP-05 Surface/Slice в ключе тайла | `MapMode`, `MapIdentity.slice` | `MapChecks` | IMPLEMENTED_NOT_TESTED |
| MAP-06 128×128 тайлы, LOD, dirty coalesce | `map/TileKey.java`, `TileMath.java`, `MapService` | `MapChecks` | IMPLEMENTED_NOT_TESTED |
| MAP-07 LRU по байтам, квота без маркеров | `BoundedTileCache`, `TileStore.enforceQuota` | `MapChecks` | IMPLEMENTED_NOT_TESTED |
| MAP-08 версионированный формат + CRC + atomic | `TileCodec.java`, `TileStore.java` | `MapChecks` (corruption) | IMPLEMENTED_NOT_TESTED |
| MAP-09 Дом/База/Спавн вручную | `destinations/*` | `DestinationChecks` | IMPLEMENTED_NOT_TESTED |
| MEDIA-01/02/03 GSMTC, Windows, NO_SESSION | `media/WindowsMediaProvider.java`, `MediaHelperBinary.java` | —; helper не собран | BLOCKED |
| MEDIA-04/05/06 дочерний процесс, hash, watchdog | `WindowsMediaProvider`, ADR-0003 | — | IMPLEMENTED_NOT_TESTED |
| MEDIA-07 IPC NDJSON/лимиты | `media/IpcCodec.java`, `IpcProtocol.java` | `MediaChecks` | IMPLEMENTED_NOT_TESTED |
| MEDIA-08 events/reconciliation | провайдер + `MediaService.tick` | — | IMPLEMENTED_NOT_TESTED |
| MEDIA-09 AUTO/PINNED | `MediaService.onSnapshot` | `MediaChecks` | IMPLEMENTED_NOT_TESTED |
| MEDIA-10/11/12 поля, capabilities, Try* | `MediaSnapshot`, `MediaCapabilities`, контракты | —; helper | IMPLEMENTED_NOT_TESTED |
| MEDIA-13 timeline/LIVE/seek | `media/MediaTimeline.java` | `MediaChecks` | IMPLEMENTED_NOT_TESTED |
| MEDIA-14 сброс прогресса, pending seek, timeout | `MediaService` | `MediaChecks` | IMPLEMENTED_NOT_TESTED |
| MEDIA-15 keybinds не назначены, ellipsis | `SilkVisClient`, `HudOverlayRenderer` | — | IMPLEMENTED_NOT_TESTED |
| MEDIA-16/17 без громкости, bounded cache, без истории | `ArtworkCache`, `MediaService` | `MediaChecks` | IMPLEMENTED_NOT_TESTED |
| 7.4 матрица совместимости | — | —; нет прогона | BLOCKED |
| CFG-01 пути и безопасные имена | восстановленный `config/ConfigPaths.java` | `git ls-files` на e87b208: файл есть; runtime не проверен | IMPLEMENTED_NOT_TESTED |
| CFG-02 разделение global/profile | восстановленные `GlobalConfig`, `UiConfig`; `DestinationsConfig` | Свежий `source-audit.log`; компиляция и поведение не проверены | IMPLEMENTED_NOT_TESTED |
| CFG-03 validate→temp→atomic→backup, quarantine | восстановленный `config/JsonConfigFile.java` | Файл присутствует; `CoreChecks` не запускался | IMPLEMENTED_NOT_TESTED |
| CFG-04 finite/range, OptionalLimit | восстановленный `config/Validators.java`, `core/OptionalLimit.java` | `CoreChecks` теперь импортирует `silkvis.core.OptionalLimit`; тесты не запускались | IMPLEMENTED_NOT_TESTED |
| PERF-01…05 бюджеты/SOAK | `diagnostics/Timings.java`, presets | — | PLANNED |
| AUTO-01…15, A01…A22 | `automation/AutomationCatalog.java` | `MiscChecks` (all PLANNED) | PLANNED |
| DEST-01 слоты без defaults | `DestinationsConfig.defaultSlots` | `DestinationChecks` | IMPLEMENTED_NOT_TESTED |
| DEST-02 tagged union | `DestinationTarget.java` | `DestinationChecks` | IMPLEMENTED_NOT_TESTED |
| DEST-03 пример DTO | `DestinationChecks` (декодирование примера из ТЗ) | `DestinationChecks` | IMPLEMENTED_NOT_TESTED |
| DEST-04 одна команда, без shell | `CommandTextValidator.java` | `DestinationChecks` | IMPLEMENTED_NOT_TESTED |
| DEST-05/06/07 состояния, verification, retries=0 | `DestinationExecutor.java` | `DestinationChecks` | IMPLEMENTED_NOT_TESTED |
| DEST-08 без обхода серверных прав | `DestinationExecutor` (walk_only) | `DestinationChecks` | IMPLEMENTED_NOT_TESTED |
| TEST-01/02 unit+integration | `src/test/java/silkvis/verify/*` | selfCheck не запущен; fixture-тесты scripts/verify.py не являются тестами мода | BLOCKED |
| TEST-03/04 ручные матрицы | — | —; нет клиента | BLOCKED |
| TEST-05 инварианты I1…I10 | self-checks + архитектура | — | IMPLEMENTED_NOT_TESTED |
| TEST-06 матрица | этот файл | Статусы привязаны к отчёту и логам; полная приёмка требований не выполнена | IMPLEMENTED_NOT_TESTED |
| DEV-01 scope до реализации | `docs/status-report.md` | Область работы зафиксирована; свежие результаты представлены в отчёте | IMPLEMENTED_NOT_TESTED |
| DEV-02 цель Minecraft/без серверного мода | `fabric.mod.json` | Чтение: environment=client, minecraft=1.21.10; загрузка/запуск не проверены | IMPLEMENTED_NOT_TESTED |
| DEV-03 mixins не введены | отсутствует mixin-конфиг; ADR-0002 | Новые mixins не добавлялись; runtime не проверен | IMPLEMENTED_NOT_TESTED |
| DEV-04 license/purpose/fallback | `docs/dependency-matrix.md` | Документ существует; новый аудит лицензий/зависимостей не выполнялся | IMPLEMENTED_NOT_TESTED |
| DEV-06 без секретов/демо в production | demo-текст только в редакторе HUD с меткой preview | — | IMPLEMENTED_NOT_TESTED |

## Дополнительные проверки AGENTS.md

Это отдельные результаты проверок исходников, не статусы приёмки всего UI-09 или мода.
Свежие lang/ASCII/source-audit: `scripts/evidence/2026-11-12-main-e87b208/`.
PowerShell/fixture-логи исторические: `scripts/evidence/2026-11-12-main-0c2d7e6/`.

| Правило / предмет | Проверка | Результат | Evidence |
|---|---|---|---|
| §2.8 RU/EN, только паритет ключей | `python scripts/verify.py` | PASS: en=118, ru=118; 0 расхождений | `verify-python.log` |
| §2.9 ASCII во всех src/**/*.java | та же команда | FAIL: 10 из 122 файлов, 20 строк; общий exit 1 | `verify-python.log` |
| Исполнение scripts/verify.ps1 | `pwsh -NoProfile -File scripts/verify.ps1` | NOT_RUN: shell exit 127, pwsh отсутствует | `verify-powershell.log` |
| Защита Python-проверки от ложного PASS | `python scripts/test_verify.py` | PASS: 12 сценариев, exit 0; PowerShell пропущен | `verifier-fixtures.log` |
| Наличие config / корректность OptionalLimit import | `git ls-files`, `rg -n` | Статически исправлено: 18 config-файлов есть; OptionalLimit импортирован из core | `source-audit.log` |
| Добавление новых config-исходников в Git | `git check-ignore -v --no-index src/main/java/silkvis/config/ConfigPaths.java` | Исправлено в main: exit 1 без вывода, пакет не игнорируется | `source-audit.log` |

Прохождение паритета не отменяет не-ASCII/hardcoded строки и не заменяет проверку перевода
или рендера. Успех Python-fixtures не означает, что PowerShell-скрипт протестирован.
