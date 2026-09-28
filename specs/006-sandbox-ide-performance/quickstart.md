# Quickstart: Measuring the Sandbox

The repeatable measurement FR-005 asks for. Run it once before the change (baseline) and once after, on the same machine, and add a row to the table at the end.

Paths below are relative to the repository. `<sandbox>` is `.intellijPlatform/sandbox/EtAlii.Adp.IntelliJ/IU-2026.2.3`.

## 1. Start clean

1. Close any running sandbox.
2. Move the old log aside so this run's entries are easy to find: rename `<sandbox>/log_runIde` to `log_runIde.<date>`.
3. Note the size of the Gradle cache's unpacked platform folder (`~/.gradle/caches/<gradle>/transforms`), for SC-005.
4. Run `./gradlew runIde` once and close the sandbox, so dependencies are present. Then run `./gradlew runIde --offline` for the measured run (SC-006: it must succeed).

## 2. Start time and indexing (SC-001, SC-002)

- Ready: time from `IDE STARTED` in `<sandbox>/log_runIde/idea.log` to the moment an example map is shown in its designer.
- Indexing: the newest file in `<sandbox>/log_runIde/indexing-diagnostic/` gives the number of scanned and indexed files and the time. Before the change, also note whether paths under `out/ide-tests` appear in `idea.log`.

## 3. Thirty minutes of use (SC-003)

Open two maps and two diagrams from the example project, edit in both the designer and the text view, close and reopen them, for thirty minutes. Then:

- Find the sandbox's process id (the `java` process whose command line contains `log_runIde`).
- Run the platform runtime's `jcmd <pid> GC.run`, then `jcmd <pid> GC.heap_info`, and note used against maximum heap.
- Count in `idea.log`: `OutOfMemoryError`, `Low memory signal`; count `threadDumps-freeze-*` folders in the log folder.

## 4. Designer latency (SC-004)

```bash
./gradlew :freemind:test --tests '*TypingLatencyTest' :drawio:test --tests '*TypingLatencyTest'
```

Each test prints its figure (`SC-004 typing ... ms`).

## 5. A failure lands in the log folder (SC-005)

Start the sandbox with a deliberately small heap, for example by adding `-Xmx256m` to `runIde`'s JVM arguments for one run, and open the large example map until it runs out of memory. Check that a `java_pid*.hprof` is in `<sandbox>/log_runIde` and that the Gradle cache folder from step 1.3 has not grown by more than 10 MB. Remove the temporary heap setting afterwards.

## 6. Clean up old downloads

Once the real-IDE tests use the per-user cache, the old `out/ide-tests` folder in each clone and worktree can be deleted by hand. The next `./gradlew integrationTest` downloads into the cache once for all clones.

## Results

| Date | Commit | Machine | Ready (s) | Indexed files / time (s) | Heap used / max | Memory events | Latency map / diagram (ms) | Cache growth (MB) |
|---|---|---|---|---|---|---|---|---|
| 2026-09-27 (from log, before) | `2c373d8` | 32 threads, 64 GB | not reached | `out/ide-tests` indexed | out of memory at 2 GB | 11 OOM, 4 low-memory, 2 freezes | not measured | about 3,000 (dump) |
| 2026-09-27 after, first start of a fresh sandbox | `47345ec` | 32 threads, 64 GB | 78 (includes about 72 s waiting on first-run dialogs) | 14 example files; 43,392 bundled platform files indexed once in 6 s | 292 MB / 2 GB | 0 | 44 / 33 | 0 (315 MB dump in `log_runIde`) |
| 2026-09-27 after, second start, `--offline` | `47345ec` | 32 threads, 64 GB | 21 | 0 files to index | 258 MB / 2 GB | 0 | 44 / 33 | 0 |

Notes on the after rows:

- Ready is measured from `IDE STARTED` to the last `exit dumb mode`; the Gradle part before it is a few seconds with everything present.
- SC-002 counts the project's own files. A fresh sandbox also indexes the platform's bundled libraries once (43,392 files, 6 s, responsiveness "ok"); later starts index nothing.
- SC-003's thirty minutes of hands-on editing was not done by the agent; heap after GC and memory events were read right after startup. Peter's first real session is the thirty-minute check.
- SC-005 was forced with a temporary `-Xmx200m` through a Gradle init script: the dump went to `log_runIde\java_pid1124432.hprof` and the Gradle cache's platform folder did not grow.
- The second start logged a platform `NullPointerException` in the refresh queue during startup, after the first sandbox had been stopped by killing its process. It is the platform's, not the plug-in's; that the hard kill caused it is likely but not proven.

