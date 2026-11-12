# silkvis-media-helper (C++/WinRT)

Мост к `Windows.Media.Control` (GSMTC). **Бинарник не собран в этой среде** (нет
Windows/MSVC), поэтому мод при запуске честно возвращает `ERROR: helper_missing`.
Спецификация протокола — §7.2/7.3 ТЗ и `docs/adr/0003-media-bridge-windows.md`.

## Контракт

- stdin/stdout: по одному JSON-объекту на строку (UTF-8, NDJSON), `schemaVersion=1`.
- `stdout` — только протокол; диагностика — `stderr`, ограниченно.
- Handshake: клиент `HELLO {protocolVersion:1}` → helper `READY`.
- Клиент → helper: `SELECT_SESSION`, `COMMAND`, `ARTWORK_REQUEST`, `SHUTDOWN`.
- Helper → клиент: `SESSIONS`, `SNAPSHOT`, `COMMAND_RESULT`, `ARTWORK`, `ERROR`.
- EOF на stdin → helper завершается сам (watchdog не оставляет orphan-процесс).
- Максимум строки 2 MiB, metadata ≤ 4096 символов, artwork PNG ≤ 512 KiB / ≤ 512×512.

## Сборка (Windows)

```bat
cmake -B build -G "Visual Studio 17 2022" -A x64
cmake --build build --config Release
copy build\Release\silkvis-media-helper.exe ..\..\runtime\
```

Требуется Windows SDK с C++/WinRT (`cppwinrt`), MSVC 2022. Пользователю MSVC не нужен:
в релиз попадает готовый `.exe` + `manifest.properties`.

## Публикация хеша

После сборки:

```bat
certutil -hashfile silkvis-media-helper.exe SHA256
```

Полученное значение внести в `manifest.properties` (`sha256=...`). Мод сравнивает хеш
перед запуском (MEDIA-05) и не запускает несовпадающий файл.
