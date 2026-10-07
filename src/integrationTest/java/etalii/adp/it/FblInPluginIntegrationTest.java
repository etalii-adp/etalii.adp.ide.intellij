package etalii.adp.it;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

/**
 * The generic FBL implementation is part of the plug-in (spec 010, FR-002): the plug-in zip
 * carries its classes, so every IDE the plug-in installs in has it, with nothing to download.
 * No IDE is started here; that the library runs on the platform is FblOnThePlatformTest's to show.
 */
class FblInPluginIntegrationTest {

    private static final String PACKAGE = "etalii/adp/fbl/";

    @Test
    void thePluginZipHoldsTheFblClasses() throws IOException {
        Path zip = Path.of(System.getProperty("adp.plugin.zip"));
        assertTrue(Files.isRegularFile(zip), "No plug-in zip at " + zip);
        List<String> classes = new ArrayList<>();
        try (InputStream in = Files.newInputStream(zip)) {
            collect(in, classes);
        }
        for (String needed : List.of("document/FblDocumentLoader.class", "history/OpenBody.class", "registration/OpenRegistration.class", "routing/Router.class")) {
            assertTrue(classes.contains(PACKAGE + needed), needed + " is not in " + zip + "; found " + classes.size() + " FBL classes");
        }
    }

    /** The class entries under the FBL package, descending into the jars the zip holds. */
    private static void collect(InputStream archive, List<String> classes) throws IOException {
        ZipInputStream zip = new ZipInputStream(archive);
        for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
            if (entry.getName().endsWith(".jar")) {
                collect(new ByteArrayInputStream(zip.readAllBytes()), classes);
            } else if (entry.getName().startsWith(PACKAGE) && entry.getName().endsWith(".class")) {
                classes.add(entry.getName());
            }
        }
    }
}
