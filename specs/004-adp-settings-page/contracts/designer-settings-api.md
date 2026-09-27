# Contract: Settings API

What a designer author implements and what framework code reads. Packages are under `etalii.adp.core`.

## On `AdpEditorProvider` (designer author may override)

```java
/** Settings this designer shows on its own page under ADP; empty for none (FR-014). */
protected List<DesignerSetting> settings()            // default: List.of()

/** Where this designer comes from (FR-007, FR-016). */
protected DesignerOrigin origin()                     // default: Module or OtherPlugin, from the registering plug-in

/** Problems found while loading; non-empty means the designer refuses every file (FR-008). */
public List<String> problems()                        // default: List.of(); DiagramEditorProvider fills it from DefinitionException

/** Everything the page shows; not overridden. */
public final DesignerInfo designerInfo()
```

`accepts(VirtualFile)` gains one rule and is otherwise unchanged: it returns false when the designer is off or has problems (FR-009, research R7).

## `settings.DesignerSetting`

```java
public record DesignerSetting(String key, String label, Kind kind, String defaultValue,
                              int min, int max, List<String> choices) {
    public enum Kind { YES_NO, NUMBER, CHOICE }
    public static DesignerSetting yesNo(String key, String label, boolean defaultValue);
    public static DesignerSetting number(String key, String label, int defaultValue, int min, int max);
    public static DesignerSetting choice(String key, String label, String defaultValue, String... choices);
}
```

## `settings.DesignerOrigin`

```java
public sealed interface DesignerOrigin {
    record Module(String pluginId) implements DesignerOrigin {}
    record OtherPlugin(String pluginId, String pluginName) implements DesignerOrigin {}
    record BundledDefinition(String name, String dedlVersion, String sourceRevision) implements DesignerOrigin {}
}
```

## `settings.AdpSettings` (application service; read by designers and the framework)

```java
public static AdpSettings getInstance();
public boolean isOff(String designerId);
public CanvasOptions canvas();                                      // the user's values
public boolean effective(CanvasOption option, ViewOptions view);    // fixed value, else the user's
public double openingZoom();
public boolean yesNo(String designerId, DesignerSetting setting);   // stored or default
public int number(String designerId, DesignerSetting setting);
public String choice(String designerId, DesignerSetting setting);
```

Writes happen only through the ADP page's `apply()`.

## `settings.SettingsSection` (framework-internal extension point)

```java
public interface SettingsSection {
    ExtensionPointName<SettingsSection> EP_NAME = ExtensionPointName.create("etalii.adp.settingsSection");
    int order();                                  // designers 10, canvas 20, designer pages 30
    @Nullable JComponent createComponent();       // null for a section that only adds child pages
    boolean isModified();
    void apply();                                 // writes AdpSettings; the page publishes the topic once
    void reset();
    List<String> searchableLabels();
    default List<Configurable> children() { return List.of(); }
    default void disposeUIResources() {}
}
```

Designer authors do not implement it.

## `settings.AdpSettingsListener`

```java
public interface AdpSettingsListener {
    Topic<AdpSettingsListener> TOPIC = Topic.create("ADP settings", AdpSettingsListener.class);
    void settingsChanged();
}
```

Published on the application message bus after `apply()` changed anything. Subscribers repaint or re-read; they never change a document (FR-006).

## `diagram.ViewOptions` (changed)

```java
public record ViewOptions(boolean zoom, boolean pan, int grid, Map<CanvasOption, Boolean> fixed)
// Builder: grid(int spacing) now sets spacing only (default 10);
//          fix(CanvasOption option, boolean value) makes the designer ignore the user's option.
```

`CanvasOption` is `SHOW_GRID`, `SNAP_TO_GRID`. Opening zoom is not fixable: a designer that must not zoom already turns zoom off.

## `diagram.edit.MoveTool.snap` (behaviour changed)

`MoveTool.snap(designer, value)` keeps its signature and callers. It snaps to `view().grid()` only when `AdpSettings.effective(SNAP_TO_GRID, view)` is true; otherwise it returns `value` unchanged.
