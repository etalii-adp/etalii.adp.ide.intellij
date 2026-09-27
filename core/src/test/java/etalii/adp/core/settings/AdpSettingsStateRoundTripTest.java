package etalii.adp.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jdom.Element;
import org.junit.jupiter.api.Test;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.xmlb.XmlSerializer;

/**
 * T005 (SC-005, FR-003, FR-015): the stored state, written the way the platform's serializer
 * writes it and loaded into a fresh service, gives every value back, including the settings of a
 * designer that is not installed.
 */
class AdpSettingsStateRoundTripTest {

    private static final DesignerSetting DIRECTION = DesignerSetting.choice("direction", "Layout direction", "right", "left", "right", "both");
    private static final DesignerSetting DEPTH = DesignerSetting.number("depth", "Depth", 3, 1, 9);
    private static final DesignerSetting COMPACT = DesignerSetting.yesNo("compact", "Compact", false);

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
        state.offDesigners.add("etalii.adp.sample");
        Element element = XmlSerializer.serialize(state);
        AdpSettings.State back = XmlSerializer.deserialize(element, AdpSettings.State.class);
        assertEquals("true", back.showGrid);
        assertEquals(state.offDesigners, back.offDesigners);
    }

    @Test
    void anUnsetValueReadsAsTheDeclaredDefault() {
        AdpSettings settings = new AdpSettings();
        assertEquals("right", settings.choice("etalii.adp.gone", DIRECTION));
        assertEquals(3, settings.number("etalii.adp.gone", DEPTH));
        assertFalse(settings.yesNo("etalii.adp.gone", COMPACT));
    }
}
