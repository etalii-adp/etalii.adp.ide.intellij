# Contract: Designer Test Kit (`testing` module, `etalii.adp.testing`)

SC-007 of spec 001 still applies: every visual behaviour is verifiable by an unattended test, and
a new such test takes under 30 minutes. `DesignerDriver` keeps spec 001's shape so the spec 001
tests port one-to-one; only host types change. It runs inside a `BasePlatformTestCase` (headless
IDE), on the event dispatch thread, and knows nothing about FreeMind.

```java
public final class DesignerDriver implements AutoCloseable {
    /** Copy an example file into the test project and open it as the IDE would. */
    public static DesignerDriver open(CodeInsightTestFixture fixture, Path exampleFile);
    /** Same, for an in-memory text (generated maps, edge cases). */
    public static DesignerDriver openText(CodeInsightTestFixture fixture, String fileName, String content);

    public AdpDesignerEditor<?> designer();
    public List<String> editorTypeIdsOffered();   // registration tests: which editors the IDE offers
    public String editorTypeIdUsed();             // which one opened by default

    // Acting
    public DesignerDriver select(Object... keys);        // format keys, e.g. NodeKey
    public DesignerDriver run(String actionId);          // through ActionManager with the designer's DataContext, as keys and menus do
    public DesignerDriver press(String keystroke);       // dispatches a key event to the focused canvas (shortcut tests)
    public DesignerDriver dragOnto(Object key, Object targetKey, DropPosition where);
    public DesignerDriver typeInPlace(String text);      // finishes an open in-place rename
    public DesignerDriver undo();                        // the platform's $Undo with the designer focused
    public DesignerDriver redo();
    public DesignerDriver showLayout(Layout layout);     // DESIGNER, TEXT or SPLIT
    public DesignerDriver editText(UnaryOperator<String> change);   // as a typed change in the text editor
    public DesignerDriver changeOnDisk(String newContent);          // external change + VFS refresh
    public DesignerDriver setReadOnly(boolean readOnly);

    // Observing
    public String text();                  // current document text
    public byte[] savedBytes();            // save all documents, then read the file's bytes
    public boolean isModified();
    public String undoLabel();             // "Undo <label>", as the Edit menu shows it
    public NodeView viewOf(Object key);    // laid-out box: bounds, colours, font, icons, indicators; null when not shown
    public List<Object> selectedKeys();
    public boolean problemShown();
    public boolean readOnlyBannerShown();
    public StructureViewModel structure(); // the Structure view model for the open designer
    public void settle();                  // runs pending EDT work (coalesced re-parse, repaint)

    @Override public void close();         // close without saving
}
```

A typical test, the 30-minute target:

```java
try (var d = DesignerDriver.open(myFixture, example("freemind-0.8.1-large-arrow-links.mm"))) {
    var before = d.text();
    d.select(key("ID_123")).run(DELETE);
    assertNull(d.viewOf(key("ID_123")));
    assertFalse(d.text().contains("DESTINATION=\"ID_123\""));
    d.undo();
    assertEquals(before, d.text());
    assertFalse(d.isModified());
}
```

The format's test source set adds `FreeMindAsserts.key(...)`, `example(...)` and
`reference(...)` helpers. Examples live in `freemind/testdata/examples/`, reference results in
`freemind/testdata/reference/` (data-model.md).

## Integration suite (`integrationTest` source set)

Starter + Driver, JUnit 5. One parameterised test per product (IntelliJ IDEA, Rider, WebStorm,
PyCharm; 2026.2): start the IDE with the built plug-in zip installed, open a project containing
one vendored map and one Objective-C++-style non-FreeMind `.mm` file, and assert: the map opens
in the FreeMind designer; the other file does not; one Add Child Node followed by Undo returns the
file to its original bytes. IntelliJ IDEA runs twice, without and with an Ultimate trial
licence where the environment provides one (skipped with a reason otherwise).
