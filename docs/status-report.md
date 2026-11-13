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
| `node parse.js src` (java-parser 2.x, ANTLR-грамматика Java) | `parsed_ok=104 syntax_errors=0 total=104` — синтаксис всех 104 `.java` корректен |
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


---

## Раунд 2 — снятие версионных неизвестностей (2026-11-12)

Цель: убрать главный риск «не соберётся из-за выдуманных версий/сигнатур», не имея JDK.

### Выполненные проверки

| Проверка | Результат |
|---|---|
| `GET meta.fabricmc.net/v2/versions/yarn/1.21.10` | существует `1.21.10+build.3` (старшая) |
| `GET meta.fabricmc.net/v2/versions/loader/1.21.10` | `fabric-loader:0.20.1` помечен `stable: true` |
| `GET maven.fabricmc.net/.../fabric-loom/1.19.4/fabric-loom-1.19.4.pom` | POM получен, версия существует |
| `GET maven.fabricmc.net/.../fabric-api/maven-metadata.xml` | последняя сборка под 1.21.10 — `0.116.18+1.21.10` |
| `FabricMC/yarn@1.21.10` (tar via codeload) | сигнатуры `DrawContext`, `Screen`, `Click`, `KeyInput`, `CharInput`, `KeyBinding`, `WorldView`, `MapColor`, `ItemStack` и др. сверены |
| `FabricMC/fabric@1.21.10` (tar via codeload) | подтверждены `HudElementRegistry`/`HudElement`/`VanillaHudElements`, `KeyBindingHelper`, `ClientPlayConnectionEvents.JOIN` |

### Исправлено по итогам сверки

1. `render/HudHook.java` переписан на реальный API 1.21.10
   (`HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, ...)`);
   прежние `HudLayerRegistrationCallback`/`IdentifiedLayer` в этой версии отсутствуют.
2. `client/SilkVisScreen.java`, `client/HudEditorScreen.java`: новая модель ввода
   (`Click`, `KeyInput`, `CharInput`) вместо `(double,double,int)`/`(int,int,int)`/`(char,int)`.
3. `client/ClientTileSampler.java`: `MapColor` импортируется из `net.minecraft.block`.
4. `client/SilkVisClient.java`: `KeyBinding.Category.create(Identifier.of(...))` вместо
   строковой категории (обязательно с 1.21.10).
5. `gradle.properties` / `fabric.mod.json`: реальные версии вместо placeholder'ов.

### Что по-прежнему не подтверждено

Компиляция (`javac`), разрешение зависимостей Gradle, запуск клиента, скриншоты,
матрица MEDIA-*, GPU-путь карты, PERF-бюджеты. Ни один пункт не помечен как проверенный.


---

## Раунд 3 — первая зелёная сборка (2026-11-12)

### Выполнено на машине пользователя (Windows, JDK 25, `build.bat`)

| Команда | Результат |
|---|---|
| `gradlew.bat clean build` | `BUILD SUCCESSFUL in 2s` |
| `selfCheck` (входит в `build`) | `passed=60 failed=0` |
| артефакт | `build\libs\silkvis-0.1.0-p0+mc1.21.10.jar` (переименован в `0.1.1-p0` в раунде 4) |

Впервые получен воспроизводимый зелёный прогон: компиляция основного кода и тестов,
60 поведенческих проверок, JAR.

### Через что прошла сборка (реальные ошибки, а не догадки)

1. отсутствовал весь пакет `silkvis.config` (съеден правилом `.gitignore` `config/`);
2. Loom `1.19.4` требует **JVM 25** — сборка переведена на JDK 25, байткод остался Java 21;
3. 7 ошибок `javac` (`BooleanConsumer`, `getWorld`, `ServerInfo.port`, `Rect.inset`,
   `VisualPreset.id`, serialVersionUID);
4. `compileTestJava`: отсутствовала перегрузка `assertEquals` с допуском;
5. Gradle 9 валил `:test` без единого теста — JUnit у нас нет, проверки идут через `selfCheck`,
   и `build` теперь зависит от `selfCheck`;
