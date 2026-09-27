package etalii.adp.core.diagram.sample;

import java.util.Set;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditorProvider;

import etalii.adp.core.diagram.DiagramDefinition;
import etalii.adp.core.diagram.view.DiagramEditorProvider;
import etalii.adp.core.settings.DesignerOrigin;

/**
 * A test-only designer that reports what the DEDL interpreter's designers will: a definition
 * bundled with the plug-in, copied out of etalii.adp at a known revision. It claims
 * {@code .adpbundled} and sample files, so it shares a file type with the sample designer.
 */
public final class BundledSampleProvider extends DiagramEditorProvider {

    public static final DesignerOrigin.BundledDefinition ORIGIN = new DesignerOrigin.BundledDefinition("sample-flow", "0.3", "a1b2c3d");

    private final String id;
    private final String name;

    private BundledSampleProvider(String id, String name, DiagramDefinition.Builder definition) {
        super(definition, SampleMapping::new);
        this.id = id;
        this.name = name;
    }

    /** The bundled sample designer, until {@code disposable} goes. */
    public static BundledSampleProvider register(Disposable disposable) {
        return register(disposable, new BundledSampleProvider("etalii.adp.sample.bundled", "Bundled Sample Designer", SampleDefinition.builder()));
    }

    /** A bundled definition the plug-in cannot interpret: its toolbox names a type nobody declares. */
    public static BundledSampleProvider registerBroken(Disposable disposable) {
        return register(disposable, new BundledSampleProvider("etalii.adp.sample.bundled.broken", "Broken Bundled Designer",
                SampleDefinition.builder().toolbox("task", "nothing")));
    }

    private static BundledSampleProvider register(Disposable disposable, BundledSampleProvider provider) {
        FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(provider, disposable);
        return provider;
    }

    @Override
    public DesignerOrigin origin() {
        return ORIGIN;
    }

    @Override
    protected Set<String> extensions() {
        return Set.of("adpbundled", "adpsample");
    }

    @Override
    protected boolean sniff(byte[] head) {
        return true;
    }

    @Override
    protected String editorName() {
        return name;
    }

    @Override
    public String getEditorTypeId() {
        return id;
    }
}
