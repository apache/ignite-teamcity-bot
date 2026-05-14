# Build and installation

This is a deliberately plain production runbook.
Examples use only local paths and assume the service user is
`teamcity`.

## 1. Prepare directories

Create the base directory once.

```bash
sudo mkdir -p /data/tcbot/work
```

## 2. Get the source code

Clone the repository if it is not there yet:

```bash
cd /data/tcbot
git clone https://github.com/apache/ignite-teamcity-bot.git ignite-teamcity-bot
```

Before each build, update the checkout:

```bash
cd /data/tcbot/ignite-teamcity-bot
git fetch origin master
git switch master
git pull --ff-only origin master
```

## 3. Build the distribution

Use Java 17. Build with Gradle. This has been tested with Gradle 9.5 and Gradle 9.4.1.

```bash
cd /data/tcbot/ignite-teamcity-bot
gradle clean build --no-daemon
gradle :jetty-launcher:clean :jetty-launcher:distZip --no-daemon
```

The distribution archive is:

```bash
/data/tcbot/ignite-teamcity-bot/jetty-launcher/build/distributions/jetty-launcher.zip
```

It contains `bin`, `lib`, and `war/ignite-tc-helper-web.war`.

Do not commit built archives or unpacked binary distributions to the Apache repository. Keep them only in the runtime
directory, a deployment staging directory, or an external artifact store.

## 4. Stop the current service

Stop TC Bot before copying or migrating anything:

```bash
sudo systemctl stop tcbot
sudo systemctl status tcbot
pgrep -af 'jetty-launcher|TcHelperJettyLauncher' || true
```

Do not continue until the Java process is gone.

## 5. Back up the old runtime and data

Create one timestamped backup directory. Keep the old runtime and the whole work directory together, so rollback can
use the same files that were running before the upgrade.

```bash
TS="$(date +%Y%m%d-%H%M%S)"
BACKUP="/data/tcbot/backup_for_update/$TS"
mkdir -p "$BACKUP"
```

Back up the live runtime:

```bash
if [ -d /data/tcbot/jetty-launcher ]; then
    cp -a /data/tcbot/jetty-launcher "$BACKUP/jetty-launcher"
fi
```

Back up the live work directory. This is the database backup too, because Ignite persistence is inside `work`:

```bash
cp -a /data/tcbot/work "$BACKUP/work"
```

Back up the generated launch scripts from the old runtime:

```bash
mkdir -p "$BACKUP/scripts"
if [ -d /data/tcbot/jetty-launcher/bin ]; then
    cp -a /data/tcbot/jetty-launcher/bin "$BACKUP/scripts/bin"
fi
```

Write a tiny restore note into the backup:

```bash
{
    echo "Created: $TS"
    echo "Runtime: $BACKUP/jetty-launcher"
    echo "Work: $BACKUP/work"
    echo "Launch scripts: $BACKUP/scripts"
} > "$BACKUP/README.txt"
```

## 6. Install the new runtime

Install the new archive into a staging directory first:

```bash
cd /data/tcbot/ignite-teamcity-bot
rm -rf /data/tcbot/jetty-launcher.new
unzip -oq jetty-launcher/build/distributions/jetty-launcher.zip -d /data/tcbot/jetty-launcher.new
```

The archive unpacks to `/data/tcbot/jetty-launcher.new/jetty-launcher`. Replace the live runtime only after the backup
exists:

```bash
rm -rf /data/tcbot/jetty-launcher
mv /data/tcbot/jetty-launcher.new/jetty-launcher /data/tcbot/jetty-launcher
rmdir /data/tcbot/jetty-launcher.new
```

Make sure the live config exists:

```bash
if [ ! -f /data/tcbot/work/branches.json ]; then
    cp /data/tcbot/ignite-teamcity-bot/conf/branches.json /data/tcbot/work/branches.json
fi
```

## 7. Linux service

Create or update `/etc/systemd/system/tcbot.service`:

