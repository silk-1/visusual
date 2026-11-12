# Отчёт проверки SilkVis (DEV-05)

## Актуализация перед публикацией

Дата: 2026-11-12. Актуальная база публикации:
`main@e87b2084089a898844aafe4eeeb86ecdbfca73cb`.
Владелец отдельно разрешил загрузить изменения в ветку и открыть PR без слияния.
Этот раздел имеет приоритет над историческим отчётом ниже.

**Build/selfCheck/runClient по-прежнему не подтверждены.** Свежая статическая
проверка не означает компиляцию мода или проверку на Windows-ПК владельца.

### Что изменилось в main

- PR #4 и #5 уже слиты. В `src/main/java/silkvis/config` теперь 18 файлов,
  включая JSON-слой. Прежний блокер «пакет отсутствует» снят только на уровне дерева.
- `.gitignore` больше не игнорирует `src/main/java/silkvis/config/ConfigPaths.java`.
- Импорт `ServerInfo` теперь из `net.minecraft.client.network`,
  `CoreChecks` импортирует `silkvis.core.OptionalLimit`.
- Изменения Arena сохранены. Публикуемый diff содержит только три разрешённых
  документа и `scripts/`; Java, `.gitignore`, build scripts, версии и ADR не меняются.
- `promt` и `AGENTS.md` совпадают с прочитанной предыдущей редакцией.

Текущий снимок получен через GitHub по SHA: проверены 151 blob, дерево
`a85b58e1c2da23b90d6621235a803fe3cc0e2db6` и исходный commit object.

### Свежие команды на e87b208

Логи: `scripts/evidence/2026-11-12-main-e87b208/`.

| Команда | Результат | Evidence |
|---|---|---|
| `python scripts/verify.py` | exit **1**; lang PASS, ASCII FAIL | [verify-python.log](../scripts/evidence/2026-11-12-main-e87b208/verify-python.log) |
| `git ls-files src/main/java/silkvis/config` | exit 0; 18 файлов | [source-audit.log](../scripts/evidence/2026-11-12-main-e87b208/source-audit.log) |
| `git check-ignore -v --no-index src/main/java/silkvis/config/ConfigPaths.java` | exit **1**, пустой вывод: совпадающих ignore-правил нет | `source-audit.log` |
| `rg -n 'ServerInfo\|OptionalLimit' ...` | exit 0; исправленные импорты присутствуют | Точные аргументы и вывод: `source-audit.log` |

```text
PASS lang: en_us=118 ru_ru=118 missing_in_ru=0 missing_in_en=0
FAIL ascii: java_files=122 non_ascii_files=10
RESULT FAIL
```

Теперь проверено 114 main + 8 test Java-файлов. Список 20 строк с не-ASCII
не изменился; адреса приведены в `known-limitations.md`.

### Что не повторялось

- Gradle build не повторялся: предыдущее скачивание Wrapper остановилось с
  `Network is unreachable`; изменений сетевых условий не подтверждено.
  Старый exit 1 относится к снимку `0c2d7e6`, не является новым билдом `e87b208`.
- selfCheck и runClient не выполнялись: зелёного build-гейта нет.
- PowerShell отсутствует; `verify.ps1` не исполнялся. Его содержимое не изменилось
  после предыдущей подготовки. Результат Python не выдаётся за PowerShell-прогон.
- 12/12 fixture-сценариев Python относятся к предыдущему прогону неизменённого
  скрипта; это не новый запуск и не selfCheck мода.
- Windows, реальный клиент, музыка, совместимость и PERF не проверялись.

Следующий шаг после публикации: владелец/Arena проверяет ASCII-нарушения и запускает
на Windows с полным JDK 21 и сетью `.\gradlew.bat clean build`, затем только после
успеха `.\gradlew.bat selfCheck`, после его успеха `.\gradlew.bat runClient`.
Версии зависимостей не менялись. `verify.ps1` запускается отдельно.

## Историческая проверка main@0c2d7e6

Ниже сохранён предыдущий отчёт вместе с исходными логами. Указанные в нём отсутствие
config, игнорирование пакета и открытый PR #4 относятся только к старому снимку;
на актуальной базе они исправлены, как зафиксировано выше. Сообщение «публикация
не выполнялась» ниже описывает состояние до отдельного разрешения владельца.

Дата: 2026-11-12. Проверяемый снимок `main`:
`0c2d7e6de7f30b4f1b9a6b51b746d4cab68c8ba5`.

**Build-гейт не пройден. Компиляция, selfCheck и игровой запуск не подтверждены.**
Этот отчёт относится к среде агента Linux, не к Windows-ПК владельца.
Доступа к ПК владельца в этой сессии нет.

## Scope и происхождение снимка

Прочитаны корневые `promt` и `AGENTS.md`. Изменения ограничены
`docs/requirements-matrix.md`, этим отчётом, `docs/known-limitations.md` и `scripts/`.
Java, зависимости, Wrapper, build scripts, ресурсы и ADR не изменялись.
Удалённая публикация не выполнялась; в `promt` BUILD-02/DEV-06 она выделена отдельно.

