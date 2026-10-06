package etalii.adp.fbl;

import java.util.List;

/**
 * What reading a body gives: its elements and relations in document order, its findings, the views
 * a blocks body defines (FBL §4.7) and the resources it holds (FBL §8.2), each in document order.
 */
public record FblModel(List<FblElement> elements, List<Finding> findings, boolean unreadable, List<FblView> views, List<String> resources) {

    public static final FblModel EMPTY = new FblModel(List.of(), List.of(), false);

    public FblModel {
        elements = List.copyOf(elements);
        findings = List.copyOf(findings);
        views = List.copyOf(views);
        resources = List.copyOf(resources);
    }

    public FblModel(List<FblElement> elements, List<Finding> findings, boolean unreadable) {
        this(elements, findings, unreadable, List.of(), List.of());
    }

    public List<FblElement> nodes() {
        return elements.stream().filter(e -> !e.isRelation()).toList();
    }

    public List<FblElement> relations() {
        return elements.stream().filter(FblElement::isRelation).toList();
    }

    /** The element or relation of that id, or null. */
    public FblElement find(String id) {
        return elements.stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
    }

    /**
     * The view a registration's {@code view} header selects (FBL §9.3): the one of that name, ignoring
     * case; without the header, the first in document order; null when there is none.
     */
    public FblView selectView(String header) {
        return views.stream().filter(v -> header == null || v.name().equalsIgnoreCase(header)).findFirst().orElse(null);
    }
}