```ini
[Unit]
Description=Ignite TeamCity Bot
After=network-online.target

[Service]
Type=simple
User=teamcity
Group=teamcity
WorkingDirectory=/data/tcbot/jetty-launcher/bin
Environment="JAVA_HOME=/usr/lib/jvm/java-17"
Environment="TCBOT_WORK_DIR=/data/tcbot/work"
ExecStart=/data/tcbot/jetty-launcher/bin/jetty-launcher
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

Reload systemd and start the service:

```bash
sudo systemctl daemon-reload
sudo systemctl enable tcbot
sudo systemctl start tcbot
sudo systemctl status tcbot
```

The generated `bin/jetty-launcher` already contains the Java options required by the runtime. Do not duplicate those
options in the systemd unit unless the generated script cannot be used.

The production launcher creates `/data/tcbot/work/diagnostic` on startup. JVM heap dumps and fatal error logs are
written there as `java_pid<pid>.hprof` and `hs_err_pid<pid>.log`.

## 8. Check after start

Check logs first:

```bash
journalctl -u tcbot -n 200 --no-pager
ls -la /data/tcbot/work
ls -la /data/tcbot/work/diagnostic
```

If the bot starts and opens the database, keep the backup. Do not delete it immediately after a major Java or Ignite
upgrade.

## 9. Rollback

Rollback uses the backup made in step 5.

```bash
sudo systemctl stop tcbot
TS="<backup timestamp>"
BACKUP="/data/tcbot/backup_for_update/$TS"

rm -rf /data/tcbot/jetty-launcher
cp -a "$BACKUP/jetty-launcher" /data/tcbot/jetty-launcher

rm -rf /data/tcbot/work
cp -a "$BACKUP/work" /data/tcbot/work

sudo systemctl start tcbot
sudo systemctl status tcbot
```

If the rollback target is a very old version, restore and use the old Java version from the same backup notes. Do not
try to run a 2022-era runtime with the new Java 17 service file unless it was already tested that way.

## 10. Migration from old 2022-era installations

Older deployments may have been built and run with Java 8 or Java 11 and an old Gradle version. Treat that upgrade as a
migration, not as a simple overwrite.

1. Keep the old environment available.

   Before changing the live service, record where the old Java and old runtime are:

   ```bash
   readlink -f "$(command -v java)"
   java -version
   ls -la /data/tcbot
   ```

   If rollback is needed, use the old runtime backup together with the old Java and old Gradle versions. Make sure the
   old Gradle is still available in `PATH` before relying on rollback or on rebuilding the old version. Build the new
   checkout with Java 17 and a tested Gradle version: 9.5 or 9.4.1. Do not assume the old backup will run correctly on
   Java 17.

   If several historical builds must stay available, prefer versioned runtime directories and a symlink, for example
   `latest -> 2.17`.

2. Install the new source and new runtime into new paths first.

   Use separate new directories for the checkout, runtime, and work directory. Do not delete the old runtime directory
   until the new service has started successfully and the backup has been verified.

3. Stop the old bot and back up everything.

   Back up the old runtime and the whole old work directory before touching the database. The important database files
   are inside the work directory, commonly under `work/db` for current versions and sometimes under `work/tcbot_srv` in
   old versions.

4. Copy the old work directory into the new location.

   If the old work directory is elsewhere, copy it to `/data/tcbot/work` while the bot is stopped:

   ```bash
   rm -rf /data/tcbot/work
   cp -a <old-work-dir> /data/tcbot/work
   ```

5. Start the new Java 17 service.

   Start with the systemd unit from this document. Watch `journalctl -u tcbot` and `/data/tcbot/work/tcbot_logs`.
   The first start may run built-in database checks and migrations during Ignite startup; let it finish instead of
   restarting repeatedly. Do not run the offline migrator directly as part of the normal installation procedure.

6. Roll back as a full set if the migration fails.

   Stop the new service, restore the old runtime and old work directory from the same timestamped backup, and start it
   with the old Java/runtime setup. Do not restore only the runtime while keeping a database already opened or migrated
   by the new version.

Local clean checks and emulated bot runs are documented in [Testing](testing.md).
