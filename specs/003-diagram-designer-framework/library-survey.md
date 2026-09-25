# Library survey: diagram-designer core

Date: 2026-09-24. Input for the 003 plan (research). Not a specification.

## Purpose and constraints

The planned diagram-designer core needs: nodes with shapes, anchors/ports, wrapping and
auto-sizing text labels, edges (straight, orthogonal, bezier), dash styles, arrowheads at both
ends, mid and end labels, edge routing, in-place editing, a toolbox/palette with drag onto the
canvas, selection, move, resize, pan and zoom, swimlanes/sectors, a property grid and model
load/save.

Constraints from `.specify/memory/constitution.md` that decide this survey:

- Principle I: the designer is a real IntelliJ Platform `FileEditor`; every change is an undoable
  command on the platform undo manager; themes, keymap, actions and selection use platform
  services.
- Principle II: the text file is the model; edits change only the part of the file concerned.
  A library that owns its own model, undo stack or serialisation works against this.
- Principle V: dependencies must be justified by a current requirement; prefer what the
  platform offers.
- Dependencies: Apache-2.0-compatible, preferably already bundled; depend only on platform
  common modules (must run in IDEA, Rider, WebStorm, PyCharm); no runtime network access.

Licence rating used below: **yes** = Apache-2.0, MIT, BSD. **weak** = EPL-2.0 or MPL-2.0
(file-level copyleft; Apache Software Foundation lists them as "category B", binary use only;
questionable under this constitution). **no** = GPL, LGPL, commercial.

## What the IntelliJ Platform itself provides

Checked against the local IntelliJ IDEA 2026.2.3 distribution used by this build
(`lib/`, `plugins/`, `license/third-party-libraries.json`).

| Item | Finding |
|---|---|
| Diagrams API (`com.intellij.diagram`) | Lives in the bundled `uml` plugin (`plugins/uml`, with `intellij.platform.commercial.verifier.jar`). Available only in paid IDEs, undocumented, meant for code-structure diagrams, not file-backed editors. Not usable: violates the common-modules rule. |
| Graph API (`com.intellij.openapi.graph`, `lib/intellij.platform.graph*.jar`) | A wrapper over **yFiles 2.13**, listed as "Commercial, available on request". Internal to the Diagrams feature; licence not transferable. Not usable. |
| JGraphT | JGraphT 1.5.1 is listed in the third-party manifest, but the classes only exist inside `plugins/fullLine` (AI completion), a private plugin classloader. Not available to other plugins. |
| ELK, JGraphX, Piccolo2D, JUNG, mxGraph | Not bundled. |
| JCEF | Bundled (JCEF 144 / Chromium 144, BSD-3) in the JetBrains Runtime of all IDEs, including Rider. Must be checked with `JBCefApp.isSupported()`; unsupported when the IDE runs on an alternative JDK. Local resources via `CefResourceRequestHandler`, JS bridge via `JBCefJSQuery`. |
| Compose Multiplatform + Jewel | Bundled: Compose 1.12.0, Skiko 0.150.1 (Apache-2.0), `intellij.platform.jewel.*`. Bundled since 251. Documented as experimental; binary compatibility not guaranteed; third-party Compose plug-ins "not officially supported". |
| Property grid | `com.intellij.designer.propertyTable.PropertyTable` exists in `lib/intellij.platform.ide.designer.jar` but is `@ApiStatus.Internal` (its `Property` model is `@Deprecated`). The UI Designer's property inspector is in a Java-only plugin. No public generic property sheet. Public building blocks: `JBTable`, `TreeTable`, Kotlin UI DSL, `ComboBox`, `ColorPanel`. |
| Rendering helpers | Batik 1.16 and JSVG 2.1.0 (SVG), MigLayout, SwingX, `JBColor`/`JBUI` scaling, `JBScrollPane`, drag-and-drop (`DnDManager`), `EditorTextField`. All public or bundled. |

## Comparison

Coverage key: S shapes, P ports/anchors, T text wrap/auto-size, E edge styles (straight,
orthogonal, bezier, dash, arrows), L edge labels, R routing, I in-place editing, B palette/DnD,
M select/move/resize, Z pan/zoom, W swimlanes, G property grid, F load/save, A auto-layout.

