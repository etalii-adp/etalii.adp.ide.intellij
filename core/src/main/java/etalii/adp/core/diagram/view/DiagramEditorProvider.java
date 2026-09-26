package etalii.adp.core.diagram.view;

import java.util.Set;
import java.util.function.Supplier;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import etalii.adp.core.AdpEditorProvider;
import etalii.adp.core.diagram.DefinitionException;
import etalii.adp.core.diagram.DiagramDefinition;
import etalii.adp.core.diagram.DiagramMapping;
import etalii.adp.core.xml.XmlTree;

/**
 * Opens a diagram format's files in a {@link DiagramDesigner} (contracts/diagram-framework.md).
 * A designer author says which files: an extension and root element names, or by overriding
 * {@code extensions()}, {@code sniff}, {@code editorName()} and {@code getEditorTypeId()}. A
 * definition given as a builder is built here, so an inconsistent one fails when the IDE loads the
 * provider, naming every problem (FR-002).
 */
public abstract class DiagramEditorProvider extends AdpEditorProvider {

    private final DiagramDefinition definition;
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
        this.definition = definition;
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

    /** @throws DefinitionException when the definition does not hold together */
    protected DiagramEditorProvider(DiagramDefinition.Builder definition, Supplier<DiagramMapping> mapping) {
        this(definition.build(), mapping);
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

    public DiagramDefinition definition() {
        return definition;
    }

    /** A {@link DiagramDesigner}; override to return a subclass with designer-specific actions. */
    @Override
    protected DiagramDesigner createDesigner(Project project, VirtualFile file, Document document) {
        return new DiagramDesigner(project, file, document, definition, mapping.get());
    }
}
