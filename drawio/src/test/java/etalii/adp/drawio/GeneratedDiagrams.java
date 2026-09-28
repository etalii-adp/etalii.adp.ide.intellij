package etalii.adp.drawio;

/** Generated draw.io files for measurements on sizes the published examples do not reach. */
public final class GeneratedDiagrams {

    private GeneratedDiagrams() {
    }

    /**
     * An uncompressed draw.io file with {@code cellCount} cells: three in five are labelled
     * rectangles {@code v0}, {@code v1}, ... on a grid, the rest are edges {@code e0}, {@code e1},
     * ... each joining one rectangle to the next.
     */
    public static String generated(int cellCount) {
        int vertices = (cellCount * 3 + 4) / 5;
        int edges = Math.min(cellCount - vertices, vertices - 1);
        int columns = (int) Math.ceil(Math.sqrt(vertices));
        StringBuilder text = new StringBuilder("<mxfile host=\"generated\">\n  <diagram id=\"generated\" name=\"Generated\">\n");
        text.append("    <mxGraphModel grid=\"1\" gridSize=\"10\">\n      <root>\n");
        text.append("        <mxCell id=\"0\"/>\n        <mxCell id=\"1\" parent=\"0\"/>\n");
        for (int v = 0; v < vertices; v++) {
            int x = 40 + (v % columns) * 160;
            int y = 40 + (v / columns) * 100;
            text.append("        <mxCell id=\"v").append(v).append("\" value=\"Cell ").append(v)
                    .append("\" style=\"rounded=0;whiteSpace=wrap;html=1;\" vertex=\"1\" parent=\"1\">\n");
            text.append("          <mxGeometry x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" width=\"120\" height=\"60\" as=\"geometry\"/>\n        </mxCell>\n");
        }
        for (int e = 0; e < edges; e++) {
            text.append("        <mxCell id=\"e").append(e).append("\" style=\"endArrow=classic;html=1;\" edge=\"1\" parent=\"1\" source=\"v")
                    .append(e).append("\" target=\"v").append(e + 1).append("\">\n");
            text.append("          <mxGeometry relative=\"1\" as=\"geometry\"/>\n        </mxCell>\n");
        }
        return text.append("      </root>\n    </mxGraphModel>\n  </diagram>\n</mxfile>\n").toString();
    }
}
