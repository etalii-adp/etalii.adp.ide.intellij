package etalii.adp.core.diagram.sample;

import java.util.List;
import java.util.Set;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditorProvider;

import etalii.adp.core.diagram.view.DiagramEditorProvider;
import etalii.adp.core.settings.ToolSetting;

/** A test-only sample diagram with settings of its own, for {@code .adpsettings} files. */
public final class SettingSampleProvider extends DiagramEditorProvider {

    public static final String EDITOR_TYPE_ID = "etalii.adp.sample.settings";
    public static final ToolSetting COMPACT = ToolSetting.yesNo("compact", "Compact rows", false);
    public static final ToolSetting DEPTH = ToolSetting.number("depth", "Depth", 3, 1, 9);
    public static final ToolSetting DIRECTION = ToolSetting.choice("direction", "Layout direction", "right", "left", "right");

    private final List<ToolSetting> settings;

    public SettingSampleProvider(List<ToolSetting> settings) {
        super(SampleDefinition.builder(), SampleMapping::new);
        this.settings = List.copyOf(settings);
    }

    /** Register the diagram with its three settings, until {@code disposable} goes. */
    public static SettingSampleProvider register(Disposable disposable) {
        return register(disposable, List.of(COMPACT, DEPTH, DIRECTION));
    }

    public static SettingSampleProvider register(Disposable disposable, List<ToolSetting> settings) {
        SettingSampleProvider provider = new SettingSampleProvider(settings);
        FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(provider, disposable);
        return provider;
    }

    @Override
    public List<ToolSetting> settings() {
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
    protected String toolName() {
        return "Settings Sample Diagram";
    }

    @Override
    public String getEditorTypeId() {
        return EDITOR_TYPE_ID;
    }
}
