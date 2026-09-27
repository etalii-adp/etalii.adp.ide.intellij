package etalii.adp.core.settings;

import java.util.List;

import com.intellij.openapi.fileEditor.FileEditorProvider;

import etalii.adp.core.AdpEditorProvider;

/**
 * Every installed ADP designer, read from the platform's file editor providers, the single source
 * of which editors exist (research R5). Their order is the platform's extension order, which
 * decides between two designers for the same file (research R11).
 */
public final class AdpDesigners {

    private AdpDesigners() {
    }

    public static List<AdpEditorProvider> providers() {
        return FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getExtensionList().stream()
                .filter(AdpEditorProvider.class::isInstance).map(AdpEditorProvider.class::cast).toList();
    }

    public static List<DesignerInfo> all() {
        return providers().stream().map(AdpEditorProvider::designerInfo).toList();
    }

    /** The designers that get a page of their own: they declare settings, and the declarations hold together (FR-014). */
    public static List<AdpEditorProvider> withPages() {
        return providers().stream().filter(p -> !p.settings().isEmpty() && DesignerSetting.problems(p.settings()).isEmpty()).toList();
    }
}
