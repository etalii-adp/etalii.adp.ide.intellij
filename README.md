# A Different Perspective (ADP)

ADP is an Eclipse plug-in that adds visual designers for text-based files. Each designer is a
real Eclipse editor. It is registered for its file type, uses the workbench's undo and redo, has
the usual dirty state and save lifecycle, and pairs a visual page with the platform's own text
editor on the same document. The text is always the source of truth: the visual page re-reads it
after every change, and every visual edit changes only the text it has to.

The first supported file format is **FreeMind mind maps** (`.mm`):

- A FreeMind `.mm` file opens in the designer by double-click. Other `.mm` files, such as
  Objective-C++ source, open with whatever editor would otherwise apply.
- The map is drawn around its root, with branches on their recorded side, folding, arrow links,
  icons, colours, fonts, links and notes.
- You can add, rename, delete, move, drag and fold nodes from the keyboard or the context menu.
  Every change is one labelled undo step in Edit > Undo, shared with the Text page.
- A file saved without edits is byte-identical. Edits leave everything else in the file as it was.
- The Outline view lists the node tree. **File > New > Other > A Different Perspective (ADP) >
  FreeMind Mind Map** creates a new map.

## Requirements

- JDK 21 or newer.
- Maven 3.9 or newer.
- Eclipse IDE 2026-09. This is the target platform in
  `releng/etalii.adp.target/etalii.adp.target.target`, which also pulls in GEF Classic.

## Build

```sh
mvn clean verify
```

This builds the bundles, runs every test and produces a p2 update site in
`releng/etalii.adp.site/target/repository/`. The first build downloads the target platform from
`download.eclipse.org`.

The layout:

| Path | What it is |
|---|---|
| `bundles/etalii.adp.core` | The designer framework. It knows no file format. |
| `bundles/etalii.adp.freemind` | The FreeMind file format: parser, text edits, visual page, contributions. |
| `tests/etalii.adp.testing` | `DesignerDriver`, the test kit visual tests use. |
| `tests/etalii.adp.core.tests`, `tests/etalii.adp.freemind.tests` | Test fragments. The vendored example maps are in `tests/etalii.adp.freemind.tests/examples/`, each with its licence. |
| `features/etalii.adp.feature`, `releng/etalii.adp.site` | The feature and the update site. |

## Run the tests

The tests run in a real workbench through the Tycho UI harness, with nobody watching the screen.

- On Windows and macOS, `mvn verify` is enough.
- On Linux without a display, run it under Xvfb: `xvfb-run mvn verify`.

To run one test class:

```sh
mvn verify -Dtest='etalii.adp.freemind.ui.UndoRedoTest' -Dsurefire.failIfNoSpecifiedTests=false
```

## Install

1. In Eclipse, choose **Help > Install New Software... > Add... > Local...**.
2. Pick `releng/etalii.adp.site/target/repository` and install **A Different Perspective (ADP)**.

## Check compatibility with FreeMind 1.0.1

FreeMind is GPL, so it cannot be a build dependency. Its check is opt-in: point `FREEMIND_HOME`
at a FreeMind 1.0.1 installation, the folder that holds `lib/freemind.jar`, and run the build.

```sh
FREEMIND_HOME=/opt/freemind mvn verify -Dtest='etalii.adp.freemind.ui.FreeMindCompatibilityTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Each example map is edited and saved, then loaded with FreeMind's own reader in a separate JVM.
The test then compares the tree and text with what the designer showed. Without `FREEMIND_HOME`
the test is skipped.

## How work is done here

Every change starts as a specification, using GitHub Spec Kit. See `CLAUDE.md` and
`specs/001-freemind-mindmap-designer/` for the first feature.

## Licence

Eclipse Public License 2.0. The example maps keep their own licences, recorded in the
`.LICENSE` file beside each map.
