package etalii.adp.core;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.eclipse.draw2d.FreeformLayer;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.Label;
import org.eclipse.draw2d.ToolbarLayout;
import org.eclipse.gef.EditPartFactory;
import org.eclipse.gef.editparts.AbstractGraphicalEditPart;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;

/**
 * The smallest possible designer, proving the framework knows nothing about FreeMind: one node per
 * line, and a line holding only {@code !} cannot be shown. Registered for {@code *.adptest} in
 * {@code fragment.xml}.
 */
public class LineListEditor extends AdpDesignerEditor<List<String>> {

    public static final String ID = "etalii.adp.core.tests.lineListEditor";

    int parseCount;
    private final Lines lines = new Lines();

    @Override
    protected List<String> parse(IDocument document) throws FormatProblem {
        parseCount++;
        List<String> result = new ArrayList<>();
        try {
            for (int line = 0; line < document.getNumberOfLines(); line++) {
                int offset = document.getLineOffset(line);
                String delimiter = document.getLineDelimiter(line);
                String text = document.get(offset, document.getLineLength(line) - (delimiter == null ? 0 : delimiter.length()));
                if (text.equals("!")) {
                    throw new FormatProblem("A line holds only '!'", offset);
                }
                if (delimiter != null || !text.isEmpty()) {
                    result.add(text);
                }
            }
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    @Override
    protected EditPartFactory createEditPartFactory() {
        return (context, model) -> {
            AbstractGraphicalEditPart part = model instanceof Lines ? new LinesPart() : new LinePart();
            part.setModel(model);
            return part;
        };
    }

    @Override
    protected Object contentsFor(List<String> model) {
        lines.value = model;
        return lines;
    }

    @Override
    protected String visualContextId() {
        return "etalii.adp.core.tests.context";
    }

    private static final class Lines {
        List<String> value = List.of();
    }

    private static final class LinesPart extends AbstractGraphicalEditPart {
        @Override
        protected IFigure createFigure() {
            FreeformLayer layer = new FreeformLayer();
            layer.setLayoutManager(new ToolbarLayout());
            return layer;
        }

        @Override
        protected List<Integer> getModelChildren() {
            return IntStream.range(0, ((Lines) getModel()).value.size()).boxed().toList();
        }

        @Override
        protected void createEditPolicies() {
        }
    }

    private static final class LinePart extends AbstractGraphicalEditPart {
        @Override
        protected IFigure createFigure() {
            return new Label();
        }

        @Override
        protected void refreshVisuals() {
            Lines lines = (Lines) getParent().getModel();
            ((Label) getFigure()).setText(lines.value.get((Integer) getModel()));
        }

        @Override
        protected void createEditPolicies() {
        }
    }
}
