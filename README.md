# ignite-teamcity-bot

[Apache Ignite Teamcity Bot](https://cwiki.apache.org/confluence/display/IGNITE/Apache+Ignite+Teamcity+Bot) (MTCGA.bot) is [Apache Ignite](https://ignite.apache.org/)-based cache over [JetBrains TeamCity](https://jetbrains.ru/products/teamcity/) server(s).

This tool intended to monitor [Apache Ignite Teamcity](https://ci.ignite.apache.org/) where [Apache Ignite](https://ignite.apache.org/) is tested.

TC tool should help to avoid new failures introduction into master and provide ways of its early detection.

Major use cases are the following:
* Continuous monitoring of master and/or release branch
* Check branch/PR changes to new failures
* MCTGA Bot for slack and for email notifications.

User-facing bot rules and workflows are documented in [TeamCity bot user guide](docs/teamcity-bot-user-guide.md).
Production build and deployment are documented in [Build and installation](docs/install.md).
Local clean checks and emulated bot runs are documented in [Testing](docs/testing.md).

This tool is available on [https://mtcga.gridgain.com/](https://mtcga.gridgain.com/) - requires apache CI credentials.

Should you have any questions, please contact Ignite Developers at dev@ignite.apache.org or dpavlov@apache.org

## Development
### Project setup
Local code can be set up using IntelliJ IDEA and Gradle project import.

For local development, run `org.apache.ignite.ci.web.Launcher.main()` from the project root.
The launcher starts Jetty on `http://localhost:8080/` and serves static web resources directly from
`ignite-tc-helper-web/src/main/webapp`.
When running this main class directly from an IDE on Java 17, use the same module options as the
`igniteJava17JvmArgs` Gradle property:

```
-XX:+IgnoreUnrecognizedVMOptions
--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED
--add-exports=java.base/sun.nio.ch=ALL-UNNAMED
--add-exports=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED
--add-exports=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED
--add-exports=java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED
--add-opens=java.base/java.io=ALL-UNNAMED
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.nio=ALL-UNNAMED
--add-opens=java.base/java.time=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=java.base/java.util.concurrent=ALL-UNNAMED
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED
```

The bot creates its working directory at `~/.ignite-teamcity-helper` by default. The directory contains
runtime data and local configuration files. The location can be changed with the
`teamcity.helper.home` system property (`TcBotSystemProperties.TEAMCITY_HELPER_HOME`), for example:

```
-Dteamcity.helper.home=/path/to/local/tc-bot-work
```

Examples of configs can be found in [conf](conf) directory. 
Main config file is [conf/branches.json](conf/branches.json). This file needs to be placed to work directory, (under user home by default).
The running bot reloads `branches.json` lazily: configuration reads are cached for up to 3 minutes, so most changes
become visible without a restart after the cache expires. Restart the bot only when you need the change to take effect
immediately.
Extra setup is required using security-sensitive information using PasswordEncoder. No TeamCity credentials are required because TC bot asks users to enter creds.

Minimal local run checklist:
* Import the Gradle project into IntelliJ IDEA.
* Copy `conf/branches.json` to the bot working directory, or prepare another `branches.json` there.
* Adjust TeamCity, JIRA, GitHub, and notification settings in the copied config.
* Run `org.apache.ignite.ci.web.Launcher.main()`.
* Open `http://localhost:8080/`, log in with actual TeamCity credentials, and add service credentials on the user page when a configured service requires them.
* Use the `Authorize Server` action in the top menu when you need background jobs, triggering, JIRA comments, notifications, or queue checks to run under your current TeamCity credentials.

Server authorization is kept in memory. If the local process is restarted, log in and authorize the server again.

### Code inspections, styles and abbreviation rules.
[Code style](https://cwiki.apache.org/confluence/display/IGNITE/Coding+Guidelines) is inherited from Apache Ignite.
Please install following components for development using IntelliJ IDEA
* Install [Abbreviation Plugin](https://cwiki.apache.org/confluence/display/IGNITE/Abbreviation+Rules#AbbreviationRules-IntelliJIdeaPlugin)
* Apply [Code Inspection Profile](https://cwiki.apache.org/confluence/display/IGNITE/Coding+Guidelines#CodingGuidelines-C.CodeInspection)
* Configure [IDEA Codestyle](https://cwiki.apache.org/confluence/display/IGNITE/Coding+Guidelines#CodingGuidelines-A.ConfigureIntelliJIDEAcodestyle)

### Internal Design
Main bot logic is placed in [ignite-tc-helper-web](ignite-tc-helper-web) module. 
[jetty-launcher](jetty-launcher) is an application module to start bot in production.

Apache Ignite TC Bot interacts with several data sources to find out current state and details of contribution.

<img src="https://docs.google.com/drawings/d/e/2PACX-1vTbvhVlSrpo-KA8V5jTL5ogRrpsx_21ByzviOps58-Yw8gV3qz9buS3nEBJvxXZdJWzUZryQjscfiCs/pub?w=488&amp;h=313">

TeamCity Bot Components and its interactions

<img src="https://docs.google.com/drawings/d/e/2PACX-1vQM6tH6-pb6C_JGjNG41sUBJP72CVpNqeBHIQdgaGYL4rGoYfZtywwzVB1JKF1Kk8haXUVl_IORI6NQ/pub?w=1356&h=733">

### Modules structure
Static content is placed in [webapp](ignite-tc-helper-web/src/main/webapp).

TC Bot services can be found in [tcbot-engine](tcbot-engine)

TC Bot integrations are placed in corresponding submodules

| Data Source | Pure Integration | Persistence-enabled |
| ----------- | ---------------- | ------------------- |
| Teamcity | [tcbot-teamcity](tcbot-teamcity) | [tcbot-teamcity-ignited](tcbot-teamcity-ignited) |
| JIRA | [tcbot-jira](tcbot-jira) | [tcbot-jira-ignited](tcbot-jira-ignited)  |
| GitHub | [tcbot-github](tcbot-github) | [tcbot-github-ignited](tcbot-github-ignited)  |

## GridIntList migration

The `migrate-GridIntList` database migration updates persisted TeamCity Bot data after replacing Ignite's internal
`org.apache.ignite.internal.util.GridIntList` with the project-owned
`org.apache.ignite.tcbot.common.util.GridIntList`.

During startup, `DbMigrations` runs `GridIntListMigrator.migrateOnInstance` once and stores the migration marker only
after the scan finishes successfully. By default the migrator scans only the caches whose persisted value graph is known
to contain compacted TeamCity parameters or statistics backed by `GridIntList`:

| Cache | Persisted GridIntList path |
| ----- | -------------------------- |
| `teamcityFatBuild` | `FatBuildCompacted.buildParameters`, `FatBuildCompacted.statistics` |
| `teamcityFatBuildType` | `BuildTypeCompacted.settings`, `BuildTypeCompacted.parameters`, snapshot dependency properties |
| `teamcitySuiteHistory` | `SuiteInvocation.suite/tests -> Invocation.parameters` |

Within those caches the migrator iterates over entries in keep-binary mode, recursively checks cache values, nested
binary objects, lists, sets, maps, and object arrays, and rebuilds only values that contain the legacy `GridIntList`
type. The standalone migrator's `--cache` option is an explicit offline override for targeted diagnostics.

For each legacy list, the migration preserves the logical list contents, not the backing array capacity. If normal
deserialization is available, it reads the old object through `GridIntList.array()`. If binary fallback is needed, it
reads both persisted fields, `arr` and `idx`, validates that `idx` is inside the backing array bounds, and copies only
`arr[0..idx)`. The copied values are then written as the new TC Bot `GridIntList` type.

The migration is intentionally fail-fast from the database marker point of view. Per-entry failures are logged with the
cache name and key, counted, and reported after the scan. Failed entries are also written as an Ignite dump plus a small
manifest under `<ignite-work>/diagnostic/grid-int-list-migration-recovery`. If any entry still cannot be repaired, the
`migrate-GridIntList` marker is not written to `apache.doneMigrations`, so the issue can be fixed and the migration can
be retried instead of silently leaving mixed old and new data.

The same migrator can also be run as a standalone tool from the `migrator` module against an Ignite work directory. The
standalone module uses the same Ignite version as the rest of the project through the shared `ignVer` Gradle property.

Heavyweight persistent-storage integration tests are excluded from the regular `test` and `build` tasks. Run them
explicitly with `gradle :migrator:integrationTest --no-daemon` when checking old Ignite 2.14 persistent storage
compatibility or migration recovery for corrupted binary metadata.