6. `TileCodec.parseIdentity` бросал `IOException` без объявления;
7. `selfCheck`: 46/60 → найдено 4 настоящих бага (пустой `pinnedAppId` ломал загрузку конфига,
   `Tab` всегда возвращал фокус на первый виджет, босс-бар считался во всю ширину,
   PINNED показывал данные исчезнувшей сессии) и 10 неверных ожиданий в тестах;
8. после `passed=60 failed=0` сборка зависала: тест карты оставлял не-демон поток.

### Чего по-прежнему нет

`runClient`, скриншоты HUD/меню, матрица MEDIA-*, GPU-путь карты, бюджеты PERF.
Ничего из этого не помечено как проверенное.


---

## Раунд 4 — первая попытка запуска в игре (2026-11-12)

### Что произошло

Мод был собран (`0.1.0-p0`) и установлен в настоящий клиент. Игра **не стартовала**:

```text
Incompatible mods found!
 - Install fabric-api, version 0.138.4 or later.
 - Replace mod 'Fabric Loader' 0.19.2 with any version between 0.20.0 (inclusive) and 0.21.0 (exclusive).
   Mod 'SilkVis' (silkvis) 0.1.0-p0+mc1.21.10 requires any version between 0.20.0 (inclusive)
   and 0.21.0 (exclusive) of mod 'Fabric Loader', but only the wrong version is present: 0.19.2!
   Mod 'SilkVis' requires version 0.138.4 or later of fabric-api, which is missing!
```

Это не краш кода мода: Loader отклонил мод **до** загрузки классов, по метаданным.
То есть первый реальный прогон в игре вскрыл дефект, который не мог поймать ни `javac`,
ни `selfCheck` — неверно объявленные зависимости.

### Разбор

| Симптом | Настоящая причина | Решение |
|---|---|---|
| `requires ... 0.20.0 <0.21.0 ... but only 0.19.2` | жёсткая **верхняя** граница в `fabric.mod.json`, скопированная из dev-окружения (`loader_version=0.20.1`), а не из требований кода | `fabricloader: ">=0.19.0"`, верхней границы нет |
| `fabric-api ... is missing` | Fabric API не был установлен у игрока, а мод без него не работает (HUD, keybind, тики, события мира) | зависимость остаётся обязательной; описан порядок установки — `docs/install.md` |
| `fabric-api 0.138.4 or later` | нижняя граница взята равной версии из dev-сборки, хотя код не требует именно её | `fabric-api: ">=0.116.0"` — подходит любой билд для 1.21.10 |

Проверено по коду: из Loader API используются только `FabricLoader#getInstance`,
`isModLoaded(String)`, `getGameDir()`, `getConfigDir()` — все стабильны с 0.16, поэтому
нижняя граница 0.19.0 обоснована, а не «на глаз».

### Изменено

- `src/main/resources/fabric.mod.json` — диапазоны зависимостей: `>=0.19.0` и `>=0.116.0`,
  верхних границ нет (они отсекают будущие версии загрузчика и ничего не гарантируют);
- `gradle.properties` — `mod_version` → `0.1.1-p0`, чтобы новый jar отличался по имени;
- `docs/install.md` — новый документ: что нужно игроку, откуда скачать, порядок установки,
  проверка «мод загрузился», таблица диагностики `Incompatible mods found`;
- `README.md` — раздел «Установка в игру»;
- `docs/dependency-matrix.md` — обновлены строки 5 и 6 и добавлен риск 5 (верхние границы);
- `docs/known-limitations.md` — пункт про непроверенный запуск уточнён.

### Статус после раунда

