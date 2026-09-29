# Tool types

The tool types this IntelliJ Platform plug-in supports or plans to support, each of one kind (diagram, designer or editor, as the [ADP glossary](https://github.com/etalii-adp/etalii.adp/blob/develop/docs/terminology.md) defines them), with how far along each one is, the theory behind its notation and an example. The format is the one [etalii.adp.ide.standalone's `docs/tools.md`](https://github.com/etalii-adp/etalii.adp.ide.standalone/blob/develop/docs/tools.md) uses, so the [ADP website](https://etalii.net/adp) reads every host's catalogue the same way.

**This document is a living catalog: move a row's state in the same change that moves the tool, and add a row when a new tool type is identified.** The website's tool catalogue and screenshots are refreshed from this file, so a row missing here is a tool missing there.

## Legend

**State** — how far the plug-in's support for the diagram type has progressed:

| Icon | State | Meaning |
|---|---|---|
| 💡 | Identified | Recognized as a candidate diagram type; no spec yet |
| 📝 | Specified | A specification exists (`specs/`) |
| ⏸️ | To-do | Specification planned and queued for implementation, not yet started |
| 🛠️ | Work-in-progress | Actively being implemented |
| ⚗️ | Prototype | Implemented as a working prototype; usable, not yet hardened to full quality |
| ✅ | Implemented | Shipped and usable in the plug-in |

**Origin** — a MIME-type-style tag identifying where the notation comes from, in the form `<architecture-or-vendor>/<diagram-type>` (e.g. `uml/class`, `c4/context`). Diagram types with no single owning standards body use the tool or author most associated with them as the vendor (e.g. `freeplane/mindmap`). A diagram type every ADP host supports carries the same origin in each host's catalogue.

**Kind** — the tool's kind: Diagram, Designer or Editor. Both tools in this plug-in are diagrams.

---

<table>
  <thead>
    <tr><th>State</th><th>Origin</th><th>Kind</th><th>Diagram</th><th>Theory</th><th>Example</th></tr>
  </thead>
  <tbody>
    <tr><td colspan="6"><h3>Knowledge & informal modeling</h3><p>Diagram types for capturing and structuring knowledge rather than formal system architecture. No single standards body owns these, so <code>Origin</code> names the tool most associated with the notation.</p></td></tr>
    <tr><td style="white-space: nowrap;">✅&nbsp;Implemented</td><td style="white-space: nowrap;"><code>freeplane/mindmap</code></td><td>Diagram</td><td>FreeMind mind map (radial/hierarchical, single central topic; FreeMind and Freeplane <code>.mm</code> files)</td><td><a href="https://freemind.sourceforge.io/">FreeMind</a> · <a href="https://www.freeplane.org/">Freeplane</a> · <code>.mm</code> file format</td><td><a href="https://github.com/etalii-adp/etalii.adp.ide.intellij/tree/develop/freemind/testdata/examples">Example maps</a></td></tr>
    <tr><td colspan="6"><h3>General-purpose diagramming</h3><p>Free-form diagramming tools whose files can hold any notation: flowcharts, activity diagrams, swimlanes, UML sketches. The diagram edits the tool's own file format and keeps everything it does not show.</p></td></tr>
    <tr><td style="white-space: nowrap;">✅&nbsp;Implemented</td><td style="white-space: nowrap;"><code>jgraph/drawio</code></td><td>Diagram</td><td>draw.io diagram (shapes, edges, labels and swimlanes; uncompressed <code>.drawio</code> files)</td><td><a href="https://www.drawio.com/">draw.io</a> · <a href="https://www.drawio.com/doc/faq/diagram-source-edit">Diagram source format</a></td><td><a href="https://github.com/etalii-adp/etalii.adp.ide.intellij/tree/develop/drawio/testdata/examples">Example diagrams</a></td></tr>
  </tbody>
</table>
