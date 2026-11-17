# Промт для второго агента: новые действия Baritone в визуальном меню

Скопируй весь текст ниже в другой агент (Cursor / Claude Code / ChatGPT). Задача
отрезана так, чтобы два агента не перетирали друг друга: **каталог действий + языковые
ключи + один чек**. Файлы экрана (`SilkVisScreen.java`) и мост (`BaritoneBridge.java`)
трогать не нужно — они читают каталог.

---

## Кто ты и что нужно

Ты — второй разработчик проекта **SilkVis**: клиентский Fabric-мод для Minecraft Java
**1.21.10**, Java 21, mod id `silkvis`. Репозиторий: `https://github.com/silk-1/visusual`.

```bash
git clone https://github.com/silk-1/visusual.git
cd visusual
git checkout -b feat/baritone-more-actions
```

**Твоя задача**: расширить визуальное меню Baritone (вкладка BARITONE) новыми
действиями. Сейчас в каталоге 20 действий, у Baritone их заметно больше. Игрок не должен
помнить синтаксис — он должен видеть плитку, форму и кнопку.

## Как устроено (прочитай эти файлы, они небольшие)

| Файл | Роль |
|---|---|
| `src/main/java/silkvis/automation/baritone/BaritoneAction.java` | **каталог действий**: команда, раздел, иконка, тип аргумента, значения по умолчанию, сборка строки команды |
| `src/main/java/silkvis/automation/baritone/BaritoneBlock.java` | сетка блоков (руды + массовые блоки), первая запись = иконка |
| `src/main/java/silkvis/automation/baritone/BaritoneParams.java` | состояние одной формы: блоки, числа, текст, точка |
| `src/main/java/silkvis/client/SilkVisScreen.java` | рисует разделы, плитки и формы **из каталога**; ищи `buildBaritoneTab`, `addBaritone*` |
| `src/main/java/silkvis/client/baritone/BaritoneBridge.java` | отправляет строку в `ICommandManager.execute`; ничего не знает о каталоге |
| `src/test/java/silkvis/verify/AutomationChecks.java` | чеки: `baritoneMenuCatalogueBuildsRealCommands`, `baritoneLangKeysExist` |

Типы аргументов (`BaritoneAction.Arg`), которые уже поддерживает экран:

| `Arg` | Что показывает форма |
|---|---|
| `NONE` | только кнопку «Начать» |
| `POINT` | три поля координат (заполняются из прицела) + «Взять из прицела» |
| `SINGLE_BLOCK` | сетку блоков с одиночным выбором |
| `BLOCKS` | сетку блоков с мультивыбором |
| `BLOCKS_AND_COUNT` | то же + лимит |
| `NUMBER` | одно число с ±шагом (`numberStep`, `numberMin`, `numberMax`) |
| `NUMBERS` | три числа (сейчас использует только тоннель) |
| `TEXT` | поле текста (ник, имя файла) |

## Жёсткие правила проекта (нарушение = работа не принимается)

1. **Никаких новых зависимостей** и никаких миксинов. Только JDK, Minecraft, Fabric.
2. **Java-исходники — только ASCII.** Русский текст живёт в
   `src/main/resources/assets/silkvis/lang/ru_ru.json`, английский — в `en_us.json`.
   **Каждый новый ключ — сразу в оба файла**, иначе чек падает.
3. **Не выдумывай синтаксис команд.** Каждая команда сверяется с исходниками Baritone
   (рецепт ниже). Если форма аргументов неизвестна — либо проверь, либо не добавляй плитку.
4. **Плитка без обязательных аргументов не должна быть нажимаемой**: `command()` возвращает
   `null`, пока вход не заполнен, и тогда кнопка «Начать» выключена. Никогда не отправляй
   половинчатую команду.
5. **Всё выключено по умолчанию**: ни одна плитка не отправляет команду сама по себе.
6. Пользовательский текст — только через ключи `silkvis.baritone.*`, никаких литералов в
   экране.

## Рецепт: как узнать точный синтаксис команды

Исходники Baritone лежат в `cabaletta/baritone`, команды — в
`src/main/java/baritone/command/defaults/`. Каждая команда печатает свои формы в
`getLongDesc()`; это и есть источник истины:

