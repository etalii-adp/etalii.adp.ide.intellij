# Feature Specification: ADP Settings Page

**Feature Branch**: `features/004-adp-settings-page` (drafted on `claude/project-thread-5ejgvd`)
**Created**: 2026-09-26
**Status**: Draft
**Input**: Add a Spec Kit specification for an ADP settings page for IntelliJ.

## Context

ADP has no settings today. Every choice a user might want to make about the designers (which designers are active, how the canvas behaves, which view a file opens in) is fixed in code. As more designers arrive through the diagram designer framework (spec 003), and as designers move to DEDL definitions interpreted by a core plug-in in each IDE, users need one place in their IDE to see which designers they have and to adjust how ADP behaves.

Constitution principle I asks that this place be the IDE's own Settings dialog, behaving like every other settings page, and that ADP does not duplicate a choice the platform already offers (such as the default editor for a file type or the keymap).

Two roles appear below. A **user** is the developer who edits files with ADP designers in their IDE. A **designer author** is the developer who builds a designer, by definition or by module.

## Clarifications

### Session 2026-09-26

- Q: Should the page let users load DEDL definitions from user or project folders (FR-016)? → A: No. Definitions written to the DEDL specification are copied out of the etalii.adp repository and included in each plug-in that can interpret and consume them. The page shows where a bundled definition came from; it does not load definitions from elsewhere.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See which designers are installed and whether they work (Priority: P1)

A user opens the IDE's Settings dialog, finds ADP by browsing or by typing "ADP" or a designer's name in the settings search, and sees a list of every installed designer: its name, the file types it handles, its version, where it comes from, and whether it loaded correctly. When a designer failed to load, the user sees why.

**Why this priority**: it is the first thing a user needs when a file does not open in the designer they expected, and it gives every later setting a home.

**Independent Test**: install the plug-in with the FreeMind designer and one deliberately broken sample designer, open Settings, and check that both are listed with their file types and status, and that the broken one shows its problems.

**Acceptance Scenarios**:

1. **Given** the plug-in is installed, **When** the user opens Settings and types "ADP" in the search field, **Then** the ADP page is found and highlighted.
2. **Given** the ADP page is open, **When** the user looks at the designer list, **Then** every installed designer is shown with its name, file types, version, origin and load status.
3. **Given** a designer whose definition has inconsistencies, **When** the user selects it in the list, **Then** each problem is shown with the part of the definition it concerns, as reported by the framework's definition check (spec 003, FR-002).
4. **Given** the ADP page is open, **When** the user follows the link for a designer's file types, **Then** the IDE's own file type and default editor settings open, instead of ADP offering its own copy of that choice.

---

### User Story 2 - Turn a designer off and on (Priority: P1)

A user who prefers the text editor for one file type, or who is troubleshooting a designer, turns that designer off on the ADP page. Files of that type then open as they would without ADP. Turning it back on restores the designer.

**Why this priority**: it is the one choice that only ADP can offer, and the safest way out when a designer misbehaves.

**Independent Test**: turn the FreeMind designer off, apply, open a `.mm` file and see it open in the text editor; turn it on again and see the designer offered and chosen as before.

**Acceptance Scenarios**:

1. **Given** the FreeMind designer is on, **When** the user turns it off and applies, **Then** `.mm` files opened afterwards open in the editor that would apply without ADP, and the designer is no longer offered for them.
2. **Given** a file is open in a designer, **When** the user turns that designer off and applies, **Then** the open editor is not closed or changed without the user's consent and no unsaved work is lost.
3. **Given** a designer is off, **When** the user turns it on and applies, **Then** files of its types are offered in and open with the designer again, following the IDE's default editor choice.

---

### User Story 3 - Adjust how the canvas behaves in every designer (Priority: P2)

A user sets preferences that apply to the canvas of every diagram designer: whether a grid is shown, whether elements snap to it, the zoom level a diagram opens at, and whether animations are played. Each designer honours them unless its definition fixes that behaviour, in which case the page says so.

**Why this priority**: these are the canvas choices users ask for first, and making them once for all designers is what a shared framework promises; but the designers are usable without them.

**Independent Test**: turn the grid on and snapping off, apply, open files in two different designers and check both show the grid and do not snap.

