# Research: JetBrains IDE Support

Phase 0 decisions for moving ADP from its previous host to the IntelliJ Platform. Platform facts
were checked against JetBrains documentation and release notes on 2026-09-24; sources are listed
per decision. Items marked **(prototype first)** are the least certain and get an early task
that proves them before code depends on them.

## R1. Target release, language level and product naming

**Decision**: target IntelliJ Platform **2026.2** (branch 262, current maintenance release
2026.2.3). The plug-in descriptor declares `since-build="262"` and no `until-build`; the Plugin
Verifier (R11) checks compatibility. Code is **Java 21**, the level the platform's bundled
runtime (JBR 21) runs and the language the format layer is already written in. Build scripts use
the Gradle Kotlin DSL; no Kotlin code ships.

Since 2025.3, IntelliJ IDEA Community and Ultimate are **one distribution**; Ultimate is a
subscription unlocked inside it. The spec's "IntelliJ IDEA (Community and Ultimate)" (FR-001)
and "IntelliJ IDEA Community, IntelliJ IDEA Ultimate" (SC-001) therefore mean: the unified
IntelliJ IDEA, verified once without and once with an active Ultimate subscription or trial.

**Rationale**: the constitution asks for the current platform release at planning time and
Java at the level that release requires. Keeping Java lets the ~1,700 lines of format code
(parser, scanner, edits, conventions) move with type-level changes only (R4).

**Alternatives considered**: Kotlin (idiomatic for new plug-ins, but a rewrite of working,
tested code for no requirement). Supporting 2025.x (the constitution allows older releases only
when a spec asks).

Sources: https://youtrack.jetbrains.com/articles/IDEA-A-2100662711/IntelliJ-IDEA-2026.2.0.1-262.8665.337-build-Release-Notes ,
https://blog.jetbrains.com/idea/2025/07/intellij-idea-unified-distribution-plan/ ,
https://www.jetbrains.com/help/idea/intellij-idea-single-distribution.html

## R2. Editor shape: the platform text editor with the designer as its preview

**Decision**: `AdpEditorProvider` (core, one instance per format) returns a
`TextEditorWithPreview` whose text side is the platform's own `TextEditor` for the file and
whose preview side is the format's visual `AdpDesignerEditor`. The default layout shows the
designer only; the editor's standard toolbar switches to "Editor", "Editor and Preview" or
"Preview", which is how the user shows the same file as text (FR-003). Both sides work on the
one `Document` the platform keeps for the file, so there is one document and one undo history.

The provider's policy is `HIDE_DEFAULT_EDITOR`: the composite already contains the text editor,
so a second plain-text tab would be redundant. The IDE's own "Open In > Text Editor" style
choices and the editor-type switch in the composite remain available.

**Rationale**: principle I ("use the platform's own mechanism"). `TextEditorWithPreview` is
public API and is how the Markdown plug-in pairs text and a visual view, so saving, modified
state, reload after external change, Local History and read-only handling come from the text
editor unchanged (FR-005).

**Alternatives considered**: a separate visual `FileEditor` alongside the default text editor
as two tabs (`PLACE_BEFORE_DEFAULT_EDITOR`): works, but the text tab would be a different editor
instance with its own caret and layout state, and switching views is less direct. A fully custom
editor owning its own save logic (violates principle I).

Sources: https://github.com/JetBrains/intellij-community/blob/master/plugins/markdown/core/src/org/intellij/plugins/markdown/ui/preview/MarkdownEditorWithPreview.java

## R3. Registration: content sniffing in the editor provider, not a file type

**Decision**: `AdpEditorProvider.accept(project, file)` returns true only when the file's
extension is one the format declares (`mm`) **and** the format's `ContentSniffer` recognises the
first 4 KB (for FreeMind: after an optional BOM, XML declaration, whitespace and comments, the
root element is `<map` with a `version` attribute). Other `.mm` files (Objective-C++ in CLion)
are not accepted and open as they would without the plug-in (FR-002, User Story 1 AS-3).

A `fileTypeDetector` is also registered so that, in IDEs where `.mm` is not claimed by another
file type, FreeMind files get a "FreeMind Mind Map" file type (icon in the Project view, the
New menu, no "register file type" prompt). Where `.mm` is already claimed, the detector is not
consulted and the provider's sniffing alone decides.

**Rationale**: the detector only runs for files the IDE does not already recognise by
extension, so it cannot carve FreeMind maps out of CLion's Objective-C++ `.mm` files. The
provider's `accept` runs for every file regardless of type. The sniff reads at most 4 KB from
`VirtualFile.getInputStream` (never the whole file) and the provider is `DumbAware`, so it is
safe while the IDE indexes.

