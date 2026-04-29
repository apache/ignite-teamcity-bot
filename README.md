# ignite-teamcity-bot

[Apache Ignite Teamcity Bot](https://cwiki.apache.org/confluence/display/IGNITE/Apache+Ignite+Teamcity+Bot) (MTCGA.bot) is [Apache Ignite](https://ignite.apache.org/)-based cache over [JetBrains TeamCity](https://jetbrains.ru/products/teamcity/) server(s).

This tool intended to monitor [Apache Ignite Teamcity](https://ci.ignite.apache.org/) where [Apache Ignite](https://ignite.apache.org/) is tested.

TC tool should help to avoid new failures introduction into master and provide ways of its early detection.

Major use cases are the following:
* Continuous monitoring of master and/or release branch
* Check branch/PR changes to new failures
* MCTGA Bot for slack and for email notifications.

User-facing bot rules and workflows are documented in [TeamCity bot user guide](docs/teamcity-bot-user-guide.md).

This tool is available on [https://mtcga.gridgain.com/](https://mtcga.gridgain.com/) - requires apache CI credentials.

Should you have any questions, please contact Ignite Developers at dev@ignite.apache.org or dpavlov@apache.org

## Development
### Project setup
Local code can be set up using IntelliJ IDEA and Gradle project import.

For local development, run `org.apache.ignite.ci.web.Launcher.main()` from the project root.
The launcher starts Jetty on `http://localhost:8080/` and serves static web resources directly from
`ignite-tc-helper-web/src/main/webapp`.

The bot creates its working directory at `~/.ignite-teamcity-helper` by default. The directory contains
runtime data and local configuration files. The location can be changed with the
`teamcity.helper.home` system property (`TcBotSystemProperties.TEAMCITY_HELPER_HOME`), for example:

```
-Dteamcity.helper.home=/path/to/local/tc-bot-work
```

Examples of configs can be found in [conf](conf) directory. 
Main config file is [conf/branches.json](conf/branches.json). This file needs to be placed to work directory, (under user home by default).
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

### Build
A build can be done using following commands
- gradle clean
- gradle build

It is recommended to use Java 11 for development.

It may be required to install 
[Java Cryptography Extension JCE Unlimited Strength Jurisdiction Policy Files 8 Download](https://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html)
because the Bot uses strong AES cryptography, but default java distribution may limit AES256 usage.


Resulting distribution can be found in projectRoot\jetty-launcher\build\distributions.
Distribution will contain start script in \bin folder.

### Running in production
Production mode is started from the `jetty-launcher` distribution. Build the distribution first:

```
gradle clean build
```

Unpack the archive from `jetty-launcher/build/distributions` on the target host. The production launcher
is `org.apache.ignite.ci.TcHelperJettyLauncher`; it starts the same web application from the packaged WAR.
The generated start scripts set the default working directory to `../work` via
`-Dteamcity.helper.home=../work`, so place production `branches.json` and other required configuration
files into that `work` directory, or override `teamcity.helper.home` with the desired production path.

When the bot is installed as a service, start or restart `tc-bot-service` after deploying a new build or
changing configuration:

```
systemctl start tc-bot-service
systemctl restart tc-bot-service
systemctl status tc-bot-service
```

After `tc-bot-service` is up, open the production bot URL, log in with TeamCity credentials, and click
`Authorize Server` in the top menu. This step is required for background operations that need TeamCity
access, including background checks, queue checks, build triggering, JIRA notifications, and cleanup.

Server authorization is not stored in `branches.json`; it is taken from the authenticated user session and
kept by the running bot process. Re-authorize the server after each service restart, deployment, or process
crash.

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

