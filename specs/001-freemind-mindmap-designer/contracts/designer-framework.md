# Contract: ADP Designer Framework (`etalii.adp.core`)

This is what a supported file format codes against. `etalii.adp.core` exports only the package
`etalii.adp.core`. A format bundle depends on it, on GEF Classic, and on nothing from another
format (principle III).

## `AdpDesignerEditor<M>` (abstract, extends `MultiPageEditorPart`)

Provided by the framework:

- Page 0 is the visual page (a GEF `ScrollingGraphicalViewer` with `ScalableFreeformRootEditPart`,
  edit domain, key handler, context menu). Page 1 is a nested `TextEditor` on
  `TextFileDocumentProvider`.
- Save, Save As, dirty state, revert and external-change handling are delegated to the text
  editor.
- Parsing on open and after every document change (coalesced to one parse per UI event-loop
  turn). The result goes to the viewer through `modelChanged`. A `FormatProblem` shows the
  problem panel.
- A read-only banner, and `isEditable()`.
- Undo and redo action handlers for the document's undo context on the visual page.
- Selection provider switching between the pages.

A format implements:

```java
/** Parse the whole document. Throw FormatProblem when it cannot be shown. Must not modify it. */
protected abstract M parse(IDocument document) throws FormatProblem;

/** Edit parts for this format's model. Called once when the viewer is created. */
protected abstract EditPartFactory createEditPartFactory();

/** The object handed to GraphicalViewer.setContents for a freshly parsed model. */
protected abstract Object contentsFor(M model);

/** The context id to activate while the visual page has focus. */
protected abstract String visualContextId();
```

A format may call:

```java
/** The latest successful parse, or null while a FormatProblem is shown. */
public M model();

/** False when the input is read-only or a FormatProblem is shown. */
public boolean isEditable();

/** Run one labelled, undoable change against the document (R2). No-op when not editable. */
public void execute(String label, TextEdit edit);

public GraphicalViewer viewer();
public IDocument document();
```

## `DocumentEditOperation`

`new DocumentEditOperation(String label, IDocument document, TextEdit edit)`. It is an
`AbstractOperation` under the document's `IDocumentUndoManager` context. `execute` applies the
edit as one compound change. It is run through `TriggeredOperations` so that undo and redo are
one step with this label. A `MalformedTreeException` or `BadLocationException` returns an error
status, and the document is left unchanged.

## `TextEditCommand` and `OperationHistoryCommandStack`

`TextEditCommand(String label, Supplier<TextEdit> edit)` extends GEF `Command`. The framework
installs `OperationHistoryCommandStack` in the edit domain. Its `execute(Command)` routes a
`TextEditCommand` to `AdpDesignerEditor.execute` and refuses any other command. Its own
undo and redo stacks stay empty, so GEF's undo actions never compete with the workbench history.

## `FormatProblem`

`public FormatProblem(String message, int offset)`, a checked exception. The framework turns the
offset into a line and column for the problem panel (FR-007).
