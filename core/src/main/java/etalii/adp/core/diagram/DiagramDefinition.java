package etalii.adp.core.diagram;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Everything a diagram declares (FR-001, research R4): element types, connection types,
 * toolbox, sectors, view options, rules, listener and optional layout. Built once with
 * {@link #builder(String)}; {@link Builder#build()} is the single validation point (FR-002).
 */
public final class DiagramDefinition {

    private static final Pattern ID = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]*");
    private static final DiagramListener NO_LISTENER = (tool, changes) -> {
    };

    private final String id;
    private final Map<String, ElementType> elementTypes;
    private final Map<String, ConnectionType> connectionTypes;
    private final List<String> toolbox;
    private final Map<String, SectorDecl> sectors;
    private final ViewOptions view;
    private final DiagramRules rules;
    private final DiagramListener listener;
    private final DiagramLayout layout;

    private DiagramDefinition(Builder builder, Map<String, ElementType> elementTypes, Map<String, ConnectionType> connectionTypes,
            Map<String, SectorDecl> sectors) {
        this.id = builder.id;
        this.elementTypes = Collections.unmodifiableMap(elementTypes);
        this.connectionTypes = Collections.unmodifiableMap(connectionTypes);
        List<String> declared = new ArrayList<>(elementTypes.keySet());
        declared.addAll(connectionTypes.keySet());
        this.toolbox = List.copyOf(builder.toolbox == null ? declared : builder.toolbox);
        this.sectors = Collections.unmodifiableMap(sectors);
        this.view = builder.view.build();
        this.rules = builder.rules;
        this.listener = builder.listener;
        this.layout = builder.layout;
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String id() {
        return id;
    }

    /** In declaration order. */
    public Map<String, ElementType> elementTypes() {
        return elementTypes;
    }

    /** In declaration order. */
    public Map<String, ConnectionType> connectionTypes() {
        return connectionTypes;
    }

    /** The declared element type, or {@code null} for an unknown id (a placeholder). */
    public ElementType elementType(String typeId) {
        return elementTypes.get(typeId);
    }

    /** The declared connection type, or {@code null} for an unknown id (a placeholder). */
    public ConnectionType connectionType(String typeId) {
        return connectionTypes.get(typeId);
    }

    /** Element and connection type ids in toolbox order. */
    public List<String> toolbox() {
        return toolbox;
    }

    public Map<String, SectorDecl> sectors() {
        return sectors;
    }

    public ViewOptions view() {
        return view;
    }

    public DiagramRules rules() {
        return rules;
    }

    public DiagramListener listener() {
        return listener;
    }

    /** The diagram's layout, or {@code null} when positions come from the diagram (research R7). */
    public DiagramLayout layout() {
        return layout;
    }

    /** Defaults: toolbox of every type in declaration order, zoom and pan on, rules allowing everything, no listener, no layout. */
    public static final class Builder {

        private final String id;
        private final Map<String, ElementType.Builder> elements = new LinkedHashMap<>();
        private final Map<String, ConnectionType.Builder> connections = new LinkedHashMap<>();
        private final Map<String, SectorDecl.Builder> sectors = new LinkedHashMap<>();
        private final List<String> duplicates = new ArrayList<>();
        private List<String> toolbox;
        private final ViewOptions.Builder view = new ViewOptions.Builder();
        private DiagramRules rules = new DiagramRules() {
        };
        private DiagramListener listener = NO_LISTENER;
        private DiagramLayout layout;

        private Builder(String id) {
            this.id = id;
        }

        public Builder element(String typeId, Consumer<ElementType.Builder> element) {
            ElementType.Builder builder = new ElementType.Builder(typeId);
            element.accept(builder);
            if (elements.putIfAbsent(typeId, builder) != null) {
                duplicates.add("element '" + typeId + "': is declared twice");
            }
            return this;
        }

        public Builder connection(String typeId, Consumer<ConnectionType.Builder> connection) {
            ConnectionType.Builder builder = new ConnectionType.Builder(typeId);
            connection.accept(builder);
            if (connections.putIfAbsent(typeId, builder) != null) {
                duplicates.add("connection '" + typeId + "': is declared twice");
            }
            return this;
        }

        public Builder sector(String sectorId, Consumer<SectorDecl.Builder> sector) {
            SectorDecl.Builder builder = new SectorDecl.Builder(sectorId);
            sector.accept(builder);
            if (sectors.putIfAbsent(sectorId, builder) != null) {
                duplicates.add("sector '" + sectorId + "': is declared twice");
            }
            return this;
        }

        public Builder toolbox(String... typeIds) {
            this.toolbox = List.of(typeIds);
            return this;
        }

        public Builder view(Consumer<ViewOptions.Builder> options) {
            options.accept(view);
            return this;
        }

        public Builder rules(DiagramRules rules) {
            this.rules = rules;
            return this;
        }

        public Builder listener(DiagramListener listener) {
            this.listener = listener;
            return this;
        }

        public Builder layout(DiagramLayout layout) {
            this.layout = layout;
            return this;
        }

        /** Checks the whole definition; throws {@link DefinitionException} listing every problem. */
        public DiagramDefinition build() {
            List<String> problems = new ArrayList<>();
            checkId(problems, "definition '" + id + "'", id);
            if (elements.isEmpty()) {
                problems.add("definition '" + id + "': declares no element types");
            }
            problems.addAll(duplicates);
            Map<String, ElementType> elementTypes = new LinkedHashMap<>();
            elements.forEach((typeId, builder) -> elementTypes.put(typeId, builder.build()));
            Map<String, ConnectionType> connectionTypes = new LinkedHashMap<>();
            connections.forEach((typeId, builder) -> connectionTypes.put(typeId, builder.build()));
            Map<String, SectorDecl> sectorDecls = new LinkedHashMap<>();
            sectors.forEach((sectorId, builder) -> sectorDecls.put(sectorId, builder.build()));

            if (toolbox != null) {
                List<String> seen = new ArrayList<>();
                for (String entry : toolbox) {
                    if (!elementTypes.containsKey(entry) && !connectionTypes.containsKey(entry)) {
                        problems.add("toolbox: names undeclared type '" + entry + "'");
                    } else if (seen.contains(entry)) {
                        problems.add("toolbox: names '" + entry + "' twice");
                    }
                    seen.add(entry);
                }
            }
            elements.values().forEach(builder -> checkElement(problems, builder, elementTypes.get(builder.id), connectionTypes));
            connections.values().forEach(builder -> checkConnection(problems, builder, connectionTypes.get(builder.id), elementTypes));
            sectorDecls.keySet().forEach(sectorId -> checkId(problems, "sector '" + sectorId + "'", sectorId));
            if (!problems.isEmpty()) {
                throw new DefinitionException(problems);
            }
            return new DiagramDefinition(this, elementTypes, connectionTypes, sectorDecls);
        }

        private void checkElement(List<String> problems, ElementType.Builder builder, ElementType type,
                Map<String, ConnectionType> connectionTypes) {
            String path = "element '" + type.id() + "'";
            checkId(problems, path, type.id());
            builder.duplicates.forEach(duplicate -> problems.add(path + " > " + duplicate));
            if (type.movable() && !type.selectable()) {
                problems.add(path + ": is movable but not selectable");
            }
            if (type.movable() && layout != null) {
                problems.add(path + ": is movable but the diagram has a layout");
            }
            if (type.resize() != Resize.NONE && type.sizing() instanceof Sizing.Fixed) {
                problems.add(path + ": is resizable but has a fixed size");
            }
            checkSizing(problems, path, type.sizing());
            for (PropertyDecl property : type.properties()) {
                checkProperty(problems, path + " > property '" + property.id() + "'", property);
            }
            for (TextSlot slot : type.texts()) {
                String slotPath = path + " > text '" + slot.id() + "'";
                checkId(problems, slotPath, slot.id());
                PropertyDecl property = type.property(slot.property());
                if (property == null) {
                    problems.add(slotPath + ": shows undeclared property '" + slot.property() + "'");
                } else if (slot.editable() && property.readOnly()) {
                    problems.add(slotPath + ": is editable but property '" + property.id() + "' is read-only");
                }
            }
            for (Anchor anchor : type.anchors()) {
                String anchorPath = path + " > anchor '" + anchor.id() + "'";
                checkId(problems, anchorPath, anchor.id());
                if (!anchor.perimeter() && (anchor.fx() < 0 || anchor.fx() > 1 || anchor.fy() < 0 || anchor.fy() > 1)) {
                    problems.add(anchorPath + ": the position must lie within 0 and 1");
                }
                for (String connectionType : anchor.accepts().keySet()) {
                    if (!connectionTypes.containsKey(connectionType)) {
                        problems.add(anchorPath + ": names undeclared connection type '" + connectionType + "'");
                    }
                }
            }
        }

        private void checkConnection(List<String> problems, ConnectionType.Builder builder, ConnectionType type,
                Map<String, ElementType> elementTypes) {
            String path = "connection '" + type.id() + "'";
            checkId(problems, path, type.id());
            if (elementTypes.containsKey(type.id())) {
                problems.add(path + ": has the id of an element type");
            }
            builder.duplicates.forEach(duplicate -> problems.add(path + " > " + duplicate));
            if (type.thickness() < 0.5f || type.thickness() > 8f) {
                problems.add(path + ": the thickness must lie within 0.5 and 8");
            }
            if (type.routed() && type.line() != LineStyle.ORTHOGONAL) {
                problems.add(path + ": is routed but its line is not orthogonal");
            }
            for (PropertyDecl property : type.properties()) {
                checkProperty(problems, path + " > property '" + property.id() + "'", property);
            }
            type.labels().forEach((slot, label) -> {
                String labelPath = path + " > label " + slot;
                PropertyDecl property = type.property(label.property());
                if (property == null) {
                    problems.add(labelPath + ": shows undeclared property '" + label.property() + "'");
                } else if (label.editable() && property.readOnly()) {
                    problems.add(labelPath + ": is editable but property '" + property.id() + "' is read-only");
                }
            });
            if (type.userConnectable()) {
                boolean source = false;
                boolean target = false;
                for (ElementType element : elementTypes.values()) {
                    for (Anchor anchor : element.anchors()) {
                        source |= anchor.accepts(type.id(), Direction.OUT);
                        target |= anchor.accepts(type.id(), Direction.IN);
                    }
                }
                if (!source) {
                    problems.add(path + ": no anchor accepts it as a source");
                }
                if (!target) {
                    problems.add(path + ": no anchor accepts it as a target");
                }
            }
        }

        private static void checkProperty(List<String> problems, String path, PropertyDecl property) {
            checkId(problems, path, property.id());
            if (property.editor() instanceof EditorKind.Number number && number.min() > number.max()) {
                problems.add(path + ": the minimum is larger than the maximum");
            }
            if (property.editor() instanceof EditorKind.Choice choice && choice.options().isEmpty()) {
                problems.add(path + ": a choice needs at least one value");
            }
        }

        private static void checkSizing(List<String> problems, String path, Sizing sizing) {
            boolean valid = switch (sizing) {
            case Sizing.Auto auto -> auto.maxWidth() > 0;
            case Sizing.Fixed fixed -> fixed.width() > 0 && fixed.height() > 0;
            case Sizing.FromDiagram from -> from.minWidth() >= 0 && from.minHeight() >= 0;
            };
            if (!valid) {
                problems.add(path + ": the size must be positive");
            }
        }

        private static void checkId(List<String> problems, String path, String id) {
            if (id == null || !ID.matcher(id).matches()) {
                problems.add(path + ": the id must start with a letter and use letters, digits, '_', '.' or '-'");
            }
        }
    }
}
