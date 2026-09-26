package etalii.adp.core.diagram.properties;

import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.text.JTextComponent;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBFont;

import etalii.adp.core.diagram.ConnectionType;
import etalii.adp.core.diagram.ConnectionType.LabelDecl;
import etalii.adp.core.diagram.EditorKind;
import etalii.adp.core.diagram.ElementType;
import etalii.adp.core.diagram.LabelSlot;
import etalii.adp.core.diagram.PropertyDecl;
import etalii.adp.core.diagram.Verdict;
import etalii.adp.core.diagram.edit.RefusalFeedback;
import etalii.adp.core.diagram.model.Connection;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.Element;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.DiagramDesigner;
import etalii.adp.core.diagram.view.ElementMeasure;

/**
 * Edits one text of the canvas in place (research R15, FR-019): an element's editable text slot
 * or a connection's editable label, with a property that is not read-only. A text field opens over
 * the text, or a text area for a wrapping or multi-line one, starting from the property's value,
 * with the font scaled by the zoom. Enter commits a text field and Ctrl+Enter a text area; Escape
 * and leaving the editor cancel. A commit is {@code setProperty} through the designer's commands,
 * the same path as the property panel, unless the caller opened it with a commit of its own. The
 * editor cancels itself, applying nothing, when its item is gone after a re-read. At most one is
 * open per canvas.
 */
public final class InPlaceEditor {

    private static final String OPEN = "etalii.adp.diagram.inPlaceEditor";
    private static final int MIN_WIDTH = 80;

    /**
     * An editable text of an item.
     *
     * @param slot the element's text slot id, or the connection's label slot name
     * @param box where the text is drawn, in diagram coordinates
     */
    public record Target(Object key, String slot, String property, Rectangle2D box, boolean multiline, boolean wrap, Font font) {
    }

    private final DiagramDesigner designer;
    private final DiagramCanvas canvas;
    private final Target target;
    private final String initialText;
    private final JTextComponent editor;
    private final Consumer<String> onCommit;
    private final Runnable modelListener = this::modelChanged;
    private boolean finished;

