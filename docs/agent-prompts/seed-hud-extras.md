# Промт для второго агента: ядро «HUD extras» (чистый Java)

Скопируй весь текст ниже в другой агент (ChatGPT / Cursor / Claude Code). Задача
специально отрезана от остального кода: **новые файлы + одна строка регистрации**.
Так два агента не перетирают друг друга.

---

## Кто ты и что нужно

Ты — второй разработчик проекта **SilkVis**: клиентский Fabric-мод для Minecraft Java
**1.21.10**, Java 21, мод id `silkvis`.

Репозиторий: `https://github.com/silk-1/visusual`. Работай в **своей** ветке от `main`:

```bash
git clone https://github.com/silk-1/visusual.git
cd visusual
git checkout -b feat/hud-extras-core
```

**Твоя задача**: реализовать ядро дополнительных HUD-показателей — чистый Java, без
единого импорта Minecraft. Это вычислительная часть, которую первый агент потом
подключит к виджетам HUD.

## Что именно реализовать

Пакет `src/main/java/silkvis/hud/extra/`. Пять небольших классов, Java 21, без
внешних зависимостей.

### 1. `SlimeChunk.java`

```java
public static boolean isSlimeChunk(long worldSeed, int chunkX, int chunkZ)
```

Ванильная формула (Java Edition) — воспроизвести **в точности**, включая переполнение
32-битных `int` в x-слагаемых:

```java
long l = (long) (chunkX * chunkX * 4987142 + chunkX * 5947611
        + chunkZ * chunkZ * 4392871L + chunkZ * 389711) ^ 987234911L;
return new java.util.Random(l).nextInt(10) == 0;
```

Требования:
- Никакой математики «на глаз»: если считаешь, что ванильная строка выглядит странно —
  так и должно быть, `int`-переполнение здесь часть алгоритма;
- метод должен быть `static`, без состояния, детерминированным;
- добавь `public static long chunkIndexOf(int blockX, int blockZ)` (block → chunk,
  floor-деление, корректно для отрицательных координат).

### 2. `CoordinateConverter.java`

- `long toNether(long overworld)` → `overworld / 8`, `long toOverworld(long nether)` → `nether * 8`
  (деление — floor, не truncation; отрицательные координаты обязаны работать);
- `long clampToWorldBorder(long coord)` — границы мира ±29 999 984;
- `String format(int x, int y, int z)` → `"X 120 \\| Y 64 \\| Z -340"` (ровно один пробел
  вокруг `|`, без лишних пробелов у отрицательных чисел);
- `String portals(int x, int z)` → строка вида `"Ад: 15 7 · Верх: 120 56"`.

### 3. `SessionClock.java`

- хранит старт в монотонных наносекундах через **инжектируемый** интерфейс
  `silkvis.core.Clock` (он уже есть в проекте; не создавай свой);
- `void reset()`, `long elapsedMillis()`, `String format()` → `mm:ss`, а с часами
  `h:mm:ss`;
- отдельный `static String formatDuration(long millis)` для тестов.

### 4. `RealTimeClockFormat.java`

- `static String format(long epochMillis, ZoneId zone, boolean twentyFourHour)` →
  `23:05` или `11:05 PM`;
- `static String formatDate(long epochMillis, ZoneId zone)` → `12.11.2026` (день.месяц.год);
- никаких `System.currentTimeMillis()` внутри — время приходит аргументом.

### 5. `PingSampler.java`

- кольцевой буфер на N значений (по умолчанию 40), `void push(int ms)`,
  `int latest()`, `int median()`, `int p95()`;
- `median`/`p95` считаются по копии, буфер никогда не растёт;
- пустой буфер: `latest()` возвращает `-1`, остальные — `-1`.

## Обязательно: самопроверки

В проекте **нет JUnit — так и должно остаться**. Проверки — обычный Java в
`src/test/java/silkvis/verify/`.

1. Создай `src/test/java/silkvis/verify/HudExtraChecks.java` по образцу существующих
   `CoreChecks`/`UiChecks` (посмотри их: там `static void run(CheckReporter)`-подобный
   стиль; используй ровно тот же API, что у соседних файлов, ничего не изобретай).
2. Покрой минимум:
   - slime-chunk: детерминированность, разумная плотность на 5000 чанках (ожидаемо
     ≈10 % т.е. 400–600), корректный floor для отрицательных chunk-координат;
   - конвертер координат: `-129 / 8 == -17` (floor, не `-16`), `-16 * 8 == -128`,
     клэмп на границах ±29 999 984;
   - таймер: `formatDuration(0) == "00:00"`, `65_000 == "01:05"`,
     `3_725_000 == "1:02:05"`;
   - время: 12/24-часовой формат для фиксированного `Instant` и `ZoneId`;
   - ping: медиана нечётного и чётного набора, p95 на 100 значениях, пустой буфер.
3. Зарегистрируй проверки **одной строкой** в
   `src/test/java/silkvis/verify/SelfCheck.java` — рядом с остальными вызовами
   `…Checks.run(...)`. Больше в этом файле ничего не меняй.

## Границы: что трогать нельзя

- **Не менять**: `build.gradle`, `gradle.properties`, `fabric.mod.json`,
  `src/main/java/silkvis/client/**`, `ui/**`, `render/**`, `map/**`, `config/**`,
  `media/**`, `docs/**`.
- Не добавлять зависимости, JUnit, Lombok, аннотации, `var`-магию и рефлексию.
- Не менять `mod_version`.
- Если кажется, что нужно править что-то за пределами своих файлов — **не правь**,
  напиши это в отчёте.

## Стиль

- Комментарии и javadoc — по-английски, кратко, только про «почему», а не «что».
- Публичные методы с javadoc; классы `final`, конструкторы приватные где уместно.
- Никаких `System.out`, никаких статических изменяемых полей, кроме констант.
- Форматирование: 4 пробела, длина строки до 110, как в соседних файлах.

## Как проверить

Windows (у автора проекта):

```bat
build.bat selfCheck
```

Ожидаемо: `passed=<было+новые> failed=0`. Сборка падает, если хоть одна проверка красная.

Linux/macOS:

```bash
./gradlew selfCheck
```

Требуется JDK 25 для запуска Gradle (Loom 1.19.x); байткод остаётся Java 21.

## Что сдать

1. Ветка `feat/hud-extras-core` с коммитом `feat(hud): HUD extras core (seed/ping/clock)`.
2. Pull request в `main` с описанием: список файлов, число проверок, вывод
   `build.bat selfCheck` целиком.
3. Если что-то не сходится с реальностью (формула, ожидаемая плотность) — **не
   подгоняй тест под код**: напиши, что именно не сходится.

## Чего делать не нужно

- Не подключать классы к HUD-виджетам — это делает первый агент.
- Не писать локализацию, экраны, рендер.
- Не рефакторить чужие файлы «за компанию».
