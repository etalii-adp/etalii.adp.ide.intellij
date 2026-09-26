package etalii.adp.freemind.model;

import etalii.adp.core.xml.Range;

/**
 * An {@code arrowlink} element. A link whose destination does not exist stays in the file and is
 * not drawn. {@code range} is the whole element, widened to whole lines like a node's.
 */
public record ArrowLink(NodeKey source, String destinationId, Range range, String startArrow, String endArrow) {
}
