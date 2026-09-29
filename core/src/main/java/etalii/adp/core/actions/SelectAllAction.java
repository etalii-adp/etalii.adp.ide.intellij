package etalii.adp.core.actions;

import etalii.adp.core.AdpToolFileEditor;

/** Select every item of the focused tool. */
public final class SelectAllAction extends ZoomActions.ToolAction {

    @Override
    protected void run(AdpToolFileEditor<?> tool) {
        tool.selectAll();
    }
}
