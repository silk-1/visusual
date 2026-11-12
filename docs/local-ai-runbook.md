# Инструкция для локального ИИ-помощника (сборка SilkVis)

Ты — **исполнитель команд**. Ты НЕ редактируешь код и НЕ меняешь настройки проекта.
Твоя работа: запустить команды, прочитать вывод, коротко отчитаться.

---

## Шаг 0. Получи СВЕЖУЮ копию проекта (каждый раз перед сборкой)

Старую папку из ZIP использовать нельзя: в ней код неизменный, и ты будешь
компилировать одно и то же, даже если на GitHub уже всё починено.

Если папки проекта ещё нет — скопируй (клонируй) репозиторий:

```powershell
cd F:\
git clone https://github.com/silk-1/visusual.git silkvis
cd F:\silkvis
```

Если папка уже есть — просто обнови её:

```powershell
cd F:\silkvis
git pull
```

Если `git` не установлен: `winget install Git.Git`, затем перезапусти PowerShell.
Без git можно скачать ZIP: https://github.com/silk-1/visusual/archive/refs/heads/main.zip
(но тогда обновлять придётся вручную каждый раз заново).

**Проверь, что копия свежая:**

```powershell
git log --oneline -1
Test-Path "src\main\java\silkvis\config\GlobalConfig.java"
```

Первая команда показывает последний коммит (он должен совпадать с тем, что на GitHub),
вторая — `True`. Если `False` — копия старая, обнови её и не начинай сборку.

## Шаг 1. Проверь Java

Открой PowerShell в папке проекта (в проводнике: Shift + правый клик по папке →
«Открыть окно PowerShell здесь») и выполни:

```powershell
java -version
```

- Если вывод содержит `25.` — всё хорошо, переходи к Шагу 3.
- Если `java` не найден или версия 8/11/17/21 — переходи к Шагу 2.
  (JDK 21 не подходит: сам Gradle-плагин Loom 1.19.4 требует JVM 25. Ошибка выглядит так:
  `Dependency requires at least JVM runtime version 25`.)

## Шаг 2. Установи JDK 21 (один раз)

```powershell
winget install EclipseAdoptium.Temurin.25.JDK
```

После установки **закрой PowerShell и открой заново** (иначе JAVA_HOME не подхватится),
затем снова `java -version` — должно быть `25.x`.

Если всё ещё показывает 21 — укажи JDK 25 явно для этой сборки:

```powershell
$env:JAVA_HOME = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory |
    Where-Object Name -like "jdk-25*" | Select-Object -First 1).FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

## Шаг 3. Перейди в папку проекта

Если в пути есть пробелы или кириллица — путь обязательно в кавычках:

```powershell
cd "F:\спек\маинкрафт визуал\test v1\visusual-main"
dir
```

В списке должны быть `gradlew.bat`, `build.gradle`, `gradle.properties`.

**Проверка, что копия свежая** (в старой версии нет пакета config, и сборка упадёт
с `package silkvis.config does not exist`):

```powershell
Test-Path "src\main\java\silkvis\config\GlobalConfig.java"
```

Должно быть `True`. Если `False` — копия устарела: обнови её (`git pull` в папке
проекта или скачай ZIP заново с GitHub, кнопка Code → Download ZIP).

**Рекомендация:** папку с кириллицей в пути лучше скопировать в простое место,
например `F:\silkvis`. Это убирает целый класс редких проблем с кодировкой пути
у Java и Gradle.

## Шаг 4. Собери проект

```powershell
.\gradlew.bat clean build 2>&1 | Tee-Object -FilePath build-log.txt
```

Важно:
- именно `.\gradlew.bat`, а **не** `gradle` (Gradle устанавливать не нужно, он скачается сам);
- первый запуск скачивает ~1 ГБ зависимостей и может идти 10–20 минут — это нормально;
- не прерывай процесс и не запускай второй раз параллельно.

## Шаг 5. Пойми результат

Смотри последние 30 строк лога:

| Что видишь | Что это значит | Что делать |
|---|---|---|
| `BUILD SUCCESSFUL` | сборка прошла | перейти к Шагу 6, вариант А |
| `BUILD FAILED` + блок `* What went wrong:` | ошибка | Шаг 6, вариант Б |
| `java` не найден / `JAVA_HOME` | JDK не установлен или не виден | вернуться к Шагу 2, перезапустить терминал |
| `Could not resolve` / `Could not download` | нет сети или блокировка Maven | проверить интернет/VPN, повторить |

## Шаг 6. Отчёт (строго по шаблону)

**Вариант А — сборка прошла.**
Выполни ещё одну команду и приложи её вывод:

```powershell
.\gradlew.bat selfCheck 2>&1 | Tee-Object -FilePath selfcheck-log.txt
```

Отчёт:

```
java -version: <первая строка>
clean build: BUILD SUCCESSFUL
selfCheck: <последние 10 строк вывода>
```

**Вариант Б — сборка упала.**
Найди в `build-log.txt` строку `* What went wrong:` и скопируй **20–40 строк после неё**.
Не присылай весь лог — он огромный и в нём мало полезного.

Отчёт:

```
java -version: <первая строка>
clean build: BUILD FAILED
Ошибка:
<первые 20-40 строк после "* What went wrong:">
```

---

## Жёсткие запреты

1. **Не редактируй** `.java`, `build.gradle`, `gradle.properties`, `.gitignore`.
2. **Не меняй версии** Loom / Gradle / Yarn / Fabric API — даже если «кажется, что дело в них».
3. **Не запускай** `gradle` (без `w`) и не устанавливай Gradle.
4. **Не удаляй** папки `build`, `gradle`, `src` и не делай `git reset`/`git clean`.
5. **Не переписывай** `.gitignore` и не добавляй файлы в git.
6. Если ошибка непонятна — так и напиши «не понял», приложив текст ошибки. Врать нельзя.
7. **Не собирай старую копию.** Перед каждой сборкой — `git pull` (Шаг 0). Иначе
   получишь ту же ошибку, которую уже исправили на GitHub.

## Если ошибка повторяется

Не запускай тот же билд по кругу. Пришли лог один раз; правки делает другой агент.
