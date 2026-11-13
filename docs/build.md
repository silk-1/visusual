# Сборка и запуск

## Требования

- **JDK 25** (`JAVA_HOME` на JDK 25). Это требование самого Loom 1.19.x: сборка падает
  с `Dependency requires at least JVM runtime version 25`, если Gradle запущен на JDK 21.
  При этом мод по-прежнему собирается в байткод Java 21 (`options.release = 21`) и
  работает на Java 21 у игроков — ровно та же схема, что в официальном шаблоне Fabric
  для 1.21.10 (`.github/workflows/build.yml`, `java-version: '25'`).
- Доступ к `maven.fabricmc.net`, `repo1.maven.org` и `libraries.minecraft.net`.
- Windows x64 — основная ОС для приёмки клиента; ядро Java от ОС не зависит.

## Wrapper

Wrapper **закоммичен целиком**: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`
(взят из официального шаблона `FabricMC/fabric-example-mod@1.21.10`) и
`gradle/wrapper/gradle-wrapper.properties` с `distributionUrl` на Gradle **9.8.2**.

Поэтому **локальный Gradle устанавливать не нужно**: `gradlew.bat` сам скачает
дистрибутив при первом запуске. Нужен только JDK 25.

Если скачивание дистрибутива блокируется сетью — это единственное место, где нужна
ручная правка: заменить `distributionUrl` на локальный zip или на 8.14.x/9.x,
совместимый с Loom.

## Версии

Все версии — в `gradle.properties`, placeholder'ов больше нет:

```properties
mod_version=0.1.2-p0
minecraft_version=1.21.10
yarn_mappings=1.21.10+build.3
loader_version=0.20.1
fabric_api_version=0.138.4+1.21.10
loom_version=1.19.4
```

`1.19.4`, `0.20.1` и `0.138.4+1.21.10` — те же версии, что использует официальный
шаблон Fabric для 1.21.10 (`FabricMC/fabric-example-mod@1.21.10`); для Loom
подтверждены и метаданные, и POM, и gradle-plugin-marker `fabric-loom:1.19.4`.
См. `docs/dependency-matrix.md`.

Важно: версии в `gradle.properties` — это версии **сборки**. Требования к установке у
игрока мягче и заданы в `src/main/resources/fabric.mod.json`
(`fabricloader >=0.19.0`, `fabric-api >=0.116.0`, `minecraft =1.21.10`) — см.
`docs/install.md`.

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
`src/test/java`. Задача `build` теперь **зависит** от `selfCheck`, то есть зелёный
`gradlew build` означает и компиляцию, и пройденные проверки. JUnit не используется
намеренно, поэтому у задачи `test` отключён `failOnNoDiscoveredTests`. Он проверяет конфиг/атомарную запись, layout/clamp, тайлы/кэш/квитанцию,
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
