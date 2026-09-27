package etalii.adp.core.diagram.view;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import etalii.adp.core.AdpEditorProvider;
import etalii.adp.core.diagram.DefinitionException;
import etalii.adp.core.diagram.DiagramDefinition;
import etalii.adp.core.diagram.DiagramMapping;
import etalii.adp.core.settings.CanvasOption;
import etalii.adp.core.xml.XmlTree;

/**
 * Opens a diagram format's files in a {@link DiagramDesigner} (contracts/diagram-framework.md).
 * A designer author says which files: an extension and root element names, or by overriding
 * {@code extensions()}, {@code sniff}, {@code editorName()} and {@code getEditorTypeId()}. A
 * definition given as a builder is built here. An inconsistent one does not stop the provider
 * from loading: it keeps every problem (FR-002), refuses every file, and the ADP page lists it as
 * not loaded with its problems (spec 004, research R5).
 */
public abstract class DiagramEditorProvider extends AdpEditorProvider {

    private final DiagramDefinition definition;
    private final List<String> definitionProblems;
    private final Supplier<DiagramMapping> mapping;
    private final String editorTypeId;
    private final String editorName;
    private final Set<String> extensions;
    private final Set<String> rootNames;

    /**
     * Files with {@code extension} whose first element, after the XML prolog, is one of
     * {@code rootNames} (see {@link XmlTree#rootName}).
     */
    protected DiagramEditorProvider(DiagramDefinition definition, Supplier<DiagramMapping> mapping, String editorTypeId, String editorName,
            String extension, String... rootNames) {
        this(new Built(definition, List.of()), mapping, editorTypeId, editorName, extension, rootNames);
    }

    private DiagramEditorProvider(Built built, Supplier<DiagramMapping> mapping, String editorTypeId, String editorName, String extension,
            String... rootNames) {
        this.definition = built.definition();
        this.definitionProblems = built.problems();
        this.mapping = mapping;
        this.editorTypeId = editorTypeId;
        this.editorName = editorName;
        this.extensions = extension == null ? null : Set.of(extension);
        this.rootNames = Set.of(rootNames);
    }

    /** For a provider that overrides {@code extensions()}, {@code sniff}, {@code editorName()} and {@code getEditorTypeId()}. */
    protected DiagramEditorProvider(DiagramDefinition definition, Supplier<DiagramMapping> mapping) {
        this(definition, mapping, null, null, null);
    }

    /** A definition that does not hold together leaves {@link #definition()} {@code null} and its problems in {@link #problems()}. */
    protected DiagramEditorProvider(DiagramDefinition.Builder definition, Supplier<DiagramMapping> mapping) {
        this(Built.of(definition), mapping, null, null, null);
    }

    /** A definition as built, or the problems that stopped it. */
    private record Built(DiagramDefinition definition, List<String> problems) {

        static Built of(DiagramDefinition.Builder builder) {
            try {
                return new Built(builder.build(), List.of());
            } catch (DefinitionException e) {
                return new Built(null, e.problems());
            }
        }
    }

    @Override
    protected Set<String> extensions() {
        return extensions;
    }

    @Override
    protected boolean sniff(byte[] head) {
        String root = XmlTree.rootName(head);
        return root != null && rootNames.contains(root);
    }

    @Override
    protected String editorName() {
        return editorName;
    }

    @Override
    public String getEditorTypeId() {
        return editorTypeId;
    }

    /** {@code null} when the definition did not hold together; see {@link #problems()}. */
    public DiagramDefinition definition() {
        return definition;
    }

    @Override
    public List<String> problems() {
        List<String> problems = new ArrayList<>(definitionProblems);
        problems.addAll(super.problems());
        return problems;
    }

    @Override
    public Set<CanvasOption> fixedOptions() {
        return definition == null ? Set.of() : definition.view().fixed().keySet();
    }

    /** A {@link DiagramDesigner}; override to return a subclass with designer-specific actions. */
    @Override
    protected DiagramDesigner createDesigner(Project project, VirtualFile file, Document document) {
        return new DiagramDesigner(project, file, document, definition, mapping.get());
    }
}
