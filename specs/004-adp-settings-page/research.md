# Research: ADP Settings Page

Phase 0 decisions for [plan.md](plan.md). Each entry records what was chosen, why, and what was rejected. The code this builds on is spec 003's diagram designer framework as it stands on `features/003-diagram-designer-framework-impl` (PR #4); implementation of this feature starts from `develop` once that pull request is merged.

## R1. Where the page lives and how it is registered

**Decision**: One `applicationConfigurable` with `parentId="tools"`, `id="etalii.adp.settings"` and `displayName="ADP"`, implemented in Java as a `SearchableConfigurable` that also implements `Configurable.Composite` for the designer subpages (R6). The form is plain Swing built with the platform's `FormBuilder`, `JBTable`, `JBCheckBox`, `ComboBox` and `ActionLink`.

The page is made of sections. `AdpConfigurable` owns the dialog contract (`isModified`, `apply`, `reset`, publishing the topic, the child pages) and delegates to every `SettingsSection` registered on a new `etalii.adp.settingsSection` extension point: the designer list, the canvas options and the designer pages each register theirs from their own descriptor fragment. This is the seam spec 003 uses for canvas features (`DiagramFeature`), and it keeps each story's files its own.

**Rationale**: `parentId="tools"` is the platform's own place for tool settings (FR-001, spec assumption). A configurable gets Apply, OK, Cancel, Reset and the modified marker from the Settings dialog for free, through `isModified`, `apply` and `reset` (FR-002). The code base is Java only; Kotlin's UI DSL would add Kotlin sources to `core` for one form.

**Alternatives considered**: a top-level node beside "Tools" (the platform reserves top level for its own groups and large products); Kotlin UI DSL `BoundConfigurable` (a second language for one page); a tool window with settings (not where users look, and no Apply/Cancel, against principle I).

## R2. Storage, export, import and Settings Sync

**Decision**: An application service `AdpSettings implements PersistentStateComponent<AdpSettings.State>`, annotated `@State(name = "AdpSettings", storages = @Storage("adp.xml"), category = SettingsCategory.PLUGINS)`. The state is a plain bean of strings, booleans and string maps. Every setting is user-level (FR-005).

**Rationale**: A `PersistentStateComponent` with the default roaming type is what File > Manage IDE Settings > Export and Import, and Settings Sync, already carry (FR-003, SC-005). No project-level state is left after the 2026-09-26 clarification, so no project service is needed.

**Alternatives considered**: `PropertiesComponent` keys (not exported as a unit and not typed); a project service (no project-level setting remains).

## R3. Reading stored settings that are damaged or newer (FR-018)

**Decision**: The state stores each value as it was written. `AdpSettings` validates each field when it is read into the typed `CanvasOptions` and designer settings: an unknown enum constant, an out-of-range zoom or an unparsable value falls back to that field's default alone. Fields it does not know are kept in the state untouched, so a downgrade followed by an upgrade loses nothing. When at least one field fell back, one notification from the `ADP` notification group says which settings were reset, once per IDE session.

**Rationale**: The platform's serializer drops a whole component only when the XML itself is broken; field-level fallback has to be ADP's. Keeping unknown fields honours "keeps what it can".

**Alternatives considered**: a version number with migrations (no second version exists to migrate from, principle V); failing silently (the spec asks for one notice).

## R4. Settings search (FR-001, SC-001)

**Decision**: Register a `SearchableOptionContributor` (`com.intellij.search.optionContributor`) that adds, for the ADP page's id, the word "ADP", every installed designer's name, and every option's label, and for each designer subpage its own option labels. Leave `buildSearchableOptions = false` in the build.

**Rationale**: The build index is disabled in this repository because building it starts a full IDE. The designer names are only known at runtime anyway, so a runtime contributor is needed regardless, and it covers the option labels at no extra cost.

**Alternatives considered**: turning `buildSearchableOptions` on (slow builds, and it still cannot index designers contributed by other plug-ins).

## R5. Which designers are installed, and their status (FR-007, FR-008)

**Decision**: A small registry `AdpDesigners` lists every `AdpEditorProvider` among the platform's `fileEditorProvider` extensions. Each provider answers `designerInfo()`: its name (`editorName()`), file types (`extensions()`), version (the `version` of the plug-in descriptor that registered the extension, via `PluginManager.getPluginByClass`), its origin (R9) and its problems. The provider's own id, `getEditorTypeId()`, is the key everything else stores against.

`DiagramEditorProvider`'s builder constructor today lets a `DefinitionException` escape, so the platform fails to create the extension and the designer vanishes without a trace. It changes to catch the exception, keep its `problems()`, and refuse every file (`accepts` returns false). A designer that failed that way is then listed as "Not loaded" with each problem (acceptance scenario 1.3, SC-004).

**Rationale**: The platform's extension list is already the single source of which editors exist (principle I). Catching the exception keeps FR-002 of spec 003 (every problem reported) and adds a place to read the report.

**Alternatives considered**: a separate ADP extension point for designers (a second registration beside `fileEditorProvider` that can drift from it); reading problems from the IDE log (not something a user can be pointed at).

## R6. Designer-specific settings (FR-014, FR-015)

**Decision**: A designer declares its own settings as data: `AdpEditorProvider.settings()` returns a list of `DesignerSetting` records (key, label, kind: yes/no, number with range, or choice list, default). A designer with a non-empty list gets a child page under ADP, built by the framework (`DesignerSettingsConfigurable`); an empty list gets no page. Values are stored in `AdpSettings` under the designer's id, so they survive the designer being off or uninstalled and apply again when it returns.