```bash
curl -s -H "Accept: application/vnd.github.raw" \
  "https://api.github.com/repos/cabaletta/baritone/contents/src/main/java/baritone/command/defaults/RenderCommand.java" \
  | grep -o '">[^"]*"'
```

Если в описании форма не видна — читай `execute()`: `args.requireMin/requireExactly/hasExactly`
и `args.getAs(Class)` показывают, обязателен ли аргумент и в каком он порядке. Пример из
этой же кодовой базы: `thisway` вызывает `args.requireExactly(1)`, то есть **дистанция
обязательна**, а `mine` берёт число через `getAsOrDefault(Integer.class, 0)`, то есть
**ноль надо опускать, а не передавать**.

## Что добавить

Сверено с исходниками Baritone (`src/main/java/baritone/command/defaults/`) 2026-11-17.
Уже реализовано 20 плиток: `goto`, `goto <block>`, `thisway`, `tunnel`, `follow player`,
`surface`, `invert`, `mine`, `farm`, `pickup`, `sel pos1`, `sel pos2`, `sel clear`,
`sel set`, `build`, `explore`, `find`, `pause`, `resume`, `cancel`.

### P0 — дешёвые плитки: новых `Arg` не нужно

| Плитка | Команда (проверено) | `Arg` |
|---|---|---|
| Задать цель | `goal` — цель в текущей позиции; `goal <x> <y> <z>` / `goal <y>` / `goal <x> <z>` | `POINT` (и отдельная плитка «Сбросить цель» → `goal reset`) |
| Начать движение к цели | `path` | `NONE` |
| К ближайшей оси | `axis` (алиас `highway`), аргументов нет; высота — настройкой `axisHeight` | `NONE` |
| Процесс | `proc` | `NONE` |
| Сколько идти | `eta` | `NONE` |
| Версия Baritone | `version` | `NONE` |
| Справка Baritone | `help` | `NONE` |
| Жёсткая отмена | `forcecancel` | `NONE` |
| Починить чанки | `render` — «fixes glitched chunk rendering»; **не** переключатель отрисовки целей | `NONE` |
| Выполняется ли | `paused` | `NONE` |
| Перепаковать кэш | `repack` | `NONE` |
| Сборка мусора кэша | `gc` | `NONE` |
| Сохранить настройки Baritone | `saveall` | `NONE` |
| Перечитать настройки | `reloadall` | `NONE` |
| Взаимодействовать с блоком | `click` | `NONE` |
| Исключить ближайший блок | `blacklist` — аргументов нет, действует на ближайший блок текущей цели | `NONE` |
| Отменить действие выделения | `sel undo` (алиас `sel u`) | `NONE` |
| Выделение в воздух | `sel cleararea` (алиас `sel ca`) | `NONE` |
| Элитры: к цели | `elytra` (также `elytra reset`, `elytra supported`, `elytra repack`) | `NONE` |
| Litematica | `litematica` (первая схема) или `litematica <n>` | `NONE` / `NUMBER` |
| Точки: список | `wp list` (алиас `wp l`) | `NONE` |
| Точка: сохранить | `wp save` без аргументов — безымянная точка в текущей позиции | `NONE` |
| Точка: идти по имени | `wp goto <tag/name>` | `TEXT` |
| Домой | `home` — алиас `wp goto home` | `NONE` |
| Запомнить дом | `sethome` — алиас `wp save home` | `NONE` |

### P1 — нужен новый `Arg` (или комбинация существующих)

