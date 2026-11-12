# Dependency matrix (BUILD-03, DEV-04)

Все координаты закреплены в `gradle.properties`. Динамические версии (`+`, `latest.*`)
запрещены. **Статус `UNVERIFIED` означает, что артефакт не проверялся на разрешимость**:
в авторской среде нет JDK и нет доступа к `maven.fabricmc.net` / Maven Central.

| # | Компонент | Координата / версия | Назначение | Лицензия | Влияние на размер | Fallback при недоступности | Статус |
|---|-----------|---------------------|------------|----------|-------------------|----------------------------|--------|
| 1 | Gradle Wrapper | `gradle-9.0.0-bin.zip` (`gradle/wrapper/gradle-wrapper.properties`) | сборка | Apache-2.0 | — (dev) | любая 8.x/9.x с Loom, зафиксировать в ADR-0001 | UNVERIFIED |
| 2 | Fabric Loom | `1.11-FABRIC_UNVERIFIED` (placeholder) | плагин сборки | LGPL-3.0 | — (dev) | ближайшая стабильная Loom для 1.21.10 | **PLACEHOLDER, не разрешается** |
| 3 | Minecraft | `com.mojang:minecraft:1.21.10` | целевая платформа | Mojang EULA (не распространяется) | — | нет: версия зафиксирована ТЗ | UNVERIFIED |
| 4 | Yarn mappings | `net.fabricmc:yarn:1.21.10+build.1:v2` | имена | CC0-1.0 | — | другая сборка Yarn для 1.21.10, ADR | UNVERIFIED |
| 5 | Fabric Loader | `net.fabricmc:fabric-loader:0.17.2` | загрузчик | Apache-2.0 | ~1.5 MiB (в поставке пользователя) | диапазон в `fabric.mod.json`: `>=0.17.0 <0.18.0` | UNVERIFIED |
| 6 | Fabric API | `net.fabricmc.fabric-api:0.135.0+1.21.10` | keybind/lifecycle/HUD hooks | Apache-2.0 | ~2 MiB | модули Fabric API по отдельности (key-binding, lifecycle-events, rendering-v1) | UNVERIFIED |
| 7 | JDK | Temurin 21 / любой JDK 21 | toolchain | GPL-2.0+CE | — | JDK 21 обязателен (`release=21`) | локально **отсутствует** |
| 8 | JUnit | не подключён | тесты | EPL-2.0 | — | self-checks на чистом JDK (`./gradlew selfCheck`) | осознанно отложен |
| 9 | C++/WinRT helper | `native/media-helper` (исходник, бинарник не собран) | GSMTC-мост | исходник в репозитории | ~150–300 KiB (план) | `ProviderStatus.ERROR: helper_missing`, остальной мод работает | **BLOCKED** (нет MSVC) |
| 10 | Sodium / Iris / Baritone | не компилируются, только probe | необязательные интеграции | их собственные | 0 | capability discovery через `isModLoaded` | IMPLEMENTED_NOT_TESTED |

## Смежные риски

1. **Loom placeholder** — единственный заведомо нерабочий вход: сборка не начнётся, пока
   не подставлена реально существующая версия Loom. Это сделано намеренно: ТЗ запрещает
   выдумывать версии, а проверить их в sandbox невозможно.
2. `mappings` — только Yarn. Смешивание с Mojang names запрещено (BUILD-03).
3. Fabric API — единственная runtime-зависимость мода; собственного JSON/HTTP/GUI-фреймворка
   не добавлено.
4. Нативный helper — единственный допустимый native-компонент (MEDIA-04). Он не
   скачивается из сети и не требует MSVC у пользователя (только у сборщика релиза).

## Что должно быть выполнено до статуса VERIFIED

```text
./gradlew --version
./gradlew dependencies --configuration modImplementation
./gradlew clean build          # exit code + размер JAR
./gradlew selfCheck            # core checks без Minecraft
./gradlew runClient            # отдельный результат, не равен сборке
```

Результаты внести в `docs/status-report.md` с датой и версиями.
