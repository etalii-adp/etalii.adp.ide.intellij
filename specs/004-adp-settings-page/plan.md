# Implementation Plan: ADP Settings Page

**Branch**: `features/004-adp-settings-page` (drafted on `claude/project-thread-5ejgvd`) | **Date**: 2026-09-27 | **Spec**: [adp-settings-page.spec.md](adp-settings-page.spec.md)

**Input**: Feature specification from `specs/004-adp-settings-page/adp-settings-page.spec.md`, with the 2026-09-26 clarification that DEDL definitions are bundled with the plug-in.

> **Depends on spec 003.** The page reads designers through spec 003's `DiagramEditorProvider`, `DefinitionException`, `ViewOptions` and `DiagramDesigner`, which exist only on `features/003-diagram-designer-framework-impl` (PR #4). Implementation branches from `develop` after that pull request is merged. Every path below is as it will be then.

## Summary

ADP gets one page at Settings > Tools > ADP. It lists every installed designer with its file types, version, origin and whether it loaded, shows each definition problem, and lets the user turn a designer off. It also holds four canvas options shared by every diagram designer, and a child page per designer for settings the designer declares. The page is a platform `SearchableConfigurable`, its values live in one application-level `PersistentStateComponent` that export, import and Settings Sync already carry, and Apply reaches open designers through a message-bus topic without touching any file.

No new dependency and no new module: everything is in `core`, in a new `settings` package and small changes to the editor provider and the diagram view.

## Technical Context

**Language/Version**: Java 25, Gradle Kotlin DSL.

**Primary Dependencies**: IntelliJ Platform 2026.2 only: `SearchableConfigurable`, `Configurable.Composite`, `PersistentStateComponent`, `SearchableOptionContributor`, the application message bus, `NotificationGroup`, `FormBuilder`, `JBTable`, `ActionLink`, and JNA as bundled by the platform (R10).

**Storage**: `adp.xml` in the IDE's options folder, through `@State` (R2). Nothing in any project or designer file.

**Testing**: headless `BasePlatformTestCase` tests in `core`, the `DiagramDriver` test kit from spec 003, and one Starter + Driver integration scenario (R12).

**Target Platform**: every IDE on IntelliJ Platform 2026.2, Windows, Linux, macOS.

**Project Type**: IntelliJ Platform plug-in, Gradle modules `core`, `freemind`, `drawio`, `testing`.

**Performance Goals**: SC-006: the Settings dialog opens no slower with the page, measured with 20 designers (19 test-only providers plus the real ones).

**Constraints**: settings never change a file, its undo history or its modified state (FR-006); no network access; no project-level state (FR-005).

**Scale/Scope**: about 12 new production files, 8 changed, 12 test files.

## Constitution Check

*GATE: checked before Phase 0 research and re-checked after Phase 1 design (constitution v2.1.0). Both pass.*

| Principle | Assessment | How the design meets it |
|---|---|---|
| I. Native IntelliJ Platform Citizenship | PASS | The page is an `applicationConfigurable` under Tools with the dialog's own Apply, Cancel, Reset and modified marker (R1). Storage is a `PersistentStateComponent`, so export, import and Settings Sync are the platform's (R2). Search goes through the platform's option index (R4). Turning a designer off uses the platform's own fallback to the next editor (R7). The default editor, file types, keymap and colours are linked, never copied (FR-004). |
| II. Text Is the Source of Truth | PASS | No setting is written to a designer's file. Apply only repaints open designers (R8), tested by asserting the document and its modified state are unchanged (SC-003). |
| III. One Framework, Many Designers | PASS | The page, storage and designer subpages are framework code in `core`. A designer contributes only data: its settings list and, for DEDL, its origin (R6, R9). No format module depends on another. |
| IV. Test-First, Against Real Files | PASS | Tests precede code in every phase. Registration, gating and live application run in a headless IDE. The broken designer that proves SC-004 is a test-only sample, as in spec 003. |
| V. Simplicity | PASS, one concern | No new module, no dependency, no priority list or migration framework (R3, R11). The "Play animations" option has no caller yet (R10): kept because FR-011 asks for it, and raised with Peter as a possible spec change. |
| Platform and technology constraints | PASS | One plug-in, platform module only, headless Gradle build runs every test. The reduced-motion check reads local OS settings and makes no network call. |

## Project Structure

