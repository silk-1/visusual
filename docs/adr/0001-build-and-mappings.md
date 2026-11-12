# ADR-0001 — Build inputs, mappings and version pinning

- Статус: предложено, требует проверки (BUILD-03)
- Дата: 2026-11-12

## Контекст

ТЗ фиксирует Minecraft 1.21.10, Fabric, Java 21 и запрещает динамические версии.
Авторская среда (sandbox) не имеет JDK и доступа к `maven.fabricmc.net` / Maven Central,
поэтому реально разрешаемые артефакты проверить невозможно.

## Решение

1. Используются **Yarn** mappings (одна система, без смешивания с Mojang names).
2. Все версии вынесены в `gradle.properties` и помечены как кандидаты:
   `minecraft_version`, `yarn_mappings`, `loader_version`, `fabric_api_version`,
   `loom_version`. Запрещены `+`, `latest.release`, SNAPSHOT-зависимости проекта.
3. `docs/dependency-matrix.md` содержит статус каждой строки. Строка без проверки
   имеет статус `UNVERIFIED` и не считается принятой.
4. Toolchain — Java 21 (`JavaLanguageVersion.of(21)`), `options.release = 21`.
5. Мод клиентский: `fabric.mod.json` объявляет `environment: client` и точную версию
   Minecraft `=1.21.10`; dedicated server не загружает клиентские классы.

## Последствия

- Первый реальный `gradlew build` обязан быть выполнен на машине с доступом к Fabric
  Maven; до этого сборка не считается воспроизводимой.
- Если `loom_version` не разрешается, версия меняется на ближайшую стабильную
  **без** смены Minecraft/Loader/Fabric API, и это фиксируется здесь.
- Смена mappings — отдельный ADR с полной миграцией имён.

## Что проверить первым

```text
./gradlew --version
./gradlew dependencies --configuration modRuntimeClasspath
./gradlew clean build
```
