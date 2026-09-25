# Quickstart: validating the JetBrains plug-in

How to prove the feature works end to end. Details of IDs, shortcuts and APIs are in
[contracts/](contracts/) and [data-model.md](data-model.md); they are not repeated here.

## Prerequisites

- JDK 21 on `PATH` (Gradle's toolchain resolves the rest).
- Network access for the first build: Gradle downloads the IntelliJ Platform 2026.2 artifacts
  and, for `integrationTest` and `verifyPlugin`, the IDEs listed in research R9 and R11. This is
  build-time only; the plug-in makes no network access at runtime (FR-021).
- For the manual checks: IntelliJ IDEA 2026.2 (with and without an Ultimate trial), Rider
  2026.2 (licence or trial), and at least two more IntelliJ Platform IDEs, e.g. WebStorm and
  PyCharm.

## 1. One headless build (FR-020, User Story 4)

```sh
./gradlew build
```

Expected: every format, platform and integration test passes; `verifyPlugin` reports no
compatibility problems for the IDEs in research R11; the installable plug-in is at
`build/distributions/etalii-adp-<version>.zip`; the "no previous host" check passes (SC-005).
No installation of the previous host or its tooling is needed.

Faster loops while developing:

```sh
./gradlew test               # format layer + headless platform tests
./gradlew integrationTest    # real IDEs (slow; downloads IDEs on first run)
./gradlew runIde             # a sandbox IntelliJ IDEA with the plug-in installed
```

## 2. Byte fidelity against spec 001 (FR-006 to FR-008, SC-002, SC-003)

Covered by `./gradlew test`: every example map opened and saved without edits is byte-identical,
and every scenario in `freemind/testdata/reference/scenarios.json` reproduces its recorded result
byte for byte, and undoing it restores the original. To look at one by hand:

1. `./gradlew runIde`, open `freemind/testdata/examples/freemind-1.0.1-rich-notes.mm`.
2. Add a child to the root, save, and diff the file with `git diff --no-index`: only the new
   node's line and the parent's `MODIFIED` value differ.
3. Undo, save: `git status` shows the file unchanged.

## 3. Install from disk and use it (SC-001, SC-006)

In each IDE: Settings > Plugins > gear icon > Install Plugin from Disk > the zip > restart.

| Check | Expected |
|---|---|
| Double-click a vendored example map in the Project view (Solution Explorer in Rider) | Opens in the FreeMind designer; tree, sides, folding, arrow links, icons, colours, fonts, links and notes as spec 001 describes |
| Open a non-FreeMind `.mm` file (in CLion: an Objective-C++ source) | Opens in the editor the IDE would use without the plug-in |
| Toolbar: Editor / Editor and Preview | Shows the same file as text; edits in either side appear in the other |
| Add five nodes, undo two, save | Edit > Undo shows "Undo Add Child Node"; the saved file has three new nodes |
| Settings > Keymap > Plug-ins > A Different Perspective (ADP), rebind Add Child Node, press it in the designer | The action runs |
| New > FreeMind Mind Map | A map with one root opens in the designer |
| Structure tool window | Lists the node tree; selection follows both ways |
| Switch to a dark theme, raise the IDE font scale | Everything legible and sharp |
| Make the file read-only | Banner explains why; editing actions are disabled |
| Change the file on disk while open (clean, then modified) | Reloads, or asks, exactly as the text editor does |

SC-006 walkthrough: give a developer who knows IntelliJ IDEA or Rider but has not seen the
designer the sentence "open this map, add five nodes, undo two of them, and save", with no other
help, and record whether they succeed at the first attempt.

## 4. FreeMind compatibility (spec 001 FR-012, SC-005)

With `FREEMIND_HOME` pointing at a FreeMind 1.0.1 install, `./gradlew test` also runs the opt-in
check that loads every saved scenario result in FreeMind's own reader. Without it the check is
skipped with a reason. Before merging, open a few saved maps in FreeMind 1.0.1 by hand.

## 5. No trace of the previous host (FR-017, FR-017a, SC-005)

`./gradlew build` runs the check automatically. By hand:

```sh
git grep -i -l "<previous host name>" -- . ':!specs/002-jetbrains-ide-support'   # expect no output
unzip -l build/distributions/etalii-adp-*.zip                                     # no previous-host jars or files
```

## Known limits

- A file with mixed line separators is saved with one separator after an edit (research R5).
  Opening and closing it without edits leaves it untouched.
- If the user enables "Ensure every saved file ends with a line break", the IDE adds one to maps
  that lack it on the next save, as it does for every file.
