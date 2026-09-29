# Screenshots

The tools as a user sees them in IntelliJ IDEA, and how each image was taken — precisely enough that a second person (or an agent) retakes a comparable image after a UI change. **When the UI changes so that one of these no longer shows what a user sees, retake it**; a stale screenshot is a false claim with a picture attached.

## The shared setup, for every image

- **Source material**: only the example files in [`freemind/testdata/examples/`](../../freemind/testdata/examples/) and [`drawio/testdata/examples/`](../../drawio/testdata/examples/) appear in any image, so every capture is reproducible from repository content alone. Each image opens one example, copied on its own into a fresh project named `Examples`.
- **IDE**: IntelliJ IDEA 2026.2.3, downloaded by the IDE Starter framework into the real-IDE tests' per-user cache (or the installation named by `ADP_IDE_HOME_IU`), with the plug-in zip from `./gradlew buildPlugin` installed. A fresh configuration each time: nothing from a contributor's own IDE settings reaches an image.
- **Window**: the IDE frame, not maximised, sized to **1600×900** and painted into the image by the IDE itself, so no desktop, other window or cursor can appear in it. Pixels are logical pixels (scale 1), whatever the display's scaling.
- **Theme**: the IDE's default (dark). Nothing toggled.
- **Layout**: only the example's own editor tab is open (the pages the IDE opens on a first start are closed); the Project, Services, Problems and Terminal tool windows are hidden; for a draw.io diagram the ADP Toolbox is shown on the right, 260 px wide. The tool shows the file as it opens, at its default zoom and scroll position, with nothing selected.
- **Known artefact**: the IDE runs on its built-in trial, so a green **Trial** pill shows in the title bar. A user with a licence or on the free tier does not see it.
- **Format and budget**: PNG; each image ≤ 300 KB.

The whole procedure is executable: [`CaptureScreenshots`](../../src/integrationTest/java/etalii/adp/it/CaptureScreenshots.java) drives all of the above through the IDE Starter framework and the Driver, and

```
./gradlew captureScreenshots
```

builds the plug-in and writes every image below into this folder (it is not part of `check`, so the ordinary build never changes these files). Retaking one image means re-running the task and committing the changed files; the rows below say what each image must show, which is what to check before committing a retake. The IDE windows open on the desktop while the task runs; leave them alone until it finishes.

## The images

| Image | Document opened | What must be visible |
|---|---|---|
| `mindmap.png` | `freemind/testdata/examples/freemind-0.9.0-arrow-links-icons-cjk.mm` | The FreeMind Mind map in its tab: the root `Thu_learn` on the left with its branches laid out to the right, the blue arrow links between nodes drawn, and the info icons on `file` and `note`. The map is taller than the window, so its lower branches run off the bottom. Nothing selected. |
| `drawio-activity.png` | `drawio/testdata/examples/activity_diagram_1.drawio` | The draw.io diagram in its tab: the three thread swimlanes with their activities, the `queue empty` decision with its `yes`/`no` branches and the red bar; the ADP Toolbox on the right listing the Elements (Rectangle to List) and Connections (Connector). Nothing selected. |
| `drawio-cross-functional.png` | `drawio/testdata/examples/cross_functional_flowchart_1.drawio` | The draw.io diagram in its tab: the `Pool` with its six lanes, the process boxes and decisions with their orthogonal connectors; the ADP Toolbox on the right as above. Nothing selected. |

Two captures that follow one row of this table should differ only in rendering noise and the memory figure in the status bar.
