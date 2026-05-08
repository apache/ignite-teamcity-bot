# Migrator

The `migrator` module contains local tools and heavyweight checks for TeamCity Bot persistent storage migrations.

## GridIntList Migration

The `migrate-GridIntList` database migration updates persisted TeamCity Bot data after replacing Ignite's internal
`org.apache.ignite.internal.util.GridIntList` with the project-owned
`org.apache.ignite.tcbot.common.util.GridIntList`.

During startup, `DbMigrations` runs `GridIntListMigrator.migrateOnInstance` once and stores the migration marker only
after the scan finishes successfully. The migrator iterates over Ignite cache entries in keep-binary mode, recursively
checks cache values, nested binary objects, lists, sets, maps, and object arrays, and rebuilds only values that contain
the legacy `GridIntList` type.

For each legacy list, the migration preserves the logical list contents, not the backing array capacity. If normal
deserialization is available, it reads the old object through `GridIntList.array()`. If binary fallback is needed, it
reads both persisted fields, `arr` and `idx`, validates that `idx` is inside the backing array bounds, and copies only
`arr[0..idx)`. The copied values are then written as the new TC Bot `GridIntList` type.

The migration is intentionally fail-fast from the database marker point of view. Per-entry failures are logged with the
cache name and key, counted, and reported after the scan. If any entry fails, the migration throws an exception and the
`migrate-GridIntList` marker is not written to `apache.doneMigrations`, so the issue can be fixed and the migration can
be retried instead of silently leaving mixed old and new data.

The same migrator can also be run as a standalone tool from the `migrator` module against an Ignite work directory. The
standalone module uses the same Ignite version as the rest of the project through the shared `ignVer` Gradle property.

## Legacy DB Compatibility Perf Test

The heavyweight legacy persistent storage compatibility test is not part of the regular `:migrator:test` task. Run it
explicitly when you need to validate old Ignite persistent storage, WAL compatibility, and current migrations:

```
./gradlew :migrator:legacyDbCompatPerfTest --no-daemon
```

On Windows:

```
gradlew.bat :migrator:legacyDbCompatPerfTest --no-daemon
```

The test creates a local database with the legacy bot checkout before `IGNITE-27101`, using Ignite 2.14 and Java 8 or
11, runs old migrations to populate `apache.doneMigrations`, then starts the current code on the same persistent store.
It verifies that the current migrator applies `migrate-GridIntList`, starts without Ignite failure handler errors, and
sweeps all user cache entries to force key and value deserialization.

The generated persistent store is left under `migrator/src/test/work/ignite-db-compat` by default for local inspection.
Override the location with:

```
-Dcompat.work.dir=/path/to/ignite-db-compat
```

