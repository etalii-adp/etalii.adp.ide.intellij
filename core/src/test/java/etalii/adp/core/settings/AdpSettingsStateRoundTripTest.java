package etalii.adp.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;

import org.jdom.Element;
import org.junit.jupiter.api.Test;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.XmlSerializer;

/**
 * T005 (SC-005, FR-003, FR-015): the stored state, written the way the platform's serializer
 * writes it and loaded into a fresh service, gives every value back, including the settings of a
 * tool that is not installed.
 */
class AdpSettingsStateRoundTripTest {

    private static final ToolSetting DIRECTION = ToolSetting.choice("direction", "Layout direction", "right", "left", "right", "both");
    private static final ToolSetting DEPTH = ToolSetting.number("depth", "Depth", 3, 1, 9);
    private static final ToolSetting COMPACT = ToolSetting.yesNo("compact", "Compact", false);

    @Test
    void everyValueComesBack() throws Exception {
        AdpSettings before = new AdpSettings();
        before.setOff("etalii.adp.sample", true);
        before.setOff("etalii.adp.gone", true);
        before.setCanvas(new CanvasOptions(true, false, 1.25));
        before.setValue("etalii.adp.gone", DIRECTION, "left");
        before.setValue("etalii.adp.gone", DEPTH, "7");
        before.setValue("etalii.adp.sample", COMPACT, "true");

        String written = JDOMUtil.write(before.getState());
        AdpSettings after = new AdpSettings();
        after.loadState(JDOMUtil.load(written));

        assertEquals(new CanvasOptions(true, false, 1.25), after.canvas());
        assertTrue(after.isOff("etalii.adp.sample"));
        assertTrue(after.isOff("etalii.adp.gone"));
        assertFalse(after.isOff("etalii.adp.other"));
        assertEquals("left", after.choice("etalii.adp.gone", DIRECTION));
        assertEquals(7, after.number("etalii.adp.gone", DEPTH));
        assertTrue(after.yesNo("etalii.adp.sample", COMPACT));
        assertEquals(written, JDOMUtil.write(after.getState()));
    }

    @Test
    void theStateBeanIsWhatThePlatformSerializerWrites() {
        AdpSettings.State state = new AdpSettings.State();
        state.showGrid = "true";
        state.offTools.add("etalii.adp.sample");
        Element element = XmlSerializer.serialize(state);
        AdpSettings.State back = XmlSerializer.deserialize(element, AdpSettings.State.class);
        assertEquals("true", back.showGrid);
        assertEquals(state.offTools, back.offTools);
    }

    @Test
    void anUnsetValueReadsAsTheDeclaredDefault() {
        AdpSettings settings = new AdpSettings();
        assertEquals("right", settings.choice("etalii.adp.gone", DIRECTION));
        assertEquals(3, settings.number("etalii.adp.gone", DEPTH));
        assertFalse(settings.yesNo("etalii.adp.gone", COMPACT));
    }

    /**
     * T049 (spec 002 of etalii.adp, research R5): the adp.xml saved before the rename, with the old
     * field names, gives every value back, and only the new names are written again.
     */
    @Test
    void theSettingsStoredBeforeTheRenameStillApply() throws Exception {
        AdpSettings settings = new AdpSettings();
        try (InputStream in = getClass().getResourceAsStream("/settings/baseline-adp.xml")) {
            settings.loadState(JDOMUtil.load(in).getChild("component"));
        }

        assertTrue(settings.isOff("etalii.adp.drawio"));
        assertFalse(settings.isOff("etalii.adp.freemind"));
        assertEquals(new CanvasOptions(true, false, 1.5), settings.canvas());
        assertEquals("left", settings.choice("etalii.adp.sample.settings", DIRECTION));

        String written = JDOMUtil.write(settings.getState());
        assertTrue(written.contains("\"offTools\"") && written.contains("\"toolSettings\""), written);
        assertFalse(written.contains("offDesigners") || written.contains("designerSettings"), written);
    }

    /** The mind map's old editor type id reads as its new one, in both the off list and the per-tool values. */
    @Test
    void theOldMindMapIdReadsAsTheNewOne() throws Exception {
        AdpSettings settings = new AdpSettings();
        settings.loadState(JDOMUtil.load("""
                <component name="AdpSettings">
                  <option name="designerSettings"><map><entry key="etalii.adp.freemind.editor/depth" value="5" /></map></option>
                  <option name="offDesigners"><set><option value="etalii.adp.freemind.editor" /></set></option>
                </component>"""));

        assertTrue(settings.isOff("etalii.adp.freemind"));
        assertFalse(settings.isOff("etalii.adp.freemind.editor"));
        assertEquals(5, settings.number("etalii.adp.freemind", DEPTH));
        String written = JDOMUtil.write(settings.getState());
        assertFalse(written.contains("etalii.adp.freemind.editor"), written);
    }

    /** When a file holds both, the new names win and the old ones are dropped. */
    @Test
    void theNewNamesWinOverTheOld() throws Exception {
        AdpSettings settings = new AdpSettings();
        settings.loadState(JDOMUtil.load("""
                <component name="AdpSettings">
                  <option name="offDesigners"><set><option value="etalii.adp.old" /></set></option>
                  <option name="offTools"><set><option value="etalii.adp.new" /></set></option>
                </component>"""));

        assertTrue(settings.isOff("etalii.adp.new"));
        assertFalse(settings.isOff("etalii.adp.old"));
        Element written = settings.getState();
        assertNull(written.getChildren().stream()
                .filter(field -> "offDesigners".equals(field.getAttributeValue("name"))).findAny().orElse(null), JDOMUtil.write(written));
    }
}
