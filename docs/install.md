# Установка SilkVis в Minecraft

SilkVis — **клиентский** мод. Серверу он не нужен, на серверные правила не влияет,
никого не банит и ничего не выпрашивает у аккаунта.

## 1. Что должно быть на машине

| Компонент | Требование | Где взять |
|---|---|---|
| Minecraft Java Edition | **ровно 1.21.10** (другая версия не подойдёт: `"minecraft": "=1.21.10"`) | лаунчер Mojang |
| Java | **21 или новее** (у тебя уже Temurin 25 — годится) | `winget install EclipseAdoptium.Temurin.25.JDK` |
| Fabric Loader | **0.19.0 или новее**, рекомендуется самый свежий | <https://fabricmc.net/use/installer/> |
| **Fabric API** | любой билд **для 1.21.10** | <https://modrinth.com/mod/fabric-api/versions?g=1.21.10> |
| Сам мод | `silkvis-<версия>+mc1.21.10.jar` | `build\libs\` после `build.bat` |

> **Fabric API — обязателен.** Это не «опциональная библиотека»: мод регистрирует через
> него HUD-элемент, keybind, тики клиента и события входа в мир. Без него Loader
> останавливает запуск с `Install fabric-api, version ... or later`.

## 2. Порядок установки

1. **Поставить Fabric Loader** для 1.21.10 — запустить installer, выбрать
   `Minecraft version: 1.21.10`, `Loader version: последняя`, `Install`.
   Installer создаст профиль вида `fabric-loader-0.20.1-1.21.10`.
2. **Открыть папку модов**: `Win+R` → `%appdata%\.minecraft\mods` → Enter.
   (Если папки нет — создать.)
3. **Скопировать туда два jar-а**:
   - `fabric-api-<версия>+1.21.10.jar` (скачанный с Modrinth);
   - `silkvis-0.1.1-p0+mc1.21.10.jar` из `F:\silkvis\build\libs\`.
   Файлы `*-sources.jar` в игру **не** кладутся.
4. В лаунчере выбрать профиль `fabric-loader-…-1.21.10` и запустить игру.
5. Проверить мод в игре (см. ниже).

Мод несовместим с OptiFine; Sodium/Iris — только как «соседи» (мод их замечает, но не
требует).

## 3. Как проверить, что мод загрузился

| Действие | Ожидаемое поведение |
|---|---|
| `Right Shift` (по умолчанию) | открывается меню SilkVis (тема Graphite/Turquoise) |
| `F1` в игре | оверлей SilkVis скрывается вместе с ванильным HUD, повторное `F1` возвращает |
| сразу после входа в мир | **ничего лишнего не нарисовано** — все модули по умолчанию выключены (требование ТЗ) |
| `run\logs\latest.log` или `.minecraft\logs\latest.log` | есть строка про загрузку `silkvis` |

Если игра запустилась, но мод «молчит» — сначала проверь, что выбран fabric-профиль, а
не ванильный.

## 4. Диагностика: `Incompatible mods found`

Реальный случай (первый запуск у автора ТЗ, 2026-11-12):

```text
- Install fabric-api, version 0.138.4 or later.
- Replace mod 'Fabric Loader' 0.19.2 with any version between 0.20.0 (inclusive) and 0.21.0 (exclusive).
```

Причина — две вещи сразу: **не было Fabric API** и **загрузчик был старее, чем разрешал
`fabric.mod.json`**. Узкое окно `>=0.20.0 <0.21.0` было ошибкой метаданных, а не
требованием кода: мод вызывает только `FabricLoader#getInstance`, `isModLoaded`,
`getGameDir`, `getConfigDir`, которые не менялись с 0.16. С версии `0.1.1-p0` диапазон
— `>=0.19.0` без верхней границы, `fabric-api` — `>=0.116.0` (любой билд для 1.21.10).

| Сообщение Loader | Что делать |
|---|---|
| `Install fabric-api, version ... or later` | скачать Fabric API для 1.21.10 и положить в `mods` |
| `Replace mod 'Fabric Loader' ... with any version between ...` | переустановить Loader свежим installer-ом, выбрать новый профиль |
| `requires version 1.21.10 of minecraft, but only ... is present` | запущена другая версия игры — выбрать профиль 1.21.10 |
| `requires Java 21` / `UnsupportedClassVersionError` | взять JDK 21+ и указать его лаунчеру (`Установки → Java`) |
| мод есть в списке, но меню не открывается | другой мод занял `Right Shift`; сменить клавишу в настройках управления |

## 5. Проверка мода без основной установки

`F:\silkvis> .\build.bat runClient` — поднимает клиент с модом в отдельной папке
`F:\silkvis\run` (чистый профиль, твои `.minecraft` и другие моды не затрагиваются).
Это самый быстрый способ посмотреть HUD и меню до копирования jar-ов.
