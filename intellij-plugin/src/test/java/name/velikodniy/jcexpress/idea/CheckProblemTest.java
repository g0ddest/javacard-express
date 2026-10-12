package name.velikodniy.jcexpress.idea;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class CheckProblemTest {

    private static final String TEXT = "private long total;";

    @Test
    public void aRangeIsKeptWhenItFits() {
        assertArrayEquals(new int[] {8, 12}, new CheckProblem("f", 8, 12, "m").range(TEXT));
    }

    @Test
    public void anEmptyRangeCoversTheWordAtItsStart() {
        assertArrayEquals(new int[] {13, 18}, new CheckProblem("f", 13, 13, "m").range(TEXT));
    }

    @Test
    public void aRangeBeyondTheTextIsCutToIt() {
        assertArrayEquals(new int[] {18, 19}, new CheckProblem("f", 18, 40, "m").range(TEXT));
        assertArrayEquals(new int[] {19, 19}, new CheckProblem("f", 50, 60, "m").range(TEXT));
    }

    @Test
    public void processorOptionsAreReadFromJavacArguments() {
        Map<String, String> options = new java.util.TreeMap<>();
        ProcessorOptions.addFrom(java.util.List.of("-Xlint:-options", "-Ajavacard.version=3.0.4", "-Aflag",
                "-Ajavacard.version=3.0.5"), options);

        assertEquals(Map.of("javacard.version", "3.0.5", "flag", ""), options);
    }
}
