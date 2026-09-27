# Tasks: ADP Settings Page

**Input**: [plan.md](plan.md), [adp-settings-page.spec.md](adp-settings-page.spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Before T001**: spec 003 (PR #4) is merged into `develop`, and this feature's branch starts from that `develop`. If PR #4 changed `ViewOptions`, `DiagramEditorProvider` or `MoveTool.snap` before merging, re-check research R5 and R8 first.

**Format**: `- [ ] **T###** [P?] [US#] Description · exact/file/path`. `[P]` means independent within its wave. `core/…/settings/` is short for `core/src/main/java/etalii/adp/core/settings/`, `core/test/…/settings/` for `core/src/test/java/etalii/adp/core/settings/`, and `core/test/…/sample/` for `core/src/test/java/etalii/adp/core/diagram/sample/`. Tests come first in every phase (constitution IV): write them, see them fail for the right reason, then implement.

**Seams that keep phases file-disjoint**:
- Each part of the page is a `SettingsSection` registered from its own descriptor fragment (research R1). `plugin.xml` and the core test descriptor include every fragment with an empty fallback from Phase 1, so no story edits another's registration.
- `AdpConfigurable`, `AdpSearchableOptions`, `AdpSettings`, `AdpEditorProvider` and `ViewOptions` are foundational and complete before any story starts. Stories only read them.
- Test designers are registered by each test on `FileEditorProvider.EP_FILE_EDITOR_PROVIDER` with the test's disposable, as spec 003's `SampleProvider` does, so no story edits a shared test descriptor.
- User Stories 1 and 2 share the designer list, so they are one MVP phase.

---

## Phase 1: Setup

**Wave 1 — independent (different files):**

- [x] **T001** [P] Include `adp-settings.xml`, `adp-settings-designers.xml`, `adp-settings-canvas.xml` and `adp-settings-designer-pages.xml`, each with an empty `xi:fallback` · `src/main/resources/META-INF/plugin.xml`
- [x] **T002** [P] Include the same four fragments in the core test descriptor · `core/src/test/resources/META-INF/plugin.xml`

**⟶ Wait for Wave 1 to finish, then:**

- [x] **T003** Build the fresh worktree and run `./gradlew check`: green before any settings code exists · (no file)

---

## Phase 2: Foundational (blocks every story)

Files: `core/…/settings/{AdpSettings,AdpSettingsListener,CanvasOption,CanvasOptions,DesignerSetting,DesignerInfo,DesignerOrigin,AdpDesigners,SettingsSection,AdpConfigurable,AdpSearchableOptions}.java`; `core/src/main/resources/META-INF/adp-settings.xml`; `core/src/main/java/etalii/adp/core/AdpEditorProvider.java`; `core/src/main/java/etalii/adp/core/diagram/ViewOptions.java`; `core/src/main/java/etalii/adp/core/diagram/view/DiagramEditorProvider.java`; the tests below.

### Tests

**Wave 1 — independent (different files):**

- [x] **T004** [P] `AdpSettings`: defaults as in data-model.md; every setting user-level; typed reads; a damaged field falls back alone while the others keep their values; unknown stored fields are written back unchanged; one `ADP` notification per session when anything fell back; the service loads and answers in a headless application (FR-005, FR-018, FR-019) · `core/test/…/settings/AdpSettingsTest.java`
- [x] **T005** [P] State round trip: serialize `AdpSettings.State` with the platform's `XmlSerializer`, load it into a fresh service, and get every value back, including designer settings of a designer that is not installed (SC-005, FR-003, FR-015) · `core/test/…/settings/AdpSettingsStateRoundTripTest.java`
- [x] **T006** [P] `DesignerSetting`: each factory; key, label, range and choice rules from data-model.md each yield one problem naming the setting · `core/test/…/settings/DesignerSettingTest.java`
- [x] **T007** [P] `AdpDesigners` and `designerInfo()`: lists every registered `AdpEditorProvider` and nothing else; name, sorted file types, version of the registering plug-in, `Module` origin for this plug-in; overlapping file types reported as conflicts on both designers; the fixed canvas options reported per designer (FR-007, FR-017) · `core/test/…/settings/AdpDesignersTest.java`
- [x] **T008** [P] A provider built from an inconsistent definition does not throw: it keeps every `DefinitionException` problem, reports `NOT_LOADED`, and refuses every file (FR-008, research R5). Uses a new test-only broken sample definition · `core/test/…/sample/BrokenSampleProvider.java`, `core/test/…/sample/BrokenSampleProviderTest.java`
- [x] **T009** [P] Gating: a designer that is off, or has problems, accepts no file and `acceptedByAny` agrees; turned on again it accepts as before (FR-009, research R7) · `core/test/…/settings/DesignerGatingTest.java`
- [x] **T010** [P] `AdpConfigurable` with two fake sections: `isModified` is any section's; `apply` applies each and publishes `AdpSettingsListener.TOPIC` once, and not at all when nothing changed; `reset` and Cancel leave `AdpSettings` untouched; children come from the sections in `order()`; id `etalii.adp.settings`, name "ADP" (FR-001, FR-002) · `core/test/…/settings/AdpConfigurableTest.java`
- [x] **T011** [P] `AdpSearchableOptions`: contributes "ADP", every installed designer's name and every section's labels for `etalii.adp.settings`, and each child page's labels for its own id (FR-001, research R4) · `core/test/…/settings/AdpSearchableOptionsTest.java`
- ~~**T012**~~ withdrawn 2026-09-27: no reduced-motion check until a designer animates (FR-013 withdrawn, research R10)
- [x] **T013** [P] `ViewOptions`: grid spacing defaults to 10; `fix` records a value per `CanvasOption`; `AdpSettings.effective` returns the fixed value when present and the user's otherwise (FR-012, research R8) · `core/src/test/java/etalii/adp/core/diagram/ViewOptionsTest.java`

### Implementation

**⟶ Wait for the tests above, then Wave 2 — independent (different files):**

- [x] **T014** [P] `CanvasOption` enum and the `CanvasOptions` record · `core/…/settings/CanvasOption.java`, `core/…/settings/CanvasOptions.java`
- [x] **T015** [P] `DesignerSetting` record, factories and validation · `core/…/settings/DesignerSetting.java`
- [x] **T016** [P] `DesignerOrigin` sealed interface with `Module`, `OtherPlugin`, `BundledDefinition` and `describe()` giving the texts in data-model.md · `core/…/settings/DesignerOrigin.java`
- [x] **T017** [P] `AdpSettingsListener` and its topic · `core/…/settings/AdpSettingsListener.java`
- ~~**T018**~~ withdrawn 2026-09-27: no `ReducedMotion` until a designer animates (FR-013 withdrawn, research R10)
- [x] **T019** [P] `SettingsSection` interface and extension point name · `core/…/settings/SettingsSection.java`

**⟶ Wait for Wave 2, then Wave 3 — independent (different files):**

- [x] **T020** [P] `ViewOptions`: `grid` is spacing with default 10; `fixed` map and `Builder.fix` · `core/src/main/java/etalii/adp/core/diagram/ViewOptions.java`
- [x] **T021** [P] `DesignerInfo` record with status, conflicts and unfollowed options · `core/…/settings/DesignerInfo.java`
- [x] **T022** [P] `AdpSettings`: `@State` service, `State` bean, typed reads with field fallback, unknown fields kept, the once-per-session notice, `effective`, designer setting reads (research R2, R3) · `core/…/settings/AdpSettings.java`

**⟶ Wait for Wave 3, then Wave 4 — independent (different files):**

- [x] **T023** [P] `AdpEditorProvider`: `settings()`, `origin()`, `problems()`, final `designerInfo()`; `accepts` refuses when off or with problems · `core/src/main/java/etalii/adp/core/AdpEditorProvider.java`
- [x] **T024** [P] `AdpConfigurable`: `SearchableConfigurable` and `Configurable.Composite` over the registered sections, publishing the topic once on a changing apply · `core/…/settings/AdpConfigurable.java`

**⟶ Wait for Wave 4, then Wave 5 — independent (different files):**

- [x] **T025** [P] `DiagramEditorProvider`: the builder constructor catches `DefinitionException` and returns its problems from `problems()` · `core/src/main/java/etalii/adp/core/diagram/view/DiagramEditorProvider.java`
- [x] **T026** [P] `AdpDesigners`: registry over the platform's `fileEditorProvider` list, version from the registering plug-in, conflicts by overlapping file types (research R5, R11) · `core/…/settings/AdpDesigners.java`
- [x] **T027** [P] `AdpSearchableOptions` · `core/…/settings/AdpSearchableOptions.java`

**⟶ Wait for Wave 5, then:**

- [x] **T028** Register the service, configurable, option contributor, `ADP` notification group and the `etalii.adp.settingsSection` extension point (contracts/plugin-contributions.md) · `core/src/main/resources/META-INF/adp-settings.xml`
- [x] **T029** Run `./gradlew test`: T004–T013 green (T012 withdrawn), and every spec 001–003 test still green · (no file)

---

## Phase 3: User Stories 1 and 2, see and switch designers (P1, MVP)

**Goal**: the ADP page lists every designer with its status and problems, and turns designers off and on.

**Independent Test**: with the sample and broken sample designers registered, the page lists both with the broken one's problems; turning the sample off makes its files open in the text editor, and on again in the designer.

Files: `core/…/settings/DesignersSection.java`, `core/…/settings/DesignerTableModel.java`, `core/src/main/resources/META-INF/adp-settings-designers.xml`, and the tests below.

### Tests

**Wave 1 — independent (different files):**

- [x] **T030** [P] [US1] Designer list: columns On, Name, File types, Version, Origin, Status; the broken sample shows `Not loaded` and each problem when selected; a conflict is shown on both rows; "File types and default editors…" opens the platform's File Types page and nothing on the page duplicates a default-editor choice; labels as in contracts/plugin-contributions.md (FR-004, FR-007, FR-008, FR-017, SC-004, acceptance 1.2–1.4) · `core/test/…/settings/DesignersSectionTest.java`
- [x] **T031** [P] [US2] Off and on end to end: untick the sample, apply, open a sample file and get the text editor with no sample designer offered; a sample file already open stays open, unmodified, with its designer; tick it, apply, and a newly opened file gets the designer (FR-009, FR-010, acceptance 2.1–2.3, SC-002) · `core/test/…/settings/TurnDesignerOffTest.java`

### Implementation

**⟶ Wait for the tests above, then Wave 2 — independent (different files):**

- [x] **T032** [P] [US1] `DesignerTableModel`: rows from `AdpDesigners`, the On column editable and buffered until apply · `core/…/settings/DesignerTableModel.java`

**⟶ Wait for Wave 2, then:**

- [x] **T033** [US1] `DesignersSection` (order 10): the table, the detail area with problems and conflicts, the File Types link; `apply` writes `offDesigners` · `core/…/settings/DesignersSection.java`
- [x] **T034** [US1] Register `DesignersSection` · `core/src/main/resources/META-INF/adp-settings-designers.xml`

**Checkpoint**: Settings > Tools > ADP lists designers, shows problems and conflicts, and turns designers off and on. This is a shippable MVP.

---

## Phase 4: User Story 3, canvas options (P2)

**Goal**: grid, snap and opening zoom apply to every diagram designer, live.

**Independent Test**: turn the grid on and snapping off, apply, and see both open diagrams show the grid and move without snapping, their files unmodified.

Files: `core/…/settings/CanvasSection.java`, `core/src/main/resources/META-INF/adp-settings-canvas.xml`, `core/src/main/java/etalii/adp/core/diagram/view/GridLayer.java`, `core/src/main/java/etalii/adp/core/diagram/view/DiagramDesigner.java`, `core/src/main/java/etalii/adp/core/diagram/edit/MoveTool.java`, `freemind/src/main/java/etalii/adp/freemind/ui/FreeMindDefinition.java`, and the tests below.

### Tests

**Wave 1 — independent (different files):**

- [x] **T035** [P] [US3] Canvas section: the three options with their defaults; opening zoom choices as in the contract; "Not followed by" lists designers that fix an option; "Reset to defaults" resets the page and nothing is stored until Apply (FR-011, FR-012, acceptance 3.2, 3.3) · `core/test/…/settings/CanvasSectionTest.java`
- [x] **T036** [P] [US3] Live apply with `DiagramDriver` on two open sample files: grid shown after apply without reopening; snapping off moves by unsnapped amounts and on snaps to the spacing; opening zoom applies to a file with no remembered state and a remembered zoom wins; a designer fixing an option ignores the user's; in every case the document text, its modified state and its undo stack are unchanged (FR-006, FR-011, FR-012, acceptance 3.1, 3.2, SC-003) · `core/src/test/java/etalii/adp/core/diagram/view/CanvasOptionsLiveTest.java`
- [x] **T037** [P] [US3] FreeMind fixes Show grid and Snap to grid off, so the page lists it under both (FR-012) · `freemind/src/test/java/etalii/adp/freemind/ui/FreeMindDefinitionTest.java`

### Implementation

**⟶ Wait for the tests above, then Wave 2 — independent (different files):**

- [x] **T038** [P] [US3] `GridLayer`: dots at the grid spacing, scaled with zoom, in a `JBColor` tone readable in light and dark themes · `core/src/main/java/etalii/adp/core/diagram/view/GridLayer.java`
- [x] **T039** [P] [US3] `MoveTool.snap` snaps only when `AdpSettings.effective(SNAP_TO_GRID, view)` is true · `core/src/main/java/etalii/adp/core/diagram/edit/MoveTool.java`
- [x] **T040** [P] [US3] FreeMind definition fixes grid and snap off · `freemind/src/main/java/etalii/adp/freemind/ui/FreeMindDefinition.java`
- [x] **T041** [P] [US3] `CanvasSection` (order 20) · `core/…/settings/CanvasSection.java`

**⟶ Wait for Wave 2, then:**

- [x] **T042** [US3] `DiagramDesigner`: installs `GridLayer` when showing is effectively on, subscribes to `AdpSettingsListener.TOPIC` for its lifetime and repaints, applies the opening zoom when no `DesignerState` was restored · `core/src/main/java/etalii/adp/core/diagram/view/DiagramDesigner.java`
- [x] **T043** [US3] Register `CanvasSection` · `core/src/main/resources/META-INF/adp-settings-canvas.xml`

**Checkpoint**: canvas options apply to every open and new diagram designer, and designers that fix an option are named.

---

## Phase 5: User Story 4, designer-specific settings (P3)

**Goal**: a designer that declares settings gets its own page under ADP.

**Independent Test**: register a sample designer declaring one yes/no setting; its page appears under ADP with that setting, and designers without settings get no page.

Files: `core/…/settings/DesignerPagesSection.java`, `core/…/settings/DesignerSettingsConfigurable.java`, `core/src/main/resources/META-INF/adp-settings-designer-pages.xml`, `core/test/…/sample/SettingSampleProvider.java`, and the test below.

### Tests

- [x] **T044** [US4] Designer pages: a page named after the designer, id `etalii.adp.settings.<designer id>`, with one editor per declared setting of the right kind; no page for a designer without settings; values stored under the designer's id, kept while it is off and after its provider is unregistered, and read back when it returns; a declaration that breaks a rule adds a problem and shows no page (FR-014, FR-015, acceptance 4.1–4.3) · `core/test/…/settings/DesignerSettingsConfigurableTest.java`, `core/test/…/sample/SettingSampleProvider.java`

### Implementation

**⟶ Wait for the test above, then:**

- [x] **T045** [US4] `DesignerSettingsConfigurable`: check box, spinner or combo per setting, buffered until apply · `core/…/settings/DesignerSettingsConfigurable.java`
- [x] **T046** [US4] `DesignerPagesSection` (order 30): no component, one child page per designer with valid settings, labels for search · `core/…/settings/DesignerPagesSection.java`
- [x] **T047** [US4] Register `DesignerPagesSection` · `core/src/main/resources/META-INF/adp-settings-designer-pages.xml`

**Checkpoint**: designer-specific settings have a home, and survive a designer being off or removed.

---

## Phase 6: User Story 5, bundled DEDL definitions (P3)

**Goal**: a designer interpreted from a bundled DEDL definition shows which definition and revision it comes from.

**Independent Test**: a test-only designer reporting a `BundledDefinition` origin shows the definition's name, DEDL version and source revision on the page.

Files: `core/test/…/sample/BundledSampleProvider.java` and the test below. Rendering is `DesignerOrigin.describe()` (T016) in the designer list (T033); this phase proves it for the bundled case.

### Tests

- [x] **T048** [US5] Bundled origin: the row reads "DEDL definition <name> (DEDL <version>), copied from etalii.adp at <revision>"; a bundled designer with problems shows `Not loaded` and each problem; the page offers no way to load a definition from elsewhere (FR-016, acceptance 5.1–5.3) · `core/test/…/settings/BundledOriginTest.java`, `core/test/…/sample/BundledSampleProvider.java`

**Checkpoint**: when the DEDL interpretation specification lands, its provider only has to return `BundledDefinition` from `origin()`.

---

## Phase 7: Polish

**Wave 1 — independent (different files):**

- [x] **T049** [P] Author guide: a "Settings" section on `settings()`, `origin()` and `ViewOptions.fix`, with the FreeMind example · `docs/diagram-designer-guide.md`
- [x] **T050** [P] Page cost: with 20 registered designers (the real ones plus test-only fillers), creating and resetting the page takes under 100 ms (SC-006) · `core/test/…/settings/SettingsPagePerformanceTest.java`
- [x] **T051** [P] Integration: in a real IDE, open Settings, search "ADP" and "FreeMind Mind Map" and land on the ADP page both times (SC-001, FR-001) · `src/integrationTest/java/etalii/adp/it/SettingsPageIntegrationTest.java`

**⟶ Wait for Wave 1 to finish, then:**

- [x] **T052** Trace every FR and SC to the test and file that shows it, against the code rather than this list, and read two traces back to their artefacts · (no file)
- [ ] **T053** Run `./gradlew check` and `./gradlew integrationTest`, each exit code captured, then walk [quickstart.md](quickstart.md) in `./gradlew runIde` · (no file)

---

## Dependencies & Execution Order

- **Setup → Foundational → stories → Polish.** Phase 3 (US1 and US2) is the MVP. Phases 4, 5 and 6 each depend only on Phase 2 and own disjoint files, so they can run in any order or in parallel after it. Polish waits for every story.
- **Phase 1**: Wave 1 (T001, T002) → T003.
- **Phase 2**: tests T004–T013 (without T012) → Wave 2 (T014–T019, without T018) → Wave 3 (T020–T022) → Wave 4 (T023, T024) → Wave 5 (T025–T027) → T028 → T029.
- **Phase 3**: tests T030, T031 → T032 → T033 → T034.
- **Phase 4**: tests T035–T037 → Wave 2 (T038–T041) → T042 → T043.
- **Phase 5**: T044 → T045 → T046 → T047.
- **Phase 6**: T048.
- **Phase 7**: Wave 1 (T049–T051) → T052 → T053.
- **Withdrawn**: T012 and T018, with FR-013, on 2026-09-27. Their numbers are kept so references stay stable; FR-013 has no task on purpose.
