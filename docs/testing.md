# Testing

## Production-like clean checks

Use a separate clean checkout. Set `PR_REF` for PR checks before running PR-specific tasks. The heavyweight
persistent-storage integration tests are disabled by default: uncomment `RUN_INTEGRATION_TESTS=1` when the local PR
check must also cover legacy Ignite storage and migration recovery. The integration task is executed only after the
optional PR/ref checkout, so the checked ref defines whether the task exists.

<details>
<summary>Windows</summary>

```bat
@echo off
setlocal

set "CHECK_ROOT=%~dp0"
set "REPO=%CHECK_ROOT%ignite-teamcity-bot-check"
set "DIST=%CHECK_ROOT%tcbot-check-runtime"
set "PR_REF="
rem set "PR_REF=pull/200/head"
set "RUN_INTEGRATION_TESTS="
rem set "RUN_INTEGRATION_TESTS=1"

if not exist "%REPO%\.git" git clone https://github.com/apache/ignite-teamcity-bot.git "%REPO%" || exit /b 1
cd /d "%REPO%" || exit /b 1
git fetch origin master || exit /b 1
git switch master || exit /b 1
git reset --hard origin/master || exit /b 1
git clean -fdx || exit /b 1

if not "%PR_REF%"=="" (
    git branch -D pr-check 2>NUL
    git fetch origin "%PR_REF%:refs/heads/pr-check" || exit /b 1
    git switch pr-check || exit /b 1
)

call gradle clean build --no-daemon || exit /b 1
if "%RUN_INTEGRATION_TESTS%"=="1" (
    call gradle :migrator:integrationTest --no-daemon || exit /b 1
) else (
    echo Skipping migrator integration tests. Uncomment RUN_INTEGRATION_TESTS to enable.
)
call gradle :jetty-launcher:clean :jetty-launcher:distZip --no-daemon || exit /b 1

powershell -NoProfile -ExecutionPolicy Bypass -Command "Remove-Item -LiteralPath '%DIST%' -Recurse -Force -ErrorAction SilentlyContinue; Expand-Archive -LiteralPath 'jetty-launcher\build\distributions\jetty-launcher.zip' -DestinationPath '%DIST%' -Force" || exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -Command "New-Item -ItemType Directory -Force -Path '%DIST%\jetty-launcher\work' | Out-Null" || exit /b 1
copy /Y "conf\branches.json" "%DIST%\jetty-launcher\work\branches.json" || exit /b 1

cd /d "%DIST%\jetty-launcher\bin" || exit /b 1
call jetty-launcher.bat
```

</details>

<details>
<summary>Linux</summary>

```bash
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="${REPO:-$SCRIPT_DIR/ignite-teamcity-bot-check}"
DIST="${DIST:-$SCRIPT_DIR/tcbot-check-runtime}"
PR_REF="${PR_REF:-}"
# PR_REF="pull/200/head"
RUN_INTEGRATION_TESTS="${RUN_INTEGRATION_TESTS:-}"
# RUN_INTEGRATION_TESTS=1

if [ ! -d "$REPO/.git" ]; then
    git clone https://github.com/apache/ignite-teamcity-bot.git "$REPO"
fi

cd "$REPO"
git fetch origin master
git switch master
git reset --hard origin/master
git clean -fdx

if [ -n "$PR_REF" ]; then
    git branch -D pr-check 2>/dev/null || true
    git fetch origin "$PR_REF:refs/heads/pr-check"
    git switch pr-check
fi

gradle clean build --no-daemon
if [ "$RUN_INTEGRATION_TESTS" = "1" ]; then
    gradle :migrator:integrationTest --no-daemon
else
    echo "Skipping migrator integration tests. Set RUN_INTEGRATION_TESTS=1 to enable."
fi
gradle :jetty-launcher:clean :jetty-launcher:distZip --no-daemon

rm -rf "$DIST"
mkdir -p "$DIST"
unzip -oq jetty-launcher/build/distributions/jetty-launcher.zip -d "$DIST"
mkdir -p "$DIST/jetty-launcher/work"
cp conf/branches.json "$DIST/jetty-launcher/work/branches.json"

cd "$DIST/jetty-launcher/bin"
./jetty-launcher
```

</details>

## Emulated bot clean check

Use this variant for a quick clean build of any PR followed by a local emulated TC Bot UI. It uses a separate checkout,
does not launch the production bot, and starts the integration-test launcher with local GitHub/JIRA/TeamCity emulators
on `http://127.0.0.1:5555/`. Unit tests and emulated integration tests are opt-in flags, so the default path stays fast
before opening the UI.

```bat
@echo off
setlocal

set "CHECK_ROOT=%~dp0"
set "REPO=%CHECK_ROOT%ignite-teamcity-bot-emulated-check"
if not "%~1"=="" set "PR_REF=%~1"
rem Usage:
rem   checkie.bat pull/225/head
rem   checkie.bat 225
rem set "RUN_TESTS=1"
rem set "RUN_EMULATED_TESTS=1"
rem set "TEST_FILTER=--tests org.apache.ignite.tcbot.integration.BotLoginTriggerQueueFlowTest"

if "%PR_REF%"=="" (
    echo Usage: %~nx0 ^<PR number or ref^>
    echo Example: %~nx0 225
    echo Example: %~nx0 pull/225/head
    exit /b 2
)

echo %PR_REF%| findstr /R "^[0-9][0-9]*$" >NUL
if "%ERRORLEVEL%"=="0" set "PR_REF=pull/%PR_REF%/head"

if not exist "%REPO%\.git" git clone https://github.com/apache/ignite-teamcity-bot.git "%REPO%" || exit /b 1
cd /d "%REPO%" || exit /b 1
git fetch origin master || exit /b 1
git switch master || exit /b 1
git reset --hard origin/master || exit /b 1
git clean -fdx || exit /b 1

if not "%PR_REF%"=="" (
    git branch -D pr-emulated-check 2>NUL
    git fetch origin "%PR_REF%:refs/heads/pr-emulated-check" || exit /b 1
    git switch pr-emulated-check || exit /b 1
)

if "%RUN_TESTS%"=="1" (
    call gradle clean build --no-daemon || exit /b 1
) else (
    call gradle clean assemble --no-daemon || exit /b 1
)

if "%RUN_EMULATED_TESTS%"=="1" (
    call gradle :tcbot-integration-tests:integrationTest %TEST_FILTER% --no-daemon || exit /b 1
) else (
    echo Skipping emulated integration tests. Set RUN_EMULATED_TESTS=1 to enable.
)

echo.
echo Starting emulated TC Bot UI:
echo   http://127.0.0.1:5555/
echo   Username: ignite.tester
echo   Password: ignite-password
echo.
call gradle :tcbot-integration-tests:runEmulatedBot --no-daemon || exit /b 1
```

When `RUN_EMULATED_TESTS=1`, the emulated integration task builds the WAR, starts the production-like launcher on an
isolated port, starts separate Python emulators for GitHub, JIRA, and TeamCity, and then shuts them down after the test
JVM exits. The final `runEmulatedBot` step starts the same emulator-backed bot for manual UI checks and keeps running
until the process is stopped.