Обычный `git clone --single-branch --branch main https://github.com/silk-1/visusual.git`
завершился HTTP 403. Все 133 файла получены через подключение GitHub по указанному SHA.
При материализации проверены Git SHA-1 всех 133 blobs, дерево
`391af66fa66237a237a7913c2e9a68741c14854e` и исходный commit object.
Рабочее дерево до изменений было чистым. Это не снимок ветки PR #4.

## Среда

| Поле | Факт |
|---|---|
| ОС | Linux x86_64, среда агента |
| Java runtime | OpenJDK 21.0.13 |
| JDK compiler | `javac` не найден; наличие Java runtime не означает полный JDK |
| PowerShell / Windows shell | `pwsh`, `powershell`, `cmd.exe` не найдены |
| Gradle | Wrapper закреплён на 9.8.2; скачивание не удалось |
| Версии проекта | Minecraft 1.21.10, Yarn 1.21.10+build.3, Loader 0.20.1, Fabric API 0.138.4+1.21.10, Loom 1.19.4 — только чтение конфигурации, без resolve |
| Windows / GUI / медиасессии | Не проверялись |

Машиночитаемые данные: [environment.json](../scripts/evidence/2026-11-12-main-0c2d7e6/environment.json).

## Выполненные команды

Все команды ниже выполнялись из корня снимка, кроме получения репозитория.
Полные логи находятся в `scripts/evidence/2026-11-12-main-0c2d7e6/`;
exit codes записаны отдельно в `*.exitcode`.

| Команда | Exit / результат | Evidence |
|---|---|---|
| `java -version` | 0; OpenJDK 21.0.13 | `environment.json` |
| `bash gradlew clean build` | **1**; Wrapper: `Network is unreachable`, до запуска Gradle | [clean-build.log](../scripts/evidence/2026-11-12-main-0c2d7e6/clean-build.log) |
| `pwsh -NoProfile -File scripts/verify.ps1` | **127**; shell не нашёл pwsh, тело скрипта не выполнялось | [verify-powershell.log](../scripts/evidence/2026-11-12-main-0c2d7e6/verify-powershell.log) |
| `python scripts/verify.py` | **1**; lang PASS, ASCII FAIL | [verify-python.log](../scripts/evidence/2026-11-12-main-0c2d7e6/verify-python.log) |
| `python scripts/test_verify.py` | **0**; 12/12 сценариев Python-проверки, PowerShell NOT_RUN | [verifier-fixtures.log](../scripts/evidence/2026-11-12-main-0c2d7e6/verifier-fixtures.log) |
| `git ls-files src/main/java/silkvis/config` | 0, пустой список: пакет отсутствует в снимке | [source-audit.log](../scripts/evidence/2026-11-12-main-0c2d7e6/source-audit.log) |
| `rg -n '^import silkvis\.config' src/main/java src/test/java` | 0; 70 импортов в 19 файлах | `source-audit.log`, `config-imports.log` |
| `git check-ignore -v --no-index src/main/java/silkvis/config/ConfigPaths.java` | 0; правило `.gitignore:15:config/` совпадает с исходным пакетом | `config-ignore.log` |
| `git diff --check` | 0; ошибок whitespace нет | Финальная проверка diff |

### Первая ошибка сборки и классификация

В логе **нет блока `* What went wrong:`**. Не создаём его искусственно:
Wrapper не смог скачать дистрибутив, Gradle ещё не начал конфигурацию проекта.

```text
Attempt 1/1 failed. Reason: Network is unreachable
Exception in thread "main" java.net.SocketException: Network is unreachable
```

Это ограничение среды/сети при bootstrap Wrapper. Оно **не соответствует ни одной
из четырёх строк классификации AGENTS.md §4**: нет ошибки resolve Loom, несовместимости
class-file/Gradle, диагностики Minecraft API или Yarn/Kotlin.
По этому логу нельзя делать вывод о доступности закреплённых версий или об ошибках Java.

Минимальное действие: выполнить тот же Wrapper на машине с доступом к
`services.gradle.org` и дальнейшим адресам загрузки зависимостей, с полным JDK 21.
Версии не менять. Повторный build в неизменной среде не выполнялся.

### Результат проверки исходников

```text
PASS lang: en_us=118 ru_ru=118 missing_in_ru=0 missing_in_en=0
FAIL ascii: java_files=104 non_ascii_files=10
RESULT FAIL
```

Это вывод **Python**, не PowerShell. Проверены 96 main + 8 test Java-файлов.
Полный список 20 строк с нарушениями — в `verify-python.log` и
[known-limitations.md](known-limitations.md). Паритет ключей не подтверждает качество
перевода, отсутствие захардкоженных строк, glyphs или работу UI.

`verify.ps1` проверяет оба направления разницы ключей с учётом регистра и все
`src/**/*.java` по байтам, включая test, BOM и повреждённый UTF-8. Он не меняет файлы.
Exit codes: 0 — обе проверки прошли; 1 — найдены нарушения; 2 — ошибка входных данных/I/O.
Корень определяется от расположения скрипта; доступен параметр `-RepositoryRoot`.
Целевой синтаксис — PowerShell 5.1+, исполнение пока не проверено.

