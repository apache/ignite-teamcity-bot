# ignite-teamcity-bot

[Apache Ignite Teamcity Bot](https://cwiki.apache.org/confluence/display/IGNITE/Apache+Ignite+Teamcity+Bot) (MTCGA.bot) is [Apache Ignite](https://ignite.apache.org/)-based cache over [JetBrains TeamCity](https://jetbrains.ru/products/teamcity/) server(s).

This tool intended to monitor [Apache Ignite Teamcity](https://ci.ignite.apache.org/) where [Apache Ignite](https://ignite.apache.org/) is tested.

TC tool should help to avoid new failures introduction into master and provide ways of its early detection.

Major use cases are following:
* Continuous monitoring of master and/or release branch
* Check branch/PR changes to new failures
* MCTGA Bot for slack and for email notifications.

This tool is available on [https://mtcga.gridgain.com/](https://mtcga.gridgain.com/) - requires apache CI credentials.

Should you have any questions please contact Ignite Developers at dev@ignite.apache.org or dpavlov@apache.org

## Development
### Project setup
Locally code can be set up using IntelliJ idea and gradle project import.
Locally it can be run using org.apache.ignite.ci.web.Launcher.main() method.
The bot will create necessary configs in ~/.ignite-teamcity-helper - it is bot Home directory.
In can be changed with org.apache.ignite.ci.tcbot.TcBotSystemProperties.TEAMCITY_HELPER_HOME system property.

Examples of configs can be found in [conf](conf) directory. 
Main config file is [conf/branches.json](conf/branches.json). This file needs to be placed to work directory, (under user home by default).
Extra setup is required using security-sensitive information using PasswordEncoder. No Teamcity credentials is required because TC bot asks users to enter creds.

### Code inspections, styles and abbreviation rules.
[Code style](https://cwiki.apache.org/confluence/display/IGNITE/Coding+Guidelines) is inherited from Apache Ignite.
Please install following components for development using IntelliJ IDEA
* Install [Abbreviation Plugin](https://cwiki.apache.org/confluence/display/IGNITE/Abbreviation+Rules#AbbreviationRules-IntelliJIdeaPlugin)
* Apply [Code Inspection Profile](https://cwiki.apache.org/confluence/display/IGNITE/Coding+Guidelines#CodingGuidelines-C.CodeInspection)
* Configure [IDEA Codestyle](https://cwiki.apache.org/confluence/display/IGNITE/Coding+Guidelines#CodingGuidelines-A.ConfigureIntelliJIDEAcodestyle)

### Build
Build can be done using following commands
- gradle clean
- gradle build

It is recommended to use Java 8 for development.

It may be required to install 
[Java Cryptography Extension JCE Unlimited Strength Jurisdiction Policy Files 8 Download](https://www.oracle.com/technetwork/java/javase/downloads/jce8-download-2133166.html)
because the Bot uses strong AES cryptography, but default java distribution may limit AES256 usage.


Resulting distribution can be found in projectRoot\jetty-launcher\build\distributions.
Distribution will contain start script in \bin folder.

### Internal Design
Apache Ignite TC Bot interacts with several data sources to find out current state and defails of contribution.

<img src='https://docs.google.com/drawings/d/e/2PACX-1vTbvhVlSrpo-KA8V5jTL5ogRrpsx_21ByzviOps58-Yw8gV3qz9buS3nEBJvxXZdJWzUZryQjscfiCs/pub?w=976&h=627'>

Teamcity Bot Components and its interactions

<img src="https://docs.google.com/drawings/d/e/2PACX-1vQM6tH6-pb6C_JGjNG41sUBJP72CVpNqeBHIQdgaGYL4rGoYfZtywwzVB1JKF1Kk8haXUVl_IORI6NQ/pub?w=1356&h=733">