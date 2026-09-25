# Spec 001 test inventory

This file lists every spec 001 test method with the behaviour it verifies and the new test that covers it (FR-018, SC-007).
`InventoryCoverageTest` fails when a row's new test class or method does not exist.

| Spec 001 test | Behaviour verified | New test |
|---|---|---|
| `AdpDesignerEditorTest.theTextPageIsATextEditorOnTheSameDocument` | The text page is a text editor on the same document as the designer. | `AdpEditorProviderTest.theTextPageIsATextEditorOnTheSameDocument` |
| `AdpDesignerEditorTest.saveDirtyAndRevertDelegateToTheTextEditor` | Save, dirty state and revert are delegated to the text editor. | `AdpEditorProviderTest.saveDirtyAndRevertDelegateToTheTextEditor` |
| `AdpDesignerEditorTest.aDocumentChangeReparsesOncePerEventLoopTurn` | Document changes are reparsed at most once per event loop turn. | `AdpEditorProviderTest.aDocumentChangeReparsesOncePerEventLoopTurn` |
| `AdpDesignerEditorTest.aFormatProblemShowsLineAndColumnAndChangesNothing` | A format problem is shown with its line and column and the text is left unchanged. | `AdpEditorProviderTest.aFormatProblemShowsLineAndColumnAndChangesNothing` |
| `AdpDesignerEditorTest.aMalformedFileOpensOnTheTextPageUnmodified` | A malformed file opens on the text page without being modified. | `AdpEditorProviderTest.aMalformedFileOpensOnTheTextPageUnmodified` |
| `AdpDesignerEditorTest.aReadOnlyInputShowsTheBannerAndCannotBeEdited` | A read-only input shows a banner and cannot be edited. | `AdpEditorProviderTest.aReadOnlyInputShowsTheBannerAndCannotBeEdited` |
| `AdpDesignerEditorTest.executeLandsOneLabelledUndoEntry` | Executing a designer edit adds exactly one labelled undo entry. | `AdpEditorProviderTest.executeLandsOneLabelledUndoEntry` |
| `DocumentEditOperationTest.oneLabelledEntryThatUndoesAndRedoesInOneStep` | A text edit is one labelled entry that undoes and redoes in one step. | `UndoBridgeTest.oneLabelledEntryThatUndoesAndRedoesInOneStep` |
| `DocumentEditOperationTest.typingAfterAnEditIsItsOwnEntry` | Typing after a designer edit becomes its own undo entry. | `UndoBridgeTest.typingAfterAnEditIsItsOwnEntry` |
| `DocumentEditOperationTest.aBadEditLeavesTheDocumentUnchanged` | An edit that cannot be applied leaves the document unchanged. | `TextChangesTest.aBadEditLeavesTheDocumentUnchanged` |
| `OperationHistoryCommandStackTest.routesATextEditCommandToItsExecutor` | A text edit command is routed to its executor. | `UndoBridgeTest.routesATextEditCommandToItsExecutor` |
| `OperationHistoryCommandStackTest.refusesOtherCommands` | Commands that are not text edits are refused. | `UndoBridgeTest.refusesOtherCommands` |
| `OperationHistoryCommandStackTest.keepsNoUndoOrRedoStackOfItsOwn` | The bridge keeps no undo or redo stack of its own. | `UndoBridgeTest.keepsNoUndoOrRedoStackOfItsOwn` |
| `FreeMindConventionsTest.escapingWritesTheFourEntitiesAndNumericReferences` | Escaping writes the four FreeMind entities and numeric character references. | `FreeMindConventionsTest.escapingWritesTheFourEntitiesAndNumericReferences` |
| `FreeMindConventionsTest.newIdsArePositiveIntsUniqueInTheMap` | New node ids are positive integers that are unique in the map. | `FreeMindConventionsTest.newIdsArePositiveIntsUniqueInTheMap` |
| `FreeMindConventionsTest.detectsTheLineSeparator` | The file's line separator is detected. | `FreeMindConventionsTest.detectsTheLineSeparator` |
| `FreeMindConventionsTest.detectsTheIndentUnit` | The file's indent unit is detected. | `FreeMindConventionsTest.detectsTheIndentUnit` |
| `MindMapEditsTest.addChildExpandsASelfClosingParent` | Adding a child to a self-closing node expands it into start and end tags. | `MindMapEditsTest.addChildExpandsASelfClosingParent` |
| `MindMapEditsTest.addChildGoesLastAndRefreshesTheParentsModified` | A new child goes last and refreshes the parent's modified time. | `MindMapEditsTest.addChildGoesLastAndRefreshesTheParentsModified` |
| `MindMapEditsTest.addChildToTheRootGetsAPositionOnTheLighterSide` | A new child of the root gets a position on the side with fewer nodes. | `MindMapEditsTest.addChildToTheRootGetsAPositionOnTheLighterSide` |
| `MindMapEditsTest.addSiblingGoesRightAfterTheNode` | A new sibling is inserted right after the node. | `MindMapEditsTest.addSiblingGoesRightAfterTheNode` |
| `MindMapEditsTest.addSiblingOnTheFirstLevelTakesTheSameSide` | A new first-level sibling takes the same side as the node. | `MindMapEditsTest.addSiblingOnTheFirstLevelTakesTheSameSide` |
| `MindMapEditsTest.addSiblingOfTheRootIsRefused` | Adding a sibling of the root is refused. | `MindMapEditsTest.addSiblingOfTheRootIsRefused` |
| `MindMapEditsTest.renameEscapesTheTextAndRefreshesModified` | Renaming escapes the text and refreshes the modified time. | `MindMapEditsTest.renameEscapesTheTextAndRefreshesModified` |
| `MindMapEditsTest.renamingARichNodeReplacesItsRichContentWithText` | Renaming a rich-text node replaces its rich content with plain text. | `MindMapEditsTest.renamingARichNodeReplacesItsRichContentWithText` |
| `MindMapEditsTest.deleteRemovesTheNodeAndArrowLinksTargetingIt` | Deleting a node also removes arrow links that target it. | `MindMapEditsTest.deleteRemovesTheNodeAndArrowLinksTargetingIt` |
| `MindMapEditsTest.deleteRemovesWholeSubtreesAndLinksIntoThem` | Deleting removes whole subtrees and arrow links into them. | `MindMapEditsTest.deleteRemovesWholeSubtreesAndLinksIntoThem` |
| `MindMapEditsTest.deletingTheRootIsRefused` | Deleting the root is refused. | `MindMapEditsTest.deletingTheRootIsRefused` |
| `MindMapEditsTest.moveUpAndDownSwapSiblings` | Move up and move down swap a node with its sibling. | `MindMapEditsTest.moveUpAndDownSwapSiblings` |
| `MindMapEditsTest.moveIntoASelfClosingNodeExpandsIt` | Moving a node into a self-closing node expands that node. | `MindMapEditsTest.moveIntoASelfClosingNodeExpandsIt` |
| `MindMapEditsTest.movingToTheFirstLevelAddsPosition` | Moving a node to the first level adds a position attribute. | `MindMapEditsTest.movingToTheFirstLevelAddsPosition` |
| `MindMapEditsTest.movingAwayFromTheFirstLevelRemovesPosition` | Moving a node away from the first level removes its position attribute. | `MindMapEditsTest.movingAwayFromTheFirstLevelRemovesPosition` |
| `MindMapEditsTest.movesIntoTheOwnSubtreeOrOfTheRootAreRefused` | Moves into the node's own subtree and moves of the root are refused. | `MindMapEditsTest.movesIntoTheOwnSubtreeOrOfTheRootAreRefused` |
| `MindMapEditsTest.foldInsertsAndUnfoldRemovesFolded` | Folding inserts the folded attribute and unfolding removes it. | `MindMapEditsTest.foldInsertsAndUnfoldRemovesFolded` |
| `MindMapEditsTest.editsOnRealMapsChangeOnlyTheEditedRange` | Edits on real example maps change only the edited range. | `MindMapEditsTest.editsOnRealMapsChangeOnlyTheEditedRange` |
| `MindMapEditsTest.writtenFormsMatchTheFreeMind101Examples` | Written XML forms match the FreeMind 1.0.1 examples. | `MindMapEditsTest.writtenFormsMatchTheFreeMind101Examples` |
| `MindMapParserTest.readsTheFieldsOfTheDataModel` | The parser reads every field of the data model. | `MindMapParserTest.readsTheFieldsOfTheDataModel` |
| `MindMapParserTest.readsArrowLinks` | The parser reads arrow links. | `MindMapParserTest.readsArrowLinks` |
| `MindMapParserTest.unknownContentIsNotAFieldButStaysInsideTheNodesRange` | Unknown content is not read as a field but stays inside the node's range. | `MindMapParserTest.unknownContentIsNotAFieldButStaysInsideTheNodesRange` |
| `MindMapParserTest.wholeLineRangesIncludeIndentAndSeparator` | Whole-line ranges include the indent and the line separator. | `MindMapParserTest.wholeLineRangesIncludeIndentAndSeparator` |
| `MindMapParserTest.theKeyIsTheIdOrTheIndexPath` | A node's key is its id or else its index path. | `MindMapParserTest.theKeyIsTheIdOrTheIndexPath` |
| `MindMapParserTest.malformedXmlIsAFormatProblemWithItsPosition` | Malformed XML is reported as a format problem with its position. | `MindMapParserTest.malformedXmlIsAFormatProblemWithItsPosition` |
| `MindMapParserTest.aDoctypeIsRefused` | A document type declaration is refused. | `MindMapParserTest.aDoctypeIsRefused` |
| `MindMapParserTest.aRootOtherThanMapIsRefused` | A root element other than map is refused. | `MindMapParserTest.aRootOtherThanMapIsRefused` |
| `MindMapParserTest.aMapNeedsExactlyOneTopLevelNode` | A map must have exactly one top-level node. | `MindMapParserTest.aMapNeedsExactlyOneTopLevelNode` |
| `MindMapParserTest.everyExampleParses` | Every example map parses. | `MindMapParserTest.everyExampleParses` |
| `RichTextTest.dropsTagsAndTheHead` | Rich text conversion drops tags and the HTML head. | `RichTextTest.dropsTagsAndTheHead` |
| `RichTextTest.blockTagsAndBreaksBecomeLineBreaks` | Block tags and breaks become line breaks. | `RichTextTest.blockTagsAndBreaksBecomeLineBreaks` |
| `RichTextTest.collapsesSourceWhitespace` | Source whitespace is collapsed. | `RichTextTest.collapsesSourceWhitespace` |
| `RichTextTest.decodesNamedAndNumericEntities` | Named and numeric entities are decoded. | `RichTextTest.decodesNamedAndNumericEntities` |
| `XmlScannerTest.aStartTagReportsExactAttributeRangesInBothQuoteStyles` | A start tag reports exact attribute ranges in both quote styles. | `XmlScannerTest.aStartTagReportsExactAttributeRangesInBothQuoteStyles` |
| `XmlScannerTest.elementEndsCommentsCdataAndProcessingInstructionsHaveExactRanges` | Element ends, comments, CDATA and processing instructions have exact ranges. | `XmlScannerTest.elementEndsCommentsCdataAndProcessingInstructionsHaveExactRanges` |
| `XmlScannerTest.decodesPredefinedAndNumericReferences` | Predefined and numeric references are decoded. | `XmlScannerTest.decodesPredefinedAndNumericReferences` |
| `XmlScannerTest.attributeValuesNormaliseLiteralWhitespace` | Attribute values normalise literal whitespace. | `XmlScannerTest.attributeValuesNormaliseLiteralWhitespace` |
| `XmlScannerTest.rescanningEveryExampleReproducesEveryRangesText` | Rescanning every example reproduces the text of every range. | `XmlScannerTest.rescanningEveryExampleReproducesEveryRangesText` |
| `AddNodeTest.addChildOpensAnInPlaceEditorAndTypingSetsItsText` | Add child opens an in-place editor and typing sets the new node's text. | `AddNodeTest.addChildOpensAnInPlaceEditorAndTypingSetsItsText` |
| `AddNodeTest.aFirstLevelChildGetsASide` | A new first-level child gets a side. | `AddNodeTest.aFirstLevelChildGetsASide` |
| `AddNodeTest.addSiblingGoesRightAfterTheSelectedNode` | Add sibling inserts the node right after the selected node. | `AddNodeTest.addSiblingGoesRightAfterTheSelectedNode` |
| `AddNodeTest.aFirstLevelSiblingKeepsTheSide` | A new first-level sibling keeps the selected node's side. | `AddNodeTest.aFirstLevelSiblingKeepsTheSide` |
| `AddNodeTest.theRootHasNoSiblings` | Add sibling is not available on the root. | `AddNodeTest.theRootHasNoSiblings` |
| `AddNodeTest.aChildOfAFoldedBranchIsShownWithoutAnotherEdit` | A child added to a folded branch is shown without another edit. | `AddNodeTest.aChildOfAFoldedBranchIsShownWithoutAnotherEdit` |
| `AddNodeTest.cancellingTheInPlaceEditorKeepsTheDefaultText` | Cancelling the in-place editor keeps the default text. | `AddNodeTest.cancellingTheInPlaceEditorKeepsTheDefaultText` |
| `ContextMenuTest.theContextMenuListsEveryCommandInTableOrder` | The context menu lists every command in command table order. | `ContextMenuTest.theContextMenuListsEveryCommandInTableOrder` |
| `ContextMenuTest.menuItemsFollowTheSelection` | Menu item enablement follows the selection. | `ContextMenuTest.menuItemsFollowTheSelection` |
| `ContextMenuTest.everyCommandHasItsKeyInTheDesignerContext` | Every command has its key binding in the designer context. | `ContextMenuTest.everyCommandHasItsKeyInTheDesignerContext` |
| `DeleteTest.deleteOneNode` | Deleting one node removes it. | `DeleteTest.deleteOneNode` |
| `DeleteTest.deleteSeveralNodesWithTheirDescendants` | Deleting several nodes removes them with their descendants. | `DeleteTest.deleteSeveralNodesWithTheirDescendants` |
| `DeleteTest.theRootAloneCannotBeDeleted` | The root on its own cannot be deleted. | `DeleteTest.theRootAloneCannotBeDeleted` |
| `DeleteTest.withTheRootSelectedTheOthersAreDeletedAndTheUserIsTold` | With the root selected the other nodes are deleted and the user is told. | `DeleteTest.withTheRootSelectedTheOthersAreDeletedAndTheUserIsTold` |
| `DeleteTest.arrowLinksIntoDeletedNodesGoAndComeBackWithOneUndo` | Arrow links into deleted nodes are removed and come back with one undo. | `DeleteTest.arrowLinksIntoDeletedNodesGoAndComeBackWithOneUndo` |
| `DragMoveTest.droppingBeforeASiblingReordersAsOneMoveEdit` | Dropping before a sibling reorders the nodes as one move edit. | `DragMoveTest.droppingBeforeASiblingReordersAsOneMoveEdit` |
| `DragMoveTest.droppingAfterANodeMovesUnderItsParent` | Dropping after a node moves the dragged node under that node's parent. | `DragMoveTest.droppingAfterANodeMovesUnderItsParent` |
| `DragMoveTest.droppingOntoANodeMakesItTheLastChild` | Dropping onto a node makes the dragged node its last child. | `DragMoveTest.droppingOntoANodeMakesItTheLastChild` |
| `DragMoveTest.droppingIntoItsOwnSubtreeOrDraggingTheRootChangesNothing` | Dropping into the node's own subtree or dragging the root changes nothing. | `DragMoveTest.droppingIntoItsOwnSubtreeOrDraggingTheRootChangesNothing` |
| `DragMoveTest.droppingOnAReadOnlyFileChangesNothing` | Dropping on a read-only file changes nothing. | `DragMoveTest.droppingOnAReadOnlyFileChangesNothing` |
| `ExternalChangeTest.aCleanEditorReloadsTheChangedFile` | A clean editor reloads a file changed outside the IDE. | `ExternalChangeTest.aCleanEditorReloadsTheChangedFile` |
| `ExternalChangeTest.aCleanEditorOnTheTextPageReloadsToo` | A clean editor showing the text page also reloads the changed file. | `ExternalChangeTest.aCleanEditorOnTheTextPageReloadsToo` |
| `ExternalChangeTest.aDirtyEditorKeepsItsTextUntilAsked` | A dirty editor keeps its text until the user is asked. | `ExternalChangeTest.aDirtyEditorKeepsItsTextUntilAsked` |
| `ExternalChangeTest.aDirtyEditorOnTheTextPageAsksAndReplaces` | A dirty editor on the text page asks and replaces its text when the user agrees. | `ExternalChangeTest.aDirtyEditorOnTheTextPageAsksAndReplaces` |
| `ExternalChangeTest.aDirtyEditorOnTheTextPageAsksAndKeeps` | A dirty editor on the text page asks and keeps its text when the user declines. | `ExternalChangeTest.aDirtyEditorOnTheTextPageAsksAndKeeps` |
| `ExternalChangeTest.aDirtyEditorAsksOnceItsTextPageHasBeenActivated` | A dirty editor asks once its text page has been activated. | `ExternalChangeTest.aDirtyEditorAsksOnceItsTextPageHasBeenActivated` |
| `ExternalChangeTest.aDirtyEditorOnTheVisualPageAsks` | A dirty editor on the visual page asks the user. | `ExternalChangeTest.aDirtyEditorOnTheVisualPageAsks` |
| `FoldTest.branchesRecordedAsFoldedShowCollapsed` | Branches recorded as folded are shown collapsed. | `FoldTest.branchesRecordedAsFoldedShowCollapsed` |
| `FoldTest.unfoldingIsALabelledUndoableEdit` | Unfolding is a labelled edit that can be undone. | `FoldTest.unfoldingIsALabelledUndoableEdit` |
| `FoldTest.foldingWritesFoldedAndHidesTheBranch` | Folding writes the folded attribute and hides the branch. | `FoldTest.foldingWritesFoldedAndHidesTheBranch` |
| `FoldTest.severalSelectedBranchesFoldAsOneEdit` | Several selected branches fold as one edit. | `FoldTest.severalSelectedBranchesFoldAsOneEdit` |
| `FoldTest.aLeafCannotBeFolded` | A leaf node cannot be folded. | `FoldTest.aLeafCannotBeFolded` |
| `FoldTest.onAReadOnlyFileFoldingIsViewOnly` | On a read-only file folding changes only the view. | `FoldTest.onAReadOnlyFileFoldingIsViewOnly` |
| `FoldTest.revealExpandsCollapsedAncestorsWithoutAnEdit` | Revealing a node expands its collapsed ancestors without an edit. | `FoldTest.revealExpandsCollapsedAncestorsWithoutAnEdit` |
| `FormatProblemTest.aTextEditIntoAnInvalidMapIsExplainedAndKept` | A text edit that makes the map invalid is explained and kept. | `FormatProblemTest.aTextEditIntoAnInvalidMapIsExplainedAndKept` |
| `FormatProblemTest.aMalformedFileOpensOnTheTextPageUnmodified` | A malformed map file opens on the text page without being modified. | `FormatProblemTest.aMalformedFileOpensOnTheTextPageUnmodified` |
| `FreeMindCompatibilityTest.freeMindReadsWhatTheDesignerSaved` | FreeMind reads the files the designer saved. | `FreeMindCompatibilityTest.freeMindReadsWhatTheDesignerSaved` |
| `InterleavedUndoTest.undoRevertsVisualAndTextEditsInReverseOrderOnEitherPage` | Undo reverts visual and text edits in reverse order on either page. | `InterleavedUndoTest.undoRevertsVisualAndTextEditsInReverseOrderOnEitherPage` |
| `InterleavedUndoTest.theVisualPageFollowsEachUndoOfATextEdit` | The visual page follows each undo of a text edit. | `InterleavedUndoTest.theVisualPageFollowsEachUndoOfATextEdit` |
| `LayoutTest.theRootIsCentredWithBranchesOnTheirSides` | The root is centred and branches are drawn on their sides. | `LayoutTest.theRootIsCentredWithBranchesOnTheirSides` |
| `LayoutTest.horizontalGapIsHonoured` | The layout honours the horizontal gap. | `LayoutTest.horizontalGapIsHonoured` |
| `LayoutTest.verticalGapAndShiftAreHonoured` | The layout honours the vertical gap and shift. | `LayoutTest.verticalGapAndShiftAreHonoured` |
| `LayoutTest.arrowLinksAreConnectionsAndMissingDestinationsAreNotDrawn` | Arrow links are drawn as connections and links to missing nodes are not drawn. | `LayoutTest.arrowLinksAreConnectionsAndMissingDestinationsAreNotDrawn` |
| `LayoutTest.aLargeMapDrawsEveryVisibleNodeAndLink` | A large map draws every visible node and link. | `LayoutTest.aLargeMapDrawsEveryVisibleNodeAndLink` |
| `MoveNodeTest.moveUpAndDownAmongSiblings` | Nodes move up and down among their siblings. | `MoveNodeTest.moveUpAndDownAmongSiblings` |
| `MoveNodeTest.indentMovesUnderThePreviousSibling` | Indent moves a node under its previous sibling. | `MoveNodeTest.indentMovesUnderThePreviousSibling` |
| `MoveNodeTest.outdentMovesAfterTheParentAndGetsASide` | Outdent moves a node after its parent and gives it a side when needed. | `MoveNodeTest.outdentMovesAfterTheParentAndGetsASide` |
| `MoveNodeTest.indentingAFirstLevelNodeDropsItsSide` | Indenting a first-level node drops its side. | `MoveNodeTest.indentingAFirstLevelNodeDropsItsSide` |
| `MoveNodeTest.firstLevelNodesMoveAmongTheirOwnSide` | First-level nodes move among the nodes on their own side. | `MoveNodeTest.firstLevelNodesMoveAmongTheirOwnSide` |
| `MoveNodeTest.enablementFollowsTheCommandTable` | Move command enablement follows the command table. | `MoveNodeTest.enablementFollowsTheCommandTable` |
| `NewWizardTest.createsANewMapAndOpensItInTheDesigner` | Creating a new map writes the file and opens it in the designer. | `NewMapTest.createsANewMapAndOpensItInTheDesigner` |
| `NewWizardTest.eachNewMapGetsAFreshId` | Each new map gets a fresh root id. | `NewMapTest.eachNewMapGetsAFreshId` |
| `NodeDetailsTest.plainAndRichTextAreReadable` | Plain and rich node text are shown readably. | `NodeDetailsTest.plainAndRichTextAreReadable` |
| `NodeDetailsTest.iconsAreGlyphsOrBadges` | Node icons are shown as glyphs or badges. | `NodeDetailsTest.iconsAreGlyphsOrBadges` |
| `NodeDetailsTest.coloursAndFontAreShown` | Node colours and font are shown. | `NodeDetailsTest.coloursAndFontAreShown` |
| `NodeDetailsTest.aNoteIsAnIndicatorWithItsTextAsTooltip` | A note is shown as an indicator with its text as tooltip. | `NodeDetailsTest.aNoteIsAnIndicatorWithItsTextAsTooltip` |
| `NodeDetailsTest.aLinkIndicatorOpensAMapRelativeFile` | A link indicator opens a file relative to the map. | `NodeDetailsTest.aLinkIndicatorOpensAMapRelativeFile` |
| `NodeDetailsTest.urlsAreToldApartFromPaths` | URLs are told apart from file paths. | `NodeDetailsTest.urlsAreToldApartFromPaths` |
| `OutlineTest.listsTheNodeTreeOfALargeMap` | The outline lists the node tree of a large map. | `StructureViewTest.listsTheNodeTreeOfALargeMap` |
| `OutlineTest.followsEdits` | The outline follows edits to the map. | `StructureViewTest.followsEdits` |
| `OutlineTest.selectingInTheOutlineRevealsTheNodeWithoutAnEdit` | Selecting in the outline reveals the node without an edit. | `StructureViewTest.selectingInTheOutlineRevealsTheNodeWithoutAnEdit` |
| `OutlineTest.selectingInTheDesignerSelectsInTheOutline` | Selecting in the designer selects the same node in the outline. | `StructureViewTest.selectingInTheDesignerSelectsInTheOutline` |
| `PerformanceTest.aThousandNodeMapOpensAndEditsWithinBudget` | A generated 1,000-node map opens and draws within its time budget. | `OpenPerformanceTest.aThousandNodeMapOpensAndEditsWithinBudget` |
| `PerformanceTest.aThousandNodeMapOpensAndEditsWithinBudget` | Add, rename, fold and delete on a 1,000-node map show within their time budget. | `EditPerformanceTest.aThousandNodeMapOpensAndEditsWithinBudget` |
| `PreservationTest.mixedEditsChangeOnlyTheEditedNodes` | A mix of edits changes only the bytes of the edited nodes. | `PreservationTest.mixedEditsChangeOnlyTheEditedNodes` |
| `ReadOnlyTest.everyEditCommandIsDisabled` | Every edit command is disabled on a read-only file. | `ReadOnlyTest.everyEditCommandIsDisabled` |
| `ReadOnlyTest.doubleClickDoesNotOpenAnEditor` | Double-click does not open an in-place editor on a read-only file. | `ReadOnlyTest.doubleClickDoesNotOpenAnEditor` |
| `ReadOnlyTest.foldingIsViewOnly` | Folding on a read-only file changes only the view. | `ReadOnlyTest.foldingIsViewOnly` |
| `ReadOnlyTest.theSameCommandsRunOnceWritableAgain` | The same commands run once the file is writable again. | `ReadOnlyTest.theSameCommandsRunOnceWritableAgain` |
| `RegistrationTest.aFreeMindMapOpensInTheDesignerByDefault` | A FreeMind map opens in the designer by default. | `RegistrationTest.aFreeMindMapOpensInTheDesignerByDefault` |
| `RegistrationTest.openWithListsTheDesignerAndTheTextEditor` | Open With lists both the designer and the text editor. | `RegistrationTest.openWithListsTheDesignerAndTheTextEditor` |
| `RegistrationTest.anObjectiveCppFileIsNotClaimed` | An Objective-C++ file with the same extension is not claimed. | `RegistrationTest.anObjectiveCppFileIsNotClaimed` |
| `RegistrationTest.theTextEditorCanBeMadeTheDefault` | The text editor can be made the default for maps. | `RegistrationTest.theTextEditorCanBeMadeTheDefault` |
| `RenameTest.renameByF2` | F2 renames the selected node. | `RenameTest.renameByF2` |
| `RenameTest.renameByDoubleClick` | Double-click renames a node. | `RenameTest.renameByDoubleClick` |
| `RenameTest.unchangedTextIsNoEdit` | Confirming unchanged text makes no edit. | `RenameTest.unchangedTextIsNoEdit` |
| `RenameTest.renameNeedsExactlyOneNode` | Rename needs exactly one selected node. | `RenameTest.renameNeedsExactlyOneNode` |
| `RenameTest.aRichNodeWarnsAndCancelChangesNothing` | Renaming a rich node warns and cancelling changes nothing. | `RenameTest.aRichNodeWarnsAndCancelChangesNothing` |
| `RenameTest.aRichNodeConfirmedBecomesPlainText` | A rich node renamed after confirming becomes plain text. | `RenameTest.aRichNodeConfirmedBecomesPlainText` |
| `RenameTest.aPlainNodeIsNotWarned` | Renaming a plain node shows no warning. | `RenameTest.aPlainNodeIsNotWarned` |
| `RoundTripTest.savingWithoutEditsIsByteIdentical` | Saving without edits writes a byte-identical file. | `RoundTripTest.savingWithoutEditsIsByteIdentical` |
| `RoundTripTest.closingWithoutSavingLeavesTheFileAsItWas` | Closing without saving leaves the file as it was. | `RoundTripTest.closingWithoutSavingLeavesTheFileAsItWas` |
| `SaveLifecycleTest.saveWritesTheEditAndClearsDirty` | Save writes the edit and clears the dirty state. | `SaveLifecycleTest.saveWritesTheEditAndClearsDirty` |
| `SaveLifecycleTest.revertRestoresTheSavedFile` | Revert restores the saved file. | `SaveLifecycleTest.revertRestoresTheSavedFile` |
| `SaveLifecycleTest.closingADirtyEditorAsksToSave` | Closing a dirty editor asks to save. | `SaveLifecycleTest.closingADirtyEditorAsksToSave` |
| `SaveLifecycleTest.saveAsWritesANewFileAndSwitchesToIt` | Save As writes a new file and switches the editor to it. | `SaveLifecycleTest.saveAsWritesANewFileAndSwitchesToIt` |
| `TextVisualSyncTest.aTextEditShowsOnTheVisualPage` | A text edit shows on the visual page. | `TextVisualSyncTest.aTextEditShowsOnTheVisualPage` |
| `TextVisualSyncTest.nodesAddedAndRemovedInTheTextAreDrawnAndErased` | Nodes added or removed in the text are drawn or erased. | `TextVisualSyncTest.nodesAddedAndRemovedInTheTextAreDrawnAndErased` |
| `TextVisualSyncTest.switchingPagesKeepsTheVisualSelection` | Switching pages keeps the visual selection. | `TextVisualSyncTest.switchingPagesKeepsTheVisualSelection` |
| `TextVisualSyncTest.aTextEditKeepsTheSelectionByKey` | A text edit keeps the selection by node key. | `TextVisualSyncTest.aTextEditKeepsTheSelectionByKey` |
| `TwoEditorsTest.anEditInOneEditorShowsInTheOtherAndTheyShareOneUndoHistory` | An edit in one editor shows in the other and both share one undo history. | `TwoEditorsTest.anEditInOneEditorShowsInTheOtherAndTheyShareOneUndoHistory` |
| `UndoRedoTest.eachActionUndoesToTheOriginalBytesAndRedoes` | Each action undoes to the original bytes and redoes. | `UndoRedoTest.eachActionUndoesToTheOriginalBytesAndRedoes` |
| `UndoRedoTest.undoGoesBackInReverseOrderAndRedoReapplies` | Undo goes back in reverse order and redo reapplies. | `UndoRedoTest.undoGoesBackInReverseOrderAndRedoReapplies` |
| `ViewerInteractionTest.zoomInAndOutThroughThePlatformCommands` | The view zooms in and out through the platform commands. | `ViewerInteractionTest.zoomInAndOutThroughThePlatformCommands` |
| `ViewerInteractionTest.panByScrolling` | The view pans by scrolling. | `ViewerInteractionTest.panByScrolling` |
| `ViewerInteractionTest.singleAndMultipleSelection` | Clicks make single and multiple selections. | `ViewerInteractionTest.singleAndMultipleSelection` |
| `ViewerInteractionTest.marqueeSelectsTheNodesInside` | A marquee selects the nodes inside it. | `ViewerInteractionTest.marqueeSelectsTheNodesInside` |
| `ViewerInteractionTest.arrowKeysMoveTheSelectionBetweenNodes` | Arrow keys move the selection between nodes. | `ViewerInteractionTest.arrowKeysMoveTheSelectionBetweenNodes` |
| `ViewerInteractionTest.theSelectionIsPublishedAsNodeKeys` | The selection is published as node keys. | `ViewerInteractionTest.theSelectionIsPublishedAsNodeKeys` |
