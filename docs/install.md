# Build and installation

Use Java 17. Build everything through the Gradle wrapper:

```
./gradlew clean build --no-daemon
./gradlew :jetty-launcher:clean :jetty-launcher:distZip --no-daemon
./gradlew :tcbot-server-node:clean :tcbot-server-node:distZip --no-daemon
```

On Windows use the same commands with `gradlew.bat`:

```
gradlew.bat clean build --no-daemon
gradlew.bat :jetty-launcher:clean :jetty-launcher:distZip --no-daemon
gradlew.bat :tcbot-server-node:clean :tcbot-server-node:distZip --no-daemon
```

The web distribution is `jetty-launcher/build/distributions/jetty-launcher.zip`. It contains `bin`, `lib`, and
`war/ignite-tc-helper-web.war`.

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
Environment="JETTY_LAUNCHER_OPTS=-Dteamcity.helper.home=/opt/ignite-teamcity-bot/work"
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

## Windows production check

Use a separate clean checkout. Set `PR_REF` only for PR checks:

```
set "REPO=C:\Tmp\ignite-teamcity-bot-check"
set "DIST=C:\Tmp\tc-bot-prod-check"
set "PR_REF="
rem set "PR_REF=pull/200/head"

if not exist "%REPO%\.git" git clone https://github.com/apache/ignite-teamcity-bot.git "%REPO%" || exit /b 1
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

call gradlew.bat clean build --no-daemon || exit /b 1
call gradlew.bat :jetty-launcher:clean :jetty-launcher:distZip --no-daemon || exit /b 1

powershell -NoProfile -ExecutionPolicy Bypass -Command "Remove-Item -LiteralPath '%DIST%' -Recurse -Force -ErrorAction SilentlyContinue; Expand-Archive -LiteralPath 'jetty-launcher\build\distributions\jetty-launcher.zip' -DestinationPath '%DIST%' -Force" || exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -Command "New-Item -ItemType Directory -Force -Path '%DIST%\jetty-launcher\work' | Out-Null" || exit /b 1
copy /Y "conf\branches.json" "%DIST%\jetty-launcher\work\branches.json" || exit /b 1

cd /d "%DIST%\jetty-launcher\bin" || exit /b 1
call jetty-launcher.bat
```
