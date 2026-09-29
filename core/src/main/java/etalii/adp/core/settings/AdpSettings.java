package etalii.adp.core.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.serialization.SerializationException;
import com.intellij.util.xmlb.XmlSerializer;

import etalii.adp.core.diagram.ViewOptions;

/**
 * Every ADP setting, for the user across all projects (FR-005, research R2), stored in
 * {@code adp.xml} so the IDE's export, import and Settings Sync carry it. Values are stored as
 * written and checked when read: a value that does not read falls back to its default alone, and
 * the user is told once per session (FR-018, research R3). Stored fields this version does not
 * know are written back unchanged. Written only by the ADP page.
 */
@State(name = "AdpSettings", storages = @Storage("adp.xml"), category = SettingsCategory.PLUGINS)
public final class AdpSettings implements PersistentStateComponent<Element> {

    /** The notification group of the ADP notices. */
    public static final String NOTIFICATION_GROUP = "ADP";

    private static final int MIN_ZOOM = 25;
    private static final int MAX_ZOOM = 400;

    /** The stored fields and what the notice calls them. */
    private static final Map<String, String> FIELDS = Map.of("offTools", "Tools turned off", "showGrid", CanvasOption.SHOW_GRID.label(),
            "snapToGrid", CanvasOption.SNAP_TO_GRID.label(), "openingZoom", "Opening zoom", "toolSettings", "Tool settings");

    /**
     * Stored field names and tool ids of earlier versions, read when the new ones are absent and
     * never written again (spec 002 of etalii.adp, research R5).
     */
    private static final Map<String, String> OLD_FIELDS = Map.of("offDesigners", "offTools", "designerSettings", "toolSettings");
    private static final Map<String, String> OLD_IDS = Map.of("etalii.adp.freemind.editor", "etalii.adp.freemind");

    /** The stored bean, as the platform's serializer writes it. Text fields keep what was stored until the page applies. */
    public static final class State {
        public Set<String> offTools = new TreeSet<>();
        public String showGrid = "false";
        public String snapToGrid = "true";
        public String openingZoom = "100";
        /** Keyed by {@code <tool id>/<setting key>}. */
        public Map<String, String> toolSettings = new TreeMap<>();
    }

    private State state = new State();
    private final List<Element> unknown = new ArrayList<>();
    private boolean told;

    public static AdpSettings getInstance() {
        return ApplicationManager.getApplication().getService(AdpSettings.class);
    }

    @Override
    public synchronized Element getState() {
        Element element = XmlSerializer.serialize(state);
        for (Element field : unknown) {
            element.addContent(field.clone());
        }
        return element;
    }

    @Override
    public synchronized void loadState(@NotNull Element stored) {
        State read = new State();
        unknown.clear();
        List<String> fellBack = new ArrayList<>();
        Set<String> present = new TreeSet<>();
        for (Element field : stored.getChildren()) {
            if (field.getAttributeValue("name") != null) {
                present.add(field.getAttributeValue("name"));
            }
        }
        for (Element field : stored.getChildren()) {
            String name = field.getAttributeValue("name");
            if ("option".equals(field.getName()) && OLD_FIELDS.containsKey(name)) {
                if (present.contains(OLD_FIELDS.get(name))) {
                    continue;
                }
                name = OLD_FIELDS.get(name);
                field = field.clone().setAttribute("name", name);
            }
            if (!"option".equals(field.getName()) || name == null || !FIELDS.containsKey(name)) {
                unknown.add(field.clone());
                continue;
            }
            // one field at a time, so a field the serializer cannot read falls back alone
            Element one = new Element(stored.getName());
            one.addContent(field.clone());
            try {
                copy(name, XmlSerializer.deserialize(one, State.class), read);
            } catch (SerializationException | IllegalStateException | ClassCastException e) {
                fellBack.add(FIELDS.get(name));
            }
        }
        read.offTools = read.offTools.stream().map(AdpSettings::newId).collect(Collectors.toCollection(TreeSet::new));
        Map<String, String> settings = new TreeMap<>();
        read.toolSettings.forEach((key, value) -> {
            int slash = key.indexOf('/');
            String renamed = slash < 0 ? key : newId(key.substring(0, slash)) + key.substring(slash);
            if (renamed.equals(key) || !read.toolSettings.containsKey(renamed)) {
                settings.put(renamed, value);
            }
        });
        read.toolSettings = settings;
        state = read;
        tell(fellBack);
    }

