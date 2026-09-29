package etalii.adp.core.diagram.sample;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditorProvider;

import etalii.adp.core.diagram.DiagramDefinition;
import etalii.adp.core.diagram.view.DiagramEditorProvider;

/**
 * The sample diagram's provider: {@code .adpsample} files whose first element is {@code <sample>}.
 * It never ships; each test registers it for its own duration, like spec 002's fake format.
 */
public final class SampleProvider extends DiagramEditorProvider {

    public static final String EDITOR_TYPE_ID = "etalii.adp.sample";

    /** The sample diagram as declared. */
    public SampleProvider() {
        this(SampleDefinition.builder());
    }

    /** A variant of the sample diagram, built here: a definition that does not hold together fails construction. */
    public SampleProvider(DiagramDefinition.Builder definition) {
        super(definition.build(), SampleMapping::new, EDITOR_TYPE_ID, "Sample Diagram", "adpsample", "sample");
    }

    /** Register the sample diagram for the rest of the test. */
    public static SampleProvider register(Disposable testDisposable) {
        return register(testDisposable, SampleDefinition.builder());
    }

    /** Register a variant of the sample diagram, such as one with a listener, for the rest of the test. */
    public static SampleProvider register(Disposable testDisposable, DiagramDefinition.Builder definition) {
        SampleProvider provider = new SampleProvider(definition);
        FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(provider, testDisposable);
        return provider;
    }
}
