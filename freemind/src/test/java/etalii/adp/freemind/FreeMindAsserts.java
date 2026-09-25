package etalii.adp.freemind;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import etalii.adp.freemind.model.NodeKey;

/**
 * Helpers shared by the FreeMind tests: the vendored example maps in {@code testdata/examples/}, the
 * reference results in {@code testdata/reference/}, node keys and generated maps.
 */
public final class FreeMindAsserts {

    private FreeMindAsserts() {
    }

    /** A vendored example map from {@code testdata/examples/}. */
    public static Path example(String name) {
        Path path = examplesDirectory().resolve(name);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("No example map " + name + " in " + examplesDirectory());
        }
        return path;
    }

    /** Every vendored example map, by name. For {@code @MethodSource}. */
    public static List<Path> examples() {
        try (Stream<Path> files = Files.list(examplesDirectory())) {
            return files.filter(p -> p.getFileName().toString().endsWith(".mm")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The text of a map file, decoded as FreeMind and Freeplane write it. */
    public static String read(Path map) {
        try {
            return new String(Files.readAllBytes(map), UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static NodeKey key(String id) {
        return NodeKey.ofId(id);
    }

    public static NodeKey keyPath(int... indices) {
        return NodeKey.ofPath(indices);
    }

    /**
     * A FreeMind 1.0.1 map with {@code nodeCount} nodes: the root has ten first-level branches, and
     * every other node up to ten children, filled breadth first. Node {@code i} has ID {@code ID_i}
     * and text {@code Node i}; the root is {@code ID_0}.
     */
    public static String generatedMap(int nodeCount) {
        int[] parents = new int[nodeCount];
        for (int i = 1; i < nodeCount; i++) {
            parents[i] = (i - 1) / 10;
        }
        StringBuilder text = new StringBuilder("<map version=\"1.0.1\">\n");
        text.append("<!-- To view this file, download free mind mapping software FreeMind from http://freemind.sourceforge.net -->\n");
        appendNode(text, 0, parents, nodeCount);
        return text.append("</map>\n").toString();
    }

    private static void appendNode(StringBuilder text, int node, int[] parents, int nodeCount) {
        text.append("<node CREATED=\"1300000000000\" ID=\"ID_").append(node).append("\" MODIFIED=\"1300000000000\"");
        if (node != 0 && parents[node] == 0) {
            text.append(" POSITION=\"").append(node % 2 == 0 ? "left" : "right").append('"');
        }
        text.append(" TEXT=\"Node ").append(node).append('"');
        int firstChild = node * 10 + 1;
        if (firstChild >= nodeCount) {
            text.append("/>\n");
            return;
        }
        text.append(">\n");
        for (int child = firstChild; child < Math.min(firstChild + 10, nodeCount); child++) {
            appendNode(text, child, parents, nodeCount);
        }
        text.append("</node>\n");
    }

    /** A recorded reference result, for example {@code reference("freeplane-1.11-sample", "05-rename.mm")}. */
    public static Path reference(String map, String result) {
        Path path = testdata().resolve("reference").resolve(map).resolve(result);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("No reference result " + map + "/" + result);
        }
        return path;
    }

    /** The reference directory: {@code scenarios.json}, one folder of results per map, the test inventory. */
    public static Path referenceDirectory() {
        return testdata().resolve("reference");
    }

    private static Path examplesDirectory() {
        return testdata().resolve("examples");
    }

    /** Set by the build; the module directory's {@code testdata} when a test runs from an IDE. */
    private static Path testdata() {
        return Path.of(System.getProperty("adp.testdata", "testdata")).toAbsolutePath();
    }
}
