# ADR-0003 — Windows media bridge (GSMTC)

- Статус: принято, нативный компонент не собран (MEDIA-04..07)
- Дата: 2026-11-12

## Контекст

Нужен P1-оверлей «сейчас играет» без скачивания музыки и без логина в сервисы.
Базовый источник на Windows — `Windows.Media.Control` (GSMTC): перечисление
опубликованных системных сессий, metadata, адресные transport-команды.
Java не имеет штатного доступа к WinRT; нативная зависимость должна быть изолирована.

## Решение

1. Отдельный **дочерний процесс** `silkvis-media-helper.exe` (C++/WinRT), общение по
   stdin/stdout в формате NDJSON, `schemaVersion=1`, `HELLO/READY` handshake.
2. `stdout` — только протокол; диагностика — в `stderr`, читается отдельным потоком и
   ограничивается 64 строками.
3. Запуск: `ProcessBuilder` с фиксированным путём и списком аргументов. Без shell,
   без elevation, без PowerShell, без установки службы.
4. Перед запуском проверяются: ОС/архитектура (Windows x64) и SHA-256 из
   `manifest.properties`. Несовпадение → `ProviderStatus.ERROR`, мод продолжает работать.
5. Ограничения: строка ≤ 2 MiB, metadata ≤ 4096 символов, artwork PNG ≤ 512 KiB и
   ≤ 512×512; arbitrary paths не открываются; управляющие строки не исполняются.
6. Отказоустойчивость: 3 рестарта с backoff, затем `ERROR` (circuit breaker);
   повторное включение модуля сбрасывает счётчик; shutdown helper — 2 s, затем
   уничтожается только дочерний процесс.
7. Java-сторона: `WindowsMediaProvider` (процесс, протокол, рестарты) и
   `MediaService` (AUTO/PINNED, generation/revision, дедлайны команд 2 s, artwork dedup).

## Открытые пункты

- Бинарник в этой среде не собран (нет MSVC/Windows). Поставлять нечего, поэтому
  провайдер честно возвращает `ERROR: helper_missing`, а `MEDIA-*` в матрице
  требований имеет статус `IMPLEMENTED_NOT_TESTED` / `BLOCKED`.
- Spike «unpackaged helper» (MEDIA-04) обязан быть выполнен до релиза V2.

## Last.fm / OAuth / streaming

Не входят в scope (0.8, MEDIA-16). Громкость источника не обещается: только transport.
