package etalii.adp.freemind.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import etalii.adp.freemind.model.MindMap;
import etalii.adp.testing.DesignerDriver;
import etalii.adp.testing.Page;

/** FR-009, SC-001, US1-AS5: opening a map and saving or closing it without edits changes no byte. */
class RoundTripTest {

    @ParameterizedTest
    @MethodSource("etalii.adp.freemind.MindMapAsserts#examples")
    void savingWithoutEditsIsByteIdentical(Path map) throws IOException {
        byte[] original = Files.readAllBytes(map);
        try (var d = DesignerDriver.open(map, MindMapEditor.ID)) {
            MindMap model = (MindMap) d.editor().model();
            assertNotNull(model);
            assertNotNull(d.figureOf(model.root().key()), "the root is drawn");
            d.select(model.root().key());
            d.showPage(Page.TEXT).showPage(Page.VISUAL);
            assertFalse(d.isDirty());
            assertArrayEquals(original, d.savedBytes());
        }
    }

    @ParameterizedTest
    @MethodSource("etalii.adp.freemind.MindMapAsserts#examples")
    void closingWithoutSavingLeavesTheFileAsItWas(Path map) throws IOException {
        byte[] original = Files.readAllBytes(map);
        try (var d = DesignerDriver.open(map, MindMapEditor.ID)) {
            MindMap model = (MindMap) d.editor().model();
            d.select(model.root().key());
            Path onDisk = d.file().getLocation().toFile().toPath();
            d.setReadOnly(false); // closes without saving, then reopens
            assertArrayEquals(original, Files.readAllBytes(onDisk));
            assertFalse(d.isDirty());
        }
    }
}
