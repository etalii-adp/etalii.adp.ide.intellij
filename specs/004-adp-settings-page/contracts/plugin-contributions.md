# Contract: Plug-in Contributions

What the settings fragments register, each included from `plugin.xml` with an empty fallback like every other fragment. `adp-settings.xml` holds everything below except the three sections, which `adp-settings-designers.xml`, `adp-settings-canvas.xml` and `adp-settings-designer-pages.xml` register as `<etalii.adp.settingsSection implementation="..."/>`.

| Contribution | Registration | Identifier |
|---|---|---|
| ADP page | `applicationConfigurable` | `id="etalii.adp.settings"`, `parentId="tools"`, `displayName="ADP"`, `instance="etalii.adp.core.settings.AdpConfigurable"` |
| Designer subpages | children of `etalii.adp.settings` via `Configurable.Composite` | `etalii.adp.settings.<designer id>`, display name = designer name |
| Settings storage | `applicationService` | `etalii.adp.core.settings.AdpSettings`, file `adp.xml`, component `AdpSettings` |
| Search | `search.optionContributor` | `etalii.adp.core.settings.AdpSearchableOptions` |
| Page sections | `extensionPoint` | `etalii.adp.settingsSection`, interface `etalii.adp.core.settings.SettingsSection`, `dynamic="true"` |
| Notices | `notificationGroup` | `id="ADP"`, `displayType="BALLOON"` |
| Topic | application message bus | `AdpSettingsListener.TOPIC`, display name "ADP settings" |

## The page, top to bottom

1. **Designers**: a table with columns On (check box), Name, File types, Version, Origin, Status. Selecting a row shows below it the designer's problems, its conflicts, and the canvas options it does not follow. A link "File types and default editors…" opens the platform's File Types page (FR-004).
2. **Canvas**: Show grid, Snap to grid, Opening zoom (combo: 50, 75, 100, 125, 150, 200 %). Under each, when any designer fixes it: "Not followed by: <names>" (acceptance scenario 3.2).
3. A "Reset to defaults" link that resets the canvas options on the page (applied only on Apply, FR-002).

Labels are the strings above; tests and the search contributor use them verbatim.
