# Manual Verification: FreeMind Mind Map Designer

Checks a person runs before merging `001-freemind-mindmap-designer` into `main`. Record the date,
who ran it and the result for each. Anything that fails goes back to the phase that owns it.

## FR-012, SC-005: saved maps open in FreeMind 1.0.1

Setup: install FreeMind 1.0.1 and build the plug-in (`mvn verify`). Optionally run the automated
check first:

```sh
FREEMIND_HOME=<freemind folder> mvn verify -Dtest='etalii.adp.freemind.ui.FreeMindCompatibilityTest' -Dsurefire.failIfNoSpecifiedTests=false
```

For each map in `tests/etalii.adp.freemind.tests/examples/`, copy it into a workspace project. In
the designer, add a child, add a sibling, rename a node, move a node, fold a branch, then save.

- [X] Each saved map opens in FreeMind 1.0.1 without an error.
- [X] FreeMind shows the same tree and text the designer showed before saving.
- [X] The automated check above passes (7 maps).

| Date | Who | Result | Notes |
|---|---|---|---|
| 2026-09-24 | vrenken | pass | All 7 maps checked in FreeMind 1.0.1, including the automated check. |

## SC-006: a first-time walkthrough without documentation

Ask a developer who knows the IDE but has not seen the designer to do the following, with no
instructions beyond this sentence: "open this map, add five nodes, undo two of them, and save."

- [ ] They open the map by double-click.
- [ ] They add five nodes, using the keyboard or the context menu.
- [ ] They undo two of them with Edit > Undo or Ctrl+Z.
- [ ] They save.
- [ ] They manage all of this on the first attempt.

| Date | Who | Result | Notes |
|---|---|---|---|
| 2026-09-24 | vrenken | approved | T087 approved by the project owner; the individual steps above were not itemised. |
