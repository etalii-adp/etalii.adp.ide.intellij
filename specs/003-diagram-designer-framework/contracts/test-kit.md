# Contract: Test Kit Additions (`testing` module, `etalii.adp.testing`)

Spec 002's `DesignerDriver` is unchanged (research R19). A new `DiagramDriver` wraps it for diagram designers: `DiagramDriver.open(fixture, file)` and `openText(fixture, name, content)` open like `DesignerDriver`, and `driver()` returns the wrapped `DesignerDriver` for select, undo, redo, text, save and the rest. Its methods let every acceptance scenario in the spec be a headless test. Positions are in unzoomed diagram coordinates. The driver converts them to screen coordinates at the current zoom and scroll, and dispatches real mouse and drag events to the canvas, so the canvas's own gesture code runs.

```java
public final class DiagramDriver implements AutoCloseable {
    public DesignerDriver driver();
    public DiagramDesigner designer();

    // Observing the diagram
    public ElementView elementView(Object key);               // null when not shown
    public ConnectionView connectionView(Object key);
    public List<Object> elementKeys();                        // shown elements, painting order
    public List<Object> connectionKeys();
    public List<Handle> handlesOf(Object key);                // resize handles offered for the selection (N, NE, E, ...)
    public List<AnchorView> anchorsOf(Object key);            // id, position, visible
    public String refusal();                                  // reason of the last refused gesture, or null
    public List<DiagramChange> lastChanges();                 // what the designer's listener received last

    // Acting on the canvas
    public DesignerDriver moveBy(double dx, double dy, Object... keys);         // select and drag
    public DesignerDriver resize(Object key, Handle handle, double dx, double dy);
    public DesignerDriver connect(Object fromKey, String fromAnchor, Object toKey, String toAnchor);
    public DesignerDriver connect(String connectionType, Object fromKey, String fromAnchor, Object toKey, String toAnchor);
    public DesignerDriver dragToSector(Object key, Object sectorKey);
    public DesignerDriver doubleClickText(Object key, String slotOrLabel);      // opens in-place editing if editable
    public boolean inPlaceEditing();
    public DesignerDriver marquee(double x1, double y1, double x2, double y2);
    public DesignerDriver zoom(int steps);                    // Ctrl+wheel; negative zooms out
    public double zoomLevel();

    // Toolbox (the real tool window content)
    public List<String> toolboxEntries();                     // type ids in toolbox order
    public DesignerDriver dragFromToolbox(String typeId, double x, double y);   // through DnDManager
    public DesignerDriver dragFromToolboxOnto(String typeId, Object targetKey);
    public DesignerDriver addFromToolboxWithKeyboard(String typeId);            // Enter on the entry

    // Property panel (the real tool window content)
    public PropertyRows properties();                         // rows for the current selection
    public DesignerDriver setProperty(String propertyId, String value);        // through the cell editor, then commit

    public record PropertyRows(List<PropertyRow> rows) {
        public PropertyRow row(String propertyId);
    }
    public record PropertyRow(String id, String label, String value, EditorKind editor,
                              boolean readOnly, boolean mixed) {}
}
```

A typical test for acceptance scenario US2-2:

```java
try (var d = DiagramDriver.open(myFixture, sample("two-tasks.adpsample"))) {
    var before = d.driver().text();
    d.connect("flow", "b", "in", "a", "out");
    assertEquals("'flow' may not start at anchor 'in' of 'task'", d.refusal());
    assertEquals(before, d.driver().text());
    assertEquals(List.of("f1"), d.connectionKeys());
}
```

## Performance helpers

`SampleFiles.generate(int elements, int connections, long seed)` writes a laid-out `.adpsample` file for SC-005. `OpenPerformanceTest` and `EditPerformanceTest` in `core` measure open-to-first-paint and edit-to-repaint on the event dispatch thread. They take the median of five runs after one warm-up, as the FreeMind performance tests do.
