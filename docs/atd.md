# `.atd` tool descriptions

The plug-in gives `.atd` files, tool descriptions written in [ATD, the ADP tool language](https://github.com/etalii-adp/etalii.adp/blob/develop/specifications/atd/ATD-specification.md), the language support of etalii.adp spec 035: the same checker, completion and formatting as every other ADP host, from the one implementation in the desktop host, `atd-lsp`, which the IDE's own language server support runs.

## What an `.atd` file gets

- **Findings.** Every finding of `atd check` is shown in the editor and in the Problems view, with its code (`ATD2001` and the others of ATD §15) and, where the checker has one, a quick fix. A change to one file of a description checks every file of it again, 150 ms after typing stops.
- **Colours** from the server's semantic tokens: keywords, types, attributes, functions, strings, numbers and comments.
- **Completion**, from what the grammar admits at the cursor and the names the position takes; **quick documentation**, with the keyword's documentation or the declaration's; **go to declaration**, **find usages**, **rename** across every file of a description (inside CEL too, never inside strings); **reformat code**, in the layout of ATD §16; and the **structure** view.

## The IDEs it works in

The support needs the IntelliJ Platform's language server support, the module `com.intellij.modules.lsp`. The plug-in declares everything of `.atd` in `atd/src/main/resources/META-INF/adp-atd.xml`, which `plugin.xml` loads only where that module is present:

```xml
<depends optional="true" config-file="adp-atd.xml">com.intellij.modules.lsp</depends>
```

In an IDE without the module the plug-in loads and its tools work as always, and an `.atd` file opens as plain text: no file type of the plug-in claims it there.

## How the server gets into the plug-in

The server is the desktop host's `atd-lsp`, published in the desktop host's releases for Windows (x64), macOS (Apple silicon) and Linux (x64). `atd-lsp.lock` names the release and the SHA-256 of each platform's archive. The `:atd` module's `fetchAtdLsp` task, which `buildPlugin` runs, downloads the three archives, checks each hash and unpacks each `atd-lsp` into `build/atd-lsp/bin/<platform>/`; the plug-in zip carries them in `bin/<platform>/`, and the IDE runs the one for its platform, one per project, over stdio. Set `ATD_RELEASE_DIR` to a folder holding the archives to build without network; they are checked all the same. The plug-in makes no network access at runtime.

To move to a newer release, change `release` and the hashes in `atd-lsp.lock`: the release lists each file's SHA-256 beside it.

## Tests

`src/integrationTest/java/etalii/adp/atd/AtdLanguageIntegrationTest.java` checks that the plug-in zip packs the server of every platform, runs the 30 steps of the walk (`atd-walk.zip` of the same release, from `ATD_RELEASE_DIR` or `-Dadp.atd.walk`) through the server the zip packs for this platform, over stdio as the IDE runs it, and checks that the support is declared only behind `com.intellij.modules.lsp`.
