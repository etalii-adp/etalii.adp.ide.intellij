# Contract: ADP Designer Framework (`etalii.adp.core`)

What a supported file format codes against. The `core` module knows nothing about any format;
a format module depends on `core` and the platform, never on another format (principle III).

## `AdpEditorProvider` (abstract, implements `FileEditorProvider`, `DumbAware`)

Provided: `accept` (extension check plus sniff), `createEditor` (a `TextEditorWithPreview` of the
platform `TextEditor` and the format's designer, default layout preview-only), `getPolicy`
(`HIDE_DEFAULT_EDITOR`), and the strip-trailing-spaces opt-out for accepted files.

A format implements:

```java
/** File extensions this format may claim, lower case, without the dot. */
protected abstract Set<String> extensions();

/** True when the start of the file (at most 4 KB) is this format. Must not throw. */
protected abstract boolean sniff(byte[] head);

/** The designer for one opened file. */
protected abstract AdpDesignerEditor<?> createDesigner(Project project, VirtualFile file, Document document);

/** Editor type id and the name shown on the composite editor. */
public abstract String getEditorTypeId();
protected abstract String editorName();
```

## `AdpDesignerEditor<M>` (abstract, implements `FileEditor`, `DocumentReferenceProvider`)

Provided by the framework:

- Parsing on open and after every document change, coalesced to one parse per event-dispatch
  turn. Success goes to `modelChanged(M)`; a `FormatProblem` shows the problem panel with
  message, line and column and a "Show Text" link that switches the composite to the text
  layout. On open with a problem, the composite starts in the text layout. Nothing is written.
- A read-only banner (`EditorNotificationPanel`) and `isEditable()`, tracking the document's
  writability and `ReadonlyStatusHandler`.
- `execute(label, changes)`: one `WriteCommandAction` named `label` that applies the changes
  (research R4, R7). No-op when not editable. Overlapping changes throw before anything is applied.
- Undo and redo integration through `getDocumentReferences()`.
- `DataContext` publishing: `ADP_DESIGNER`, `SELECTED_ITEMS`.
- Zoom (`ViewState.zoom`) and the scroll pane.
- Lifecycle: `dispose()` removes document listeners; `getState`/`setState` keep zoom and
  selection across tab switches (never written to the file).

A format implements:

```java
/** Parse the whole text. Throw FormatProblem when it cannot be shown. Must not modify anything. */
protected abstract M parse(CharSequence text) throws FormatProblem;

/** The component that shows a model; called once. */
protected abstract JComponent createView();

/** Show a freshly parsed model, keeping selection and expansion by key. */
protected abstract void modelChanged(M model);

/** The Structure view for this format, or null. */
@Override public abstract StructureViewBuilder getStructureViewBuilder();
```

A format may call:

```java
public M model();                    // latest successful parse, or null while a problem is shown
public boolean isEditable();         // false when read-only or a problem is shown
public void execute(String label, TextChanges changes);
public Document document();
public Project project();
```

## `TextChange`, `TextChanges`, `Rgb`, `FormatProblem`

As in data-model.md. `FormatProblem(String message, int offset)` is a checked exception.

## Format module layout (FreeMind)

| Package | Depends on | Contents |
|---|---|---|
| `etalii.adp.freemind.model` | `core` value types only | `MindMap`, `MapNode`, `ArrowLink`, `NodeKey`, `NodeRanges`, `Range`, `Side`, `FontSpec`, `AttributeRange` |
| `etalii.adp.freemind.parse` | model, JDK SAX | `XmlScanner`, `MindMapParser`, `RichText` |
| `etalii.adp.freemind.edit` | model, `core` value types | `MindMapEdits`, `FreeMindConventions` |
| `etalii.adp.freemind` | the above | `FreeMindSniffer`, `FreeMindFileType`, `FreeMindFileTypeDetector` |
| `etalii.adp.freemind.ui` | platform, Swing | `MindMapEditorProvider`, `MindMapDesigner`, `MindMapCanvas`, `MindMapLayout`, `NodeView`, `NodePainter`, `FreeMindIcons`, `LinkOpener`, `MindMapStructureView`, `NewMindMapAction`, `actions/*` |

The first three packages import nothing from the platform, AWT or Swing ("format layer purity"
test).