**Rationale**: Storage in ADP's own state is what makes FR-015 hold for an uninstalled designer. Declaring settings as data matches spec 003's "a designer is declared, not coded" and a future DEDL definition can declare the same list.

**Alternatives considered**: each designer contributing its own `Configurable` (its storage would leave with the designer, breaking FR-015, and every designer would rebuild the same form).

## R7. Turning a designer off (FR-009, FR-010)

**Decision**: `AdpEditorProvider.accepts(file)` returns false when `AdpSettings.isOff(getEditorTypeId())`. `acceptedByAny` goes through `accepts`, so it follows. Nothing closes or rebuilds an open editor: acceptance is only consulted when a file is opened.

**Rationale**: When no provider accepts a file, the platform opens it with the editor that applies without ADP, which is exactly FR-009. The file type (icon, name) is registered separately and stays; that is the platform's file type, not the designer.

**Alternatives considered**: unregistering the extension at runtime (dynamic plug-in machinery for a boolean); closing open designers on Apply (against FR-010).

## R8. Canvas options and how they reach open designers (FR-006, FR-011, FR-012)

**Decision**: `AdpSettings` publishes `AdpSettingsListener.TOPIC` on the application message bus after Apply. `DiagramDesigner` subscribes for its lifetime and repaints; it never touches its document.

- **Show grid**: a new `GridLayer` in `core/…/diagram/view/` paints dots at the grid spacing when showing is on.
- **Snap to grid**: every snap already goes through `MoveTool.snap(designer, value)`, which `ResizeTool` and `ToolboxDropTarget` call too. Its body changes to snap only when the effective option is on; no caller changes.
- **Opening zoom**: applies when a file opens with no remembered `DesignerState`; a remembered zoom still wins, because the user chose it for that file.
- **Fixed by the definition**: `ViewOptions` keeps `grid` as the spacing in unscaled pixels (default 10) and gains `fixed`: a map from `CanvasOption` to the value the designer insists on. The effective value is the fixed one when present, else the user's. The FreeMind designer, whose positions come from its layout, fixes grid and snap off. The draw.io designer already declares `grid(10)` and keeps it as its spacing. The page lists, under each option, the designers that do not follow it (acceptance scenario 3.2).

**Rationale**: One topic and one effective-value function keep every tool reading the same answer. Keeping `grid` as spacing and moving on/off into settings removes today's overloading of `grid = 0` as "no snap".

**Alternatives considered**: reopening editors on Apply (against FR-006); each tool subscribing on its own (four subscriptions answering one question).

## R9. Origin and bundled DEDL definitions (FR-007, FR-016)

**Decision**: `DesignerOrigin` is a sealed interface with three records: `Module(pluginId)` for a designer built into this plug-in, `OtherPlugin(pluginId, pluginName)` for one another plug-in registers, and `BundledDefinition(name, dedlVersion, sourceRevision)` for a designer interpreted from a DEDL definition shipped inside the plug-in. `AdpEditorProvider.origin()` defaults to `Module` or `OtherPlugin` from the registering plug-in's id. The DEDL interpreter that a later specification introduces overrides it with `BundledDefinition`, reading the three values from the bundled definition and the revision recorded when it was copied. In this feature only the sample designer (test code) reports a `BundledDefinition`, so the page's rendering of it is tested before a real one exists.

**Rationale**: The 2026-09-26 clarification settles that definitions are copied from etalii.adp and bundled. The page only has to show the result, and the record is three strings.

**Alternatives considered**: waiting with User Story 5 until DEDL interpretation lands (possible, and the tasks keep it in its own last story phase so it can be dropped without touching the rest).

## R10. Animations (dropped 2026-09-27)

**Decision**: no "Play animations" option and no reduced-motion check. Peter dropped them from the spec on 2026-09-27 (FR-013 withdrawn), because no designer animates yet, so the option and the operating system query behind it would have had no caller (principle V). They come back, with the reduced-motion check, when a designer first animates (for example DEDL's `flow` style).

**Alternatives considered**: keeping the option with a per-platform reduced-motion query and no caller (rejected as unused code).

## R11. Conflicting designers (FR-017)

**Decision**: Two designers conflict when their file types overlap. The page shows the conflict on both rows. The designer used is the first `AdpEditorProvider` in the platform's extension order that accepts the file; the other is still offered in the editor's tab switcher. How to change it: turn the one you do not want off on this page.

**Rationale**: The platform already decides between editor providers by extension order. Turning one off is the one choice ADP owns (R7).

**Alternatives considered**: a priority list on the page (a second ordering beside the platform's, principle V).

## R12. Testing approach

**Decision**: Headless platform tests (`BasePlatformTestCase`) for the service, the configurable's `isModified`/`apply`/`reset`, the registry, gating, search contributions and live application to an open designer through `DiagramDriver`. A state round trip test serializes `AdpSettings.State` with the platform's `XmlSerializer` and loads it back, including damaged and unknown fields (SC-005, FR-018). One Starter + Driver integration scenario opens the real Settings dialog, searches "ADP" and a designer name, and measures that the dialog opens within the same time budget with and without the page (SC-001, SC-006). A deliberately broken sample designer (test-only) proves SC-004.

**Rationale**: Constitution IV: registration and platform behaviour in a headless IDE, and the few things only a real dialog shows in the integration suite.

**Alternatives considered**: UI screenshot tests (brittle across themes and platform releases).
