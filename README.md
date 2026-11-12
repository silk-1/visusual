# SilkVis

Клиентский Fabric-мод для **Minecraft Java 1.21.10**: дизайн-система Graphite/Turquoise,
HUD с редактором, собственная карта, оверлей «сейчас играет», точки назначения и
запланированная (не исполняемая) автоматизация.

> **Статус: V0/V1-каркас, сборка и запуск не подтверждены.**
> В среде разработки не было JDK и доступа к `maven.fabricmc.net`, поэтому
> `gradlew build` и `runClient` ни разу не выполнялись, а версия Loom оставлена
> явным placeholder. См. `docs/status-report.md` и `docs/known-limitations.md`.
> Ни одна функция не выдаётся за проверенную: у каждой строки в
> `docs/requirements-matrix.md` стоит честный статус.

## Что уже реализовано кодом

| Область | Содержание |
|---------|------------|
| Ядро | реестр модулей с reason-кодами, состояния enabled/available/running/degraded/failed, `WorldSessionToken`, bounded CPU/IO пулы, классификация ошибок |
| Конфиг | собственный строгий JSON, `schemaVersion`, атомарная запись temp→rename, backup, карантин повреждённых файлов, debounce 500 ms, `OptionalLimit` |
| UI | токены темы (без hex в рендере), адаптивный layout (960×620, breakpoints 700/420), кнопки/тумблеры/слайдеры/text-input/scroll/focus/tooltips, поиск по модулям |
| HUD | 10 P1-виджетов, anchor+offset, safe-area clamp, snap/guides, политика видимости (F1/debug/chat/inventory/pause/spectator), редактор |
| Карта | identity (мир+измерение+режим+sliceY), тайлы 128×128 с LOD, CRC32-формат `.svt`, atomic store, LRU-кэш по байтам, квота без удаления маркеров, бюджет сэмплирования 1 ms/tick |
| Музыка | контракты провайдера, NDJSON IPC с лимитами, timeline с экстраполяцией и LIVE, AUTO/PINNED, дедлайны команд 2 s, bounded artwork cache |
| Точки | Дом/База/Спавн с пустыми командами, валидация одной команды, state machine с verification/cooldown/timeout и без авто-ретраев |
| Автоматизация | только каталог A01..A22 со статусом `PLANNED` (P2) |

## Быстрый старт

```bash
gradle wrapper --gradle-version 9.0.0   # один раз, нужен установленный Gradle
./gradlew selfCheck                     # проверки ядра без Minecraft
./gradlew clean build                   # требует доступ к Fabric Maven
./gradlew runClient                     # отдельный результат, не равен сборке
```

Перед первой сборкой заменить `loom_version` в `gradle.properties` на реально
существующую версию Loom для 1.21.10 (`docs/dependency-matrix.md`).

## Структура

```text
src/main/java/silkvis/
  client/       Fabric entrypoint, экраны, HUD-оверлей, сэмплер, keybinds
  core/         модули, состояния, токены мира, пулы, ошибки
  config/       JSON, кодеки, атомарная запись, миграции
  config/json/  строгий парсер/writer без внешних зависимостей
  ui/           тема, layout, виджеты, фокус, поиск, тосты
  ui/hud/       конфиг HUD, anchor/clamp/snap, политики видимости
  render/       адаптеры DrawContext и HUD-слоя (единственные MC-точки рендера)
  hud/          view model, FPS/rate-агрегаторы
  map/          identity, тайлы, кодек, кэш, store, сервис сэмплирования
  media/        контракты, IPC, timeline, сервис, Windows-провайдер
  destinations/ команды и state machine
  automation/   каталог P2 (PLANNED)
  integration/  безопасный probe Sodium/Iris/Baritone
  diagnostics/  bounded log и перцентили
src/test/java/silkvis/verify/   self-checks без JUnit
native/media-helper/            C++/WinRT helper (исходник, не собран)
docs/                           матрицы, ADR, отчёт, ограничения
```

## Документация

- `docs/requirements-matrix.md` — requirement-id → файл → evidence → статус
- `docs/status-report.md` — что реально проверено, чем и с каким результатом
- `docs/known-limitations.md` — известные ограничения и пробелы
- `docs/dependency-matrix.md` — версии, лицензии, fallback, статусы
- `docs/build.md` — сборка, wrapper, артефакты
- `docs/adr/` — ADR-0001…0005 (build, render adapter, media bridge, config, map)

## Границы

Нет скачивания музыки, аккаунтов, облака, телеметрии, встроенного браузера и
изменения серверных разрешений. Автоматизация не исполняется в этой сборке.
