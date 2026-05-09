# Build and installation

Use Java 17. Build everything through the Gradle wrapper:

```
./gradlew clean build --no-daemon
./gradlew :jetty-launcher:clean :jetty-launcher:distZip --no-daemon
```

On Windows use the same commands with `gradlew.bat`:

```
gradlew.bat clean build --no-daemon
gradlew.bat :jetty-launcher:clean :jetty-launcher:distZip --no-daemon
```

The web distribution is `jetty-launcher/build/distributions/jetty-launcher.zip`. It contains `bin`, `lib`, and
`war/ignite-tc-helper-web.war`.

The production launcher creates `work/diagnostic` on startup. JVM heap dumps and fatal error logs are written there:
`java_pid<pid>.hprof` for OOME heap dumps and `hs_err_pid<pid>.log` for JVM crash logs.

## Linux service

Unpack the distribution and put production config into `work`:

```
sudo mkdir -p /opt/ignite-teamcity-bot/releases /opt/ignite-teamcity-bot/work
sudo unzip -oq jetty-launcher.zip -d /opt/ignite-teamcity-bot/releases
sudo ln -sfn /opt/ignite-teamcity-bot/releases/jetty-launcher /opt/ignite-teamcity-bot/current
sudo cp branches.json /opt/ignite-teamcity-bot/work/branches.json
```

`/etc/systemd/system/tc-bot-service.service`:

```
[Unit]
Description=Ignite TeamCity Bot
After=network-online.target

[Service]
Type=simple
User=tc-bot
Group=tc-bot
WorkingDirectory=/opt/ignite-teamcity-bot/current/bin
Environment="JAVA_HOME=/usr/lib/jvm/java-17"
Environment="TCBOT_WORK_DIR=/opt/ignite-teamcity-bot/work"
ExecStart=/opt/ignite-teamcity-bot/current/bin/jetty-launcher
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

Start it:

```
sudo systemctl daemon-reload
sudo systemctl enable tc-bot-service
sudo systemctl restart tc-bot-service
sudo systemctl status tc-bot-service
```

Use generated `bin/jetty-launcher`; it already contains the Java 17 module options required by Ignite and Guice.

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

set "REPO=C:\Tmp\ignite-teamcity-bot-check"
set "DIST=C:\Tmp\tc-bot-prod-check"
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

call gradlew.bat clean build --no-daemon || exit /b 1
if "%RUN_INTEGRATION_TESTS%"=="1" (
    call gradlew.bat :migrator:integrationTest --no-daemon || exit /b 1
) else (
    echo Skipping migrator integration tests. Uncomment RUN_INTEGRATION_TESTS to enable.
)
call gradlew.bat :jetty-launcher:clean :jetty-launcher:distZip --no-daemon || exit /b 1

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

REPO="${REPO:-/tmp/ignite-teamcity-bot-check}"
DIST="${DIST:-/tmp/tc-bot-prod-check}"
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

./gradlew clean build --no-daemon
if [ "$RUN_INTEGRATION_TESTS" = "1" ]; then
    ./gradlew :migrator:integrationTest --no-daemon
else
    echo "Skipping migrator integration tests. Set RUN_INTEGRATION_TESTS=1 to enable."
fi
./gradlew :jetty-launcher:clean :jetty-launcher:distZip --no-daemon

rm -rf "$DIST"
mkdir -p "$DIST"
unzip -oq jetty-launcher/build/distributions/jetty-launcher.zip -d "$DIST"
mkdir -p "$DIST/jetty-launcher/work"
cp conf/branches.json "$DIST/jetty-launcher/work/branches.json"

cd "$DIST/jetty-launcher/bin"
./jetty-launcher
```

</details>