**Alternatives considered**: a file type claiming extension `mm` (would take `.mm` away from
Objective-C++ in CLion, violating FR-002). `FileTypeOverrider` (reclassifies files globally,
heavier than needed, and changes how other plug-ins see the file).

Sources: https://intellij-support.jetbrains.com/hc/en-us/community/posts/360008395719 ,
https://www.plugin-dev.com/intellij/custom-language/file-type-detection/

## R4. Carrying the format layer over

**Decision**: the packages `etalii.adp.freemind.model`, `.parse` and `.edit` move as they are,
with two type substitutions (see data-model.md): `Rgb` for the host graphics colour and
`TextChange`/`TextChanges` for the host text-edit tree. `XmlScanner`, `MindMapParser`,
`RichText` and `FreeMindConventions` keep their logic. The format layer imports nothing from the
platform, AWT or Swing; a unit test scans its imports.

Applying a `TextChanges` happens in core, inside
`WriteCommandAction.writeCommandAction(project, file).withName(label).run(...)`, replacing from
the highest offset down so earlier offsets stay valid. One command is one undo step, named by the
label (FR-004).

**Rationale**: the byte-exact behaviour lives in this layer, and the reference results (R10) are
produced by it. Moving it unchanged is the cheapest way to meet FR-006 to FR-008.

**Alternatives considered**: re-parsing through the platform's XML PSI (would pull in the XML
plug-in, which not every IntelliJ-based IDE is guaranteed to expose to a platform-only plug-in,
and its tree does not preserve the exact ranges the edits rely on).

## R5. Byte fidelity in the new host

**Decision**: the designer edits the platform `Document`, whose text uses `\n`; the platform
records each file's separator on load and writes it back on save. Three host behaviours could
change bytes and are neutralised:

1. *Strip trailing spaces on save*: a `stripTrailingSpacesFilterFactory` returns
   `StripTrailingSpacesFilter.NOT_ALLOWED` for documents the ADP provider accepts.
2. *Ensure line feed at end of file on save*: honoured only if the user enabled it (off by
   default); documented in the quickstart as a user setting that alters bytes, as it would for
   any file.
3. *Mixed line separators*: the platform normalises a mixed-separator file on save. Such a file
   opens normally and saving without edits does not write it (unmodified documents are not
   saved), so FR-006 holds; an edit to a mixed-separator file saves with one separator. None of
   the vendored examples is mixed (six use CRLF throughout, the legacy map uses a lone CR). This
   is recorded as a known limit in the quickstart.

Encoding: FreeMind maps without an XML declaration are read with the IDE's encoding detection;
the designer never changes the encoding, and the platform writes the text back in the encoding
it was loaded with.

**Rationale**: FR-006 and FR-007 require byte-identical results; SC-002 checks all examples.

Sources: https://plugins.jetbrains.com/intellij-platform-explorer?extensions=com.intellij.stripTrailingSpacesFilterFactory ,
https://intellij-support.jetbrains.com/hc/en-us/community/posts/206862225-Keep-line-separators-when-editing-files-

## R6. Visual stack: a Swing canvas with a layout model

**Decision**: the designer is `MindMapCanvas`, a Swing `JComponent` in a `JBScrollPane`, painted
with `Graphics2D`. `MindMapLayout` (ported; its algorithm is host-neutral) produces a `NodeView`
box per visible node; painting, hit-testing, selection, keyboard focus, drag feedback and zoom
work from those boxes. In-place rename uses a `JBTextField` overlay on the node. Arrow links are
drawn as cubic curves with arrowheads. All sizes go through `JBUI.scale`, colours through
`JBColor` and the editor colour scheme, fonts through `JBFont` or the node's own `FontSpec`.

**Rationale**: GEF does not exist in this host. The platform's graph libraries are either
internal or product-specific, and nothing in the platform lays out mind maps. A single canvas
over precomputed boxes is small, fast at a few thousand nodes (SC-004), and fully testable
without a screen (the test kit reads the boxes, R9).

**Alternatives considered**: one Swing component per node (thousands of components, slow
layout, focus traversal problems). JCEF/HTML rendering (heavy, not available in every
environment, and a web stack to maintain). A third-party graph library such as JGraphX (an
unmaintained dependency for what is a small tree layout).

## R7. Undo from the designer, and named steps

**Decision**: every visual edit runs as one `WriteCommandAction` named with the edit's label
(R4). The visual `AdpDesignerEditor` implements `DocumentReferenceProvider`, returning the
file's document reference, so that Edit > Undo and Redo, and their shortcuts, act on the
document's history while focus is in the designer (User Story 2 AS-4, FR-004). Undo from the
text editor side uses the same history.