    private static String newId(String id) {
        return OLD_IDS.getOrDefault(id, id);
    }

    private static void copy(String name, State from, State to) {
        switch (name) {
        case "offTools" -> to.offTools = from.offTools == null ? new TreeSet<>() : new TreeSet<>(from.offTools);
        case "showGrid" -> to.showGrid = from.showGrid;
        case "snapToGrid" -> to.snapToGrid = from.snapToGrid;
        case "openingZoom" -> to.openingZoom = from.openingZoom;
        case "toolSettings" -> to.toolSettings = from.toolSettings == null ? new TreeMap<>() : new TreeMap<>(from.toolSettings);
        default -> throw new IllegalArgumentException(name);
        }
    }

    /** One notice per session, naming the settings shown at their defaults. */
    private void tell(List<String> fellBack) {
        if (fellBack.isEmpty() || told) {
            return;
        }
        told = true;
        NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification("ADP settings", "Some stored ADP settings could not be read and are shown at their defaults: "
                        + String.join(", ", fellBack) + ". They are stored again when you apply the ADP settings page.", NotificationType.WARNING)
                .notify(null);
    }

    public synchronized boolean isOff(String toolId) {
        return state.offTools.contains(toolId);
    }

    public synchronized Set<String> offTools() {
        return Collections.unmodifiableSet(new TreeSet<>(state.offTools));
    }

    public synchronized void setOff(String toolId, boolean off) {
        if (off) {
            state.offTools.add(toolId);
        } else {
            state.offTools.remove(toolId);
        }
    }

    /** The user's values. */
    public synchronized CanvasOptions canvas() {
        List<String> fellBack = new ArrayList<>();
        CanvasOptions defaults = CanvasOptions.DEFAULTS;
        boolean showGrid = yesNo(state.showGrid, defaults.showGrid(), CanvasOption.SHOW_GRID.label(), fellBack);
        boolean snapToGrid = yesNo(state.snapToGrid, defaults.snapToGrid(), CanvasOption.SNAP_TO_GRID.label(), fellBack);
        double openingZoom = defaults.openingZoom();
        try {
            int percent = Integer.parseInt(state.openingZoom);
            if (percent >= MIN_ZOOM && percent <= MAX_ZOOM) {
                openingZoom = percent / 100.0;
            } else {
                fellBack.add(FIELDS.get("openingZoom"));
            }
        } catch (NumberFormatException e) {
            fellBack.add(FIELDS.get("openingZoom"));
        }
        tell(fellBack);
        return new CanvasOptions(showGrid, snapToGrid, openingZoom);
    }

    private static boolean yesNo(String stored, boolean fallback, String label, List<String> fellBack) {
        if ("true".equals(stored) || "false".equals(stored)) {
            return Boolean.parseBoolean(stored);
        }
        fellBack.add(label);
        return fallback;
    }

    public synchronized void setCanvas(CanvasOptions options) {
        state.showGrid = Boolean.toString(options.showGrid());
        state.snapToGrid = Boolean.toString(options.snapToGrid());
        state.openingZoom = Long.toString(Math.round(options.openingZoom() * 100));
    }

    /** The value the tool keeps when its definition fixes {@code option}, else the user's. */
    public boolean effective(CanvasOption option, ViewOptions view) {
        Boolean fixed = view.fixed().get(option);
        return fixed != null ? fixed : canvas().value(option);
    }

    /** The zoom a diagram opens at, 1.0 being 100%. */
    public double openingZoom() {
        return canvas().openingZoom();
    }

    public boolean yesNo(String toolId, ToolSetting setting) {
        return Boolean.parseBoolean(value(toolId, setting));
    }

    public int number(String toolId, ToolSetting setting) {
        return Integer.parseInt(value(toolId, setting));
    }

    public String choice(String toolId, ToolSetting setting) {
        return value(toolId, setting);
    }

    /** The stored value when it is valid for the declaration, else the declared default. */
    public synchronized String value(String toolId, ToolSetting setting) {
        String stored = state.toolSettings.get(key(toolId, setting));
        if (stored == null) {
            return setting.defaultValue();
        }
        if (setting.accepts(stored)) {
            return stored;
        }
        tell(List.of(setting.label()));
        return setting.defaultValue();
    }

    public synchronized void setValue(String toolId, ToolSetting setting, String value) {
        state.toolSettings.put(key(toolId, setting), value);
    }

    private static String key(String toolId, ToolSetting setting) {
        return toolId + "/" + setting.key();
    }
}
