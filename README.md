# ignite-teamcity-helper

Ignite TC Helper/MTCGA.bot is [Apache Ignite](https://ignite.apache.org/)-based cache over [JetBrains TeamCity](https://jetbrains.ru/products/teamcity/) server(s).

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
In can be changed with org.apache.ignite.ci.ITcHelper.TEAMCITY_HELPER_HOME system property.

Examples of configs can be found in [conf](conf) directory.

### Build
Build can be done using following commands
- gradle clean
- gradle build

Resulting distribution can be found in projectRoot\jetty-launcher\build\distributions.
Distribution will contain start script in \bin folder.