**Rationale**: `UndoManager` resolves the undo target from the focused `FileEditor`; a
`DocumentReferenceProvider` is the platform's way for a non-text editor to say which documents
it edits.

**(prototype first)**: the first core test opens a map, performs a named change through the
designer, and checks `UndoManager.getUndoActionNameAndDescription` and a real undo while the
designer has focus, before any editing code is written.

Sources: https://github.com/JetBrains/intellij-community/blob/master/platform/core-api/src/com/intellij/openapi/command/WriteCommandAction.java ,
https://plugins.jetbrains.com/docs/intellij/documents.html

## R8. Structure view, actions, New file, themes

**Structure view (FR-015)**: `AdpDesignerEditor.getStructureViewBuilder()` returns a
`TreeBasedStructureViewBuilder` over the latest parse. Tree elements wrap `NodeKey`s. Selecting
in the Structure view navigates (`Navigatable.navigate`) to the node in the designer; selection
changes in the designer are published through the structure view model's
`getCurrentEditorElement` and a model listener so "Autoscroll from Source" follows.
**(prototype first)**: documentation covers this mechanism for text editors only; an early test
proves both directions for the custom editor.

**Actions and keymap (FR-012, FR-013)**: each editing action (add child, add sibling, rename,
delete, move up, move down, indent, outdent, fold/unfold, zoom in, zoom out, reset zoom) is an
`AnAction` registered in `plugin.xml` with its default keyboard shortcut in the `$default`
keymap, so it appears under Settings > Keymap and can be rebound. The canvas's context menu is an
`ActionGroup` shown through `PopupHandler`. Actions read the designer from the `DataContext` and
are enabled only when it is focused, editable and the selection fits.

**New file (FR-014)**: a "FreeMind Mind Map" action in the `NewGroup` asks for a name, creates
the file with `FreeMindConventions.newMapText(...)` (so the root has a FreeMind-style ID and
timestamps, which a file template could not produce), and opens it.

**Themes and scale (FR-016)**: canvas background, default node text, selection and connectors
use `JBColor` pairs and the editor colour scheme. A colour taken from the file is used as
written; when its contrast against what it is drawn on is below 3:1 (WCAG non-text contrast),
the node is drawn on a plate of the file's own background colour or a neutral light plate, so the
author's colour is kept and stays legible in a dark theme.

**Links (spec 001 FR-018)**: URLs open through `BrowserUtil.browse`; paths relative to the map
open through `FileEditorManager.openFile` after resolving against the map's directory.

## R9. Tests and the test kit

**Decision**: three test layers, all run by `./gradlew check`.

1. **Format layer**: plain JUnit 5 (Jupiter) tests, ported from spec 001's parser, scanner, rich
   text, conventions and edits tests, plus the reference-result comparisons (R10) and the
   import-purity check.
2. **Platform tests (headless IDE)**: `BasePlatformTestCase`-based tests (JUnit 4 run on the
   Vintage engine alongside Jupiter), ported from spec 001's editor tests: registration and
   sniffing, undo and redo with labels, save lifecycle and byte fidelity, external change,
   read-only, text and visual sync, two editors on one file, Structure view, New file, keymap,
   context menu, drag, fold, rename, layout, node details and performance. They drive the
   designer through `DesignerDriver` in the `testing` module, whose contract keeps spec 001's
   shape with host types replaced (contracts/test-kit.md).
3. **Integration (full IDE)**: a small Starter + Driver suite (`TestFrameworkType.Starter`, JUnit
   5) that installs the built plug-in into real IntelliJ IDEA, Rider, WebStorm and PyCharm
   2026.2 instances, opens a vendored map, checks the designer is the editor shown, performs one
   edit, undoes it and closes. This is the "real IDE" part of FR-020 and the automated part of
   SC-001. It runs in its own Gradle task `integrationTest`, which `check` depends on.

**Rationale**: the constitution requires registration and undo to be tested in a headless IDE
instance; FR-020 adds real-IDE runs; SC-007 needs unattended tests that are quick to write.

**Alternatives considered**: Starter tests for everything (minutes per test, too slow for the
~200 behaviour tests). Only light tests (would not prove installation into Rider and WebStorm).

Sources: https://plugins.jetbrains.com/docs/intellij/tests-and-fixtures.html ,
https://blog.jetbrains.com/platform/2025/02/integration-tests-for-plugin-developers-intro-dependencies-and-first-integration-test/ ,
https://platform.jetbrains.com/t/testframeworktype-starter-plan-to-include-per-product-modules-after-262/4258

## R10. Recording the reference results before removal

