package etalii.adp.core;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.testing.DesignerDriver;

/** The Structure view and the designer stay in step both ways, for a non-text editor (research R8). */
@RunWith(JUnit4.class)
public class StructureSyncTest extends FileEditorManagerTestCase {

    private static final String TEXT = FakeFormat.HEADER + "alpha\nbeta\ngamma\n";

    @Override
    public void setUp() {
        super.setUp();
        FakeFormat.register(getTestRootDisposable());
    }

    @Test
    public void theCompositeStructureViewIsTheDesigners() {
        try (var d = DesignerDriver.openText(myFixture, "items.txt", TEXT)) {
            assertInstanceOf(d.composite().getStructureViewBuilder(), AdpStructureView.class);
            StructureViewTreeElement root = d.structure().getRoot();
            assertEquals("Items", root.getPresentation().getPresentableText());
            assertEquals(List.of("alpha", "beta", "gamma"),
                    List.of(root.getChildren()).stream().map(e -> e.getPresentation().getPresentableText()).toList());
        }
    }

    @Test
    public void choosingAnElementRevealsAndSelectsTheItem() {
        try (var d = DesignerDriver.openText(myFixture, "items.txt", TEXT)) {
            var element = (StructureViewTreeElement) d.structure().getRoot().getChildren()[2];

            element.navigate(true);

            assertEquals(2, ((FakeFormat.Designer) d.designer()).revealed);
            assertEquals(List.of(2), d.selectedKeys());
        }
    }

    @Test
    public void theDesignersSelectionIsTheCurrentElement() {
        try (var d = DesignerDriver.openText(myFixture, "items.txt", TEXT)) {
            var model = d.structure();
            AtomicInteger moves = new AtomicInteger();
            model.addEditorPositionListener(moves::incrementAndGet);

            d.select(1);

            assertEquals(1, model.getCurrentEditorElement());
            assertEquals(1, moves.get());
        }
    }

    @Test
    public void aDocumentChangeRebuildsTheTree() {
        try (var d = DesignerDriver.openText(myFixture, "items.txt", TEXT)) {
            var model = d.structure();
            AtomicInteger changes = new AtomicInteger();
            model.addModelListener(changes::incrementAndGet);

            d.editText(t -> t + "delta\n");

            assertTrue(changes.get() >= 1);
            assertEquals(4, model.getRoot().getChildren().length);
        }
    }
}