| Library | Licence | Compatible | Route | Maintained / last release | Coverage | Notes |
|---|---|---|---|---|---|---|
| JGraphX | BSD-3 | yes | Swing / Java2D | No. Archived on GitHub, last push 2020-11; last Maven release 4.2.2 (2021-02, `com.github.vlsi.mxgraph`) | S P T E L I B M Z W F (own XML), partial R | Closest functional match. Owns its model (`mxGraphModel`), undo (`mxUndoManager`) and codec, so every edit would have to be bridged to the platform undo manager and the text file. Unmaintained; HiDPI and theme behaviour dated. Useful as a reference for edge styles and perimeters. |
| JGraphT | LGPL-2.1-or-later OR EPL-2.0 | weak (via EPL) | Java, no UI | Yes. 1.5.3 on Maven Central (2026-04) | graph algorithms only (shortest paths, no geometric routing) | Not reachable from the platform (only inside `fullLine`). We need no graph algorithms beyond what a small router needs. |
| Eclipse ELK | EPL-2.0 | weak | Java, no UI, pure layout | Yes. v0.12.0 (2026-07) | A, orthogonal/spline R for laid-out graphs | Best open layout engine (layered, orthogonal routing, ports). Pulls in EMF-free core but several jars. Only relevant if automatic layout becomes a requirement. |
| elkjs | EPL-2.0 (GPL-3.0 added as secondary option in 0.12.0) | weak | JS via JCEF, or GraalJS | Yes. 0.12.0 (2026-07) | A, R | Same engine as ELK; no reason to use it from Java. |
| Piccolo2D | BSD-3 | yes | Swing / Java2D scene graph | Barely. Repo active (last push 2025-02) but last release 3.0.1 (2019-01) | Z, M (building blocks), basic S | Zoomable scene graph only; no diagram semantics. Small gain over plain Java2D. |
| JUNG | BSD-3 | yes | Swing / Java2D | No release since 2.1.1 (2016-09); repo still gets pushes | S (basic), Z, M, A (force/tree) | Network-visualisation toolkit, not a diagram editor. |
| yFiles for Java (Swing) | Commercial | no | Swing | Yes | everything | Excluded by licence. The platform's own yFiles copy is not licensed to plug-ins. |
| Eclipse GEF Classic / Draw2d | EPL-2.0 | weak | SWT | Yes. 3.28.0 (2026-06), quarterly | S P E L R I B M Z W G F | SWT only; cannot be hosted in a Swing `FileEditor`. Useful as a design reference (EditPart/Request/Command pattern). |
| mxGraph (JS) | Apache-2.0 | yes | JS via JCEF | No. Archived, last push 2020-11 | as JGraphX | Superseded by maxGraph. |
| maxGraph | Apache-2.0 | yes | JS/TS via JCEF | Yes. v0.24.0 (2026-07), pre-1.0 | S P T E L R I B M Z W F | Maintained successor of mxGraph. Pre-1.0 API churn. Undo, keymap, themes, find and selection would all be bridged through `JBCefJSQuery`. |
| draw.io embedding | Apache-2.0 (source) | yes | JS app via JCEF, bundled offline | Yes. v31.5.2 (2026-09) | everything incl. G, own palette | A complete application with its own file format, menus, undo and theming. Precedent: the "Diagrams.net Integration" plug-in (docToolchain), which reports blank-canvas problems with JCEF out-of-process mode and breakage on 2026.2. Fits a `.drawio` designer, not a reusable core for our own formats. About 50 MB. |
| JointJS core | MPL-2.0 | weak | JS via JCEF | Yes, active (push 2026-09) | S P E L R (orthogonal, manhattan) I M Z F | MPL file-level copyleft; many editor features (palette, property inspector, selection tools) are in the commercial JointJS+. |
| Eclipse Sprotty | EPL-2.0 | weak | TS via JCEF, model server | Slow. v1.4.0 (2024-12) | S P E L R M Z | Designed around a separate language/model server; heavy architecture for our need. |
| React Flow / xyflow | MIT | yes | React via JCEF | Yes. Releases weekly (latest 2026-09-24) | P E (straight, step, bezier) L I M Z, custom S | Needs a React bundle; no orthogonal obstacle routing, no swimlanes, no property grid. |
| GoJS | Commercial | no | JS | Yes | everything | Excluded by licence. |
| Cytoscape.js | MIT | yes | JS via JCEF | Yes. v3.34.3 (2026-09) | S E (bezier, taxi) Z M A | Graph analysis/visualisation; weak on editing (no ports, in-place editing, palette). |
| Dagre | MIT | yes | JS (layout only) | Yes. v2.0.0 (2025-11) | A (layered), simple R | Layout only; would need JS on the JVM or JCEF. |
| libavoid / adaptagrams | LGPL-2.1 | no | C++ (native) | Repo active (push 2025-10) | R (object-avoiding orthogonal and poly-line) | Best-known connector router, refused by licence and native-code packaging. |
| kmp-graphine | Apache-2.0 | yes | Compose Multiplatform (Jewel) | New: created 2026-08, 1 star | S P M Z A, orthogonal R | Too young to depend on. Only notable Compose diagram library found. |

## Recommendation

**Build the core ourselves on Swing/Java2D, using platform components.** No surveyed library
offers the editing features without also owning the model, the undo stack or the serialisation,
and each of those conflicts with principles I and II. The ones with a clean licence and full
coverage are either archived (JGraphX, mxGraph) or run in JCEF (maxGraph, draw.io), where every
platform integration (undo, keymap, themes, selection, find, headless tests) becomes a JS bridge
and JCEF support is not guaranteed in every IDE setup.

Adopt:

- **Platform only**, no new runtime dependency for the first version: Java2D (`Path2D`,
  `CubicCurve2D`, `BasicStroke` dashes, `LineBreakMeasurer` for text wrapping), `JBColor`/`JBUI`
  for themes and scaling, `DnDManager` for palette drag, `EditorTextField` or a `JBTextField`
  overlay for in-place editing, `JBTable` or Kotlin UI DSL for the property panel, JSVG/Batik
  (bundled) if SVG shapes or export are needed.
