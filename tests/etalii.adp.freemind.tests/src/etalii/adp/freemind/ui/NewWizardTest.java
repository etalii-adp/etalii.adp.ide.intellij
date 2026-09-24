package etalii.adp.freemind.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.WizardNewFileCreationPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import etalii.adp.freemind.model.MindMap;

/** FR-025, US2-AS7: the New wizard creates FreeMind 1.0.1's new-map text and opens it in the designer. */
class NewWizardTest {

    private static final Pattern NEW_MAP = Pattern.compile("""
            <map version="1\\.0\\.1">
            <!-- To view this file, download free mind mapping software FreeMind from http://freemind\\.sourceforge\\.net -->
            <node CREATED="(\\d+)" ID="(ID_\\d+)" MODIFIED="(\\d+)" TEXT="New Mindmap"/>
            </map>
            """);

    private IProject project;

    @BeforeEach
    void createProject() throws CoreException {
        project = ResourcesPlugin.getWorkspace().getRoot().getProject("adp-new-wizard");
        if (project.exists()) {
            project.delete(true, true, null);
        }
        project.create(null);
        project.open(null);
    }

    @AfterEach
    void deleteProject() throws CoreException {
        page().closeAllEditors(false);
        settle();
        project.delete(true, true, null);
    }

    @Test
    void createsANewMapAndOpensItInTheDesigner() throws IOException {
        long before = System.currentTimeMillis();
        IFile file = finish(null);
        long after = System.currentTimeMillis();

        assertTrue(file.exists());
        String text = Files.readString(file.getLocation().toFile().toPath(), UTF_8);
        Matcher matcher = NEW_MAP.matcher(text);
        assertTrue(matcher.matches(), text);
        assertEquals(matcher.group(1), matcher.group(3), "one clock read");
        long created = Long.parseLong(matcher.group(1));
        assertTrue(before <= created && created <= after);

        IEditorPart editor = page().getActiveEditor();
        MindMapEditor designer = assertInstanceOf(MindMapEditor.class, editor);
        assertEquals(file, ((IFileEditorInput) designer.getEditorInput()).getFile());
        MindMap map = designer.model();
        assertNotNull(map);
        assertEquals("New Mindmap", map.root().text());
        assertTrue(map.root().children().isEmpty());
        assertTrue(!designer.isDirty());
    }

    @Test
    void eachNewMapGetsAFreshId() throws IOException {
        String first = Files.readString(finish("one.mm").getLocation().toFile().toPath(), UTF_8);
        String second = Files.readString(finish("two.mm").getLocation().toFile().toPath(), UTF_8);
        Matcher a = NEW_MAP.matcher(first);
        Matcher b = NEW_MAP.matcher(second);
        assertTrue(a.matches() && b.matches());
        assertNotEquals(a.group(2), b.group(2));
    }

    /** Run the wizard as the New dialog would, without opening the dialog; {@code null} keeps the default name. */
    private IFile finish(String fileName) {
        NewMindMapWizard wizard = new NewMindMapWizard();
        wizard.init(PlatformUI.getWorkbench(), new StructuredSelection(project));
        WizardDialog dialog = new WizardDialog(window().getShell(), wizard);
        try {
            dialog.create();
            WizardNewFileCreationPage page = assertInstanceOf(WizardNewFileCreationPage.class, wizard.getPages()[0]);
            assertEquals("mindmap.mm", page.getFileName());
            assertEquals(project.getFullPath(), page.getContainerFullPath());
            if (fileName != null) {
                page.setFileName(fileName);
            }
            assertTrue(wizard.canFinish());
            assertTrue(wizard.performFinish());
            settle();
            return project.getFile(fileName == null ? "mindmap.mm" : fileName);
        } finally {
            dialog.close();
        }
    }

    private static IWorkbenchWindow window() {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        return window != null ? window : PlatformUI.getWorkbench().getWorkbenchWindows()[0];
    }

    private static IWorkbenchPage page() {
        return window().getActivePage();
    }

    private static void settle() {
        Display display = Display.getCurrent();
        for (int round = 0; round < 3; round++) {
            while (display.readAndDispatch()) {
                // keep dispatching
            }
        }
    }
}
