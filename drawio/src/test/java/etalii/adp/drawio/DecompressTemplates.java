package etalii.adp.drawio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * The recorded, repeatable tool that made {@code drawio/testdata/examples} (research R20). It turns
 * published draw.io templates, whose pages are compressed, into uncompressed {@code .drawio} files
 * and writes a {@code .LICENSE} next to each one. It never touches the network: download the
 * templates first, for example with
 * {@code curl -O https://raw.githubusercontent.com/jgraph/drawio/<COMMIT>/src/main/webapp/templates/flowcharts/flowchart_1.xml}.
 *
 * <p>Usage: {@code java DecompressTemplates.java <downloaded templates dir> <examples dir>}.
 */
public final class DecompressTemplates {

    /** The commit of jgraph/drawio the templates were taken from. */
    static final String COMMIT = "6c66d8c3ecf59a00e110b92832504ad1f9530140";

    /** Template path under {@code src/main/webapp/templates}, without {@code .xml}. */
    static final List<String> DECOMPRESSED = List.of("flowcharts/flowchart_1", "flowcharts/cross_functional_flowchart_1",
            "flowcharts/data_flow_1", "flowcharts/workflow_1", "uml/activity_diagram_1", "uml/uml_1");

    /** Kept compressed, as {@code compressed.drawio}, for the text-view fallback. */
    static final String COMPRESSED = "flowcharts/flowchart_2";

    private static final Pattern DIAGRAM = Pattern.compile("(<diagram\\b[^>]*>)([^<]*)(</diagram>)");

    private DecompressTemplates() {
    }

    public static void main(String[] args) throws IOException, DataFormatException {
        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);
        Files.createDirectories(out);
        for (String template : DECOMPRESSED) {
            String name = template.substring(template.indexOf('/') + 1);
            String published = Files.readString(in.resolve(name + ".xml"), StandardCharsets.UTF_8);
            Files.writeString(out.resolve(name + ".drawio"), indent(decompress(published)), StandardCharsets.UTF_8);
            Files.writeString(out.resolve(name + ".drawio.LICENSE"), licence(template, name + ".drawio",
                    "decompressed from the published template: each <diagram> page's base64, raw-deflated, URL-encoded content is"
                            + " replaced by the <mxGraphModel> it holds, and the file is indented with two spaces per level as draw.io"
                            + " writes uncompressed files. No cell, attribute or value was changed."), StandardCharsets.UTF_8);
        }
        String name = COMPRESSED.substring(COMPRESSED.indexOf('/') + 1);
        Files.copy(in.resolve(name + ".xml"), out.resolve("compressed.drawio"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(out.resolve("compressed.drawio.LICENSE"), licence(COMPRESSED, "compressed.drawio",
                "none: the bytes are unmodified and still compressed; only the file name was changed."), StandardCharsets.UTF_8);
    }

    /** Every compressed page replaced by the XML it holds. */
    static String decompress(String file) throws DataFormatException {
        Matcher matcher = DIAGRAM.matcher(file);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String content = matcher.group(2).trim();
            String page = content.isEmpty() ? "" : inflate(content);
            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1) + page + matcher.group(3)));
        }
        return matcher.appendTail(result).toString();
    }

    /** draw.io's page compression: base64 of raw deflate of the URL-encoded XML. */
    static String inflate(String base64) throws DataFormatException {
        Inflater inflater = new Inflater(true);
        inflater.setInput(Base64.getDecoder().decode(base64));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!inflater.finished()) {
            int n = inflater.inflate(buffer);
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                break;
            }
            bytes.write(buffer, 0, n);
        }
        inflater.end();
        return URLDecoder.decode(bytes.toString(StandardCharsets.US_ASCII), StandardCharsets.UTF_8);
    }

    /** Each tag on its own line, two spaces per level; the pages hold no text content, only tags. */
    static String indent(String xml) {
        StringBuilder out = new StringBuilder();
        int depth = 0;
        int at = 0;
        while (at < xml.length()) {
            int start = xml.indexOf('<', at);
            if (start < 0) {
                break;
            }
            int end = start;
            char quote = 0;
            while (++end < xml.length()) {
                char c = xml.charAt(end);
                if (quote != 0) {
                    quote = c == quote ? 0 : quote;
                } else if (c == '"' || c == '\'') {
                    quote = c;
                } else if (c == '>') {
                    break;
                }
            }
            String tag = xml.substring(start, end + 1);
            boolean closing = tag.startsWith("</");
            depth -= closing ? 1 : 0;
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append("  ".repeat(Math.max(0, depth))).append(tag);
            depth += !closing && !tag.endsWith("/>") && !tag.startsWith("<?") && !tag.startsWith("<!") ? 1 : 0;
            at = end + 1;
        }
        return out.append('\n').toString();
    }

    private static String licence(String template, String file, String modification) {
        String url = "https://raw.githubusercontent.com/jgraph/drawio/" + COMMIT + "/src/main/webapp/templates/" + template + ".xml";
        return "Vendored test example: " + file + "\n\n"
                + "Source (raw file): " + url + "\n"
                + "Source (repository page): https://github.com/jgraph/drawio/blob/" + COMMIT + "/src/main/webapp/templates/" + template + ".xml\n"
                + "Repository: https://github.com/jgraph/drawio (pinned to commit " + COMMIT + ")\n"
                + "Copyright holder: JGraph Ltd\n"
                + "Licence: Creative Commons Attribution 4.0 International (CC-BY-4.0), as stated by\n"
                + "https://github.com/jgraph/drawio/blob/" + COMMIT + "/src/main/webapp/templates/LICENSE\n"
                + "Licence text: https://creativecommons.org/licenses/by/4.0/legalcode\n\n"
                + "Modification: " + modification + "\n"
                + "Made with drawio/src/test/java/etalii/adp/drawio/DecompressTemplates.java.\n";
    }
}