**Acceptance Scenarios**:

1. **Given** the grid is off, **When** the user turns it on and applies, **Then** open and newly opened diagrams show the grid without being reopened.
2. **Given** a designer whose definition fixes snapping, **When** the user views the snapping option, **Then** the page shows that this designer does not follow the option, and why.
3. **Given** animations are turned off on the page or the operating system asks for reduced motion, **When** a designer would animate, **Then** it does not.
4. **Given** the user has changed canvas options, **When** they choose Reset, **Then** every option returns to its default.

---

### User Story 4 - Designer-specific settings (Priority: P3)

A designer that has settings of its own (for example, the FreeMind designer's layout direction) shows them on its own page under the ADP page, so a user finds every ADP setting in one place.

**Why this priority**: no current designer needs its own settings yet; the structure matters so the first one that does fits in.

**Independent Test**: give the sample designer one setting of its own, open Settings, and find it on a page under ADP named after the designer.

**Acceptance Scenarios**:

1. **Given** a designer that declares its own settings, **When** the user opens Settings, **Then** a page named after the designer appears under the ADP page, with those settings.
2. **Given** a designer without settings of its own, **When** the user opens Settings, **Then** no empty page appears for it.
3. **Given** a designer is turned off, **When** the user views its page, **Then** its settings remain visible and editable, and are kept for when it is turned on again.

---

### User Story 5 - See which DEDL definition a designer comes from (Priority: P3)

Designers defined in DEDL are not loaded from the user's disk: their definitions are copied out of the etalii.adp repository and shipped inside the plug-in that can interpret them. A user looking at such a designer on the ADP page sees that it comes from a bundled definition, which definition and DEDL version it is, and which etalii.adp revision it was copied from, so a problem can be reported against the right source.

**Why this priority**: it matters once designers are defined in DEDL and interpreted by the plug-in, which no IntelliJ specification promises yet; until then every designer is built into the plug-in as a module.

**Independent Test**: ship the plug-in with one bundled DEDL definition, open the ADP page, and check that its designer shows the definition's name, DEDL version and source revision.

**Acceptance Scenarios**:

1. **Given** a designer defined by a bundled DEDL definition, **When** the user selects it in the designer list, **Then** its origin reads as a bundled definition, with the definition's name, its DEDL version and the etalii.adp revision it was copied from.
2. **Given** a bundled definition the plug-in cannot interpret, **When** the user selects it, **Then** it is listed as not loaded, with each problem and the part of the definition it concerns.
3. **Given** two designers claim the same file type, **When** the user views the list, **Then** the conflict is shown, with which designer is used and how to change that.

---

### Edge Cases

- A setting is changed while a file is open in a designer: the change applies to that editor without reopening it, and without adding an entry to the file's undo history or marking it modified.
- The user cancels the Settings dialog: nothing changes, including open editors.
- The stored settings are unreadable or come from a newer version of the plug-in: ADP falls back to defaults for the settings it cannot read, keeps what it can, and tells the user once.
- A designer is uninstalled: its stored settings are kept but no longer shown; it is not listed.
- The last designer for a file type is turned off while the file is open with unsaved changes: nothing is lost; the change takes effect the next time the file is opened.
- The IDE runs headless (for tests or inspections): settings are still read and applied, with no dialog.
- A bundled definition needs a DEDL feature the plug-in does not support: it is listed as not loaded, naming the missing feature, and the other designers still load.

## Requirements *(mandatory)*

### Functional Requirements

**Placement and behaviour**

- **FR-001**: ADP MUST offer one settings page in the IDE's own Settings dialog, placed where the IDE places settings for tools, and found by the IDE's settings search for "ADP", for each designer's name and for each option's label.
- **FR-002**: The page MUST behave like the IDE's own settings pages: changes take effect only on Apply or OK, Cancel discards them, the modified state of the page is shown as the IDE shows it, and every option can be reset to its default.
- **FR-003**: Settings MUST be stored, exported, imported and synchronised through the IDE's own settings mechanisms, so they move with the user's other IDE settings.
- **FR-004**: ADP MUST NOT offer its own copy of a choice the IDE already offers (default editor per file type, file type associations, keymap, colour scheme), and SHOULD link to the IDE's own page for it instead.
- **FR-005**: Every setting MUST be stored for the user, across all projects.
- **FR-006**: Applying a setting MUST take effect in open designers without reopening them, and MUST NOT change any file, its undo history or its modified state.

**Designer list**

- **FR-007**: The page MUST list every installed designer with its name, the file types it handles, its version, its origin (a module built into the plug-in, a DEDL definition bundled with the plug-in, or another plug-in) and its load status.
- **FR-008**: For a designer that failed to load or has definition problems, the page MUST show each problem with the part of the definition it concerns.
- **FR-009**: Users MUST be able to turn each designer off and on. A designer that is off MUST NOT be offered for any file, and its files MUST open as they would without ADP.
- **FR-010**: Turning a designer off MUST NOT close, change or discard an editor already open in it.

**Canvas options**

- **FR-011**: The page MUST offer these canvas options, applying to every diagram designer: show grid, snap to grid, the zoom level a diagram opens at, and play animations.
- **FR-012**: A designer whose definition fixes the behaviour of a canvas option MUST keep its own behaviour, and the page MUST show which designers do not follow that option.
- **FR-013**: Animations MUST NOT play when the option is off or when the operating system asks for reduced motion.

**Designer-specific settings**

- **FR-014**: A designer MUST be able to declare settings of its own, shown on a page named after it, under the ADP page. A designer without such settings MUST NOT get an empty page.
- **FR-015**: A designer's own settings MUST be kept while it is turned off or uninstalled, and MUST apply again when it returns.

**Bundled definitions**

- **FR-016**: For a designer defined by a DEDL definition bundled with the plug-in, the page MUST show the definition's name, its DEDL version and the etalii.adp revision it was copied from. Users MUST NOT be offered a way to load definitions from elsewhere.
- **FR-017**: When two designers claim the same file type, the page MUST show the conflict, which designer is used, and how the user changes that.

**Robustness**

- **FR-018**: Settings that cannot be read MUST fall back to their defaults without affecting the settings that can, and the user MUST be told once.
- **FR-019**: The settings MUST be readable and applied when the IDE runs without a user interface.

### Key Entities

- **ADP settings**: the user-level choices: which designers are off and the canvas options.
- **Designer entry**: one installed designer as the page shows it: name, file types, version, origin, load status, problems, on or off.
- **Canvas options**: show grid, snap to grid, opening zoom level, play animations; each with a default.
- **Designer-specific settings**: the settings a designer declares for itself, with their defaults, shown on its own page.
- **Bundled definition**: a DEDL definition copied out of the etalii.adp repository and shipped inside the plug-in, with its name, DEDL version and source revision.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user finds the ADP page from the Settings search in one search, by typing "ADP" or any installed designer's name.
- **SC-002**: A user who wants a file type to open without its designer achieves it from the ADP page in under 30 seconds, without editing any file.
- **SC-003**: 100% of the page's options take effect in already-open designers on Apply, without reopening the file, and leave every open file unmodified, in automated tests.
- **SC-004**: 100% of definition problems the framework reports for a designer are visible on the page, in automated tests with a deliberately broken sample designer.
- **SC-005**: Settings survive an IDE restart and a settings export and import unchanged, in automated tests.
- **SC-006**: The Settings dialog opens as fast with the ADP page as without it; the page adds no noticeable delay with 20 designers installed.

## Assumptions

- The page sits where the IDE places settings for tools (Settings > Tools > ADP). Its exact place is a presentation choice for the plan.
- The default editor for a file type (designer or text) stays the IDE's own choice (spec 001, FR-003); the ADP page links to it rather than repeating it.
- Canvas options apply to designers built on the diagram designer framework (spec 003). The FreeMind designer follows them once it is migrated onto that framework.
- Defaults: every designer on, grid off, snapping on, opening zoom 100%, animations on (still subject to reduced motion).
- Colours, fonts and key bindings are the IDE's own colour scheme and keymap settings, not ADP settings.
- A visual editor for designer definitions is out of scope.
- Copying definitions out of etalii.adp into the plug-in, and interpreting them, belong to the specification that introduces DEDL interpretation in the IntelliJ plug-in; this feature only shows the result.
- No setting causes network access (constitution, Platform and Technology Constraints).
