package etalii.adp.core;

import com.intellij.openapi.actionSystem.DataKey;

/** What a designer publishes to actions through its {@code DataContext}. */
public final class AdpDataKeys {

    /** The focused designer. Actions read it to find the model, the selection and {@code execute}. */
    public static final DataKey<AdpDesignerEditor<?>> ADP_DESIGNER = DataKey.create("etalii.adp.designer");

    private AdpDataKeys() {
    }
}
