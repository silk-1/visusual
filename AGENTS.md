# AGENTS.md — инструкция для ИИ-агента (Cursor / Claude Code / любой другой)

Этот файл — единственная точка входа для агента. Прочитай его целиком перед первым
изменением кода. Всё, что ниже, — результат реальных проверок, а не догадки.

---

## 1. Что за проект

Клиентский мод **SilkVis** для Minecraft Java **1.21.10** (Fabric), mod id `silkvis`,
Java 21, без серверной части. Полное ТЗ — файл `promt` в корне (русский,
«SILKVIS — ТЕХНИЧЕСКОЕ ЗАДАНИЕ v2.0»). ТЗ — источник истины; этот файл только
фиксирует текущее состояние и правила игры.

Порядок работ строго последовательный: **P0 → P1 → P2**. Вкладка «Автоматизация» —
только «Запланировано», без реализации.

---

## 2. Жёсткие правила (нарушение = работа не принимается)

1. **Никогда не выдумывай версии зависимостей.** Версии живут только в
   `gradle.properties`. Если версия не резолвится — правь `gradle.properties`,
   а не строку в `build.gradle` (там `version "${loom_version}"`).
2. **Нельзя менять `loom_version` на `1.10.5`, `1.11-SNAPSHOT` или что-либо
   «на угад».** Рабочая пара для 1.21.10: Loom `1.19.4` + Gradle `9.8.2`
   (взято из официального шаблона `FabricMC/fabric-example-mod@1.21.10`).
3. **Ничего не помечать как протестированное без реального прогона.**
   Статусы в `docs/requirements-matrix.md` меняются только после выполнения команды,
   лог которой можно показать.
4. **Не добавлять зависимости.** Никаких JUnit, Jackson, Guava, HTTP-клиентов,
   Kotlin. Разрешены: JDK, Minecraft, Fabric Loader, Fabric API.
5. **Не добавлять mixins.** Если миксин реально нужен — сначала ADR в `docs/adr/`,
   потом код. Сейчас mixin-конфига нет намеренно (см. ADR-0002).
6. **Никакой серверной части.** `environment: "client"` в `fabric.mod.json`,
   ни одного server entrypoint, ни одного пакета между игроками.
7. **Всё выключено по умолчанию.** Любая новая функция при первом запуске не должна
   рисовать, качать или писать на диск.
8. **RU/EN обязательны.** Любой новый пользовательский текст — одновременно в
   `assets/silkvis/lang/en_us.json` и `ru_ru.json`, паритет ключей проверяется.
9. **Java-исходники — только ASCII** (русский текст живёт в lang-файлах и `docs/`).
10. **Один слой адаптера к версии игры.** Всё, что рисуется в мире/HUD, идёт через
    `render/HudHook.java`, `render/VanillaPainter.java`, `client/ClientTileSampler.java`.
    Остальной код не должен импортировать `net.minecraft.client.*` напрямую.

---

## 3. Текущее состояние (на 2026-11-12)

