package etalii.adp.core.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import etalii.adp.core.settings.AdpSettings;
import etalii.adp.core.settings.CanvasOption;
import etalii.adp.core.settings.CanvasOptions;

/**
 * T013 (FR-012, research R8): the grid is a spacing, 10 by default; a definition fixes a canvas
 * option, and the effective value is the fixed one when present and the user's otherwise.
 */
class ViewOptionsTest {

    private static ViewOptions view(Consumer<ViewOptions.Builder> options) {
        return DiagramDefinition.builder("mini").element("box", e -> {
        }).view(options).build().view();
    }

    @Test
    void theGridSpacingDefaultsToTen() {
        ViewOptions defaults = view(v -> {
        });
        assertEquals(10, defaults.grid());
        assertEquals(Map.of(), defaults.fixed());
        assertEquals(25, view(v -> v.grid(25)).grid());
    }

    @Test
    void aDefinitionFixesAValuePerOption() {
        ViewOptions fixed = view(v -> v.fix(CanvasOption.SHOW_GRID, false).fix(CanvasOption.SNAP_TO_GRID, true));
        assertEquals(Map.of(CanvasOption.SHOW_GRID, false, CanvasOption.SNAP_TO_GRID, true), fixed.fixed());
    }

    @Test
    void theEffectiveValueIsTheFixedOneWhenPresentAndTheUsersOtherwise() {
        AdpSettings settings = new AdpSettings();
        settings.setCanvas(new CanvasOptions(true, false, 1.0));
        ViewOptions free = view(v -> {
        });
        ViewOptions fixed = view(v -> v.fix(CanvasOption.SHOW_GRID, false).fix(CanvasOption.SNAP_TO_GRID, true));

        assertTrue(settings.effective(CanvasOption.SHOW_GRID, free));
        assertFalse(settings.effective(CanvasOption.SNAP_TO_GRID, free));
        assertFalse(settings.effective(CanvasOption.SHOW_GRID, fixed));
        assertTrue(settings.effective(CanvasOption.SNAP_TO_GRID, fixed));
    }
}