| Плитка | Команда (проверено) | Что нужно |
|---|---|---|
| Стены выделения | `sel walls <block>` (алиас `sel w`) | `SINGLE_BLOCK` — уже есть, плитка бесплатная |
| Оболочка выделения | `sel shell <block>` (алиас `sel shl`) | `SINGLE_BLOCK` — бесплатная |
| Шар / полый шар | `sel sphere <block>`, `sel hsphere <block>` | `SINGLE_BLOCK` — бесплатная |
| Цилиндр | `sel cylinder <block> [axis]` (`y` по умолчанию), `sel hcylinder <block> [axis]` | новый: блок + необязательная ось (`x`/`y`/`z`) |
| Замена блоков | `sel replace <blocks...> <with>` | новый: набор блоков + один блок |
| Копировать / вставить | `sel copy <x> <y> <z>`, `sel paste <x> <y> <z>` | `POINT` — бесплатная |
| Растянуть / сжать / сдвинуть | `sel expand <target> <direction> <blocks>`, `sel contract ...`, `sel shift ...` (`target` — какую цель выделения, `direction` — сторона, `blocks` — сколько) | новый: два выбора из списка + число. Сначала прочитай `SelCommand.execute`, чтобы узнать допустимые `target`/`direction` |
| Схематика в координаты | `build <file> <x> <y> <z>` | `TEXT` + `POINT` |
| Litematica по имени | `litematica <file> <x> <y> <z>` | `TEXT` (+ `POINT`) |
| Точка: инфо / удалить / очистить тег | `wp info <tag/name>`, `wp delete <tag/name>`, `wp clear <tag>` | `TEXT` |
| Восстановить точки | `wp restore <n>` | `NUMBER` |
| Цель на точку | `wp goal <tag/name>` | `TEXT` |
| Прийти к игроку | `come` — идти к тому, кто вызвал (в одиночке смысла нет) | `NONE`, но в меню ставить только с пояснением |
| Фильтр исследования | `explorefilter <path> [invert]` — путь к JSON | `TEXT` (и предупреждение в подсказке, что нужен файл) |

### P2 — это не плитка, а отдельная фича

* **`set <setting> <value>`** — самое ценное и самое большое: визуальный редактор
  настроек Baritone (`set list`, `set modified`, `set <name>`, `set <name> <value>`,
  `set toggle <name>`, `set reset <name>|all`). Это отдельный экран со списком настроек и
  типами (boolean / число / enum), а не одна плитка. Если берёшься — сначала ADR, как в
  `docs/adr/`, потом код: экран читает `set list`, но разбирать его вывод хрупко, честнее
  взять список настроек из самого Baritone через рефлексию к `Settings` — и это уже
  изменение моста, то есть зона первого агента. Начни с ADR и согласуй.
* **`sel` как интерактивный редактор** — расширения/сдвиги удобнее делать не командами, а
  перетаскиванием в мире; это отдельная тема, не каталог.
* **Автонастройка под игрока** («копать только то, что видно», «не ломать чужие постройки»)
  — это `set` + профили, тоже отдельно.

## Как именно добавить плитку

1. В `BaritoneAction` — новая константа в **конец своего раздела**, с командой, разделом,
   id иконки (это id предмета Minecraft, например `compass`) и типом аргумента:

```java
    WAYPOINT_GOTO("wp goto", Category.MOVE, "lodestone", Arg.TEXT),
```

2. В `BaritoneAction.command(...)` — только если форма аргументов не выражается текущими
   типами. В 90% случаев правок в `command()` не нужно: он уже собирает строку по `Arg`.
3. Языковые ключи в **оба** файла: `silkvis.baritone.action.waypoint_goto` (название) и
   `silkvis.baritone.action.waypoint_goto.note` (что делает, одним-двумя предложениями).
   Название — короткое, как на кнопке («Идти к точке»), пояснение — для новичка.
4. В `AutomationChecks.baritoneMenuCatalogueBuildsRealCommands` — если команда новой
   плитки имеет нестандартную форму, добавь точную проверку строки (см. примеры `mine`,
   `farm`, `tunnel` в этом методе). Общий цикл по `values()` подхватит новую плитку сам.
5. Прогони чек и сборку:

```powershell
.\build.bat selfCheck
.\build.bat
```

Ожидаемо: `failed=0`, затем `BUILD SUCCESSFUL`. Версия в `gradle.properties`
(`mod_version`) и запись в `docs/versions.md` — обязательны: по ним в игре видно, какая
сборка запущена (первая строка вкладки BARITONE).

## Чего НЕ делать

* Не менять `SilkVisScreen.java` и `BaritoneBridge.java` — это зона первого агента;
  конфликт в 1500-строчном файле обойдётся дороже любой новой плитки.
* Не вызывать типизированный API Baritone (`getMineProcess()`, `GoalBlock`,
  `follow(Predicate<Entity>)`) — только строки команд. Причина описана в
  `docs/adr/0013-baritone-integration.md`: типизированный API требует классов Minecraft
  из конкретной сборки Baritone и падает с `NoSuchMethodException` на чужой версии.
* Не добавлять `#` в команды: `#` — префикс чата, а `ICommandManager.execute` принимает
  команду без него.
* Не помечать в `docs/requirements-matrix.md` ничего как протестированное без реального
  прогона в игре.
