package etalii.adp.atd;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * {@code .atd} tool descriptions in the plug-in (etalii.adp spec 035, T116): the plug-in zip packs the {@code atd-lsp}
 * of every platform it ships one for; the walk of SC-005 (30 steps of completion, hover, definition, references,
 * rename, formatting and diagnostics) runs through the server the zip packs for this platform, over stdio, as the
 * IDE's language server support runs it; and the plug-in declares its {@code .atd} support only behind
 * {@code com.intellij.modules.lsp}, so an IDE without it loads the plug-in and opens an {@code .atd} as plain text.
 * The walk and its examples are in the server's release, {@code atd-walk.zip}, which {@code ATD_RELEASE_DIR} or the
 * {@code adp.atd.walk} property names a folder of; without it the walk is skipped.
 */
class AtdLanguageIntegrationTest {

    private static final List<String> PLATFORMS = List.of("windows-x64/atd-lsp.exe", "macos-arm64/atd-lsp", "linux-x64/atd-lsp");

    private final Path repository = Path.of(System.getProperty("adp.repository", "."));
    private final Path zip = Path.of(System.getProperty("adp.plugin.zip", "missing.zip"));

    /** The zip's entries under the plug-in's {@code bin/}, by their path below it, with their bytes. */
    private Map<String, byte[]> packedServers() throws IOException {
        assertTrue(Files.isRegularFile(zip), "No plug-in zip at " + zip);
        Map<String, byte[]> found = new TreeMap<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                int bin = entry.getName().indexOf("/bin/");
                if (bin >= 0 && !entry.isDirectory()) {
                    found.put(entry.getName().substring(bin + "/bin/".length()), in.readAllBytes());
                }
            }
        }
        return found;
    }

    @Test
    void thePluginZipPacksTheServerOfEveryPlatform() throws IOException {
        Map<String, byte[]> servers = packedServers();
        for (String platform : PLATFORMS) {
            assertTrue(servers.containsKey(platform) && servers.get(platform).length > 0, "The zip packs bin/" + platform + "; it has " + servers.keySet());
        }
    }

    @Test
    void theAtdSupportIsDeclaredOnlyBehindTheLanguageServerModule() throws IOException {
        String plugin = Files.readString(repository.resolve("src/main/resources/META-INF/plugin.xml"), UTF_8);
        assertTrue(plugin.contains("<depends optional=\"true\" config-file=\"adp-atd.xml\">com.intellij.modules.lsp</depends>"), "plugin.xml loads adp-atd.xml only with com.intellij.modules.lsp");
        String atd = Files.readString(repository.resolve("atd/src/main/resources/META-INF/adp-atd.xml"), UTF_8);
        assertTrue(atd.contains("etalii.adp.atd.AtdFileType") && atd.contains("platform.lsp.integrationProvider"), "adp-atd.xml registers the file type and the server");
        // Nothing else registers the .atd file type, so without the module an .atd is plain text.
        try (var files = Files.walk(repository)) {
            List<Path> others = files.filter(p -> p.toString().endsWith(".xml") && p.toString().contains("/src/main/resources/META-INF/"))
                    .filter(p -> !p.getFileName().toString().equals("adp-atd.xml") && !p.toString().contains("/build/"))
                    .filter(p -> {
                        try {
                            return Files.readString(p, UTF_8).contains("AtdFileType") || Files.readString(p, UTF_8).contains("extensions=\"atd\"");
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .toList();
            assertEquals(List.of(), others);
        }
    }

    /** The folder holding the walk and the examples it edits, or none. */
    private static Path walkFolder() {
        String folder = System.getProperty("adp.atd.walk");
        if (folder == null || folder.isBlank()) {
            String release = System.getenv("ATD_RELEASE_DIR");
            folder = release == null ? null : Path.of(release).resolve("atd-walk").toString();
        }
        return folder == null ? null : Path.of(folder);
    }

    @Test
    void theWalkPassesThroughThePackedServer(@TempDir Path work) throws Exception {
        Path walkFolder = walkFolder();
        assumeTrue(walkFolder != null && Files.isRegularFile(walkFolder.resolve("walk.json")), "No walk: set adp.atd.walk or ATD_RELEASE_DIR");
        String platform = AtdServerBinaryNames.thisPlatform();
        assumeTrue(platform != null, "No atd-lsp is released for this platform");
        byte[] binary = packedServers().get(platform);
        assertTrue(binary != null, "The zip packs bin/" + platform);
        Path server = work.resolve("server").resolve(Path.of(platform).getFileName().toString());
        Files.createDirectories(server.getParent());
        Files.write(server, binary);
        server.toFile().setExecutable(true);
        Path folder = work.resolve("walk");
        Files.createDirectories(folder);
        try (var files = Files.list(walkFolder)) {
            for (Path file : files.toList()) {
                Files.copy(file, folder.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        JsonObject walk = JsonParser.parseString(Files.readString(folder.resolve("walk.json"), UTF_8)).getAsJsonObject();
        try (Client client = new Client(new ProcessBuilder(server.toString()).directory(folder.toFile()).start())) {
            client.request("initialize", obj("capabilities", new JsonObject()));
            client.notify("initialized", new JsonObject());
            for (JsonElement file : walk.getAsJsonArray("files")) {
                Path path = folder.resolve(file.getAsString());
                client.notify("textDocument/didOpen", obj("textDocument", obj("uri", uri(path), "languageId", "atd", "version", 1, "text", Files.readString(path, UTF_8))));
            }
            List<String> failures = new ArrayList<>();
            for (JsonElement element : walk.getAsJsonArray("steps")) {
                JsonObject step = element.getAsJsonObject();
                String problem = client.step(folder, step);
                if (problem != null) {
                    failures.add("step " + step.get("id").getAsInt() + " (" + step.get("doc").getAsString() + "): " + problem);
                }
            }
            assertEquals(List.of(), failures, failures.size() + " of 30 steps failed");
        }
    }

    // ---- a minimal client of the protocol, over the server's stdio --------------------------------------------------

    private static JsonObject obj(Object... pairs) {
        JsonObject out = new JsonObject();
        Gson gson = new Gson();
        for (int i = 0; i < pairs.length; i += 2) {
            out.add((String) pairs[i], pairs[i + 1] instanceof JsonElement e ? e : gson.toJsonTree(pairs[i + 1]));
        }
        return out;
    }

    private static String uri(Path path) {
        return path.toUri().toString();
    }

    /** The JSON-RPC of the protocol over a process's stdin and stdout, with the diagnostics it publishes kept. */
    private static final class Client implements AutoCloseable {
        private final Process process;
        private final OutputStream out;
        private final InputStream in;
        private final Map<String, List<String>> diagnostics = new HashMap<>();
        private final Map<String, Integer> versions = new HashMap<>();
        /** The text the server holds of each file the walk changed. */
        private final Map<Path, String> texts = new HashMap<>();
        private int nextId;

        Client(Process process) {
            this.process = process;
            this.out = process.getOutputStream();
            this.in = new BufferedInputStream(process.getInputStream());
        }

        void send(JsonObject message) throws IOException {
            message.addProperty("jsonrpc", "2.0");
            byte[] body = message.toString().getBytes(UTF_8);
            out.write(("Content-Length: " + body.length + "\r\n\r\n").getBytes(UTF_8));
            out.write(body);
            out.flush();
        }

        JsonObject read() throws IOException {
            int length = -1;
            StringBuilder line = new StringBuilder();
            while (true) {
                int c = in.read();
                if (c < 0) {
                    throw new IOException("the server closed its output");
                }
                if (c == '\n') {
                    String header = line.toString().trim();
                    line.setLength(0);
                    if (header.isEmpty()) {
                        break;
                    }
                    if (header.toLowerCase().startsWith("content-length:")) {
                        length = Integer.parseInt(header.substring("content-length:".length()).trim());
                    }
                } else {
                    line.append((char) c);
                }
            }
            return JsonParser.parseString(new String(in.readNBytes(length), UTF_8)).getAsJsonObject();
        }

        void keep(JsonObject message) {
            if (message.has("method") && message.get("method").getAsString().equals("textDocument/publishDiagnostics")) {
                JsonObject params = message.getAsJsonObject("params");
                List<String> codes = new ArrayList<>();
                for (JsonElement d : params.getAsJsonArray("diagnostics")) {
                    codes.add(d.getAsJsonObject().get("code").getAsString());
                }
                diagnostics.put(params.get("uri").getAsString(), codes);
            }
        }

        JsonElement request(String method, JsonObject params) throws IOException {
            int id = ++nextId;
            send(obj("id", id, "method", method, "params", params));
            while (true) {
                JsonObject message = read();
                if (message.has("id") && message.get("id").getAsInt() == id && !message.has("method")) {
                    if (message.has("error")) {
                        throw new IOException(method + ": " + message.get("error"));
                    }
                    return message.get("result");
                }
                keep(message);
            }
        }

        void notify(String method, JsonObject params) throws IOException {
            send(obj("method", method, "params", params));
        }

        /** Waits for the diagnostics a change publishes: after the server's 150 ms of quiet, every file of the description. */
        List<String> settle(Path folder, String file) throws IOException, InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            diagnostics.clear();
            while (System.nanoTime() < deadline) {
                if (in.available() > 0) {
                    keep(read());
                    continue;
                }
                if (!diagnostics.isEmpty()) {
                    Thread.sleep(300);
                    if (in.available() == 0) {
                        break;
                    }
                    continue;
                }
                Thread.sleep(20);
            }
            String name = file.split("\\.")[0];
            List<String> codes = new ArrayList<>();
            diagnostics.forEach((uri, list) -> {
                String base = uri.substring(uri.lastIndexOf('/') + 1);
                if (base.split("\\.")[0].equals(name)) {
                    codes.addAll(list);
                }
            });
            codes.sort(null);
            return codes;
        }

        String step(Path folder, JsonObject step) throws Exception {
            String file = step.get("file").getAsString();
            Path path = folder.resolve(file);
            JsonObject document = obj("uri", uri(path));
            JsonObject position = obj("line", step.get("line").getAsInt() - 1, "character", step.get("utf16").getAsInt());
            JsonObject expect = step.getAsJsonObject("expect");
            switch (step.get("request").getAsString()) {
                case "completion" -> {
                    JsonElement result = request("textDocument/completion", obj("textDocument", document, "position", position));
                    List<String> got = new ArrayList<>();
                    for (JsonElement item : result.getAsJsonObject().getAsJsonArray("items")) {
                        got.add(item.getAsJsonObject().get("label").getAsString());
                    }
                    got.sort(null);
                    if (expect.has("exactly")) {
                        List<String> want = strings(expect.getAsJsonArray("exactly"));
                        return got.equals(want) ? null : "completion " + got + ", expected exactly " + want;
                    }
                    List<String> missing = strings(expect.getAsJsonArray("includes")).stream().filter(e -> !got.contains(e)).toList();
                    List<String> unwanted = expect.has("excludes") ? strings(expect.getAsJsonArray("excludes")).stream().filter(got::contains).toList() : List.of();
                    return missing.isEmpty() && unwanted.isEmpty() ? null : "completion lacks " + missing + ", has " + unwanted;
                }
                case "hover" -> {
                    JsonElement result = request("textDocument/hover", obj("textDocument", document, "position", position));
                    String value = result.isJsonObject() ? result.getAsJsonObject().getAsJsonObject("contents").get("value").getAsString() : "";
                    String want = expect.get("contains").getAsString();
                    return value.contains(want) ? null : "hover " + value + " lacks " + want;
                }
                case "definition" -> {
                    JsonElement result = request("textDocument/definition", obj("textDocument", document, "position", position));
                    JsonObject location = result.isJsonArray() ? result.getAsJsonArray().get(0).getAsJsonObject() : result.getAsJsonObject();
                    JsonObject start = location.getAsJsonObject("range").getAsJsonObject("start");
                    boolean ok = location.get("uri").getAsString().endsWith(expect.get("file").getAsString())
                            && start.get("line").getAsInt() == expect.get("line").getAsInt() - 1
                            && start.get("character").getAsInt() == expect.get("utf16").getAsInt();
                    return ok ? null : "definition " + location + ", expected " + expect;
                }
                case "references" -> {
                    JsonElement result = request("textDocument/references", obj("textDocument", document, "position", position, "context", obj("includeDeclaration", true)));
                    int count = result.getAsJsonArray().size();
                    return count == expect.get("count").getAsInt() ? null : count + " references, expected " + expect.get("count");
                }
                case "rename" -> {
                    JsonElement result = request("textDocument/rename", obj("textDocument", document, "position", position, "newName", step.get("newName").getAsString()));
                    Map<String, Integer> counts = new TreeMap<>();
                    for (Map.Entry<String, JsonElement> change : result.getAsJsonObject().getAsJsonObject("changes").entrySet()) {
                        counts.put(change.getKey().substring(change.getKey().lastIndexOf('/') + 1), change.getValue().getAsJsonArray().size());
                    }
                    Map<String, Integer> want = new TreeMap<>();
                    expect.getAsJsonObject("files").entrySet().forEach(e -> want.put(e.getKey(), e.getValue().getAsInt()));
                    return counts.equals(want) ? null : "rename edits " + counts + ", expected " + want;
                }
                case "format" -> {
                    JsonObject options = obj("tabSize", 2, "insertSpaces", true);
                    JsonArray edits = request("textDocument/formatting", obj("textDocument", document, "options", options)).getAsJsonArray();
                    if (!edits.isEmpty()) {
                        String text = edits.get(0).getAsJsonObject().get("newText").getAsString();
                        change(path, document, null, null, text, true);
                    }
                    JsonArray again = request("textDocument/formatting", obj("textDocument", document, "options", options)).getAsJsonArray();
                    return again.isEmpty() ? null : "the second formatting changed the file: " + again;
                }
                case "diagnostics" -> {
                    notify("textDocument/didSave", obj("textDocument", document));
                    List<String> got = settle(path.getParent(), file);
                    List<String> want = strings(expect.getAsJsonArray("codes"));
                    return got.equals(want) ? null : "diagnostics " + got + ", expected " + want;
                }
                case "edit" -> {
                    JsonObject edit = step.getAsJsonObject("edit");
                    JsonObject end = position;
                    if (edit.has("endLine")) {
                        String line = text(path).split("\n", -1)[edit.get("endLine").getAsInt() - 1];
                        int units = line.codePoints().limit(edit.get("endColumn").getAsInt() - 1).map(Character::charCount).sum();
                        end = obj("line", edit.get("endLine").getAsInt() - 1, "character", units);
                    }
                    change(path, document, position, end, edit.get("text").getAsString(), false);
                    List<String> got = settle(path.getParent(), file);
                    List<String> want = new ArrayList<>();
                    for (JsonElement d : expect.getAsJsonArray("diagnostics")) {
                        want.add(d.getAsJsonObject().get("code").getAsString());
                    }
                    want.sort(null);
                    return got.equals(want) ? null : "diagnostics after the edit " + got + ", expected " + want;
                }
                default -> {
                    return "an unknown request " + step.get("request");
                }
            }
        }

        String text(Path path) throws IOException {
            String known = texts.get(path);
            return known != null ? known : Files.readString(path, UTF_8);
        }

        /** The offset in a text of a position in lines and UTF-16 code units. */
        private static int offset(String text, JsonObject position) {
            int line = position.get("line").getAsInt();
            int at = 0;
            for (int i = 0; i < line; i++) {
                at = text.indexOf('\n', at) + 1;
            }
            return at + position.get("character").getAsInt();
        }

        /** Sends a change of a document, as a range or the whole text, and keeps the text the server holds. */
        void change(Path path, JsonObject document, JsonObject start, JsonObject end, String text, boolean whole) throws IOException {
            int version = versions.merge(path.toString(), 2, (a, b) -> a + 1);
            JsonObject change = whole || start == null ? obj("text", text) : obj("range", obj("start", start, "end", end), "text", text);
            JsonObject versioned = obj("uri", document.get("uri").getAsString(), "version", version);
            notify("textDocument/didChange", obj("textDocument", versioned, "contentChanges", List.of(change)));
            String before = text(path);
            texts.put(path, whole || start == null ? text : before.substring(0, offset(before, start)) + text + before.substring(offset(before, end)));
        }

        @Override
        public void close() throws Exception {
            try {
                request("shutdown", new JsonObject());
                notify("exit", new JsonObject());
            } finally {
                process.waitFor(5, TimeUnit.SECONDS);
                process.destroyForcibly();
            }
        }
    }

    private static List<String> strings(JsonArray array) {
        List<String> out = new ArrayList<>();
        if (array != null) {
            array.forEach(e -> out.add(e.getAsString()));
        }
        out.sort(null);
        return out;
    }

    /** The platform folder of this machine's packed server, as {@code AtdServerBinary} chooses it. */
    private static final class AtdServerBinaryNames {
        static String thisPlatform() {
            String os = System.getProperty("os.name").toLowerCase();
            String arch = System.getProperty("os.arch").toLowerCase();
            boolean x64 = arch.equals("amd64") || arch.equals("x86_64");
            if (os.contains("win") && x64) {
                return "windows-x64/atd-lsp.exe";
            }
            if (os.contains("mac") && arch.equals("aarch64")) {
                return "macos-arm64/atd-lsp";
            }
            if (os.contains("linux") && x64) {
                return "linux-x64/atd-lsp";
            }
            return null;
        }
    }
}
