# Сборка и запуск

## Требования

- JDK 21 (`JAVA_HOME` на JDK 21, не 17/8).
- Доступ к `maven.fabricmc.net`, `repo1.maven.org` и `libraries.minecraft.net`.
- Windows x64 — основная ОС для приёмки клиента; ядро Java от ОС не зависит.

## Wrapper

Бинарный `gradle-wrapper.jar` в репозиторий не добавлен (бинарные артефакты в этой
среде не генерируются). Один раз на машине с Gradle:

```bash
gradle wrapper --gradle-version 9.0.0
```

`gradle/wrapper/gradle-wrapper.properties` уже содержит закреплённый
`distributionUrl` и `validateDistributionUrl=true`.

## Версии

Все версии — в `gradle.properties`. Перед первой сборкой заменить
`loom_version=1.11-FABRIC_UNVERIFIED` на реально существующую версию Loom для 1.21.10
и убедиться, что `yarn_mappings`, `loader_version`, `fabric_api_version` разрешаются.
См. `docs/dependency-matrix.md`.

## Команды

Windows:

```bat
gradlew.bat clean build
gradlew.bat selfCheck
gradlew.bat runClient
```

POSIX:

```bash
./gradlew clean build
./gradlew selfCheck
./gradlew runClient
```

`selfCheck` не требует Minecraft и не требует сети: это обычный `JavaExec` по
`src/test/java`. Он проверяет конфиг/атомарную запись, layout/clamp, тайлы/кэш/квитанцию,
IPC-парсер, timeline, выбор сессии, состояния команд назначения и инварианты остановки.

## Артефакты

- `build/libs/silkvis-<version>+mc1.21.10.jar` — remapped production JAR (BUILD-06).
- `build/libs/silkvis-<version>+mc1.21.10-sources.jar` — sources.
- Dev JAR из `run/` не является пользовательским релизом и не должен так называться.

## Нативный media helper

См. `native/media-helper/README.md`. Бинарник собирается на Windows с MSVC и
C++/WinRT, кладётся рядом с `manifest.properties` (SHA-256). В этой среде он **не
собран**, поэтому музыкальный провайдер при запуске честно сообщает
`ERROR: helper_missing` вместо фиктивных данных.
