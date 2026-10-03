package name.velikodniy.jcexpress.model;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Execution;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * The transcript of a failed test marks where the test method starts: after the installs of its applets and their
 * automatic SELECT, which the transcript shows before the mark, and after the {@code @BeforeEach} methods.
 */
class TestBodyMarkerTest {

    @Test
    void theFailureTranscriptMarksWhereTheTestBodyStarts() {
        List<Throwable> failures = EngineTestKit.engine("junit-jupiter")
                .configurationParameters(Map.of(OnlyInTestKit.PARAMETER, "true"))
                .selectors(selectClass(ModelScenarios.Failing.class)).execute()
                .allEvents().executions().failed().stream().map(Execution::getTerminationInfo)
                .map(info -> info.getExecutionResult().getThrowable().orElseThrow()).toList();

        assertThat(failures).singleElement().satisfies(failure -> assertThat(Arrays.stream(failure.getSuppressed())
                .map(Throwable::getMessage)).anySatisfy(transcript -> {
                    int install = transcript.indexOf("# install " + ModelApplet.class.getName());
                    int body = transcript.indexOf("# test body: fails()");
                    int command = transcript.indexOf("C: 801000000");
                    assertThat(List.of(install, body, command)).doesNotContain(-1).isSorted();
                }));
    }
}
