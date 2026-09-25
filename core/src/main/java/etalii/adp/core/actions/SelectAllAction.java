package etalii.adp.core.actions;

import etalii.adp.core.AdpDesignerEditor;

/** Select every item of the focused designer. */
public final class SelectAllAction extends ZoomActions.DesignerAction {

    @Override
    protected void run(AdpDesignerEditor<?> designer) {
        designer.selectAll();
    }
}
