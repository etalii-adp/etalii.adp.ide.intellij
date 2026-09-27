package etalii.adp.freemind.ui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import etalii.adp.core.diagram.ConnectionType;
import etalii.adp.core.diagram.DiagramDefinition;
import etalii.adp.core.diagram.ElementType;
import etalii.adp.core.diagram.LineStyle;
import etalii.adp.core.diagram.Outline;
import etalii.adp.core.diagram.PropertyDecl;
import etalii.adp.core.settings.CanvasOption;

/**
 * T087: the FreeMind definition holds together (FR-002) and declares what research R19 lists.
 * Spec 004 T037 (FR-012): its positions come from its layout, so it keeps the grid hidden and
 * snapping off, and the ADP page lists it under both options.
 */
class FreeMindDefinitionTest {

    @Test
    void theDefinitionBuildsWithoutProblems() {
        assertDoesNotThrow(() -> FreeMindDefinition.builder().build());
        assertNotNull(FreeMindDefinition.DEFINITION);
    }

    @Test
    void nodesAreLaidOutAndDroppedOntoOneAnother() {
        DiagramDefinition definition = FreeMindDefinition.DEFINITION;
        assertNotNull(definition.layout());
        assertEquals(List.of(FreeMindDefinition.ROOT, FreeMindDefinition.NODE, FreeMindDefinition.BUBBLE), List.copyOf(definition.elementTypes().keySet()));
        assertEquals(Outline.ELLIPSE, definition.elementType(FreeMindDefinition.ROOT).outline());
        assertEquals(Outline.ROUNDED_RECTANGLE, definition.elementType(FreeMindDefinition.BUBBLE).outline());
        for (ElementType type : definition.elementTypes().values()) {
            assertFalse(type.movable(), type.id());
            assertEquals(!type.id().equals(FreeMindDefinition.ROOT), type.droppableOnto(), type.id());
            assertEquals(List.of(FreeMindMapping.TEXT, FreeMindMapping.ICONS, FreeMindMapping.INDICATORS, FreeMindMapping.FOLD_MARKER),
                    type.texts().stream().map(slot -> slot.id()).toList(), type.id());
        }
        assertEquals(List.of(FreeMindDefinition.NODE, FreeMindDefinition.ARROW_LINK), definition.toolbox());
    }

    @Test
    void theNodePropertiesAreThePanelsRows() {
        ElementType node = FreeMindDefinition.DEFINITION.elementType(FreeMindDefinition.NODE);
        List<String> editable = node.properties().stream().filter(p -> !p.readOnly()).map(PropertyDecl::id).toList();
        assertEquals(List.of(FreeMindMapping.TEXT, FreeMindMapping.FOLDED, FreeMindMapping.LINK, FreeMindMapping.COLOR, FreeMindMapping.BACKGROUND_COLOR),
                editable);
        assertTrue(node.property(FreeMindMapping.ID).readOnly());
        assertTrue(node.text(FreeMindMapping.TEXT).editable());
    }

    @Test
    void branchesComeFromTheTreeAndArrowLinksAreDrawn() {
        ConnectionType branch = FreeMindDefinition.DEFINITION.connectionType(FreeMindDefinition.BRANCH);
        ConnectionType link = FreeMindDefinition.DEFINITION.connectionType(FreeMindDefinition.ARROW_LINK);
        assertFalse(branch.userConnectable());
        assertTrue(link.userConnectable());
        assertEquals(LineStyle.CURVED, branch.line());
        assertEquals(LineStyle.CURVED, link.line());
    }

    @Test
    void theGridIsHiddenAndSnappingOffWhateverTheUserChose() {
        assertEquals(Map.of(CanvasOption.SHOW_GRID, false, CanvasOption.SNAP_TO_GRID, false), FreeMindDefinition.DEFINITION.view().fixed());
        assertEquals(Set.of(CanvasOption.SHOW_GRID, CanvasOption.SNAP_TO_GRID), new MindMapEditorProvider().fixedOptions());
    }
}
