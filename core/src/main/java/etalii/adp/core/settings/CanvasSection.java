package etalii.adp.core.settings;

import java.awt.event.ActionListener;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JList;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;

/**
 * The canvas options every diagram designer follows (US3, FR-011, FR-012): Show grid, Snap to
 * grid and the zoom a diagram opens at. Under an option that a designer's definition fixes, the
 * page names the designers that do not follow it. "Reset to defaults" resets the page only;
 * nothing is stored until Apply.
 */
public final class CanvasSection implements SettingsSection {

    static final String TITLE = "Canvas";
    static final String OPENING_ZOOM = "Opening zoom";
    static final String RESET = "Reset to defaults";
    static final List<Integer> ZOOMS = List.of(50, 75, 100, 125, 150, 200);

    private final Map<CanvasOption, JBCheckBox> boxes = new EnumMap<>(CanvasOption.class);
    private final Map<CanvasOption, JBLabel> unfollowed = new EnumMap<>(CanvasOption.class);
    private DefaultComboBoxModel<Integer> zooms;
    private ComboBox<Integer> zoom;

    @Override
    public int order() {
        return 20;
    }

    @Override
    public JComponent createComponent() {
        FormBuilder form = FormBuilder.createFormBuilder().addComponent(new TitledSeparator(TITLE));
        for (CanvasOption option : CanvasOption.values()) {
            JBCheckBox box = new JBCheckBox(option.label());
            JBLabel names = new JBLabel();
            names.setComponentStyle(UIUtil.ComponentStyle.SMALL);
            names.setForeground(UIUtil.getContextHelpForeground());
            boxes.put(option, box);
            unfollowed.put(option, names);
            form.addComponent(box).addComponentToRightColumn(names, 0);
        }
        zooms = new DefaultComboBoxModel<>(ZOOMS.toArray(Integer[]::new));
        zoom = new ComboBox<>(zooms);
        zoom.setRenderer(new SimpleListCellRenderer<>() {
            @Override
            public void customize(@NotNull JList<? extends Integer> list, Integer percent, int index, boolean selected, boolean hasFocus) {
                setText(percent == null ? "" : percent + " %");
            }
        });
        form.addLabeledComponent(OPENING_ZOOM + ":", zoom);
        form.addComponent(new ActionLink(RESET, (ActionListener) e -> show(CanvasOptions.DEFAULTS)));
        return form.getPanel();
    }

    @Override
    public boolean isModified() {
        return zoom != null && !shown().equals(AdpSettings.getInstance().canvas());
    }

    @Override
    public void apply() {
        AdpSettings.getInstance().setCanvas(shown());
    }

    @Override
    public void reset() {
        if (zoom == null) {
            return;
        }
        show(AdpSettings.getInstance().canvas());
        List<DesignerInfo> designers = AdpDesigners.all();
        for (CanvasOption option : CanvasOption.values()) {
            String names = designers.stream().filter(d -> d.unfollowed().contains(option)).map(DesignerInfo::name).collect(Collectors.joining(", "));
            JBLabel label = unfollowed.get(option);
            label.setText(names.isEmpty() ? "" : "Not followed by: " + names);
            label.setVisible(!names.isEmpty());
        }
    }

    @Override
    public List<String> searchableLabels() {
        return List.of(TITLE, CanvasOption.SHOW_GRID.label(), CanvasOption.SNAP_TO_GRID.label(), OPENING_ZOOM, RESET);
    }

    @Override
    public void disposeUIResources() {
        boxes.clear();
        unfollowed.clear();
        zooms = null;
        zoom = null;
    }

    private void show(CanvasOptions options) {
        boxes.get(CanvasOption.SHOW_GRID).setSelected(options.showGrid());
        boxes.get(CanvasOption.SNAP_TO_GRID).setSelected(options.snapToGrid());
        int percent = (int) Math.round(options.openingZoom() * 100);
        if (zooms.getIndexOf(percent) < 0) {
            // a stored zoom the list does not offer, such as one a newer version wrote, is shown as it is
            zooms.addElement(percent);
        }
        zoom.setSelectedItem(percent);
    }

    private CanvasOptions shown() {
        Integer percent = (Integer) zoom.getSelectedItem();
        return new CanvasOptions(boxes.get(CanvasOption.SHOW_GRID).isSelected(), boxes.get(CanvasOption.SNAP_TO_GRID).isSelected(),
                (percent == null ? 100 : percent) / 100.0);
    }
}
