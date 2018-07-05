# ignite-teamcity-helper

Ignite TC Helper/MTCGA.bot is [Apache Ignite](https://ignite.apache.org/)-based cache over [JetBrains TeamCity](https://jetbrains.ru/products/teamcity/) server(s).

This tool intended to monitor [Apache Ignite Teamcity](https://ci.ignite.apache.org/) where [Apache Ignite](https://ignite.apache.org/) is tested.

TC tool should help to avoid new failures introduction into master and provide ways of its early detection.

Major use cases are following:
* Continuous monitoring of master and/or release branch
* Check branch/PR changes to new failures
* MCTGA Bot for slack and for email notifications.

This tool is available on [https://mtcga.gridgain.com/](https://mtcga.gridgain.com/) - requires apache CI credentials.

Should you have any questions please contact dpavlov@apache.org