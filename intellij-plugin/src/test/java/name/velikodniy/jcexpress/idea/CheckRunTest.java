package name.velikodniy.jcexpress.idea;

import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** {@link CheckRun} with the toolkit's own javac check, as the plugin runs it in the IDE, without the IDE. */
public class CheckRunTest {

    private static final String PATH = "/project/src/main/java/com/example/wallet/WalletApplet.java";

    @Test
    public void aLongFieldIsAnErrorWhereTheBuildReportsIt_jcvm31_2_2_1_3() {
        CheckReport report = CheckRun.run(request(Applets.WITH_LONG, Map.of()), () -> { });

        assertEquals(report.note().toString(), 1, report.problems().size());
        CheckProblem problem = report.problems().get(0);
        assertTrue(problem.message(), problem.message().startsWith("Java Card: long type not supported"));
        assertEquals(PATH, problem.path());
        int[] range = problem.range(Applets.WITH_LONG);
        String marked = Applets.WITH_LONG.substring(range[0], range[1]);
        assertTrue("marked: " + marked, marked.contains("total"));
    }

    @Test
    public void aValidAppletHasNoProblem() {
        CheckReport report = CheckRun.run(request(Applets.VALID, Map.of()), () -> { });

        assertTrue(report.note().toString(), report.note().isEmpty());
        assertEquals(List.of(), report.problems());
    }

    @Test
    public void theBuildsOptionsReachTheCheck_jcvm31_4_5_2() {
        CheckReport onTheTarget = CheckRun.run(request(Applets.WITH_ARRAY_FILL,
                Map.of("javacard.version", "3.0.4")), () -> { });
        CheckReport onANewerOne = CheckRun.run(request(Applets.WITH_ARRAY_FILL,
                Map.of("javacard.version", "3.0.5")), () -> { });

        assertEquals(1, onTheTarget.problems().size());
        assertTrue(onTheTarget.problems().get(0).message(),
                onTheTarget.problems().get(0).message().contains("arrayFill"));
        assertEquals(List.of(), onANewerOne.problems());
    }

    @Test
    public void anExportPathNothingResolvedLeavesTheLinkingToTheConversion_jcvm31_4_3_3() {
        Map<String, String> options = Map.of("javacard.version", "3.0.4",
                "javacard.exportPath", "${session.executionRootDirectory}/ep");

        CheckReport subset = CheckRun.run(request(Applets.WITH_LONG, options), () -> { });
        CheckReport linking = CheckRun.run(request(Applets.WITH_ARRAY_FILL, options), () -> { });

        assertEquals(1, subset.problems().size());
        assertEquals(List.of(), linking.problems());
    }

    @Test
    public void javacsOwnErrorsAreLeftToTheIde() {
        CheckReport report = CheckRun.run(request(Applets.WITH_LONG.replace("return;", "return 1;"), Map.of()),
                () -> { });

        assertEquals(List.of(), report.problems());
    }

    @Test
    public void aCanceledRunStopsWithTheCancellation() {
        RuntimeException canceled = new IllegalStateException("canceled");
        try {
            CheckRun.run(request(Applets.WITH_LONG, Map.of()), () -> {
                throw canceled;
            });
            fail("the run went on");
        } catch (RuntimeException e) {
            assertSame(canceled, e);
        }
    }

    @Test
    public void theKeyFollowsTheTextAndTheSettings() {
        String key = request(Applets.VALID, Map.of()).key();

        assertEquals(key, request(Applets.VALID, Map.of()).key());
        assertNotEquals(key, request(Applets.WITH_LONG, Map.of()).key());
        assertNotEquals(key, request(Applets.VALID, Map.of("javacard.version", "3.0.4")).key());
    }

    private static CheckRequest request(String text, Map<String, String> options) {
        return new CheckRequest("wallet.main", Applets.PACKAGE, List.of(new CheckRequest.Source(PATH, text)),
                Applets.apiStubs(), Applets.processorPath(), options, 8);
    }
}
