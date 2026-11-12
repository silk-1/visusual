@echo off
setlocal EnableDelayedExpansion
rem ============================================================================
rem  SILKVIS - one-click build for Windows.
rem
rem  Usage:
rem    build.bat              -> gradlew clean build
rem    build.bat selfCheck    -> gradlew selfCheck
rem    build.bat runClient    -> gradlew runClient
rem
rem  Why this file exists: Loom 1.19.x needs a JDK 25 JVM, while a player-facing
rem  mod must stay Java 21 bytecode. This script finds JDK 25 and points Gradle at
rem  it, so PATH/JAVA_HOME order on your machine does not matter.
rem ============================================================================

set "JDK="
for %%R in (
  "C:\Program Files\Eclipse Adoptium"
  "C:\Program Files\Microsoft"
  "C:\Program Files\Java"
  "C:\Program Files\Zulu"
  "%USERPROFILE%\.jdks"
  "F:\jdk25"
  "C:\jdk25"
) do (
  if exist "%%~R" (
    for /d %%D in ("%%~R\jdk-25*" "%%~R\*25*") do (
      if not defined JDK if exist "%%~D\bin\java.exe" set "JDK=%%~D"
    )
  )
)

if not defined JDK (
  echo.
  echo [SilkVis] JDK 25 not found.
  echo.
  echo   Install it with one of:
  echo     winget install --id EclipseAdoptium.Temurin.25.JDK -e
  ^|^| download: https://adoptium.net/temurin/releases/?version=25
  echo.
  echo   JDK 21 is NOT enough: Loom 1.19.x requires JVM 25.
  echo   After installing, close this window and run build.bat again.
  echo.
  pause
  exit /b 1
)

set "JAVA_HOME=%JDK%"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [SilkVis] Using JDK: %JAVA_HOME%
echo.
"%JAVA_HOME%\bin\java.exe" -version
echo.

if not exist "%~dp0gradlew.bat" (
  echo [SilkVis] gradlew.bat not found next to this script. Open the project folder.
  pause
  exit /b 1
)

if "%~1"=="" (
  call "%~dp0gradlew.bat" clean build
) else (
  call "%~dp0gradlew.bat" %*
)
set "CODE=%ERRORLEVEL%"

echo.
if "%CODE%"=="0" (
  echo [SilkVis] SUCCESS. Jar is in build\libs\
) else (
  echo [SilkVis] FAILED with exit code %CODE%.
  echo [SilkVis] Copy the lines after "* What went wrong:" and send them back.
)
echo.
pause
exit /b %CODE%
