package etalii.adp.core;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.Icon;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.ide.structureView.FileEditorPositionListener;
import com.intellij.ide.structureView.ModelListener;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.treeView.smartTree.Filter;
import com.intellij.ide.util.treeView.smartTree.Grouper;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.Editor;

/**
 * The Structure view of a tool's model, kept in step with the tool both ways (research R8):
 * choosing an element reveals and selects the item in the tool, and the tool's selection is
 * the model's current element. A format says what the tree is; element values are the format's keys.
 */
public abstract class AdpStructureView extends TreeBasedStructureViewBuilder {

    private final AdpToolFileEditor<?> tool;

    protected AdpStructureView(AdpToolFileEditor<?> tool) {
        this.tool = tool;
    }

    /** The root item's key, or {@code null} while the tool shows no model. */
    protected abstract Object rootKey();

    /** The text shown for an item. */
    protected abstract String text(Object key);

    /** The children's keys, in document order. */
    protected abstract List<?> children(Object key);

    @Override
    public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new Model();
    }

    @Override
    public boolean isRootNodeShown() {
        return true;
    }

    /** One tree over the tool's latest model. */
    public final class Model implements StructureViewModel {

        private final List<ModelListener> modelListeners = new CopyOnWriteArrayList<>();
        private final List<FileEditorPositionListener> positionListeners = new CopyOnWriteArrayList<>();
        private final Runnable onModel = () -> modelListeners.forEach(ModelListener::onModelChanged);
        private final Runnable onSelection = () -> positionListeners.forEach(FileEditorPositionListener::onCurrentElementChanged);

        Model() {
            tool.addModelListener(onModel);
            tool.viewState().addSelectionListener(onSelection);
        }

        @Override
        public @NotNull StructureViewTreeElement getRoot() {
            return new Element(rootKey());
        }

        /** The key of the tool's first selected item, or {@code null}. */
        @Override
        public @Nullable Object getCurrentEditorElement() {
            List<Object> selection = tool.selection();
            return selection.isEmpty() ? null : selection.get(0);
        }

        @Override
        public void addEditorPositionListener(@NotNull FileEditorPositionListener listener) {
            positionListeners.add(listener);
        }

        @Override
        public void removeEditorPositionListener(@NotNull FileEditorPositionListener listener) {
            positionListeners.remove(listener);
        }

        @Override
        public void addModelListener(@NotNull ModelListener listener) {
            modelListeners.add(listener);
        }

        @Override
        public void removeModelListener(@NotNull ModelListener listener) {
            modelListeners.remove(listener);
        }

        @Override
        public Grouper @NotNull [] getGroupers() {
            return Grouper.EMPTY_ARRAY;
        }

        @Override
        public Sorter @NotNull [] getSorters() {
            return Sorter.EMPTY_ARRAY;
        }

        @Override
        public Filter @NotNull [] getFilters() {
            return Filter.EMPTY_ARRAY;
        }

        @Override
        public boolean shouldEnterElement(Object element) {
            return true;
        }

        @Override
        public void dispose() {
            tool.removeModelListener(onModel);
            tool.viewState().removeSelectionListener(onSelection);
        }
    }

    /** One item: its value is the format's key; navigating reveals it in the tool. */
    public final class Element implements StructureViewTreeElement, ItemPresentation {

        private final Object key;

        Element(Object key) {
            this.key = key;
        }

        @Override
        public Object getValue() {
            return key;
        }

        @Override
        public @NotNull ItemPresentation getPresentation() {
            return this;
        }

        @Override
        public String getPresentableText() {
            return key == null ? "" : text(key);
        }

        @Override
        public @Nullable Icon getIcon(boolean unused) {
            return null;
        }

        @Override
        public StructureViewTreeElement @NotNull [] getChildren() {
            return key == null ? EMPTY_ARRAY : children(key).stream().map(Element::new).toArray(StructureViewTreeElement[]::new);
        }

        @Override
        public void navigate(boolean requestFocus) {
            if (key != null) {
                tool.reveal(key);
            }
        }

        @Override
        public boolean canNavigate() {
            return key != null;
        }

        @Override
        public boolean canNavigateToSource() {
            return key != null;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof AdpStructureView.Element element && Objects.equals(key, element.key);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(key);
        }
    }
}