- **JGraphX and GEF Classic as design references only** (read, do not depend): JGraphX for edge
  styles, perimeter/anchor maths and swimlane behaviour; GEF for the EditPart, Request and
  Command split that maps well onto platform undoable commands.

Keep in reserve:

- **Eclipse ELK** (EPL-2.0) if a later spec asks for automatic layout. It is the only serious
  open layout engine; its licence needs an explicit decision recorded in the plan's complexity
  tracking before adoption.
- **maxGraph via JCEF** only if a future spec decides a web-based canvas is acceptable; it is the
  one Apache-2.0, maintained, feature-complete option.
- **Compose/Jewel** once JetBrains declares it stable for third-party plug-ins.

Reject:

- yFiles, GoJS (commercial); libavoid (LGPL); JGraphT (not reachable from the platform, LGPL/EPL,
  not needed); `com.intellij.diagram` and `com.intellij.openapi.graph` (paid IDEs only, yFiles
  underneath, not a common module); the internal `PropertyTable` (`@ApiStatus.Internal`).
- Piccolo2D and JUNG (stale, little value over plain Java2D); Sprotty, JointJS core, React Flow,
  Cytoscape.js, Dagre (JCEF route plus partial coverage, and MPL/EPL for the first two).
- draw.io embedding as the core: it is a whole application with its own format. It could be a
  separate designer for `.drawio` files, but a community plug-in already does that.

Still to build ourselves: diagram view model mapped onto format-specific text edits; renderer
and hit testing; shape set; anchors and ports; text wrap and auto-size; edge geometry for
straight, orthogonal and bezier; arrowheads and dash styles; mid and end labels; an orthogonal
router (grid or visibility-graph A* around node bounds; a few hundred lines); selection,
move and resize handles; pan and zoom; in-place editing; palette tool window with drag onto the
canvas; swimlanes/sectors as container nodes; property panel; and mapping every gesture to one
platform undoable command.

## Sources

- JGraphX: <https://github.com/jgraph/jgraphx> (archived), Maven <https://repo1.maven.org/maven2/com/github/vlsi/mxgraph/jgraphx/maven-metadata.xml>
- JGraphT: <https://github.com/jgrapht/jgrapht> (licence in README), Maven <https://repo1.maven.org/maven2/org/jgrapht/jgrapht-core/maven-metadata.xml>
- Eclipse ELK: <https://github.com/eclipse/elk/releases>, <https://eclipse.dev/elk/>
- elkjs: <https://github.com/kieler/elkjs/releases>
- Piccolo2D: <https://github.com/piccolo2d/piccolo2d.java>, Maven <https://repo1.maven.org/maven2/org/piccolo2d/piccolo2d-core/maven-metadata.xml>
- JUNG: <https://github.com/jrtom/jung>, Maven <https://repo1.maven.org/maven2/net/sf/jung/jung-api/maven-metadata.xml>
- yFiles: <https://www.yworks.com/products/yfiles-for-java>
- GEF Classic: <https://github.com/eclipse-gef/gef-classic/releases>, <https://download.eclipse.org/tools/gef/classic/release/latest/>
- mxGraph: <https://github.com/jgraph/mxgraph> (archived); maxGraph: <https://github.com/maxGraph/maxGraph/releases>
- draw.io: <https://github.com/jgraph/drawio> (Apache-2.0); IntelliJ plug-in: <https://github.com/docToolchain/diagrams.net-intellij-plugin>, <https://plugins.jetbrains.com/plugin/15635-diagrams-net-integration>
- JointJS: <https://github.com/clientIO/joint> (MPL-2.0)
- Sprotty: <https://github.com/eclipse-sprotty/sprotty/releases>
- xyflow: <https://github.com/xyflow/xyflow>
- GoJS: <https://gojs.net/latest/license.html>
- Cytoscape.js: <https://github.com/cytoscape/cytoscape.js/releases>
- Dagre: <https://github.com/dagrejs/dagre/releases>
- adaptagrams: <https://github.com/mjwybrow/adaptagrams> (`cola/COPYING`, LGPL-2.1)
- kmp-graphine: <https://github.com/KarpiLabs/kmp-graphine>
- JCEF in plug-ins: <https://plugins.jetbrains.com/docs/intellij/embedded-browser-jcef.html>
- Jewel/Compose: <https://github.com/JetBrains/intellij-community/tree/master/platform/jewel>, <https://github.com/JetBrains/jewel/blob/main/README.md>
- Diagrams API availability: <https://platform.jetbrains.com/t/any-documentation-for-com-intellij-diagrams/320>, <https://intellij-support.jetbrains.com/hc/en-us/community/posts/18878444756754-use-Diagrams-platform-in-a-new-plugin>
- Bundled libraries: local IntelliJ IDEA 2026.2.3 distribution, `license/third-party-libraries.json`, `lib/`, `plugins/uml`, `plugins/fullLine`.
