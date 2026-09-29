package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;
import static etalii.adp.freemind.ui.RenameTest.RENAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.freemind.FreeMindAsserts;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.testing.ToolDriver;
import etalii.adp.testing.Layout;

/** Spec 001 FR-009, SC-001; FR-006, SC-002, US2-AS2: opening a map and saving or closing it without edits changes no byte. */
@RunWith(JUnit4.class)
public class RoundTripTest extends FileEditorManagerTestCase {

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void savingWithoutEditsIsByteIdentical() throws IOException {
        for (Path map : FreeMindAsserts.examples()) {
            byte[] original = Files.readAllBytes(map);
            try (var d = ToolDriver.open(myFixture, map)) {
                MindMap model = LayoutTest.tool(d).model();
                assertNotNull(map.toString(), model);
                assertNotNull("the root is drawn", d.viewOf(model.root().key()));
                d.select(model.root().key());
                d.showLayout(Layout.TEXT).showLayout(Layout.TOOL);
                assertFalse(map.toString(), d.isModified());
                assertArrayEquals(map.toString(), original, d.savedBytes());
            }
        }
    }

    @Test
    public void closingWithoutSavingLeavesTheFileAsItWas() throws IOException {
        for (Path map : FreeMindAsserts.examples()) {
            byte[] original = Files.readAllBytes(map);
            try (var d = ToolDriver.open(myFixture, map)) {
                d.select(LayoutTest.tool(d).model().root().key());
                FileEditorManager.getInstance(getProject()).closeFile(d.file());
                d.settle();
                assertArrayEquals(map.toString(), original, d.file().contentsToByteArray());
                assertFalse(d.isModified());
            }
        }
    }

    /** Windows and classic Mac line separators survive an unedited save and an edited one. */
    @Test
    public void crlfAndLoneCrFilesKeepTheirSeparators() {
        for (String separator : new String[] { "\r\n", "\r" }) {
            byte[] original = MAP.replace("\n", separator).getBytes(UTF_8);
            try (var d = ToolDriver.openBytes(myFixture, "separators" + separator.length() + ".mm", original)) {
                assertArrayEquals(separator.length() + "", original, d.savedBytes());
                d.select(key("B")).run(RENAME).typeInPlace("Edited");
                assertArrayEquals(d.text().replace("\n", separator).getBytes(UTF_8), d.savedBytes());
            }
        }
    }

    /** With "Strip trailing spaces on save" set for the whole file, an edited map keeps every other byte. */
    @Test
    public void stripTrailingSpacesLeavesTheMapAlone() {
        EditorSettingsExternalizable settings = EditorSettingsExternalizable.getInstance();
        String strip = settings.getStripTrailingSpaces();
        settings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_WHOLE);
        try {
            String spaced = MAP.replace("TEXT=\"Root\">\n", "TEXT=\"Root\">   \n").replace("</map>\n", "</map>  \n");
            try (var d = ToolDriver.openText(myFixture, "spaces.mm", spaced)) {
                d.select(key("B")).run(RENAME).typeInPlace("Edited");
                String saved = new String(d.savedBytes(), UTF_8);
                assertEquals(spaced.replace("TEXT=\"B\"", "TEXT=\"Edited\"").replaceAll("MODIFIED=\"\\d+\" POSITION=\"right\" TEXT=\"Edited\"", ""),
                        saved.replaceAll("MODIFIED=\"\\d+\" POSITION=\"right\" TEXT=\"Edited\"", ""));
                assertTrue("trailing spaces stay", saved.contains("TEXT=\"Root\">   \n"));
            }
        } finally {
            settings.setStripTrailingSpaces(strip);
        }
    }
}
