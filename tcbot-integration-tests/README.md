# TC Bot integration tests

This module contains black-box integration tests for the TC Bot business flow. The tests are intentionally not part of
the default `build` or `check` lifecycle; run them explicitly:

```bash
./gradlew :tcbot-integration-tests:integrationTest
```

The module depends on the production WAR from `:ignite-tc-helper-web:war` and the production-like launcher distribution
from `:jetty-launcher:installDist`.

## Local IDEA launcher

Use the shared IDEA run configuration `Emulated TC Bot` from `.run/Emulated TC Bot.run.xml`, or run:

```bash
./gradlew :tcbot-integration-tests:runEmulatedBot
```

The launcher starts separate Python emulator processes for GitHub, JIRA, and TeamCity, recreates an isolated bot work
directory under `tcbot-integration-tests/build/emulated-bot/work`, and starts the production launcher class against the
built WAR. Open the UI at `http://127.0.0.1:5555/`.

Press Enter in the launcher console to stop the bot and all Python emulators cleanly. If the launcher is started from a
non-interactive Gradle process and standard input closes, it also shuts the emulated environment down and exits
successfully.

Login immediately after opening the link:

* User: `ignite.tester`
* Password: `ignite-password`

## Ports and profile

Default local ports:

* Bot WAR: `5555`, via `-Dtcbot.http.port=5555`.
* Bot Ignite discovery: `55433`, via `-Dtcbot.ignite.discovery.port=55433`.
* GitHub emulator: `8011`.
* JIRA emulator: `8012`.
* TeamCity emulator: `8013`.
* Local emulator control REST: `8010`.

The bot must be started with:

* `-Dtcbot.profile=integration-test`
* `-Dtcbot.ignite.inMemory=true`

Bot test-only API is exposed only in this profile. It must only kick the bot to refresh its own state from configured
services:

* `POST /rest/__test__/bot/refresh-github`
* `POST /rest/__test__/bot/refresh-jira`
* `POST /rest/__test__/bot/refresh-teamcity-builds`
* `POST /rest/__test__/bot/run-build-observer`

Without the `integration-test` profile these endpoints return `404` and must be treated as unavailable.

Data mutation belongs to the emulator process, not to the bot:

* `POST http://127.0.0.1:8011/__test__/github/create-pr`
* `POST http://127.0.0.1:8011/__test__/github/reset`
* `POST http://127.0.0.1:8012/__test__/jira/create-issue`
* `POST http://127.0.0.1:8012/__test__/jira/reset`
* `POST http://127.0.0.1:8013/__test__/teamcity/complete-build`
* `POST http://127.0.0.1:8013/__test__/teamcity/reset`

The local IDEA launcher also exposes a control REST endpoint for Python process management. Use it after editing an
emulator script when the bot itself does not need to restart:

* `POST http://127.0.0.1:8010/__test__/emulators/restart?service=github`
* `POST http://127.0.0.1:8010/__test__/emulators/restart?service=jira`
* `POST http://127.0.0.1:8010/__test__/emulators/restart?service=teamcity`
* `GET http://127.0.0.1:8010/__test__/emulators/status`

## External calls

Integration tests must not call real GitHub, JIRA, TeamCity, Slack, or mail services. All URLs in
`src/integrationTest/resources/branches.json` point to `127.0.0.1`, and Slack/mail settings are intentionally empty.
Any unexpected request to an emulator returns an error so the test fails near the real cause.

Emulators should answer in the same wire format as the real service endpoint. TeamCity REST endpoints are XML unless a
real TeamCity endpoint is known to return JSON; GitHub and JIRA endpoints are JSON. The emulators log every request as
`[service:port] METHOD path -> status content-type` so missing or wrong-format endpoints are visible in the launcher
output.

Executable integration suites use one shared `IntegrationTestEnvironment` per Gradle `integrationTest` JVM. It starts
all service emulators and the WAR bot once, reuses them across test classes, and stops them from a JVM shutdown hook.
Per-test setup should mutate emulator state through emulator-only hooks when a scenario needs new PRs, JIRA issues, or
TeamCity builds.

## Current scenarios

Each emulator script keeps a minimal starter data set at the top of the file. Keep it as scenario state, not as copied
REST response payloads: if a field can be derived by an endpoint, such as URLs, `self`, `html_url`, branch names, build
hrefs, or commit URLs, the endpoint renderer should build it from the state.

* `IGNITE-20001` has PR `#12001`.
* `IGNITE-20002` is a ticket without PR.
* PR `#12003` intentionally has no JIRA ticket marker.
* `IGNITE-20005` has PR `#12005` with an already finished successful RunAll build `800101` on
  `pull/12005/head` and starter JIRA/GitHub visa comments.
* TeamCity has master history for deterministic flaky, random flaky, known marker flaky, and hanging-suite cases.
* RunAll contains several child suites so tests can model two- and three-level TeamCity chains.
* TeamCity UI links produced by bot pages, such as
  `http://127.0.0.1:8013/buildConfiguration/IgniteTests24Java17_RunAll?branch=%3Cdefault%3E` and
  `viewLog.html?buildId=...`, return text-only emulator pages with the relevant test set and build result.

The executable tests currently verify the harness contract and the first bot flow: login, trigger build queue, and
complete the queued build through the TeamCity emulator hook.
