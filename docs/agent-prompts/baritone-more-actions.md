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

## Что добавить (сверь каждую строку по рецепту выше)

Команды Baritone, которых в меню пока нет:

| Команда | Что делает | Предлагаемый `Arg` |
|---|---|---|
| `render <on/off>` | вкл/выкл отрисовку целей Baritone | `NONE` (две плитки: `render on`, `render off`) |
| `path` | начать движение по уже заданной цели | `NONE` |
| `forcecancel` | жёсткая отмена зависшего процесса | `NONE` |
| `wp save user <имя>` | сохранить точку | `TEXT` (имя) |
| `wp goto <имя>` | идти к точке по имени | `TEXT` (имя) |
| `wp list` | список точек | `NONE` |
| `come` | идти к игроку, который вызвал | `NONE` |
| `click` | взаимодействовать с блоком под прицелом | `NONE` |
| `axis` / `highway` | тоннель-магистраль | `NUMBERS` (уточни порядок аргументов!) |
| `eta` | сколько осталось идти | `NONE` |
| `proc` | какой процесс работает | `NONE` |
| `version` | версия Baritone в чат | `NONE` |
| `set <настройка> <значение>` | правка настройки Baritone | `TEXT` или отдельная форма |
| `blacklist <блок>` | запретить Baritone трогать блок | `SINGLE_BLOCK` |
| `sel undo` / `sel walls <блок>` / `sel shell <блок>` / `sel contract` / `sel expand` | расширенные операции с выделением | `NONE` / `SINGLE_BLOCK` |
| `litematica <файл>` | сборка по litematica-схеме | `TEXT` |
| `repack` / `save` / `gc` / `help` | служебные | `NONE` |

Порядок работы: **одна плитка = одна строка каталога + два языковых ключа (EN и RU) +
одна строка в чеке**. Не добавляй новых `Arg`-типов без нужды: если форма ввода
уникальная (например, «две настройки через запятую»), сначала попробуй выразить её через
существующие типы, и только потом расширяй `Arg` — расширение типа требует правок в
экране, то есть выходит за границы этой задачи.

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
