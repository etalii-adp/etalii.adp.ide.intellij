# Data Model: ADP Settings Page

Entities from the spec's Key Entities, with fields, rules and where each lives. Java names are those in [contracts/designer-settings-api.md](contracts/designer-settings-api.md).

## AdpSettings.State (stored, user level)

The bean `AdpSettings` persists to `adp.xml` (research R2). Values are stored as written and validated on read (R3).

| Field | Type | Default | Rule |
|---|---|---|---|
| `offDesigners` | set of designer ids | empty | An id not currently installed is kept (FR-015 for the on/off choice too). |
| `showGrid` | string (`true`/`false`) | `false` | Anything else reads as the default. |
| `snapToGrid` | string | `true` | As above. |
| `openingZoom` | string (percent) | `100` | Integer 25 to 400; otherwise the default. |
| `designerSettings` | map: designer id → (key → value) | empty | Kept for designers that are off or uninstalled (FR-015). A value is validated against the designer's current `DesignerSetting` when read. |
| unknown elements | kept as read | – | Written back unchanged, so a newer plug-in's fields survive (FR-018). |

**Fallback**: a field that fails validation reads as its default; the stored text is left alone until the user applies the page. If any field fell back during a session, one `ADP` notification lists the settings shown at their defaults.

## CanvasOptions (derived, not stored)

The typed view of the three canvas fields: `showGrid`, `snapToGrid`, `openingZoom` (double, 1.0 = 100%). `CanvasOption` names the two that a designer can fix, for `ViewOptions.fixed`.

**Effective value** for a designer: `definition.view().fixed().get(option)` when present, else the user's value (R8).

## DesignerInfo (derived per designer)

What the page shows for one installed designer (FR-007).

| Field | Source |
|---|---|
| `id` | `getEditorTypeId()` |
| `name` | `editorName()` |
| `fileTypes` | `extensions()`, sorted |
| `version` | version of the plug-in that registered the provider |
| `origin` | `DesignerOrigin` (below) |
| `problems` | list of strings; empty when loaded |
| `status` | `LOADED` when `problems` is empty, else `NOT_LOADED` |
| `on` | not in `offDesigners` |
| `conflictsWith` | ids of other designers sharing a file type (R11) |
| `unfollowed` | the `CanvasOption`s this designer fixes |

**State transitions**: `on` ↔ off, by the user on Apply only. `status` is decided when the provider is created and does not change during a session.

## DesignerOrigin (sealed)

| Variant | Fields | Shown as |
|---|---|---|
| `Module` | `pluginId` | "Built into ADP" |
| `OtherPlugin` | `pluginId`, `pluginName` | "From plug-in <pluginName>" |
| `BundledDefinition` | `name`, `dedlVersion`, `sourceRevision` | "DEDL definition <name> (DEDL <dedlVersion>), copied from etalii.adp at <sourceRevision>" (FR-016) |

## DesignerSetting (declared by a designer)

One setting a designer declares for its own page (FR-014).

| Field | Rule |
|---|---|
| `key` | Unique within the designer, `[A-Za-z][A-Za-z0-9_.-]*`. |
| `label` | Non-empty; also indexed for search. |
| `kind` | `YES_NO`, `NUMBER` (with `min`, `max`) or `CHOICE` (with non-empty `choices`). |
| `defaultValue` | Valid for its kind. |

A designer whose list breaks a rule has the problem added to its `problems`, and its page is not shown.
