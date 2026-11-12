# ADR-0001 — Build inputs, mappings and version pinning

- Статус: предложено, требует проверки (BUILD-03)
- Дата: 2026-11-12

## Контекст

ТЗ фиксирует Minecraft 1.21.10, Fabric, Java 21 и запрещает динамические версии.
Авторская среда (sandbox) не имеет JDK и доступа к `maven.fabricmc.net` / Maven Central,
поэтому реально разрешаемые артефакты проверить невозможно.

## Решение

1. Используются **Yarn** mappings (одна система, без смешивания с Mojang names).
2. Все версии вынесены в `gradle.properties`. На 2026-11-12 они закреплены по
   метаданным Fabric: `minecraft_version=1.21.10`, `yarn_mappings=1.21.10+build.3`,
   `loader_version=0.20.1`, `fabric_api_version=0.116.18+1.21.10`, `loom_version=1.19.4`.
   Запрещены `+` (как динамика), `latest.release`, SNAPSHOT-зависимости проекта.
3. `docs/dependency-matrix.md` содержит статус каждой строки. Существование версий
   подтверждено, но фактическое скачивание/разрешение зависимостей в авторской среде
   выполнить нельзя, поэтому статус — `PINNED_FROM_METADATA`, а не `VERIFIED`.
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