    private InPlaceEditor(DiagramDesigner designer, Target target, String initialText, Consumer<String> commit) {
        this.designer = designer;
        this.canvas = designer.canvas();
        this.target = target;
        this.initialText = initialText;
        this.onCommit = commit;
        double zoom = canvas.zoom();
        Font font = target.font().deriveFont((float) (target.font().getSize2D() * zoom));
        Rectangle box = canvas.toCanvas(target.box());
        if (target.multiline()) {
            JBTextArea area = new JBTextArea(initialText);
            area.setFont(font);
            area.setLineWrap(target.wrap());
            area.setWrapStyleWord(true);
            area.setBorder(BorderFactory.createLineBorder(JBColor.border()));
            int width = Math.max(box.width, JBUIScale.scale(MIN_WIDTH));
            area.setSize(width, Short.MAX_VALUE);
            area.setBounds(box.x, box.y, width, Math.max(box.height, area.getPreferredSize().height));
            // Ctrl+Enter is also an IDE shortcut; one registered on the area itself comes first
            action(this::commit).registerCustomShortcutSet(CustomShortcutSet.fromString("control ENTER", "meta ENTER"), area);
            editor = area;
        } else {
            JBTextField field = new JBTextField(initialText);
            field.setFont(font);
            int width = Math.max(box.width, field.getPreferredSize().width + JBUIScale.scale(16));
            field.setBounds(box.x, box.y, Math.max(width, JBUIScale.scale(MIN_WIDTH)), Math.max(box.height, field.getPreferredSize().height));
            field.addActionListener(event -> commit());
            editor = field;
        }
        // Escape is also an IDE shortcut; one registered on the editor itself comes first
        action(this::cancel).registerCustomShortcutSet(CustomShortcutSet.fromString("ESCAPE"), editor);
        // and a key delivered to the editor directly, without the IDE's dispatcher, cancels too
        editor.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    e.consume();
                    cancel();
                }
            }
        });
        editor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (!e.isTemporary()) {
                    cancel();
                }
            }
        });
    }

    private static DumbAwareAction action(Runnable run) {
        return new DumbAwareAction() {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                run.run();
            }
        };
    }

    /** The editable text drawn at a diagram point: the topmost element's, then a connection's; {@code null} when the text there is not editable. */
    public static Target at(DiagramDesigner designer, Point2D point) {
        Diagram diagram = designer.diagram();
        if (diagram == null) {
            return null;
        }
        List<Object> elements = designer.elementKeys();
        for (int i = elements.size() - 1; i >= 0; i--) {
            Target found = textAt(designer, diagram, elements.get(i), point);
            if (found != null) {
                return found.property() == null ? null : found;
            }
        }
        List<Object> connections = designer.connectionKeys();
        for (int i = connections.size() - 1; i >= 0; i--) {
            Target found = textAt(designer, diagram, connections.get(i), point);
            if (found != null) {
                return found.property() == null ? null : found;
            }
        }
        return null;
    }

    /** The item's first editable text that is drawn, or {@code null}. */
    public static Target first(DiagramDesigner designer, Object key) {
        Diagram diagram = designer.diagram();
        if (diagram == null) {
            return null;
        }
        for (Target text : texts(designer, diagram, key)) {
            if (text.property() != null) {
                return text;
            }
        }
        return null;
    }

    /** The text of the item drawn at the point, editable or not (then its property is {@code null}), or {@code null}. */
    private static Target textAt(DiagramDesigner designer, Diagram diagram, Object key, Point2D point) {
        for (Target text : texts(designer, diagram, key)) {
            if (text.box().contains(point)) {
                return text;
            }
        }
        return null;
    }

    /** The item's drawn texts in declared order; a text that cannot be edited here has no property. */
    private static List<Target> texts(DiagramDesigner designer, Diagram diagram, Object key) {
        boolean editable = designer.isEditable();
        Element element = diagram.element(key);
        if (element != null) {
            ElementType type = designer.definition().elementType(element.type());
            if (type == null) {
                return List.of();
            }
            return type.texts().stream().map(slot -> {
                Rectangle2D box = designer.textBounds(key, slot.id());
                if (box == null) {
                    return null;
                }
                PropertyDecl decl = type.property(slot.property());
                boolean open = editable && slot.editable() && decl != null && !decl.readOnly()
                        && designer.definition().rules().canSetProperty(diagram, key, slot.property()).allowed();
                return new Target(key, slot.id(), open ? slot.property() : null, box, slot.wrap() || multiline(decl), slot.wrap(),
                        ElementMeasure.font(slot.style(), element));
            }).filter(t -> t != null).toList();
        }
        Connection connection = diagram.connection(key);
        ConnectionType type = connection == null ? null : designer.definition().connectionType(connection.type());
        if (type == null) {
            return List.of();
        }
        return type.labels().entrySet().stream().map(entry -> {
            LabelSlot slot = entry.getKey();
            LabelDecl label = entry.getValue();
            Rectangle2D box = designer.textBounds(key, slot.name());
            if (box == null) {
                return null;
            }
            PropertyDecl decl = type.property(label.property());
            boolean open = editable && label.editable() && decl != null && !decl.readOnly()
                    && designer.definition().rules().canSetProperty(diagram, key, label.property()).allowed();
            return new Target(key, slot.name(), open ? label.property() : null, box, multiline(decl), multiline(decl), JBFont.small());
        }).filter(t -> t != null).toList();
    }

    private static boolean multiline(PropertyDecl decl) {
        return decl != null && decl.editor() instanceof EditorKind.Multiline;
    }

    /** Open the editor on an editable target, closing one that is open; false when the target cannot be edited now. */
    public static boolean open(DiagramDesigner designer, Target target) {
        return open(designer, target, null);
    }

    /**
     * The same, with {@code commit} getting a changed text instead of the property being set; for
     * a designer's own edit of a text, such as FreeMind's rename of formatted text after asking.
     */
    public static boolean open(DiagramDesigner designer, Target target, Consumer<String> commit) {
        Diagram diagram = designer.diagram();
        if (target == null || target.property() == null || diagram == null || !designer.isEditable()) {
            return false;
        }
        Map<String, String> values = diagram.element(target.key()) != null ? diagram.element(target.key()).properties()
                : diagram.connection(target.key()) != null ? diagram.connection(target.key()).properties() : null;
        if (values == null) {
            return false;
        }
        DiagramCanvas canvas = designer.canvas();
        if (canvas.getClientProperty(OPEN) instanceof InPlaceEditor open) {
            open.cancel();
        }
        new InPlaceEditor(designer, target, values.getOrDefault(target.property(), ""), commit).show();
        return true;
    }

    /** The open editor's component on this canvas, or {@code null}. */
    public static JTextComponent component(DiagramCanvas canvas) {
        return canvas.getClientProperty(OPEN) instanceof InPlaceEditor open ? open.editor : null;
    }

    /** Whether an in-place editor is open on this canvas. */
    public static boolean isOpen(DiagramCanvas canvas) {
        return canvas.getClientProperty(OPEN) != null;
    }

    // simplified: the editor keeps its place and font if the zoom changes while it is open;
    // if that binds, cancel it from the designer's zoom change as it is cancelled on a re-read

    private void show() {
        canvas.putClientProperty(OPEN, this);
        canvas.add(editor);
        designer.addModelListener(modelListener);
        canvas.revalidate();
        canvas.repaint();
        canvas.scrollRectToVisible(editor.getBounds());
        editor.selectAll();
        editor.requestFocusInWindow();
    }

    /** The item went away (an undo, a change in the text): nothing is left to edit. */
    private void modelChanged() {
        Diagram diagram = designer.diagram();
        if (diagram == null || diagram.element(target.key()) == null && diagram.connection(target.key()) == null) {
            cancel();
        }
    }

    private void commit() {
        if (finished) {
            return;
        }
        String text = editor.getText();
        close();
        if (text.equals(initialText)) {
            return;
        }
        Diagram diagram = designer.diagram();
        if (diagram == null || diagram.element(target.key()) == null && diagram.connection(target.key()) == null) {
            return;
        }
        if (onCommit != null) {
            onCommit.accept(text);
            return;
        }
        Verdict verdict = designer.commands().setProperty(List.of(target.key()), target.property(), text);
        RefusalFeedback feedback = RefusalFeedback.of(canvas);
        if (!verdict.allowed() && feedback != null) {
            feedback.balloon(new Point2D.Double(target.box().getCenterX(), target.box().getCenterY()), verdict.reason());
        }
    }

    private void cancel() {
        if (!finished) {
            close();
        }
    }

    private void close() {
        finished = true;
        designer.removeModelListener(modelListener);
        canvas.remove(editor);
        if (canvas.getClientProperty(OPEN) == this) {
            canvas.putClientProperty(OPEN, null);
        }
        canvas.revalidate();
        canvas.repaint();
        canvas.requestFocusInWindow();
    }
}
