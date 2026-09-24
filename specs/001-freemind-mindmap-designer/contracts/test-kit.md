# Contract: Designer Test Kit (`etalii.adp.testing`)

SC-007 asks that every visual behaviour be verifiable by an unattended test, and that a new such
test take under 30 minutes to write. `DesignerDriver` is the one entry point for that. It runs
inside the Tycho UI harness on the UI thread and knows nothing about FreeMind, so later formats
reuse it.

```java
public final class DesignerDriver implements AutoCloseable {
    /** Copy an example file into a fresh workspace project and open it with the given editor. */
    public static DesignerDriver open(Path exampleFile, String editorId);
    /** Same, for an in-memory text (generated maps, edge cases). */
    public static DesignerDriver openText(String fileName, String content, String editorId);

    public AdpDesignerEditor<?> editor();
    public String editorIdUsed();            // registration tests: which editor the workbench chose

    // Acting
    public DesignerDriver select(Object... models);    // GEF models, e.g. NodeKey
    public DesignerDriver run(String commandId);       // through IHandlerService, as keys and menus do
    public DesignerDriver dragOnto(Object model, Object targetModel, DropPosition where);
    public DesignerDriver typeInPlace(String text);    // finishes an open direct edit
    public DesignerDriver undo();                       // the workbench history, standard command
    public DesignerDriver redo();
    public DesignerDriver showPage(Page page);          // VISUAL or TEXT
    public DesignerDriver editText(UnaryOperator<String> change);   // edit through the text page
    public DesignerDriver changeOnDisk(String newContent);          // external change

    // Observing
    public String text();                     // current document
    public byte[] savedBytes();               // save, then read the file from disk
    public boolean isDirty();
    public String undoLabel();                // the Edit menu's current undo label
    public IFigure figureOf(Object model);    // rendered figure, for layout, colour and font asserts
    public List<Object> selectedModels();
    public boolean problemShown();
    public void settle();                     // runs pending async UI work (coalesced re-parse)

    @Override public void close();            // close without saving, delete the project
}
```

A typical FR test, which is the 30-minute target:

```java
try (var d = DesignerDriver.open(example("arrow-links.mm"), MindMapEditor.ID)) {
    var before = d.text();
    d.select(key("ID_123")).run(DELETE);
    assertNull(d.figureOf(key("ID_123")));
    assertFalse(d.text().contains("DESTINATION=\"ID_123\""));
    d.undo();
    assertEquals(before, d.text());
    assertFalse(d.isDirty());
}
```

Format bundles add small helpers, such as `MindMapAsserts.key(...)` and `example(...)`, in their
own test fragment.
