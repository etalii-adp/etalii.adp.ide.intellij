# The FBL test baseline and its counterparts

One row for each of the 86 tests of `EtAlii.Adp.Specification.Fbl.Tests` in `etalii.adp.ide.standalone` at commit `25fc7b4a`. A baseline test is named `Folder/File.Method`, where the file is `Folder/File.Tests.cs`. Its counterpart is a test class under `fbl/src/test/java/etalii/adp/fbl/` and a method of it, with standalone's method name in lower camel case, the same inputs and the same expected results. A theory there is a `@ParameterizedTest` here with the same rows.

`BaselineCoverageTest` reads this table: it has 86 rows, every counterpart exists, and only the rows spec 010 allows are not applicable.

| Baseline test | Counterpart | Reason |
| --- | --- | --- |
| `Bytes/BodyText.AColumnCountsCodePointsNotBytesOrUtf16Units` | `BodyTextTest.aColumnCountsCodePointsNotBytesOrUtf16Units` | |
| `Bytes/BodyText.TheByteOrderMarkBelongsToNoColumn` | `BodyTextTest.theByteOrderMarkBelongsToNoColumn` | |
| `Bytes/BodyText.CrlfLfAndALoneCrEachEndALine` | `BodyTextTest.crlfLfAndALoneCrEachEndALine` | |
| `Bytes/BodyText.ABodyOfOnlyALoneCrIsOneEmptyLineEndedByCr` | `BodyTextTest.aBodyOfOnlyALoneCrIsOneEmptyLineEndedByCr` | |
| `Bytes/BodyText.CrlfWinsATieAndALoneCrCountsAsNeither` | `BodyTextTest.crlfWinsATieAndALoneCrCountsAsNeither` | |
| `Bytes/BodyText.AnInvalidUtf8SequenceIsFoundWithItsOffset` | `BodyTextTest.anInvalidUtf8SequenceIsFoundWithItsOffset` | |
| `Conformance/ConformanceFixtures.TheFixturesAreFound` | `ConformanceFixturesTest.theFixturesAreFound` | |
| `Conformance/ConformanceFixtures.TheFixturePasses` | `ConformanceFixturesTest.theFixturePasses` | |
| `Conformance/ConformanceFixtures.EveryByteOfTheInputBelongsToTheReading` | `ConformanceFixturesTest.everyByteOfTheInputBelongsToTheReading` | |
| `Expressions/Expressions.AConstructOutsideTheSubsetIsRejectedByName` | `ExpressionsTest.aConstructOutsideTheSubsetIsRejectedByName` | |
| `Expressions/Expressions.AnExpressionInTheSubsetIsAccepted` | `ExpressionsTest.anExpressionInTheSubsetIsAccepted` | |
| `Expressions/Expressions.DigitsAndWordCharactersAreAsciiOnly` | `ExpressionsTest.digitsAndWordCharactersAreAsciiOnly` | |
| `Expressions/Expressions.ACelExpressionEvaluatesOnAnEntry` | `ExpressionsTest.aCelExpressionEvaluatesOnAnEntry` | |
| `Expressions/Expressions.AnExpressionOutsideTheSubsetFailsToCompile` | `ExpressionsTest.anExpressionOutsideTheSubsetFailsToCompile` | |
| `History/History.ASnapshotUndoEqualsAnInverseSpliceUndo` | `HistoryTest.aSnapshotUndoEqualsAnInverseSpliceUndo` | |
| `History/History.RedoRepeatsTheEditAndIsRefusedOnDrift` | `HistoryTest.redoRepeatsTheEditAndIsRefusedOnDrift` | |
| `History/History.AReloadClearsTheHistory` | `HistoryTest.aReloadClearsTheHistory` | |
| `History/History.ASaveHandsTheHostsWriterTheEditedBytes` | `HistoryTest.aSaveHandsTheHostsWriterTheEditedBytes` | |
| `History/History.AnUnreadableBodyIsNeverSaved` | `HistoryTest.anUnreadableBodyIsNeverSaved` | |
| `Loading/Loading.AValidDocumentLoadsWithoutAProblem` | `LoadingTest.aValidDocumentLoadsWithoutAProblem` | |
| `Loading/Loading.ADuplicateKeyAnywhereIsRejectedAtItsPointer` | `LoadingTest.aDuplicateKeyAnywhereIsRejectedAtItsPointer` | |
| `Loading/Loading.AnotherMajorVersionIsRefused` | `LoadingTest.anotherMajorVersionIsRefused` | |
| `Loading/Loading.ANewerMinorVersionLoadsWithAWarning` | `LoadingTest.aNewerMinorVersionLoadsWithAWarning` | |
| `Loading/Loading.ANameThatDoesNotResolveIsRejectedAtItsPointer` | `LoadingTest.aNameThatDoesNotResolveIsRejectedAtItsPointer` | |
| `Loading/Loading.AStepSixCheckIsApplied` | `LoadingTest.aStepSixCheckIsApplied` | |
| `Loading/Loading.ASharedClaimWithAMarkerIsAccepted` | `LoadingTest.aSharedClaimWithAMarkerIsAccepted` | |
| `Loading/Loading.EveryProblemIsReportedRatherThanTheFirst` | `LoadingTest.everyProblemIsReportedRatherThanTheFirst` | |
| `Loading/Loading.AReferenceResolvesAgainstTheReferringDocument` | `LoadingTest.aReferenceResolvesAgainstTheReferringDocument` | |
| `Plugins/PluginBody.AMissingPluginOpensTheBodyReadOnlyWithAFinding` | `PluginBodyTest.aMissingPluginOpensTheBodyReadOnlyWithAFinding` | |
| `Plugins/PluginBody.APluginWithAnotherIdCountsAsMissing` | `PluginBodyTest.aPluginWithAnotherIdCountsAsMissing` | |
| `Plugins/PluginBody.ThePluginsSplicesAreAppliedRecordedAndUndone` | `PluginBodyTest.thePluginsSplicesAreAppliedRecordedAndUndone` | |
| `Plugins/PluginBody.APluginsRefusalWritesNothing` | `PluginBodyTest.aPluginsRefusalWritesNothing` | |
| `Plugins/PluginBody.AReadOnlyBindingsPluginIsNeverAskedToPlan` | `PluginBodyTest.aReadOnlyBindingsPluginIsNeverAskedToPlan` | |
| `Reading/Reading.TheFirstRuleInBindingOrderTakesAnEntryAndAnEntryBecomesOneElement` | `ReadingTest.theFirstRuleInBindingOrderTakesAnEntryAndAnEntryBecomesOneElement` | |
| `Reading/Reading.AnEntryWithoutAnIdIsAddressedByItsPlaceAndMarkedNotStored` | `ReadingTest.anEntryWithoutAnIdIsAddressedByItsPlaceAndMarkedNotStored` | |
| `Reading/Reading.ASidecarIdIsTakenFromTheRegistrationsIdentities` | `ReadingTest.aSidecarIdIsTakenFromTheRegistrationsIdentities` | |
| `Reading/Reading.TheSecondOfTwoEqualIdsIsReportedAndNotStored` | `ReadingTest.theSecondOfTwoEqualIdsIsReportedAndNotStored` | |
| `Reading/Reading.AMissingHeaderMarkIsReportedAndTheBodyIsStillRead` | `ReadingTest.aMissingHeaderMarkIsReportedAndTheBodyIsStillRead` | |
| `Reading/Reading.ARequiredHeaderThatIsMissingMakesTheBodyUnreadableWithOneFinding` | `ReadingTest.aRequiredHeaderThatIsMissingMakesTheBodyUnreadableWithOneFinding` | |
| `Reading/Reading.AnEntryARuleMatchesButCannotReadIsReportedAndKept` | `ReadingTest.anEntryARuleMatchesButCannotReadIsReportedAndKept` | |
| `Reading/Reading.AStatementNoRuleReadsIsReportedWhenTheBindingAsksForIt` | `ReadingTest.aStatementNoRuleReadsIsReportedWhenTheBindingAsksForIt` | |
| `Reading/Reading.PlanningIsDeterministic` | `ReadingTest.planningIsDeterministic` | |
| `Reading/Reading.AnUnknownRegistrationHeaderIsKeptAndReported` | `ReadingTest.anUnknownRegistrationHeaderIsKeptAndReported` | |
| `Reading/Reading.AStaleLayoutEntryIsReportedAndRemovedAtTheNextWrite` | `ReadingTest.aStaleLayoutEntryIsReportedAndRemovedAtTheNextWrite` | |
| `RealFiles/DeclaredBodies.TheEnumerationFindsTheFiles` | `DeclaredBodiesTest.theEnumerationFindsTheFiles` | |
| `RealFiles/DeclaredBodies.TheFileReads` | `DeclaredBodiesTest.theFileReads` | |
| `RealFiles/DeclaredBodies.ASaveWithoutAnEditWritesTheBytesThatWereRead` | `DeclaredBodiesTest.aSaveWithoutAnEditWritesTheBytesThatWereRead` | |
| `RealFiles/DeclaredBodies.AnEditChangesOnlyItsSplicesAndItsUndoRestoresTheFile` | `DeclaredBodiesTest.anEditChangesOnlyItsSplicesAndItsUndoRestoresTheFile` | |
| `RealFiles/DeclaredBodies.ARemovalChangesOnlyItsSplicesAndItsUndoRestoresTheFile` | `DeclaredBodiesTest.aRemovalChangesOnlyItsSplicesAndItsUndoRestoresTheFile` | |
| `RealFiles/DeclaredBodies.AnUndoAfterTheFileChangedIsRefused` | `DeclaredBodiesTest.anUndoAfterTheFileChangedIsRefused` | |
| `RealFiles/DeclaredBodies.EveryListedDivergenceNamesAFileOfTheSuite` | `DeclaredBodiesTest.everyListedDivergenceNamesAFileOfTheSuite` | |
| `RealFiles/ModuleCrossCheck.TheBindingReadsTheIdsTheModuleReads` | `FreeMindCrossCheckTest.theBindingReadsTheIdsTheModuleReads` | Compared with the FreeMind module's parser only: this host has no timeline, causal loop or C4 module to compare with. |
| `RealFiles/Registrations.TheEnumerationFindsTheRegistrations` | `RegistrationsTest.theEnumerationFindsTheRegistrations` | |
| `RealFiles/Registrations.TheRegistrationParsesAndSavesUnchanged` | `RegistrationsTest.theRegistrationParsesAndSavesUnchanged` | |
| `RealFiles/Registrations.ADeclaredBindingsRegistrationOpensItsBody` | `RegistrationsTest.aDeclaredBindingsRegistrationOpensItsBody` | |
| `RealFiles/Registrations.AW3CReadingsSuggestMatchesItsBody` | not applicable | It runs over standalone's W3C registrations and their reading suggestions; this corpus copies no file of a plugin-read binding. |
| `RealFiles/Registrations.AC4RegistrationReadsItsLegacyLayout` | not applicable | It runs over standalone's C4 registrations and their legacy sidecars, which this corpus does not copy. The legacy sidecar itself is covered by `RegistrationTest`. |
| `RealFiles/Registrations.TheC4LegacyLayoutsArePositionedThroughTheirRegistrations` | not applicable | It needs standalone's C4 legacy layout files, which this corpus does not copy. |
| `RealFiles/Registrations.EveryChartFolderIsRecognisedAndEveryTurtleFileRoutesToTheTurtleBinding` | not applicable | It runs over standalone's chart folders and Turtle files, which this corpus does not copy. Folder recognition and routing are covered by `RoutingTest`. |
| `Registration/Registration.TheBodyIsTheSiblingWithTheRegistrationsBaseName` | `RegistrationTest.theBodyIsTheSiblingWithTheRegistrationsBaseName` | |
| `Registration/Registration.AMissingBodyOpensAsMissing` | `RegistrationTest.aMissingBodyOpensAsMissing` | |
| `Registration/Registration.ABodyOutsideTheWorkspaceIsRefused` | `RegistrationTest.aBodyOutsideTheWorkspaceIsRefused` | |
| `Registration/Registration.ABodyReachedThroughALinkIsRefused` | `RegistrationTest.aBodyReachedThroughALinkIsRefused` | |
| `Registration/Registration.AnIdentityIsStoredInOrderAfterTheLayout` | `RegistrationTest.anIdentityIsStoredInOrderAfterTheLayout` | |
| `Registration/Registration.ALegacyLayoutIsReadForItsViewIgnoringCase` | `RegistrationTest.aLegacyLayoutIsReadForItsViewIgnoringCase` | |
| `Registration/Registration.ALegacyLayoutIsWrittenBySplicesAndUndone` | `RegistrationTest.aLegacyLayoutIsWrittenBySplicesAndUndone` | |
| `Registration/Registration.LegacyIdentitiesAreReadAndWritten` | `RegistrationTest.legacyIdentitiesAreReadAndWritten` | |
| `Registration/Registration.TheSidecarPathIsBesideTheBodyWithItsBaseName` | `RegistrationTest.theSidecarPathIsBesideTheBodyWithItsBaseName` | |
| `Routing/Routing.APatternMarkerLooksAtItsFirstLines` | `RoutingTest.aPatternMarkerLooksAtItsFirstLines` | |
| `Routing/Routing.ARootKeyMarkerReadsYamlAndJsonWithoutABinding` | `RoutingTest.aRootKeyMarkerReadsYamlAndJsonWithoutABinding` | |
| `Routing/Routing.AFirstLineMarkerIsMatchedAfterAByteOrderMark` | `RoutingTest.aFirstLineMarkerIsMatchedAfterAByteOrderMark` | |
| `Routing/Routing.ARegistrationOnlyBindingIsNeverACandidate` | `RoutingTest.aRegistrationOnlyBindingIsNeverACandidate` | |
| `Routing/Routing.AnExtensionIsMatchedIgnoringCase` | `RoutingTest.anExtensionIsMatchedIgnoringCase` | |
| `Routing/Routing.SeveralCandidatesAreAllReturned` | `RoutingTest.severalCandidatesAreAllReturned` | |
| `Routing/Routing.AReadingWhoseSuggestMatchesIsOfferedFirst` | `RoutingTest.aReadingWhoseSuggestMatchesIsOfferedFirst` | |
| `Routing/Routing.AGlobMatchesAsFblDefinesIt` | `RoutingTest.aGlobMatchesAsFblDefinesIt` | |
| `Routing/Routing.AFolderIsRecognisedAndItsFilesSelectedWithoutFollowingLinks` | `RoutingTest.aFolderIsRecognisedAndItsFilesSelectedWithoutFollowingLinks` | |
| `Routing/Templates.EveryDeclaredTemplateReadsBackWithoutAWarning` | `TemplatesTest.everyDeclaredTemplateReadsBackWithoutAWarning` | |
| `Routing/Templates.ATemplateByOriginWinsOverTheBindingsText` | `TemplatesTest.aTemplateByOriginWinsOverTheBindingsText` | |
| `Routing/Templates.TheKeyPlaceholderIsSanitisedExactly` | `TemplatesTest.theKeyPlaceholderIsSanitisedExactly` | |
| `Routing/Templates.OnlyTheFourPlaceholdersAreReplaced` | `TemplatesTest.onlyTheFourPlaceholdersAreReplaced` | |
| `Routing/Templates.APluginWithoutTemplateTextIsAskedForOne` | `TemplatesTest.aPluginWithoutTemplateTextIsAskedForOne` | |
| `Yaml/YamlScalars.APlainSafeStringIsWrittenPlain` | `YamlScalarsTest.aPlainSafeStringIsWrittenPlain` | |
| `Yaml/YamlScalars.ADateIsPlainSafeWhenTheAttributeIsADate` | `YamlScalarsTest.aDateIsPlainSafeWhenTheAttributeIsADate` | |
| `Yaml/YamlScalars.ADoubleQuotedStringEscapesBackslashQuoteAndControlCharacters` | `YamlScalarsTest.aDoubleQuotedStringEscapesBackslashQuoteAndControlCharacters` | |
| `Yaml/YamlScalars.ASingleQuotedStringDoublesItsQuotes` | `YamlScalarsTest.aSingleQuotedStringDoublesItsQuotes` | |
