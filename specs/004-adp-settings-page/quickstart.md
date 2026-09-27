# Quickstart: Validating the ADP Settings Page

Prerequisites: spec 003 merged into `develop`; JDK 25; this feature's branch checked out.

## Automated

```bash
./gradlew test               # headless: service, fallback, page, gating, search, live apply
./gradlew integrationTest    # real IDE: search "ADP" and a designer name, dialog open time
./gradlew check              # everything, plus the plug-in verifier
```

All three must exit zero.

## By hand, in a sandbox IDE (`./gradlew runIde`)

| Check | Steps | Expected | Covers |
|---|---|---|---|
| Find the page | Settings, type "ADP", then "FreeMind" | The ADP page is found both times | SC-001, FR-001 |
| Designer list | Open Tools > ADP | FreeMind and draw.io listed with file types, version, "Built into ADP", Loaded | US1 |
| Turn off | Untick FreeMind, Apply, open a `.mm` file | Opens in the text editor; an already open map stays open | US2, FR-010 |
| Turn on | Tick it, Apply, open the file again | Opens in the designer | US2 |
| Grid | Tick Show grid, Apply with a `.drawio` file open | Grid appears without reopening; the file is not marked modified | US3, FR-006 |
| Not followed | Select Snap to grid | "Not followed by: FreeMind Mind Map" shown | FR-012 |
| Cancel | Change anything, Cancel | Nothing changes | FR-002 |
| Export | File > Manage IDE Settings > Export, reset, Import | Settings come back | SC-005 |

Designer subpages and a bundled-definition origin are shown only by the test-only sample designer; the headless tests cover them.
