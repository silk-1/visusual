# Музыка: как проверить мост до сборки мода

Бэкенд — PowerShell-мост к Windows GSMTC (ADR-0008). Его можно проверить **одной
командой**, не собирая и не запуская мод: если скрипт не работает, дальше смотреть смысла
нет.

## Проверка скрипта (обязательный первый шаг)

Включи музыку в любом плеере (Spotify, Яндекс Музыка, SoundCloud, YouTube в Chrome/Edge) и
выполни в PowerShell:

```powershell
cd F:\silkvis\scripts\media-helper
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File .\silkvis-media-helper.ps1 -Probe
```

Ожидаемо — одна-две строки JSON:

```json
{"schemaVersion":1,"type":"SESSIONS","payload":{"sessions":[{"sessionId":"Spotify.exe","appId":"Spotify.exe","displayName":"Spotify","current":true}]}}
{"schemaVersion":1,"type":"SNAPSHOT","payload":{"sessionId":"Spotify.exe","title":"...","artist":"...","playbackState":"PLAYING", ...}}
```

Разбор результатов:

| Что видно | Что значит | Что делать |
|---|---|---|
| две строки JSON с названием трека | мост работает | собирать мод |
| `ERROR code=gsmtc_unavailable` | Windows не отдаёт сессии | обновить Windows; проверить, что плеер виден в микшере громкости |
| `ERROR code=winrt_unavailable` | в системе нет Windows Runtime | это не Windows или политика вырезала WinRT |
| ничего, окно закрылось мгновенно | скрипт заблокирован политикой/антивирусом | см. раздел ниже |
| `... не может быть загружено, так как выполнение сценариев отключено` | ExecutionPolicy | запусти с `-ExecutionPolicy Bypass`, как в команде выше |

## Проверка обложки отдельной командой

Обложка идёт по тому же мосту, но её можно проверить, не запуская игру:

```powershell
cd F:\silkvis\scripts\media-helper
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File .\silkvis-media-helper.ps1 -ProbeArtwork
```

Строка печатается в stderr, без base64. Ожидаемо:

```
[silkvis-helper] artwork mime=image/jpeg bytes=48213 reason= ref=probe
```

| Что видно | Что значит |
|---|---|
| `mime=image/jpeg`, `bytes` больше нуля | обложка читается — мод получит её |
| `mime=image/png` | то же, PNG (Spotify иногда отдаёт PNG) |
| `reason=no_thumbnail` | у текущего трека нет картинки (локальный файл, подкаст) |
| `reason=too_large` | картинка больше 512 КБ — Windows отдаёт такую редко, сообщи |
| `reason=not_an_image` | источник отдал не картинку |
| `ERROR code=no_session` | нет активной медиа-сессии: включи трек и повтори |

До 0.1.18 обложка отбрасывалась, если была не PNG: Spotify и Chrome присылают JPEG, поэтому
пусто было почти всегда.

## Если PowerShell блокирован

1. Проверить политику: `Get-ExecutionPolicy -List`.
2. Проверить, что запуск разрешён: `powershell.exe -NoProfile -Command "$PSVersionTable.PSVersion"`.
3. Если корпоративная политика запрещает скрипты — сообщи, и мост переключится на
   нативный helper (нужен `.exe` + `manifest.properties`), либо на другой путь
   (например, чтение через COM-интерфейс плеера).

Мод показывает причину отказа на вкладке «Музыка» строкой `Источник: …`:
`disabled`, `unavailable / powershell_missing`, `unavailable / hash_mismatch`,
`ERROR / gsmtc_unavailable` и т.п.

## Что делает мод с этим скриптом

- Извлекает `silkvis-media-helper.ps1` в
  `%appdata%\.minecraft\silkvis-data\native\media-helper-1\`.
- Сверяет SHA-256 с `media-helper.properties` из jar. Не совпало — скрипт **не
  запускается**.
- Запускает `powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File <скрипт>`
  и обменивается с ним NDJSON-строками.
- Раз в 500 мс отправляет `SNAPSHOT_REQUEST` (скрипт не может тикать сам, пока ждёт stdin).

## Что ещё не сделано

- Виджет Now Playing: варианты отображения (строка / строка+прогресс+обложка / карточка)
  и отрисовка обложки — следующий шаг, он требует загрузки PNG в GPU-текстуру.
- Кнопки управления в HUD (режим «Взаимодействие с HUD»).
- Нативный `.exe`: `ExeHelperLauncher` готов, нужен бинарник и манифест.