**Decision**: the first implementation phase, while the previous host's build still works, adds a
recorder to its test suite. For every vendored example map it runs a fixed scenario list
(`scenarios.json`: each editing action type on chosen nodes, with a fixed random seed and a
fixed clock injected into `FreeMindConventions`) through that implementation's parser and edit
layer, and writes each result's bytes to `freemind/testdata/reference/`. It also writes the inventory of
spec 001's test methods and what each verifies. Both are committed before any of the previous
host's code is removed. The new format tests replay the same scenarios and compare bytes
(FR-008, SC-003); the inventory is checked off against the new tests (FR-018, SC-007).

**Rationale**: FR-008 requires the reference to exist before removal. The edit layer is where
the bytes come from; the UI only chooses which edit to build.

**Alternatives considered**: recording through the old UI harness (slower, and the UI adds
nothing to the bytes). Writing expected files by hand (not a record of what spec 001 wrote).

## R11. Build, packaging and verification

**Decision**: Gradle 9 (wrapper checked in) with the IntelliJ Platform Gradle Plugin 2.19.
Root project `EtAlii.Adp.IntelliJ` builds the plug-in; `core` and `freemind` apply
`org.jetbrains.intellij.platform.module` and are composed into the plug-in jar with
`pluginComposedModule`; `testing` is a test-only module. The platform dependency is
`intellijIdea("2026.2.3")` with `bundledPlugins` empty, and the descriptor depends only on
`com.intellij.modules.platform` (constitution: single host, common modules only).

`./gradlew build` compiles, runs `check` (all three test layers), `verifyPluginProjectConfiguration`
and `verifyPlugin`, and produces `build/distributions/etalii-adp-<version>.zip`, the file users
install from disk. `verifyPlugin` runs the Plugin Verifier against IntelliJ IDEA, Rider,
WebStorm, PyCharm, CLion, GoLand, PhpStorm and RubyMine 2026.2 (`select { types = ...;
sinceBuild = "262" }`), which covers FR-001 for the products not exercised by the integration
suite.

The build downloads the IDEs it tests against; that is build-time network use. The plug-in
itself makes no network access at runtime (FR-021); `LinkOpener` only hands URLs to the user's
browser on an explicit click.

**Rationale**: the constitution names Gradle with the IntelliJ Platform Gradle Plugin and a
headless build that runs every test.

Sources: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html ,
https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html

## R12. Rider and split mode

**Decision**: the plug-in is a plain platform plug-in (no frontend/backend modules). Rider loads
it in its IntelliJ-based frontend, where `FileEditorProvider`s run, and `.mm` files reached from
the Solution Explorer or any other file view open through the same provider. The integration
suite includes Rider (R9). Whether a local Rider session runs plug-ins in split mode by default
in 2026.2 could not be confirmed; if the Rider integration test shows the designer is not
offered, the fallback is to declare the editor registration in a frontend content module, which
the Gradle plug-in supports. Remote Development and Gateway remain out of scope (spec
assumption).

**(prototype first)**: the Rider integration test is written in the first platform phase, not
at the end.

Sources: https://plugins.jetbrains.com/docs/intellij/rider.html ,
https://plugins.jetbrains.com/docs/intellij/configuring-split-mode.html ,
https://blog.jetbrains.com/platform/2026/05/make-your-plugin-remote-development-ready/

## R13. Removing the previous host and renaming

**Decision**: removal is the last phase, after the reference results are recorded and every
item of the spec 001 test inventory has a passing new test. It deletes the previous build
(`pom.xml`, `.mvn/`), `bundles/`, `features/`, `releng/`, the old `tests/` tree, host entries in
`.gitignore`, and spec 001's host-specific artifacts (`plan.md`, `research.md`, `data-model.md`,
`contracts/`, `tasks.md`), and removes host references from spec 001's `.spec-context.json`
(FR-017a). The vendored example maps and their licence files move to
`freemind/testdata/examples/` with `git mv` so their history is kept. `README.md` and
`CLAUDE.md` are rewritten for the new plug-in. A final check searches the repository (excluding
`specs/002-jetbrains-ide-support/`), the built zip and the installed plug-in for the previous
host's name (SC-005) and fails the build if it finds any.

Renaming the repository folder and any remote to `EtAlii.Adp.IntelliJ` is done by the
maintainer outside the build; the task list ends with that as a manual step.

## R14. Licence

**Decision**: add `LICENSE` (Apache-2.0 full text) at the root, state Apache-2.0 in the README
and the plug-in descriptor's vendor/description, and remove every reference to the previous
licence (FR-022). The example maps keep their own licences in the `.LICENSE` file beside each
map. Every build and test dependency is checked for Apache-2.0 compatibility in a task; the
platform itself is Apache-2.0.
