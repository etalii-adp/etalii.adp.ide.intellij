package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.example;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.ui.IEditorDescriptor;
import org.eclipse.ui.IEditorRegistry;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import etalii.adp.testing.DesignerDriver;

/** FR-001 to FR-003, US1-AS1: which editor the workbench picks for a {@code .mm} file. */
class RegistrationTest {

    private static final String CONTENT_TYPE = "etalii.adp.freemind.mindmap";
    private static final String TEXT_EDITOR = "org.eclipse.ui.DefaultTextEditor";
    private static final String OBJECTIVE_CPP = """
            #import <Foundation/Foundation.h>
            #include <vector>

            int main(int argc, const char *argv[]) {
                std::vector<int> map;
                return 0;
            }
            """;

    @ParameterizedTest
    @MethodSource("etalii.adp.freemind.MindMapAsserts#examples")
    void aFreeMindMapOpensInTheDesignerByDefault(Path map) {
        try (var d = DesignerDriver.open(map, null)) {
            assertEquals(MindMapEditor.ID, d.editorIdUsed());
            assertInstanceOf(MindMapEditor.class, d.editor());
            assertNotNull(d.editor().model(), "the map is drawn, not shown as a problem");
        }
    }

    @Test
    void openWithListsTheDesignerAndTheTextEditor() throws CoreException {
        try (var d = DesignerDriver.open(example("freemind-1.0.1-rich-notes.mm"), null)) {
            IContentDescription description = d.file().getContentDescription();
            assertNotNull(description);
            assertEquals(CONTENT_TYPE, description.getContentType().getId());

            IEditorRegistry registry = PlatformUI.getWorkbench().getEditorRegistry();
            IContentType type = Platform.getContentTypeManager().getContentType(CONTENT_TYPE);
            List<String> ids = Arrays.stream(registry.getEditors(d.file().getName(), type)).map(IEditorDescriptor::getId).toList();
            assertTrue(ids.contains(MindMapEditor.ID), ids.toString());
            assertTrue(ids.contains(TEXT_EDITOR), ids.toString());
            assertEquals(MindMapEditor.ID, registry.getDefaultEditor(d.file().getName(), type).getId());
        }
    }

    /**
     * The editor that would otherwise apply is asked of the workbench rather than opened, because
     * it can be an external program associated with {@code .mm} by the operating system.
     */
    @Test
    void anObjectiveCppFileIsNotClaimed() throws CoreException {
        try (var d = DesignerDriver.openText("main.mm", OBJECTIVE_CPP, TEXT_EDITOR)) {
            IContentDescription description = d.file().getContentDescription();
            assertTrue(description == null || !CONTENT_TYPE.equals(description.getContentType().getId()));

            IEditorDescriptor chosen = IDE.getEditorDescriptor(d.file(), true, false);
            assertNotEquals(MindMapEditor.ID, chosen.getId());

            assertEquals(TEXT_EDITOR, d.editorIdUsed());
            assertNull(d.editor());
        }
    }

    @Test
    void theTextEditorCanBeMadeTheDefault() {
        try (var d = DesignerDriver.open(example("freemind-1.0.1-rich-notes.mm"), null)) {
            assertEquals(MindMapEditor.ID, d.editorIdUsed());
            try {
                IDE.setDefaultEditor(d.file(), TEXT_EDITOR);
                d.setReadOnly(false); // closes and reopens with the default editor
                assertEquals(TEXT_EDITOR, d.editorIdUsed());
            } finally {
                IDE.setDefaultEditor(d.file(), null);
            }
            d.setReadOnly(false);
            assertEquals(MindMapEditor.ID, d.editorIdUsed());
        }
    }
}
