# Build and installation

Use Java 17 for development and production. A build can be done using following commands:

```
gradle clean
gradle build
```

Build the production archives explicitly when preparing a server deployment:

```
gradle :jetty-launcher:clean :jetty-launcher:distZip
gradle :tcbot-server-node:clean :tcbot-server-node:distZip
```

Use `clean` when rebuilding production archives after dependency or JVM option changes. The generated scripts and
runtime classpath are part of the archive.

Resulting distributions can be found in `jetty-launcher/build/distributions` and
`tcbot-server-node/build/distributions`. The `jetty-launcher` archive contains the start scripts, runtime libraries,
and `war/ignite-tc-helper-web.war`.

## Linux production service

Production mode is started from the `jetty-launcher` distribution. Copy
`jetty-launcher/build/distributions/jetty-launcher.zip` to the Linux server and unpack it:

```
sudo mkdir -p /opt/ignite-teamcity-bot/releases /opt/ignite-teamcity-bot/work
sudo unzip -oq jetty-launcher.zip -d /opt/ignite-teamcity-bot/releases
sudo ln -sfn /opt/ignite-teamcity-bot/releases/jetty-launcher /opt/ignite-teamcity-bot/current
sudo cp branches.json /opt/ignite-teamcity-bot/work/branches.json
```

Create `/etc/systemd/system/tc-bot-service.service`. Use the generated `bin/jetty-launcher` script because it already
contains the required Java 17 module options for Ignite 2.18 and Guice:

```
[Unit]
Description=Ignite TeamCity Bot
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=tc-bot
Group=tc-bot
WorkingDirectory=/opt/ignite-teamcity-bot/current/bin
Environment="JAVA_HOME=/usr/lib/jvm/java-17"
Environment="JETTY_LAUNCHER_OPTS=-Dteamcity.helper.home=/opt/ignite-teamcity-bot/work"
ExecStart=/opt/ignite-teamcity-bot/current/bin/jetty-launcher
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

Start or restart `tc-bot-service` after deploying a new build or changing configuration:

```
sudo systemctl daemon-reload
sudo systemctl enable tc-bot-service
sudo systemctl start tc-bot-service
sudo systemctl restart tc-bot-service
sudo systemctl status tc-bot-service
sudo journalctl -u tc-bot-service -f
```

After `tc-bot-service` is up, open the production bot URL, log in with TeamCity credentials, and click
`Authorize Server` in the top menu. This step is required for background operations that need TeamCity
access, including background checks, queue checks, build triggering, JIRA notifications, and cleanup.

Server authorization is not stored in `branches.json`; it is taken from the authenticated user session and
kept by the running bot process. Re-authorize the server after each service restart, deployment, or process
crash.

## Windows production check

Use these commands to verify a clean checkout and run the bot from the generated production distribution on Windows.
Set `PR_REF` only when checking a pull request:

```
set "SCRIPT_DIR=%~dp0"
set "REPO=%SCRIPT_DIR%ignite-teamcity-bot-check"
set "DIST=C:\Tmp\tc-bot-prod-check"

set "PR_REF="
rem set PR_REF=pull/200/head

if not exist "%REPO%\.git" (
    git clone https://github.com/apache/ignite-teamcity-bot.git "%REPO%" || exit /b 1
)

cd /d "%REPO%" || exit /b 1
git fetch origin master || exit /b 1
git switch master || exit /b 1
git reset --hard origin/master || exit /b 1
git clean -fdx || exit /b 1

if not "%PR_REF%"=="" (
    git branch -D pr-check 2>NUL
    git fetch origin %PR_REF%:refs/heads/pr-check || exit /b 1
    git switch pr-check || exit /b 1
)

java -version || exit /b 1
call gradlew.bat clean build --no-daemon || exit /b 1
call gradlew.bat :jetty-launcher:clean :jetty-launcher:distZip --no-daemon || exit /b 1

powershell -NoProfile -ExecutionPolicy Bypass -Command "Remove-Item -LiteralPath '%DIST%' -Recurse -Force -ErrorAction SilentlyContinue; Expand-Archive -LiteralPath 'jetty-launcher\build\distributions\jetty-launcher.zip' -DestinationPath '%DIST%' -Force" || exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -Command "New-Item -ItemType Directory -Force -Path '%DIST%\jetty-launcher\work' | Out-Null" || exit /b 1
copy /Y "conf\branches.json" "%DIST%\jetty-launcher\work\branches.json" || exit /b 1

cd /d "%DIST%\jetty-launcher\bin" || exit /b 1
call jetty-launcher.bat
```