| Что | Значение |
|---|---|
| `main` | `b9dd30b` (merge PR #2) |
| Файлов `.java` | 104 (96 main + 8 test) |
| Синтаксис | `104/104` без ошибок (java-parser 2.x, ANTLR) |
| **Компиляция** | **ни разу не выполнялась** (в среде агента нет JDK и доступа к Fabric Maven) |
| Сборка | **ни разу не проходила** — это блокер №1 |
| Запуск клиента | не выполнялся |
| Gradle Wrapper | закоммичен (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`), Gradle `9.8.2` |
| JVM для сборки | **JDK 25** (Loom 1.19.x); мод собирается в байткод Java 21 |

Закреплённые версии (`gradle.properties`):

```properties
minecraft_version=1.21.10
yarn_mappings=1.21.10+build.3
loader_version=0.20.1
fabric_api_version=0.138.4+1.21.10
loom_version=1.19.4
```

---

## 4. Блокер №1: билд-гейт. Сделай это первым

Цель — получить `BUILD SUCCESSFUL` и зелёный `selfCheck`. Пока этого нет, новую
функциональность не пишем.

```bat
:: нужен JDK 25 (требование Loom 1.19.x), Gradle ставить НЕ надо (wrapper в репозитории)
java -version
gradlew.bat clean build
gradlew.bat selfCheck
gradlew.bat runClient
```

Порядок действий при падении:

1. Читай **первый** блок `* What went wrong:` — остальные ошибки обычно его следствия.
2. Классификация:
   - `Could not resolve net.fabricmc:fabric-loom:...` → проблема в `gradle.properties`
     (`loom_version`) или в `pluginManagement.repositories` (`settings.gradle`).
   - `Dependency requires at least JVM runtime version 25` → Gradle запущен на старом
     JDK. Нужен JDK 25 (см. `docs/build.md`), версию Loom не меняй.
   - `Unsupported class file major version` / `requires Gradle X` → несовместимость
     Loom/Gradle: меняй только `distributionUrl` в
     `gradle/wrapper/gradle-wrapper.properties` (8.14.x → 9.8.2), Loom не трогай.
   - `cannot find symbol` в `silkvis/...` с `net.minecraft.*` типами → это **реальная**
     несовместимость API. Сверься с Yarn-исходниками (см. §5), не угадывай.
   - `The following Kotlin...`/`unresolved reference` → обычно неверный Yarn build;
     версия должна быть `1.21.10+build.3`.
3. После каждого исправления — снова `gradlew.bat clean build`, не переходи дальше
   с красным билдом.

Когда гейт зелёный: обнови `docs/status-report.md` (что реально прогнано, с логом),
`docs/known-limitations.md` (пункт 1 «сборка не проверена» удаляется только теперь)
и статусы в матрице.

---

## 5. Уже сверенные API 1.21.10 — не переизобретай

Проверено по `FabricMC/yarn@1.21.10` и исходникам Fabric API ветки `1.21.10`.
**Эти вызовы трогать не нужно**, они подтверждены:

- HUD: `HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier, HudElement)`,
  `HudElement.render(DrawContext, RenderTickCounter)`. Старых `HudLayerRegistrationCallback`
  и `IdentifiedLayer` в 1.21.10 **нет** — не возвращай их.
- Ввод экранов (новая модель): `mouseClicked(Click, boolean)`, `mouseDragged(Click, double, double)`,
  `mouseReleased(Click)`, `keyPressed(KeyInput)`, `charTyped(CharInput)`;
  аксессоры `Click.x()/y()/button()`, `KeyInput.key()/scancode()/modifiers()`,
  `CharInput.asString()`. Старые `(double,double,int)` / `(int,int,int)` / `(char,int)`
  **не переопределяют ничего** и молча не компилируются.
- `DrawContext`: `fill(int,int,int,int,int)`, `drawText(TextRenderer,String,int,int,int,boolean)`,
  `drawTextWithShadow(...)`, `drawTexture(RenderPipeline,Identifier,int,int,float,float,int,int,int,int)`,
  `enableScissor(int,int,int,int)` / `disableScissor()`.
- Кейбинды: `new KeyBinding(id, InputUtil.Type.KEYSYM, code, KeyBinding.Category.create(Identifier.of(...)))`
  + `KeyBindingHelper.registerKeyBinding(...)`. Строковая категория больше не принимается.
- Мир: `WorldView.getTopY(Heightmap.Type,int,int)`, `getBottomY()`, `isChunkLoaded(int,int)`;
  `AbstractBlock.AbstractBlockState.getMapColor(BlockView,BlockPos)`;
  `MapColor.getRenderColor(MapColor.Brightness)`; класс `MapColor` — в `net.minecraft.block`.
- Клиент: `MinecraftClient.textRenderer`, `getDebugHud().shouldShowDebugHud()`,
  `options.hudHidden`, `getCurrentServerEntry()`;
  `MinecraftServer.getSaveProperties().getLevelName()`.

Как проверять новые вызовы, если JDK нет: `git clone`/`codeload` репозитория
`FabricMC/yarn` ветки `1.21.10` и поиск по `mappings/**.mapping` (там настоящие
сигнатуры `METHOD имя (дескриптор)`), плюс исходники `FabricMC/fabric@1.21.10`.

---

## 6. Очередь задач после зелёного билда (в этом порядке)

**P1-A. Экран карты + CRUD путевых точек** — MAP-01…03, UI-04.
Файлы: `map/TileCache.java`, `map/WaypointStore.java`, `client/` (новый `MapScreen`),
`ui/widget/*`. Критерий: точка создаётся/переименовывается/удаляется, переживает
перезаход, координаты и измерение сохраняются в JSON атомарно (tmp + `ATOMIC_MOVE`).

**P1-B. ASSET-01…03: загрузчик темы** — `visual/ThemeTokens.java`, JSON-схема темы,
валидатор с понятной ошибкой при битом файле и полный fallback на встроенную
Graphite/Turquoise. Критерий: битый JSON не роняет клиент, в лог идёт причина.

**P1-C. HUD-05: редактирование оверлея** — `client/HudEditorScreen.java`, `ui/hud/*`.
Критерий: перетаскивание, снапы, сохранение позиции, откат по Esc.

**P1-D. UI-05 остатки**: toast-уведомления, модальные подтверждения, file picker
(через нативный диалог ОС или честный отказ, без выдуманных API).

Правило: одна задача = один PR, зелёный `build` + `selfCheck`, обновлённые доки.

---

## 7. Definition of Done для любой задачи

1. `gradlew.bat clean build` — зелёный.
2. `gradlew.bat selfCheck` — зелёный (или добавлены новые проверки в
   `src/test/java/silkvis/verify/*` для новой логики, без внешних библиотек).
3. Паритет lang-ключей RU/EN, все новые строки переведены.
4. Java-исходники ASCII.
5. Обновлены: `docs/requirements-matrix.md` (ID → файл → чем проверено → статус),
   `docs/status-report.md` (что реально прогонялось), при смене подхода — ADR.
6. Никаких новых зависимостей, миксинов и серверного кода.

---

## 8. Как отчитываться (шаблон ответа)

```
Сделано: <1-3 пункта, по факту>
Проверено: <команда + результат, напр. "gradlew.bat clean build — BUILD SUCCESSFUL">
Не проверено: <честно, что осталось>
Риски: <что может сломаться и почему>
Дальше: <следующий шаг>
```

---

## 9. Где что лежит

| Путь | Что |
|---|---|
| `promt` | полное ТЗ (источник истины) |
| `src/main/java/silkvis/core` | конфиг, журнал, события, инварианты остановки |
| `src/main/java/silkvis/config[/json]` | модель настроек и сериализация |
| `src/main/java/silkvis/ui` | Painter (абстракция отрисовки), тема, виджеты |
| `src/main/java/silkvis/map` | тайлы, кэш, путевые точки, identity мира |
| `src/main/java/silkvis/media` | IPC-контракт с нативным helper'ом (не собран) |
| `src/main/java/silkvis/client` | вход в клиент, HUD-оверлей, экраны, сэмплер тайлов |
| `src/main/java/silkvis/render` | HudHook + VanillaPainter — единственный адаптер к версии игры |
| `src/main/java/silkvis/automation` | каталог запланированных функций (ничего не исполняется) |
| `src/test/java/silkvis/verify` | selfCheck без JUnit |
| `docs/` | матрица требований, отчёт, ограничения, ADR, build, dependency-matrix |
| `native/media-helper/` | эталонный C++ helper (собирается вручную на Windows) |

---

## 10. Чего делать нельзя (частые ошибки)

- Править версию плагина в `build.gradle` вместо `gradle.properties`.
- Ставить Loom `1.10.5`/`1.11-SNAPSHOT` «потому что так советуют».
- Возвращать `HudLayerRegistrationCallback`, `IdentifiedLayer`, старые сигнатуры
  ввода экранов — они в 1.21.10 не существуют.
- Добавлять `mixin`, `@Inject`, ASM — только через ADR и только если без этого никак.
- Писать «протестировано», не выполнив команду. Одна такая строка обесценивает
  весь отчёт.
- Добавлять фичи из P2/AUTO раньше, чем закрыт P1.
- Заявлять работу нативных медиа-функций: бинарник в репозиторий не входит,
  провайдер обязан возвращать `ERROR: helper_missing`.
