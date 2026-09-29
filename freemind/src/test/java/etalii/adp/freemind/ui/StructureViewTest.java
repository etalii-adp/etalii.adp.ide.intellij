package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.example;
import static etalii.adp.freemind.FreeMindAsserts.key;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.testing.ToolDriver;

/** FR-015, US3-AS2: the Structure view lists the node tree, with selection linked both ways. */
@RunWith(JUnit4.class)
public class StructureViewTest extends FileEditorManagerTestCase {

    private static final String FOLDED_MAP = """
            <map version="1.0.1">
            <node CREATED="1" ID="ID_1" MODIFIED="1" TEXT="Root">
            <node CREATED="2" FOLDED="true" ID="ID_2" MODIFIED="2" POSITION="right" TEXT="Folded">
            <icon BUILTIN="idea"/>
            <node CREATED="3" ID="ID_3" MODIFIED="3" TEXT="Hidden">
            <node CREATED="4" ID="ID_4" MODIFIED="4" TEXT="Deep"/>
            </node>
            </node>
            <node CREATED="5" ID="ID_5" MODIFIED="5" POSITION="left" TEXT="Open"/>
            </node>
            </map>
            """;

    @Test
    public void listsTheNodeTreeOfALargeMap() {
        try (var d = ToolDriver.open(myFixture, example("freeplane-large-map.mm"))) {
            MindMap map = (MindMap) d.tool().model();
            StructureViewTreeElement root = d.structure().getRoot();

            assertEquals(map.root().key(), root.getValue());
            assertEquals(map.root().text(), root.getPresentation().getPresentableText());
            assertEquals(map.nodesByKey().size(), all(root).size());
        }
    }

    @Test
    public void showsTheFirstIconBeforeTheText() {
        try (var d = ToolDriver.openText(myFixture, "structure.mm", FOLDED_MAP)) {
            TreeElement folded = d.structure().getRoot().getChildren()[0];

            assertEquals(FreeMindIcons.display("idea") + " Folded", folded.getPresentation().getPresentableText());
        }
    }

    @Test
    public void followsEdits() {
        try (var d = ToolDriver.openText(myFixture, "structure.mm", FOLDED_MAP)) {
            StructureViewModel structure = d.structure();
            MindMapEdits.Edit add = MindMapEdits.addChild((MindMap) d.tool().model(), key("ID_5"), "Added");

            d.tool().execute(add.label(), add.changes());
            d.settle();

            assertTrue(values(all(structure.getRoot())).contains(add.created()));
        }
    }

    @Test
    public void selectingInTheOutlineRevealsTheNodeWithoutAnEdit() {
        try (var d = ToolDriver.openText(myFixture, "structure.mm", FOLDED_MAP)) {
            assertNull("hidden inside a folded branch", d.viewOf(key("ID_4")));
            StructureViewTreeElement deep = find(d.structure().getRoot(), key("ID_4"));

            deep.navigate(true);
            d.settle();

            assertNotNull(d.viewOf(key("ID_4")));
            assertEquals(List.of(key("ID_4")), d.selectedKeys());
            assertFalse(d.isModified());
            assertEquals(FOLDED_MAP, d.text());
        }
    }

    @Test
    public void selectingInTheToolSelectsInTheOutline() {
        try (var d = ToolDriver.openText(myFixture, "structure.mm", FOLDED_MAP)) {
            StructureViewModel structure = d.structure();

            d.select(key("ID_5"));

            assertEquals(key("ID_5"), structure.getCurrentEditorElement());
        }
    }

    private static List<StructureViewTreeElement> all(StructureViewTreeElement element) {
        List<StructureViewTreeElement> elements = new ArrayList<>();
        elements.add(element);
        for (TreeElement child : element.getChildren()) {
            elements.addAll(all((StructureViewTreeElement) child));
        }
        return elements;
    }

    private static List<Object> values(List<StructureViewTreeElement> elements) {
        return elements.stream().map(StructureViewTreeElement::getValue).toList();
    }

    private static StructureViewTreeElement find(StructureViewTreeElement root, Object key) {
        return all(root).stream().filter(e -> key.equals(e.getValue())).findFirst().orElseThrow();
    }
}
