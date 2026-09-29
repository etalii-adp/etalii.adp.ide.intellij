package etalii.adp.core.diagram.sample;

import java.util.Set;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditorProvider;

import etalii.adp.core.diagram.view.DiagramEditorProvider;

/**
 * A test-only diagram whose definition does not hold together: its toolbox names a type nobody
 * declares, and one of its anchors accepts an undeclared connection. It claims {@code .adpbroken}
 * files whose first element is {@code <sample>}.
 */
public final class BrokenSampleProvider extends DiagramEditorProvider {

    public static final String EDITOR_TYPE_ID = "etalii.adp.sample.broken";

    public BrokenSampleProvider() {
        super(SampleDefinition.builder().toolbox("task", "nothing"), SampleMapping::new);
    }

    public static BrokenSampleProvider register(Disposable testDisposable) {
        BrokenSampleProvider provider = new BrokenSampleProvider();
        FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(provider, testDisposable);
        return provider;
    }

    @Override
    protected Set<String> extensions() {
        return Set.of("adpbroken");
    }

    @Override
    protected boolean sniff(byte[] head) {
        return new String(head, java.nio.charset.StandardCharsets.UTF_8).contains("<sample");
    }

    @Override
    protected String toolName() {
        return "Broken Sample Diagram";
    }

    @Override
    public String getEditorTypeId() {
        return EDITOR_TYPE_ID;
    }
}