### Documentation (this feature)

```text
specs/004-adp-settings-page/
├── adp-settings-page.spec.md
├── plan.md                      # this file
├── research.md                  # Phase 0 decisions R1–R12
├── data-model.md                # settings state, designer entry, origin, designer settings
├── quickstart.md                # validation guide
├── contracts/
│   ├── designer-settings-api.md # what a designer author and the framework code against
│   └── plugin-contributions.md  # configurable ids, extensions, topic, notification group
├── checklists/requirements.md
└── tasks.md                     # Phase 2 (/speckit-companion-tasks)
```

### Source Code (repository root, after the feature)

```text
src/main/resources/META-INF/plugin.xml     # + xi:include of the four adp-settings*.xml fragments
docs/diagram-designer-guide.md             # + "Settings" section: settings(), origin(), fixed options
core/src/main/resources/META-INF/
├── adp-settings.xml                       # configurable, service, option contributor, notification group, section EP
├── adp-settings-designers.xml             # US1/US2: the designer list section
├── adp-settings-canvas.xml                # US3: the canvas section
└── adp-settings-designer-pages.xml        # US4: the designer pages section
core/src/main/java/etalii/adp/core/
├── AdpEditorProvider.java                 # accepts() honours off; designerInfo(), settings(), origin(), problems()
├── settings/                              # new
│   ├── AdpSettings.java                   # application service + State bean, typed reads, fallback (R2, R3)
│   ├── AdpSettingsListener.java           # message-bus topic (R8)
│   ├── CanvasOption.java, CanvasOptions.java
│   ├── DesignerSetting.java               # declared setting: key, label, kind, default, range/choices (R6)
│   ├── DesignerInfo.java, DesignerOrigin.java
│   ├── AdpDesigners.java                  # registry over fileEditorProvider (R5, R11)
│   ├── ReducedMotion.java                 # OS query (R10)
│   ├── SettingsSection.java               # extension point: one part of the page (R1)
│   ├── AdpConfigurable.java               # the ADP page: delegates to sections (R1)
│   ├── AdpSearchableOptions.java          # SearchableOptionContributor over sections (R4)
│   ├── DesignersSection.java, DesignerTableModel.java      # US1/US2
│   ├── CanvasSection.java                                  # US3
│   └── DesignerPagesSection.java, DesignerSettingsConfigurable.java   # US4 (R6)
└── diagram/
    ├── ViewOptions.java                   # grid is spacing (default 10); + fixed options (R8)
    └── view/
        ├── DiagramEditorProvider.java     # catches DefinitionException, keeps problems (R5)
        ├── DiagramDesigner.java           # subscribes to the topic; opening zoom
        └── GridLayer.java                 # new: paints the grid
core/src/main/java/etalii/adp/core/diagram/edit/MoveTool.java                # snap() honours the effective option
freemind/src/main/java/etalii/adp/freemind/ui/FreeMindDefinition.java        # fixes grid and snap off
core/src/test/java/etalii/adp/core/settings/                                 # new tests
core/src/test/java/etalii/adp/core/diagram/sample/                           # + BrokenSampleProvider, sample setting and origin
src/integrationTest/java/etalii/adp/it/SettingsPageIntegrationTest.java      # search and open-time scenario
```

**Structure Decision**: a `settings` package in `core` beside `diagram`, because the page serves every designer, including FreeMind, which is not only a diagram. The diagram view changes stay in `diagram/`. No format module gains a dependency.

## Phase outline

1. **Foundational**: `AdpSettings` with its state and fallback, the topic, `DesignerSetting`, `DesignerInfo`, `DesignerOrigin`, the provider's new methods, and the registry. Everything later reads these.
2. **US1** designer list and problems, with search; **US2** turning designers off. Together the MVP.
3. **US3** canvas options through to open designers.
4. **US4** designer subpages.
5. **US5** bundled-definition origin on the page.
6. **Polish**: author guide, integration scenario, full check.

## Concerns

- **Animations have no caller** (R10). FR-011 and FR-013 ask for an option nothing uses until a designer animates. Dropping it from the spec until then is simpler; the plan keeps it because the spec asks for it.
- **Spec 003 must merge first.** If PR #4 changes `ViewOptions`, `DiagramEditorProvider` or the snapping tools before merging, R5 and R8 are re-checked against the merged code before tasks start.
