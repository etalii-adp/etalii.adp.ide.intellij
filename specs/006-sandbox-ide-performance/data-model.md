# Data Model: A Fast Sandbox IDE

This feature adds no persisted data to the plug-in. Its entities are folders and one record of measurements.

## Sandbox

The IDE started by `./gradlew runIde`, under `.intellijPlatform/sandbox/EtAlii.Adp.IntelliJ/<platform>/`.

| Folder | Holds | Changed by this feature |
|---|---|---|
| `config_runIde` | settings, recent projects | no |
| `system_runIde` | indexes and caches | no, but stays small because the project is small |
| `plugins_runIde` | the built plug-in | no |
| `log_runIde` | `idea.log`, freeze reports, indexing diagnostics | now also heap dumps (`java_pid<pid>.hprof`) and crash logs (`hs_err_pid<pid>.log`) |
| `example-project` | copies of the example `.mm` and `.drawio` files | new |

Rules: a sandbox run never writes outside these folders (SC-005). `example-project` is refreshed from the vendored examples on every run; files a contributor adds there are kept.

## Test IDE home

Where the IDE Starter framework keeps the IDEs the real-IDE tests download and the folders each test runs in.

| Field | Value |
|---|---|
| Location | `adp.ideTests.home`: `%LOCALAPPDATA%\etalii-adp\ide-tests-home` on Windows, `${XDG_CACHE_HOME:-~/.cache}/etalii-adp/ide-tests-home` elsewhere, unless overridden |
| Layout below it | `out/ide-tests/installers`, `out/ide-tests/cache`, `out/ide-tests/tests` (Starter's own) |
| Shared by | every clone and worktree of this repository for the same user |

Rule: never inside the repository (FR-009). A test fails if the resolved location is inside `adp.repository`.

## Performance baseline

A table in `quickstart.md`, one row per measurement run.

| Field | Meaning |
|---|---|
| Date, commit | when and on what the run was taken |
| Machine | processors and memory |
| Ready (s) | `IDE STARTED` to the example map shown (SC-001) |
| Indexed files, indexing (s) | from `indexing-diagnostic` (SC-002) |
| Used heap after GC / max | after thirty minutes of use (SC-003) |
| Memory events | count of `OutOfMemoryError`, low-memory signals and freeze folders (SC-003) |
| Tool latency (ms) | printed by the `TypingLatencyTest` classes (SC-004) |
| Gradle cache growth (MB) | after a forced out-of-memory run (SC-005) |
