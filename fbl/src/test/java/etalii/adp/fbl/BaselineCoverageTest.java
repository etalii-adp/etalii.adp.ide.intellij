package etalii.adp.fbl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import etalii.adp.fbl.support.Corpus;

/**
 * Every test of standalone's FBL test project has a counterpart here (spec 010, FR-017, FR-018,
 * SC-002): each of the 86 rows of {@code fbl-test-inventory.md} names a test class and method
 * that exist, or is one of the four rows that cannot apply to this host, with its reason.
 */
class BaselineCoverageTest {

    private static final Pattern ROW = Pattern.compile("^\\| `([\\w/.]+)` \\| (?:`(\\w+)\\.(\\w+)`|(not applicable)) \\| (.*)\\|\\s*$");

    /** The baseline tests that run over files only standalone has. */
    private static final Set<String> NOT_APPLICABLE = Set.of(
            "RealFiles/Registrations.AW3CReadingsSuggestMatchesItsBody",
            "RealFiles/Registrations.AC4RegistrationReadsItsLegacyLayout",
            "RealFiles/Registrations.TheC4LegacyLayoutsArePositionedThroughTheirRegistrations",
            "RealFiles/Registrations.EveryChartFolderIsRecognisedAndEveryTurtleFileRoutesToTheTurtleBinding");

    @Test
    void everyBaselineTestHasItsCounterpart() throws IOException {
        Path tests = Path.of("src/test/java");
        Map<String, Path> classes = Corpus.files(tests).stream().filter(f -> f.endsWith(".java")).map(tests::resolve)
                .collect(Collectors.toMap(p -> p.getFileName().toString().replace(".java", ""), Function.identity()));

        List<String> problems = new ArrayList<>();
        Set<String> notApplicable = new TreeSet<>();
        int rows = 0;
        for (String line : Files.readAllLines(Corpus.root().resolve("baseline/fbl-test-inventory.md"), UTF_8)) {
            Matcher row = ROW.matcher(line);
            if (!row.matches()) {
                continue;
            }
            rows++;
            String baseline = row.group(1);
            if (row.group(4) != null) {
                notApplicable.add(baseline);
                if (row.group(5).isBlank()) {
                    problems.add(baseline + " is not applicable without a reason");
                }
                continue;
            }
            Path source = classes.get(row.group(2));
            if (source == null) {
                problems.add(baseline + " -> " + row.group(2) + " (no such test class)");
            } else if (!Pattern.compile("\\bvoid " + row.group(3) + "\\s*\\(").matcher(Files.readString(source, UTF_8)).find()) {
                problems.add(baseline + " -> " + row.group(2) + "." + row.group(3) + " (no such test method)");
            }
        }
        assertEquals(86, rows, "The baseline has 86 tests");
        assertEquals(new TreeSet<>(NOT_APPLICABLE), notApplicable);
        assertEquals(List.of(), problems);
    }
}