`verify.py` использует только стандартную библиотеку Python; это независимая проверка,
не новая зависимость мода. Fixture-проверки запускают CLI в отдельном временном каталоге:
успех, различие ключей в обоих направлениях, регистр, Unicode в main/test, BOM,
битый JSON, JSON-массив, отсутствующий lang-файл, отсутствующий src и отсутствие Java.
Они не изменяют исходники проекта. Если PowerShell установлен,
`test_verify.py` дополнительно исполняет те же сценарии для `verify.ps1`.

## Подтверждённые статические блокеры исходников

1. В `main` отсутствуют `silkvis.config` и `silkvis.config.json`; 70 импортов из этих
   пакетов находятся в 19 Java-файлах. Наличие ссылок не означает наличие реализации.
   Дополнительно `git check-ignore` подтвердил: правило `.gitignore:15` (`config/`)
   игнорирует и путь исходников `src/main/java/silkvis/config/ConfigPaths.java`.
   Это препятствует обычному добавлению новых файлов пакета. Arena следует проверить
   правило и предложить корневое `/config/` вместо `config/`; `.gitignore` вне нашей
   зоны и не изменён. Причина исторического отсутствия пакета этим не доказана.
2. `src/test/java/silkvis/verify/CoreChecks.java:8` импортирует
   `silkvis.config.OptionalLimit`, а существующий класс находится в `silkvis.core`.
3. `src/main/java/silkvis/client/SilkVisClient.java:13` содержит импорт
   `net.minecraft.server.network.ServerInfo`. PR #4 описывает замену на
   `net.minecraft.client.network.ServerInfo`; в этой сессии Yarn повторно не сверялся.
4. В 10 Java-файлах обнаружены не-ASCII байты, что нарушает AGENTS.md §2.9.

Это результаты чтения/проверки файлов, **не вывод компилятора**.
Исправления Java относятся к Arena и здесь не выполнялись.

При чтении GitHub PR #4 был открыт, не слит; head:
`73a81bcf438a450f86cbcb78842d57f91b2e2c82`.
Описание PR сообщает о JSON-слое и исправлениях импортов, но ещё перечисляет
недостающие конфигурационные классы. Это не подтверждение завершения или компиляции PR.
Перед следующим прогоном нужно заново установить фактический SHA.

## Не выполнено

| Команда / сценарий | Почему |
|---|---|
| `.\gradlew.bat clean build` на ПК владельца | Нет доступа к ПК |
| `.\gradlew.bat selfCheck` / POSIX selfCheck | Красный build-гейт; не повторяем тот же bootstrap. Task объявлен в build.gradle и зависит от testClasses |
| `.\gradlew.bat runClient` / POSIX runClient | До зелёных build + selfCheck не переходим |
| `gradlew test` | Не запускался; наличие selfCheck не означает его выполнение задачей build/test |
| `powershell -NoProfile -File .\scripts\verify.ps1` на Windows | Нет PowerShell/Windows в среде агента |
| Проверка Java-синтаксиса parser'ом / компиляция javac | В этом раунде не выполнялась |
| UI/HUD, карта, config recovery, M-01…M-08, Sodium/Iris/shaders, PERF/soak | Нет запущенного клиента и соответствующей тестовой среды |
| Production JAR в чистом Fabric-профиле | Сборка не получена |

`selfCheck` не требует JUnit, но запуск через Gradle всё равно требует разрешения
Loom/Minecraft и компиляции testClasses. Утверждение «Gradle selfCheck без сети»
для чистой среды неверно.

## Следующие действия

1. Arena завершает восстановление конфигурационного слоя и исправления в своей зоне;
   владелец проверяет актуальное состояние PR #4.
2. На ПК владельца в корне актуального checkout: `java -version`, `javac -version`.
3. `.\gradlew.bat clean build`. При ошибке сохранить первый `* What went wrong:`;
   при bootstrap-ошибке сохранить её фактическое сообщение. Не менять версии наугад.
4. Только после успешного build: `.\gradlew.bat selfCheck`; после его успеха:
   `.\gradlew.bat runClient`. Запуск игры и ручная приёмка фиксируются отдельно.
5. Независимо от Gradle: `powershell -NoProfile -File .\scripts\verify.ps1`;
   сохранить вывод и `$LASTEXITCODE`. Java-нарушения исправляет владелец соответствующей зоны.
6. Обновлять матрицу только по новым логам. P0/V0 и переход к P1/P2 закрыты не были.

## Предыдущие отчёты

Предыдущая редакция доступна в истории
[`docs/status-report.md@0c2d7e6`](https://github.com/silk-1/visusual/blob/0c2d7e6de7f30b4f1b9a6b51b746d4cab68c8ba5/docs/status-report.md).
Её сообщения о `java-parser: 104/104` и прошлой сверке API являются историческими:
в этой сессии они не воспроизводились. Старое утверждение о реализованном
`silkvis.config` опровергается текущим деревом; совет менять Loom/генерировать Wrapper
не является текущей рекомендацией. Закреплённые версии сохранены.
