# TC Bot internals: caching strategy

## Layers

TC Bot uses two different kinds of cache, and they answer different questions:

- Ignite persistent caches are the bot's local database. They survive process restart and are the source of truth for
  already synchronized TeamCity, JIRA, GitHub, visa, defect, and user data.
- Guava in-memory caches are local accelerators. They are intentionally short-lived, process-local, and disposable.
  They may contain complete data for a narrow already-known slice, or only a temporary overlay that has not yet reached
  the persistent layer.

The important rule is that "cached by Guava" does not always mean "globally complete". When a caller needs correctness
across restart or across a wider history window, it must rely on the Ignite cache or explicitly ask the source service
again.

## TeamCity build refs

Build refs are the short TeamCity records used to know that a build exists, what suite/build type it belongs to, what
branch it ran on, and whether it is queued, running, finished, cancelled, or successful.

The persistent layer is `BuildRefDao.buildRefsCache`, the Ignite cache named `teamcityBuildRef`. It is keyed by
`serverId + buildId`, so there is only one persistent record per TeamCity build id per server. This cache is filled by
`BuildRefSync.runActualizeBuildRefs(...)`, which reads the global TeamCity builds feed through
`ITeamcityConn.getBuildRefsPage(null, ...)`. That feed starts from
`app/rest/latest/builds?locator=defaultFilter:false` and then follows TeamCity `nextHref` pages. In other words, the
normal actualizer is not filtered by suite or branch; it moves through the global stream of build refs for the server.

`BuildRefDao` then keeps several Guava views over that persistent cache:

| Cache | Key | Contents | Completeness |
| ----- | --- | -------- | ------------ |
| `buildRefsInMemCacheForAllBranch` | `serverId + branchId` | Persistent build refs for one branch. | Complete only relative to what is already in `teamcityBuildRef`. It is not a fresh TeamCity query. |
| `buildRefsInMemCache` | `serverId + suiteId + branchId` | Persistent build refs for one suite on one branch, derived from the branch view. | Complete only relative to the persistent cache. |
| `temporaryBuildRefsInMemCache` | `serverId + suiteId + branchId` | Refs found by direct TeamCity suite/branch lookup from the PR report fallback. | Partial, process-local, and temporary. It contains only what that direct lookup found. |

The normal read path `getAllBuildsCompacted(...)` first loads the persistent suite/branch slice and then overlays
temporary refs for the same key, skipping ids already present in the persistent slice. This lets the UI see a build that
TeamCity knows about even if the global incremental sync has not reached that build yet.

The direct PR-page recheck is deliberately narrow: it queries TeamCity by `buildTypeId + branchName`, pages through a
bounded number of recent results, and stores what it finds in `temporaryBuildRefsInMemCache`. That cache is not meant to
replace the global actualizer. It is a fast bridge for a concrete report page.

There is one edge case where a temporary ref should become persistent: the global actualizer may already have scanned
past the build id and therefore will not pick it up later. After each build-ref sync, the bot records the oldest build
id reached by that sync and promotes temporary refs below that horizon into `teamcityBuildRef` with `putIfAbsent`.
Existing persistent records are never overwritten by temporary data. Promoted refs are also scheduled for fat-build
loading, because a build ref alone is not enough for full report analysis.

Whenever persistent build refs are added or changed, `BuildRefDao.invalidateHistoryInMem(...)` clears the affected
Guava branch/suite views and increments the branch update counter. Temporary refs also increment the branch counter so
already-open report pages can see that their branch data changed.

## TeamCity fat builds and history

Build refs are only the index. Detailed reports use larger persistent TeamCity records:

- `teamcityFatBuild` stores loaded build details, parameters, statistics, dependencies, and related data.
- `teamcityFatBuildType` stores full TeamCity build type metadata.
- `teamcitySuiteHistory` stores compacted suite/test invocation history derived from loaded builds.

These caches are persisted in Ignite. Some services put short Guava views over them for speed, but those views should be
treated as derived snapshots. If the underlying Ignite data changes, the relevant service must invalidate or naturally
expire the view.

## Build log analysis

Build log checks have two layers:

- `buildLogCheckResult` is the persistent Ignite cache for compacted log analysis.
- `BuildLogProcessor.logCheckResultCache` is a small Guava cache with a short access TTL and soft values.

If a build has no downloadable log or log processing fails, the bot stores an empty `LogCheckResultCompacted` to avoid
retrying the same broken log forever. Each stored empty result is a fresh object; the bot must not reuse one mutable
singleton across different cache entries.

## JIRA and GitHub

JIRA and GitHub ignited connectors also use Ignite as their persistent local database:

- JIRA tickets are stored in `jiraTickets`.
- GitHub pull requests are stored in `gitHubPr`.
- GitHub branches are stored in `gitHubBranch`.
- GitHub users are stored in `gitHubUsers`.

Provider and DAO methods may use Guava to avoid repeatedly constructing connector instances or repeatedly scanning a
small slice, but those Guava entries are convenience caches over the persisted Ignite data or over the configured service
connection. They should not be treated as independent durable state.

## Configuration and computed views

Some non-source data is intentionally cached only briefly:

- Local JSON configuration reads are Guava-cached for a few minutes, so `branches.json` changes usually become visible
  without restart after the cache expires.
- Branch-to-ticket matching and trend computations use Guava caches because they are derived from persistent/source data
  and are cheap to rebuild compared with keeping them durable.
- Monitoring cache preview is diagnostic only. It reads Ignite caches directly and should not be used as an application
  data path.

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
explicitly with `./gradlew :migrator:integrationTest --no-daemon` when checking old Ignite 2.14 persistent storage
compatibility or migration recovery for corrupted binary metadata.
