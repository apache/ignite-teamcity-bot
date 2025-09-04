# Migrator: Ignite internal API GridIntList → TCbot util GridIntList

Purpose:
- Offline tool to migrate TeamCity Bot Ignite persistence (work/*), replacing legacy ```org.apache.ignite.internal.util.GridIntList``` with ```org.apache.ignite.tcbot.common.util.GridIntList```
- Works directly on binary data (keepBinary), recursively transforms fields/collections/maps/arrays and rewrites only changed entries.
- Supports dry-run (no writes) and apply (write changes) modes.
- Safe to run on a copy of work/ directory and robust to partial errors (skips bad entries, logs warnings).

Safety:
- Stop tcbot before migration.
- Always run on a copy of work/: never point at a live folder.
- Never run in parallel with tcbot or another migrator on the same persistence.

Requirements:
- JDK 11+
- Gradle wrapper from tcbot repo
- New GridIntList on classpath of migrator (org.apache.ignite.tcbot.common.util.GridIntList)

Build:
- From repo root:
    - ```./gradlew -p migrator clean build```

Quick start (macOS/Linux):
1) Backup work:
    - ```cp -a </path/to/tcbot/work> </path/to/work_backup>```
2) Dry-run with verbose report (no changes apply):
    - ```export IGNITE_WORK_DIR=</path/to/work_backup>```
    - ```./gradlew -p migrator run --args="--verbose"```
3) Apply (write changes):
    - ```export IGNITE_WORK_DIR=</path/to/work_backup>```
    - ```./gradlew -p migrator run --args="--apply"```
    - Optional: focus on a cache: `````--cache <cacheName>`````

CLI arguments:
- ```--apply```           write changes (otherwise dry-run)
- ```--verbose```         print extra diagnostics
- ```--cache <substr>```  process only caches whose name contains the substring
- ```--report <N>```      progress interval (log every N scanned entries)
- ```--workDir <path>```  path to work/ directory (overrides IGNITE_WORK_DIR)

How it works:
- Scans caches with ScanQuery in keepBinary mode (values as BinaryObject).
- Recursively traverses value graphs:
    - If a node is legacy GridIntList (BinaryObject or Java object) → extract int[] → build new GridIntList (fallback to int[] if class missing).
    - For BinaryObject parents → rebuild with BinaryObjectBuilder only if any child changed.
    - For List/Set/Map/Object[] → transform elements and rebuild container only if needed (stable order via LinkedHashSet/LinkedHashMap).
- Writes back only changed entries.

What it changes / does not change:
- Changes: any occurrence of legacy GridIntList in values (fields, collections, arrays) is replaced with the new type (or int[] fallback).
- Does NOT change: cache keys, unrelated fields/types, untouched caches.

Verification:
- Migrator logs per-cache summary: ```scanned=``` ```updated=```

Notes:
- The migrator guards recursion (MAX_DEPTH=32). It logs and skips overly deep branches to stay robust. MAX_DEPTH is
eligible to be tuned due specific cache.
- The tool logs with SLF4J (console). Deteministic iteration order is preserved for Set/Map to keep binary output stable.