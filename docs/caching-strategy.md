# TC Bot internals: caching strategy

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
