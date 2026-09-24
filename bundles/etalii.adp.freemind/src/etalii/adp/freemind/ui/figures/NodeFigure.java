package etalii.adp.freemind.ui.figures;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.eclipse.draw2d.ColorConstants;
import org.eclipse.draw2d.Clickable;
import org.eclipse.draw2d.Figure;
import org.eclipse.draw2d.Graphics;
import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.Label;
import org.eclipse.draw2d.LineBorder;
import org.eclipse.draw2d.MarginBorder;
import org.eclipse.draw2d.PositionConstants;
import org.eclipse.draw2d.ToolbarLayout;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.jface.resource.ColorDescriptor;
import org.eclipse.jface.resource.FontDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;

import etalii.adp.freemind.model.FontSpec;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.ui.FreeMindIcons;

/**
 * One node as FreeMind shows it (FR-014, FR-018): icons, text in the node's font and colours, a
 * link indicator that follows the link when clicked, a note indicator whose tooltip is the note,
 * and a marker when the branch is folded. Only rebuilt when what it shows changes.
 */
public class NodeFigure extends Figure {

    static final String LINK_GLYPH = "\ud83d\udd17";
    static final String NOTE_GLYPH = "\ud83d\udcdd";
    static final String FOLDED_GLYPH = "\u2295";

    /** Everything drawn, to skip rebuilding when a re-parse changed nothing here. */
    private record Look(String text, List<String> icons, RGB color, RGB background, FontSpec font, String link, String note,
            boolean folded, boolean root) {
    }

    private final Runnable followLink;
    private final List<Label> icons = new ArrayList<>();
    private final Label text = new Label();
    private Look look;
    private Color fill;
    private Clickable linkIndicator;
    private Label noteIndicator;
    private Label foldedMarker;
    private boolean root;

    /** {@code followLink} runs when the link indicator is clicked. */
    public NodeFigure(Runnable followLink) {
        this.followLink = followLink;
        ToolbarLayout layout = new ToolbarLayout(true);
        layout.setSpacing(3);
        layout.setMinorAlignment(ToolbarLayout.ALIGN_CENTER);
        setLayoutManager(layout);
        setBorder(new MarginBorder(3, 6, 3, 6));
        text.setLabelAlignment(PositionConstants.LEFT);
    }

    /** Show the node. Resources come from {@code resources}, which owns and disposes them. */
    public void update(MapNode node, boolean shownFolded, boolean isRoot, ResourceManager resources) {
        Look next = new Look(node.text(), node.icons(), node.color(), node.backgroundColor(), node.font(), node.link(), node.note(),
                shownFolded, isRoot);
        if (next.equals(look)) {
            return;
        }
        look = next;
        root = isRoot;
        removeAll();
        icons.clear();
        for (String icon : node.icons()) {
            Label label = new Label(FreeMindIcons.display(icon));
            if (FreeMindIcons.glyph(icon) == null) {
                label.setBorder(new LineBorder(ColorConstants.gray, 1));
                label.setToolTip(new Label(icon));
            }
            icons.add(label);
            add(label);
        }

        text.setText(node.text() == null ? "" : node.text());
        text.setForegroundColor(node.color() == null ? null : resources.create(ColorDescriptor.createFrom(node.color())));
        text.setFont(font(node.font(), resources));
        add(text);
        fill = node.backgroundColor() == null ? null : resources.create(ColorDescriptor.createFrom(node.backgroundColor()));

        linkIndicator = null;
        if (node.link() != null) {
            linkIndicator = new Clickable(new Label(LINK_GLYPH));
            linkIndicator.setRequestFocusEnabled(false);
            linkIndicator.setToolTip(new Label(node.link()));
            linkIndicator.addActionListener(event -> followLink.run());
            add(linkIndicator);
        }
        noteIndicator = null;
        if (node.note() != null) {
            noteIndicator = new Label(NOTE_GLYPH);
            noteIndicator.setToolTip(new Label(node.note()));
            add(noteIndicator);
        }
        foldedMarker = null;
        if (shownFolded) {
            foldedMarker = new Label(FOLDED_GLYPH);
            foldedMarker.setToolTip(new Label("Folded"));
            add(foldedMarker);
        }
        revalidate();
        repaint();
    }

    private static Font font(FontSpec spec, ResourceManager resources) {
        if (spec == null) {
            return null;
        }
        FontData base = JFaceResources.getDefaultFont().getFontData()[0];
        int style = (spec.bold() ? SWT.BOLD : SWT.NORMAL) | (spec.italic() ? SWT.ITALIC : SWT.NORMAL);
        String name = Objects.requireNonNullElse(spec.name(), base.getName());
        int height = spec.size() == null ? base.getHeight() : spec.size();
        return resources.create(FontDescriptor.createFrom(name, height, style));
    }

    @Override
    protected void paintFigure(Graphics graphics) {
        Rectangle area = getBounds().getCopy().shrink(1, 1);
        if (fill != null) {
            graphics.setBackgroundColor(fill);
            graphics.fillRoundRectangle(area, 8, 8);
        }
        if (root) {
            graphics.setForegroundColor(ColorConstants.gray);
            graphics.drawRoundRectangle(area, 12, 12);
        }
    }

    // What is shown, for tests and accessibility

    public String text() {
        return text.getText();
    }

    /** The icons in order: a glyph for a built-in icon, the name for any other. */
    public List<String> iconTexts() {
        return icons.stream().map(Label::getText).toList();
    }

    public Color textColor() {
        return text.getForegroundColor();
    }

    /** The node's background colour, or {@code null} when it has none. */
    public Color fillColor() {
        return fill;
    }

    public Font textFont() {
        return text.getFont();
    }

    /** The link indicator, or {@code null} when the node has no link. */
    public Clickable linkIndicator() {
        return linkIndicator;
    }

    /** The note indicator, whose tooltip is the note, or {@code null}. */
    public IFigure noteIndicator() {
        return noteIndicator;
    }

    public boolean foldedMarkerShown() {
        return foldedMarker != null;
    }
}
