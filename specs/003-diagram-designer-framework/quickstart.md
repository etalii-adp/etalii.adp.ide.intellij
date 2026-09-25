# Quickstart: Diagram Designer Framework

How to check that the feature works. The automated parts run with `./gradlew check`. The manual parts are listed last.

## 1. Build and run everything

```powershell
./gradlew check        # unit, headless IDE, integration tests, verifier, licence check
./gradlew runIde       # sandbox IDE with the plug-in
```

`check` fails when:

- any test fails;
- a runtime dependency is not on the licence allowlist (SC-006);
- the sample or draw.io designer exceeds 342 lines, or uses drawing, selection, undo or table code (SC-001);
- open or edit timing on the 1,000-element, 1,500-connection sample file exceeds 2 s or 100 ms (SC-005).

## 2. See each story in the sandbox IDE

| Story | Steps | Expected |
|---|---|---|
| US1 view | Open `drawio/testdata/examples/flowchart_1.drawio`. | Shapes, dashed and curved edges, arrows and labels as in draw.io. Switch the theme: colours stay readable. |
| US1 fallback | Open `drawio/testdata/examples/compressed.drawio`. | The text view, with a banner explaining that compressed pages are not supported. |
| US2 edit | Open View > Tool Windows > ADP Toolbox. Drag "Rectangle" onto the canvas. Drag between two anchors. Move, resize, delete. Press Undo until the file is unchanged. | Each step is one Undo entry, and the file is marked modified. After undoing everything, `git diff` is empty. |
| US2 refusal | In FreeMind, drag "Node" from the toolbox onto empty canvas. | Nothing is added. A balloon says "a node needs a parent". |
| US3 panel | Open ADP Properties. Select a draw.io shape and change its fill colour. Select two shapes and change the label. | The canvas updates. Each edit is one Undo. The Id row is read-only. |
| US3 in place | Double-click an edge label. | An in-place field opens. Enter commits, Escape cancels. |
| US4 lanes | Open `drawio/testdata/examples/cross_functional_flowchart_1.drawio`. Zoom in with Ctrl+wheel. Drag a shape into another lane. | Lanes scale with the content. The shape's `parent` in the file changes and its coordinates become relative to the new lane, in one Undo step. |
| FreeMind | Open any `freemind/testdata/examples/*.mm`. Use the existing actions, the toolbox and the panel. | Behaviour and saved bytes are as before the migration. The panel edits text, colour, folded and link. |

## 3. Manual checks

- **SC-002**: a developer who has not seen the framework follows `docs/diagram-designer-guide.md` and gets the two-element, one-connection designer opening a sample file. Record the time taken in the task.
- **Theme and scale**: repeat the US1 view check in the light theme, the dark theme and at 200 % IDE scale.