Сборка (`clean build`, `selfCheck 60/60`) от этих правок не зависит — менялись ресурсы и
версия. **Запуск в игре по-прежнему не подтверждён**: следующий шаг — пересобрать
(`build.bat`), положить `silkvis-0.1.1-p0+mc1.21.10.jar` + `fabric-api-…+1.21.10.jar`
в `mods` и проверить `F1` / `Right Shift` / «по умолчанию ничего не нарисовано».
До этого ни один UI/HUD-статус не поднимается выше `IMPLEMENTED_NOT_TESTED`.


---

## Раунд 5 — первый успешный запуск в игре (2026-11-12)

### Что подтверждено скриншотами

Мод `0.1.1-p0` запущен в клиенте 1.21.10 с Fabric API. Игра стартовала, меню открылось
по **Right Shift**, в мире рисуется HUD-оверлей.

| Проверено | Наблюдение |
|---|---|
| запуск клиента | игра дошла до мира, мод загружен, краха нет |
| Right Shift | открывает окно SilkVis |
| тема | Graphite/Turquoise, акцент на выбранном элементе |
| вкладки | Визуал, HUD, Карта, Музыка, Тема, Интеграции, Настройки + «Автоматизация» с текстом «планируется на следующий этап, задачи не запускаются» |
| контролы | тумблеры (on/off с подписью), слайдер (перетаскивание, значение 4000↔5100), кнопки «Открыть редактор HUD», «Сбросить раскладку», пресет Balanced |
| локализация | весь интерфейс по-русски, кириллица рендерится корректно |
| HUD в мире | панель FPS в левом верхнем углу и миникарта-заглушка справа |
| размер окна | окно укладывается в 960×620, компактный режим (<700 scaled) включился сам |

### Дефекты, найденные по скриншотам (а не догадкам)

1. **Подсветка вкладки не следовала выбору.** Содержимое менялось, а акцентная рамка
   оставалась на вкладке, выбранной в момент последнего `init()` экрана (на скриншотах —
   «Музыка» при открытых «Настройках», «Карте», «HUD»). Причина: `ButtonWidget.Style`
   выставлялся только при сборке экрана. Исправлено: стили выводятся каждый кадр
   (`syncNavigationStyles`).
2. **FPS всегда `0 FPS 0.0 ms`.** `HudDataCollector.onFrame()` существовал, но ниоткуда не
   вызывался — счётчик кадров не работал. Исправлено: вызов из HUD-хука
   (`SilkVisClient.onFrame`), до ранних `return` рендерера.
3. **Поле поиска — пустой ящик без подсказки и без фокуса.** `TextInputWidget` получал
   `placeholderKey` и никогда его не рисовал, а `FocusManager.focus()` не вызывался по
   клику (фокус ставил только Tab). Исправлено: подсказка рисуется, клик по контролу
   передаёт ему фокус.
4. **Координаты не появились в HUD при включённом виджете.** Точная причина не доказана;
   найдена реальная хрупкость: `RateLimiter`/`FpsAggregator` считали начало отсчёта равным
   нулю, а `System.nanoTime()` имеет произвольную (в т.ч. отрицательную) точку старта —
   на такой машине 10-герцевые обновления координат не сработали бы никогда. Оба класса
   переведены на явный флаг первого вызова. Требуется повторная проверка в игре.

### Чего этот запуск не доказывает

`F1` (скрытие оверлея), сохранение настроек между перезапусками, редактор HUD,
перетаскивание виджетов, карта (тайлы, GPU), музыка/Now Playing, медиа-IPC,
точки назначения, перцентили PERF. Всё это остаётся `IMPLEMENTED_NOT_TESTED`
или `PLANNED` в матрице.

### Изменено

`client/SilkVisScreen.java`, `client/SilkVisClient.java`, `render/HudHook.java`,
`ui/widget/TextInputWidget.java`, `hud/RateLimiter.java`, `hud/FpsAggregator.java`, `mod_version` → `0.1.2-p0`
(чтобы исправленный jar отличался по имени), плюс матрица требований и
`docs/known-limitations.md`.
Синтаксис: 122/122 файлов разобраны парсером; **компиляции и повторного запуска не было**.
