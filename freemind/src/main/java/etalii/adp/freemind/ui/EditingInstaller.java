package etalii.adp.freemind.ui;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.SwingUtilities;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;

import etalii.adp.core.AdpEditorProvider;
import etalii.adp.freemind.model.NodeKey;

/**
 * Adds the mouse gestures that edit (drag-and-drop move, double-click rename) to each FreeMind
 * designer the IDE opens, and takes them off when the designer is disposed. The canvas itself
 * stays a viewer; this is how User Story 2 adds editing to it without changing it.
 */
public final class EditingInstaller implements FileEditorManagerListener {

    private static final String ATTACHED = "etalii.adp.freemind.EditingInstaller";

    @Override
    public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
        for (FileEditor editor : source.getAllEditors(file)) {
            if (editor instanceof AdpEditorProvider.Composite composite && composite.designer() instanceof MindMapDesigner designer) {
                attach(designer);
            }
        }
    }

    /** Whether the editing gestures are on this canvas. */
    public static boolean isAttached(MindMapCanvas canvas) {
        return canvas.getClientProperty(ATTACHED) != null;
    }

    static void attach(MindMapDesigner designer) {
        MindMapCanvas canvas = designer.canvas();
        if (canvas == null || isAttached(canvas)) {
            return;
        }
        DragMove drag = new DragMove(designer);
        DoubleClickRename rename = new DoubleClickRename(designer);
        drag.attach();
        canvas.addMouseListener(rename);
        canvas.putClientProperty(ATTACHED, Boolean.TRUE);
        Disposer.register(designer, () -> {
            drag.detach();
            canvas.removeMouseListener(rename);
            canvas.putClientProperty(ATTACHED, null);
        });
    }

    /** A double-click on a node opens its in-place editor, as F2 does. */
    private static final class DoubleClickRename extends MouseAdapter {

        private final MindMapDesigner designer;

        DoubleClickRename(MindMapDesigner designer) {
            this.designer = designer;
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 2) {
                return;
            }
            NodeKey key = designer.canvas().keyAt(e.getPoint());
            if (key != null && designer.isEditable()) {
                InPlaceRename.open(designer, key);
            }
        }
    }
}
