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

For local development, use one of the shared IDEA run configurations:

* `TC Bot Local - Live Services` starts the server directly from Java classes and uses configured real services.
* `TC Bot Local - Stub Services` starts GitHub, JIRA, and TeamCity stubs, then runs the bot server code in the same JVM.
  Use it for normal debugging: breakpoints hit the server code, static resources are read from source on every request,
  and Python stubs can be restarted through the control REST without restarting the bot.
* `TC Bot WAR - Live Services` runs the production-like WAR launcher against configured real services. Its before-run
  Gradle step builds `:ignite-tc-helper-web:war` and prepares `jetty-launcher/build/install/jetty-launcher`.
* `TC Bot WAR - Stub Services` is the Gradle production-like stub-services run that starts the bot from the built WAR.

For command-line production-like emulator checks, run:

```
./gradlew :tcbot-integration-tests:runEmulatedTcBotWar
```

`WAR` configurations use Gradle-built WAR artifacts. `Local` configurations are live Java runs intended for IDE
debugging. `Live Services` uses configured external services; `Stub Services` starts local Python service stubs. Refresh
the browser for HTML/JS/CSS changes; restart the Java run only for Java changes.

When running Java main classes directly from an IDE on Java 17, use the same module options as the
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
JIRA and GitHub tokens can be specified as plain text in `branches.json` or protected with `PasswordEncoder`.
When `authTokEncoded` is not set, the bot auto-detects encoded hex values and otherwise treats tokens as plain.
Set `authTokEncoded` only when you need to force a mode. For JIRA Personal Access Tokens, use
`authScheme: "Bearer"`; legacy base64 username/password tokens can still use `authScheme: "Basic"`. If
JIRA `authScheme` is omitted, encoded tokens default to Basic for compatibility and plain tokens default to Bearer.
No TeamCity credentials are required because TC bot asks users to enter creds.

Minimal local run checklist:
* Import the Gradle project into IntelliJ IDEA.
* Copy `conf/branches.json` to the bot working directory, or prepare another `branches.json` there.
* Adjust TeamCity, JIRA, GitHub, and notification settings in the copied config.
* Run `TC Bot WAR - Live Services` for a production-like WAR run, or `TC Bot Local - Stub Services` for local stub-backed UI work.
* Open `http://localhost:8080/` for `Live Services` runs, or `http://127.0.0.1:5555/` for `Stub Services` runs.
* Log in with actual TeamCity credentials for real-service runs, and add service credentials on the user page when a configured service requires them.
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

Internal storage and cache notes live in [TC Bot internals: caching strategy](docs/caching-strategy.md).
