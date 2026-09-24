package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;
import static etalii.adp.freemind.ui.RenameTest.RENAME;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.fileEditor.impl.MemoryDiskConflictResolver;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.testing.DesignerDriver;
import etalii.adp.testing.Layout;

/**
 * Spec 001 FR-005, US2-AS5: a change on disk follows the IDE's rules for text files. A clean file
 * reloads; a modified one makes the IDE ask, here answered by a test stand-in for its File Cache
 * Conflict question.
 */
@RunWith(JUnit4.class)
public class ExternalChangeTest extends FileEditorManagerTestCase {

    private static final String ON_DISK = MAP.replace("TEXT=\"B\"", "TEXT=\"Changed on disk\"");

    private final List<String> asked = new ArrayList<>();
    private boolean loadFromDisk;

    @Override
    public void setUp() {
        super.setUp();
        FileDocumentManagerImpl documents = (FileDocumentManagerImpl) FileDocumentManager.getInstance();
        documents.overrideConflictsSolverEnabled(true, getTestRootDisposable());
        documents.setAskReloadFromDisk(getTestRootDisposable(), new MemoryDiskConflictResolver() {
            @Override
            protected boolean askReloadFromDisk(@NotNull VirtualFile file, @NotNull Document document) {
                asked.add(file.getName());
                return loadFromDisk;
            }
        });
    }

    private DesignerDriver open() {
        return DesignerDriver.openText(myFixture, "external.mm", MAP);
    }

    @Test
    public void aCleanEditorReloadsTheChangedFile() {
        try (var d = open()) {
            d.changeOnDisk(ON_DISK);
            assertEquals(ON_DISK, d.text());
            assertFalse(d.isModified());
            assertTrue("a clean file is not asked about", asked.isEmpty());
            assertEquals("the designer follows", "Changed on disk", d.viewOf(key("B")).text());
        }
    }

    @Test
    public void aCleanEditorOnTheTextPageReloadsToo() {
        try (var d = open()) {
            d.showLayout(Layout.TEXT);
            d.changeOnDisk(ON_DISK);
            assertEquals(ON_DISK, d.text());
            assertFalse(d.isModified());
            d.showLayout(Layout.DESIGNER);
            assertEquals("Changed on disk", d.viewOf(key("B")).text());
        }
    }

    @Test
    public void aDirtyEditorKeepsItsTextUntilAsked() {
        loadFromDisk = false;
        try (var d = open()) {
            d.editText(text -> text.replace("TEXT=\"A1\"", "TEXT=\"Mine\""));
            String mine = d.text();
            d.changeOnDisk(ON_DISK);
            assertEquals("a modified file is never replaced silently", List.of("external.mm"), asked);
            assertEquals(mine, d.text());
            assertTrue(d.isModified());
        }
    }

    @Test
    public void aDirtyEditorOnTheTextPageAsksAndReplaces() {
        loadFromDisk = true;
        try (var d = open()) {
            d.showLayout(Layout.TEXT);
            d.editText(text -> text.replace("TEXT=\"A1\"", "TEXT=\"Mine\""));
            d.changeOnDisk(ON_DISK);

            assertEquals("asked once", List.of("external.mm"), asked);
            assertEquals("the answer loads the file", ON_DISK, d.text());
            assertFalse(d.isModified());
            d.showLayout(Layout.DESIGNER);
            assertEquals("Changed on disk", d.viewOf(key("B")).text());
            assertEquals("A1", d.viewOf(key("A1")).text());
        }
    }

    @Test
    public void aDirtyEditorOnTheTextPageAsksAndKeeps() {
        loadFromDisk = false;
        try (var d = open()) {
            d.showLayout(Layout.TEXT);
            d.editText(text -> text.replace("TEXT=\"A1\"", "TEXT=\"Mine\""));
            String mine = d.text();
            d.changeOnDisk(ON_DISK);

            assertEquals("asked once", List.of("external.mm"), asked);
            assertEquals("the user's text stays", mine, d.text());
            assertTrue(d.isModified());
            d.showLayout(Layout.DESIGNER);
            assertEquals("Mine", d.viewOf(key("A1")).text());
        }
    }

    /** Both answers, in the split layout where the text and the designer are both showing. */
    @Test
    public void aDirtyEditorAsksOnceItsTextPageHasBeenActivated() {
        for (boolean load : new boolean[] { true, false }) {
            loadFromDisk = load;
            asked.clear();
            try (var d = DesignerDriver.openText(myFixture, "external-" + load + ".mm", MAP)) {
                d.showLayout(Layout.SPLIT);
                d.editText(text -> text.replace("TEXT=\"A1\"", "TEXT=\"Mine\""));
                String mine = d.text();
                d.changeOnDisk(ON_DISK);

                assertEquals(load + ": " + asked, 1, asked.size());
                assertEquals(String.valueOf(load), load ? ON_DISK : mine, d.text());
                assertEquals(String.valueOf(load), !load, d.isModified());
                assertEquals(load ? "Changed on disk" : "B", d.viewOf(key("B")).text());
            }
        }
    }

    @Test
    public void aDirtyEditorOnTheVisualPageAsks() {
        loadFromDisk = true;
        try (var d = open()) {
            d.select(key("A1")).run(RENAME).typeInPlace("Mine");
            assertEquals(Layout.DESIGNER, d.layout());
            assertTrue(d.isModified());
            d.changeOnDisk(ON_DISK);

            assertEquals("a modified file showing the designer asks as well", List.of("external.mm"), asked);
            assertEquals(ON_DISK, d.text());
            assertFalse(d.isModified());
            assertEquals("Changed on disk", d.viewOf(key("B")).text());
        }
    }
}
