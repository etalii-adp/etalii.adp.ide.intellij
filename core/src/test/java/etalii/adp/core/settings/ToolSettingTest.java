package etalii.adp.core.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** T006: each factory, and each rule of data-model.md yielding one problem that names the setting. */
class ToolSettingTest {

    @Test
    void theFactoriesDeclareEachKind() {
        ToolSetting yesNo = ToolSetting.yesNo("compact", "Compact", true);
        assertEquals(ToolSetting.Kind.YES_NO, yesNo.kind());
        assertEquals("true", yesNo.defaultValue());

        ToolSetting number = ToolSetting.number("depth", "Depth", 3, 1, 9);
        assertEquals(ToolSetting.Kind.NUMBER, number.kind());
        assertEquals("3", number.defaultValue());
        assertEquals(1, number.min());
        assertEquals(9, number.max());

        ToolSetting choice = ToolSetting.choice("direction", "Layout direction", "right", "left", "right");
        assertEquals(ToolSetting.Kind.CHOICE, choice.kind());
        assertEquals(List.of("left", "right"), choice.choices());
        assertEquals("right", choice.defaultValue());

        assertEquals(List.of(), ToolSetting.problems(List.of(yesNo, number, choice)));
    }

    @Test
    void aValueIsCheckedAgainstItsKind() {
        ToolSetting yesNo = ToolSetting.yesNo("compact", "Compact", true);
        assertTrue(yesNo.accepts("false"));
        assertFalse(yesNo.accepts("maybe"));
        ToolSetting number = ToolSetting.number("depth", "Depth", 3, 1, 9);
        assertTrue(number.accepts("9"));
        assertFalse(number.accepts("10"));
        assertFalse(number.accepts("three"));
        ToolSetting choice = ToolSetting.choice("direction", "Layout direction", "right", "left", "right");
        assertTrue(choice.accepts("left"));
        assertFalse(choice.accepts("up"));
        assertFalse(choice.accepts(null));
    }

    private static void oneProblem(String expected, ToolSetting... settings) {
        assertEquals(List.of(expected), ToolSetting.problems(List.of(settings)));
    }

    @Test
    void aKeyMustStartWithALetterAndUseOnlyLettersDigitsAndSeparators() {
        oneProblem("setting '1st': the key must match [A-Za-z][A-Za-z0-9_.-]*", ToolSetting.yesNo("1st", "First", true));
        oneProblem("setting 'a b': the key must match [A-Za-z][A-Za-z0-9_.-]*", ToolSetting.yesNo("a b", "A b", true));
    }

    @Test
    void aKeyIsUniqueWithinTheTool() {
        oneProblem("setting 'compact': is declared twice", ToolSetting.yesNo("compact", "Compact", true),
                ToolSetting.yesNo("compact", "Compact again", false));
    }

    @Test
    void aLabelIsNotEmpty() {
        oneProblem("setting 'compact': the label is empty", ToolSetting.yesNo("compact", " ", true));
    }

    @Test
    void aNumberHasARangeHoldingItsDefault() {
        oneProblem("setting 'depth': the range 9 to 1 is empty", ToolSetting.number("depth", "Depth", 3, 9, 1));
        oneProblem("setting 'depth': the default 12 is outside 1 to 9", ToolSetting.number("depth", "Depth", 12, 1, 9));
    }

    @Test
    void aChoiceHasChoicesHoldingItsDefault() {
        oneProblem("setting 'direction': declares no choices", ToolSetting.choice("direction", "Direction", "right"));
        oneProblem("setting 'direction': the default 'up' is not one of the choices", ToolSetting.choice("direction", "Direction", "up", "left", "right"));
    }
}
