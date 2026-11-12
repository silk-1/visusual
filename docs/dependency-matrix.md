# Dependency matrix (BUILD-03, DEV-04)

Все координаты закреплены в `gradle.properties`. Динамические версии (`+`, `latest.*`)
запрещены.

**Что проверено 2026-11-12:** существование версий подтверждено по метаданным Fabric
(`meta.fabricmc.net`), POM Loom и `maven-metadata.xml` Fabric API. **Что не проверено:**
фактическая загрузка/разрешение зависимостей — в авторской среде нет JDK и нет доступа
к `maven.fabricmc.net`. Поэтому статус — `PINNED_FROM_METADATA`, а не `VERIFIED`.

| # | Компонент | Координата / версия | Назначение | Лицензия | Влияние на размер | Fallback при недоступности | Статус |
|---|-----------|---------------------|------------|----------|-------------------|----------------------------|--------|
| 0 | JVM сборки | JDK 25 (`winget install EclipseAdoptium.Temurin.25.JDK`) | запуск Gradle | GPLv2+CE | — (вне артефакта) | JDK 21 | REQUIRED_BY_LOOM (подтверждено ошибкой сборки) |
| 1 | Gradle Wrapper | `gradle-9.8.2-bin.zip` + закоммиченный `gradle-wrapper.jar` из шаблона Fabric 1.21.10 | сборка | Apache-2.0 | ~130 КБ в репозитории | 8.14.x / 9.x, совместимые с Loom | PINNED (дистрибутив не скачивался) |
| 2 | Fabric Loom | `1.19.4` | плагин сборки | LGPL-3.0 | — (dev) | предыдущая стабильная Loom (`1.19.3`) | PINNED_FROM_METADATA (POM `1.19.4` получен) |
| 3 | Minecraft | `com.mojang:minecraft:1.21.10` | целевая платформа | Mojang EULA (не распространяется) | — | нет: версия зафиксирована ТЗ | PINNED (версия есть в Fabric meta) |
| 4 | Yarn mappings | `net.fabricmc:yarn:1.21.10+build.3:v2` | имена | CC0-1.0 | — | `build.2` / `build.1` для 1.21.10 | PINNED_FROM_METADATA + сверка имён по `FabricMC/yarn@1.21.10` |
| 5 | Fabric Loader | `net.fabricmc:fabric-loader:0.20.1` | загрузчик | Apache-2.0 | ~1.5 MiB (в поставке пользователя) | диапазон в `fabric.mod.json`: `>=0.20.0 <0.21.0` | PINNED_FROM_METADATA (0.20.1 = stable) |
| 6 | Fabric API | `net.fabricmc.fabric-api:0.138.4+1.21.10` | keybind/lifecycle/HUD hooks | Apache-2.0 | ~2 MiB | модули Fabric API по отдельности (key-binding, lifecycle-events, rendering-v1) | PINNED_FROM_METADATA + сверка API по исходникам ветки `1.21.10` |
| 7 | JDK | Temurin 21 / любой JDK 21 | toolchain | GPL-2.0+CE | — | JDK 21 обязателен (`release=21`) | локально **отсутствует** |
| 8 | JUnit | не подключён | тесты | EPL-2.0 | — | self-checks на чистом JDK (`./gradlew selfCheck`) | осознанно отложен |
| 9 | C++/WinRT helper | `native/media-helper` (исходник, бинарник не собран) | GSMTC-мост | исходник в репозитории | ~150–300 KiB (план) | `ProviderStatus.ERROR: helper_missing`, остальной мод работает | **BLOCKED** (нет MSVC) |
| 10 | Sodium / Iris / Baritone | не компилируются, только probe | необязательные интеграции | их собственные | 0 | capability discovery через `isModLoaded` | IMPLEMENTED_NOT_TESTED |

## Смежные риски

1. **Loom `1.19.4`** — версия подтверждена POM-файлом и gradle-plugin-marker'ом
   (`fabric-loom:1.19.4` → `net.fabricmc:fabric-loom:1.19.4`). Связка «Loom 1.19.4 +
   Gradle 9.8.2» взята из официального шаблона Fabric для 1.21.10, но реальным
   прогоном здесь не проверена (см. ADR-0001).
2. `mappings` — только Yarn. Смешивание с Mojang names запрещено (BUILD-03).
3. Fabric API — единственная runtime-зависимость мода; собственного JSON/HTTP/GUI-фреймворка
   не добавлено.
4. Нативный helper — единственный допустимый native-компонент (MEDIA-04). Он не
   скачивается из сети и не требует MSVC у пользователя (только у сборщика релиза).

## Сверенные API 1.21.10 (по `FabricMC/yarn@1.21.10` и исходникам Fabric API)

| Вызов в коде | Подтверждено |
|---|---|
| `HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, Identifier, HudElement)` | да (ветка `1.21.10`) |
| `HudElement.render(DrawContext, RenderTickCounter)` | да |
| `DrawContext.fill`, `drawText(TextRenderer,String,int,int,int,boolean)`, `drawTextWithShadow`, `enableScissor(IIII)`, `disableScissor()` | да |
| `Screen.render(DrawContext,int,int,float)`, `init()`, `close()`, `shouldPause()`, `mouseScrolled(DDDD)` | да |
| `mouseClicked(Click,boolean)`, `mouseDragged(Click,DD)`, `mouseReleased(Click)`, `keyPressed(KeyInput)`, `charTyped(CharInput)` | да (новая input-модель 1.21.10) |
| `Click.x()/y()/button()`, `KeyInput.key()/scancode()/modifiers()`, `CharInput.asString()` | да |
| `KeyBinding.Category.create(Identifier)` + `new KeyBinding(id, InputUtil.Type.KEYSYM, code, category)` | да |
| `KeyBindingHelper.registerKeyBinding` | да |
| `WorldView.getTopY(Heightmap.Type,int,int)`, `getBottomY()`, `isChunkLoaded(int,int)` | да |
| `AbstractBlock.AbstractBlockState.getMapColor(BlockView,BlockPos)`, `MapColor.getRenderColor(MapColor.Brightness)` | да (класс `net.minecraft.block.MapColor`) |
| `MinecraftClient.textRenderer`, `getDebugHud().shouldShowDebugHud()`, `options.hudHidden`, `getCurrentServerEntry()` | да |
| `MinecraftServer.getSaveProperties().getLevelName()` | да |
| `Identifier.of(...)`, `Text.translatable(...).getString()` | да |

## Что должно быть выполнено до статуса VERIFIED

```text
./gradlew --version
./gradlew dependencies --configuration modImplementation
./gradlew clean build          # exit code + размер JAR
./gradlew selfCheck            # core checks без Minecraft
./gradlew runClient            # отдельный результат, не равен сборке
```

Результаты внести в `docs/status-report.md` с датой и версиями.
