package etalii.adp.core;

import com.intellij.openapi.actionSystem.DataKey;

/** What a tool publishes to actions through its {@code DataContext}. */
public final class AdpDataKeys {

    /** The focused tool. Actions read it to find the model, the selection and {@code execute}. */
    public static final DataKey<AdpToolFileEditor<?>> ADP_TOOL = DataKey.create("etalii.adp.tool");

    private AdpDataKeys() {
    }
}
