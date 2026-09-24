package etalii.adp.freemind.ui;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.ThreadLocalRandom;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.ILog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.WizardNewFileCreationPage;
import org.eclipse.ui.ide.IDE;

import etalii.adp.freemind.edit.FreeMindConventions;

/**
 * File > New > FreeMind Mind Map (FR-025): a container and file name, then FreeMind 1.0.1's own
 * new-map text with a fresh {@code ID} and timestamps, opened in the editor the workbench chooses
 * for it, which is the designer unless the user changed the default.
 */
public class NewMindMapWizard extends Wizard implements INewWizard {

    static final String DEFAULT_FILE_NAME = "mindmap.mm";

    private IWorkbench workbench;
    private IStructuredSelection selection = StructuredSelection.EMPTY;
    private WizardNewFileCreationPage page;

    public NewMindMapWizard() {
        setWindowTitle("New FreeMind Mind Map");
    }

    @Override
    public void init(IWorkbench workbench, IStructuredSelection selection) {
        this.workbench = workbench;
        this.selection = selection == null ? StructuredSelection.EMPTY : selection;
    }

    @Override
    public void addPages() {
        page = new WizardNewFileCreationPage("newMindMap", selection) {
            @Override
            protected InputStream getInitialContents() {
                long now = FreeMindConventions.now();
                String id = FreeMindConventions.newId(candidate -> false, ThreadLocalRandom.current());
                return new ByteArrayInputStream(initialContent(id, now).getBytes(UTF_8));
            }
        };
        page.setTitle("FreeMind Mind Map");
        page.setDescription("Create a FreeMind mind map with a single root node.");
        page.setFileName(DEFAULT_FILE_NAME);
        page.setFileExtension("mm");
        addPage(page);
    }

    @Override
    public boolean performFinish() {
        IFile file = page.createNewFile();
        if (file == null) {
            return false;
        }
        IWorkbench bench = workbench != null ? workbench : PlatformUI.getWorkbench();
        IWorkbenchWindow window = bench.getActiveWorkbenchWindow();
        if (window == null) {
            window = bench.getWorkbenchWindows()[0];
        }
        try {
            IDE.openEditor(window.getActivePage(), file, true);
        } catch (PartInitException e) {
            ILog.of(getClass()).log(e.getStatus());
        }
        return true;
    }

    /** The text FreeMind 1.0.1 writes for a new map, with the root text "New Mindmap". */
    static String initialContent(String id, long now) {
        return "<map version=\"1.0.1\">\n"
                + "<!-- To view this file, download free mind mapping software FreeMind from http://freemind.sourceforge.net -->\n"
                + "<node CREATED=\"" + now + "\" ID=\"" + id + "\" MODIFIED=\"" + now + "\" TEXT=\"New Mindmap\"/>\n"
                + "</map>\n";
    }
}
