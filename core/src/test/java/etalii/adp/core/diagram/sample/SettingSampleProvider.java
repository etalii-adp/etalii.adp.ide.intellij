package etalii.adp.core.diagram.sample;

import java.util.List;
import java.util.Set;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditorProvider;

import etalii.adp.core.diagram.view.DiagramEditorProvider;
import etalii.adp.core.settings.DesignerSetting;

/** A test-only sample designer with settings of its own, for {@code .adpsettings} files. */
public final class SettingSampleProvider extends DiagramEditorProvider {

    public static final String EDITOR_TYPE_ID = "etalii.adp.sample.settings";
    public static final DesignerSetting COMPACT = DesignerSetting.yesNo("compact", "Compact rows", false);
    public static final DesignerSetting DEPTH = DesignerSetting.number("depth", "Depth", 3, 1, 9);
    public static final DesignerSetting DIRECTION = DesignerSetting.choice("direction", "Layout direction", "right", "left", "right");

    private final List<DesignerSetting> settings;

    public SettingSampleProvider(List<DesignerSetting> settings) {
        super(SampleDefinition.builder(), SampleMapping::new);
        this.settings = List.copyOf(settings);
    }

    /** Register the designer with its three settings, until {@code disposable} goes. */
    public static SettingSampleProvider register(Disposable disposable) {
        return register(disposable, List.of(COMPACT, DEPTH, DIRECTION));
    }

    public static SettingSampleProvider register(Disposable disposable, List<DesignerSetting> settings) {
        SettingSampleProvider provider = new SettingSampleProvider(settings);
        FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(provider, disposable);
        return provider;
    }

    @Override
    public List<DesignerSetting> settings() {
        return settings;
    }

    @Override
    protected Set<String> extensions() {
        return Set.of("adpsettings");
    }

    @Override
    protected boolean sniff(byte[] head) {
        return true;
    }

    @Override
    protected String editorName() {
        return "Settings Sample Designer";
    }

    @Override
    public String getEditorTypeId() {
        return EDITOR_TYPE_ID;
    }
}
