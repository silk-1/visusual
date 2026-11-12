# Отчёт этапа (DEV-05)

Дата: 2026-11-12. Ветка: `arena/6a2f8991-visusual`, база `79ebbb4`.

## Scope этапа

V0 (каркас, конфиг, UI-адаптеры) + вертикальные срезы P1, которые можно проверить без
клиента: HUD-layout/редактор, карта (identity/тайлы/кэш/store/scheduler), музыкальные
контракты и IPC, точки назначения, диагностика. Автоматизация — только каталог PLANNED.

## Реализованные подсистемы

| Подсистема | Файлы (пример) |
|------------|----------------|
| Модули/состояния | `core/ModuleRegistry.java`, `ModuleDescriptor.java`, `ModuleState.java`, `SilkError.java` |
| Сессии мира | `core/WorldSessionToken.java`, `client/SilkVisClient.java`, `SilkVisRuntime.java` |
| Конфиг | `config/JsonConfigFile.java`, `config/GlobalConfig.java`, `config/UiConfig.java`, `config/Debouncer.java`, `config/json/*` |
| UI | `ui/ThemeTokens.java`, `ui/MainScreenLayout.java`, `ui/widget/*`, `client/SilkVisScreen.java` |
| HUD | `ui/hud/*`, `hud/*`, `client/HudOverlayRenderer.java`, `client/HudEditorScreen.java` |
| Карта | `map/*`, `client/ClientTileSampler.java`, `client/MinimapRenderer.java` |
| Музыка | `media/*` (контракты, IPC, timeline, сервис, Windows-провайдер) |
| Точки | `destinations/*` |
| Диагностика | `diagnostics/*` |

## Выполненные проверки (фактические команды и результаты)

| Команда | Результат |
|---------|-----------|
| `java -version` | `java: command not found` — JDK в среде отсутствует |
| `curl https://maven.fabricmc.net/` | `000` (соединение недоступно), Maven Central — тоже `000` |
| попытка скачать Temurin 21 с GitHub releases | редирект на `release-assets.githubusercontent.com` заблокирован |
| `node parse.js src` (java-parser 2.x, ANTLR-грамматика Java) | `parsed_ok=127 syntax_errors=0 total=127` — синтаксис всех 127 `.java` корректен |
| сверка lang-ключей `en_us.json` ↔ `ru_ru.json` | расхождений нет (0 missing / 0 extra) |
| сверка используемых в коде ключей с lang | отсутствующих литеральных ключей нет (совпали только префиксы для конкатенации) |
| поиск hex-цветов в `render/` и `client/` | только `0xFF000000` (упаковка альфы) в сэмплере; UI-цвета берутся из токенов |
| `gradlew clean build` | **не выполнялся** — нет JDK и нет доступа к Fabric Maven |
| `gradlew selfCheck` | **не выполнялся** по той же причине; харнесс dependency-free, запускается на JDK без сети |
| `gradlew runClient` | **не выполнялся** |

## Что НЕ подтверждено (и не выдаётся за подтверждённое)

1. Компиляция мода: сигнатуры Fabric/Minecraft 1.21.10 не проверены по исходникам версии.
   Наибольший риск — `silkvis/render/HudHook.java` (регистрация HUD-слоя) и
   `client/ClientTileSampler.java` (`getMapColor`/`Heightmap`).
2. Запуск клиента, скриншоты, RU/EN в живом рендере, F1/chat/reload, resize, GUI scale.
3. Музыка: helper не собран → матрица MEDIA-* не заполнена, M-01…M-08 не выполнены.
4. Карта: нет GPU-пути загрузки тайлов, производительность не измерена.
5. Бюджеты PERF-01…PERF-05 не измерялись (нет reference-машины и запуска).

## Следующий этап

1. На машине с JDK 21 и сетью: `gradle wrapper`, подставить реальный Loom,
   `./gradlew clean build selfCheck`, зафиксировать exit codes и версии.
2. Проверить `HudHook` по исходникам 1.21.10; при необходимости заменить только его.
3. Собрать `silkvis-media-helper.exe`, заполнить `manifest.properties` (SHA-256),
   прогнать M-01…M-08 и матрицу источников.
4. Добавить GPU-путь minimap (NativeImage → texture) и замерить PERF-02/03.
5. V4: загрузчик темы (PNG/JPEG + HTTP-лимиты), custom sky, тотем; V5: production JAR.

## Инварианты, покрытые self-check'ами (при запуске на JDK)

I1 (один владелец действия — арбитр P2, здесь проверяется отсутствие запуска задач),
I2 (stale generation), I3 (disable отменяет), I5 (unknown ≠ success), I7 (bounded
очереди/кэши), I8 (импорт/загрузка не запускают команд), I10 (в P1 нет задач).
