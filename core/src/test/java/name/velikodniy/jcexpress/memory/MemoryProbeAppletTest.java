package name.velikodniy.jcexpress.memory;

import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MemoryProbeApplet} and {@link MemoryInfo}.
 */
class MemoryProbeAppletTest {

    private EmbeddedSession session;

    @BeforeEach
    void setUp() {
        session = new EmbeddedSession(false);
        session.install(MemoryProbeApplet.class);
    }

    @AfterEach
    void tearDown() {
        session.close();
    }

    @Test
    void shouldReturnMemoryInfo() {
        APDUResponse response = session.send(0x80, 0x01);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.data()).hasSize(12);
    }

    @Test
    void shouldParseMemoryInfo() {
        APDUResponse response = session.send(0x80, 0x01);
        MemoryInfo info = MemoryInfo.from(response);

        // jCardSim should report non-negative values
        assertThat(info.persistent()).isGreaterThanOrEqualTo(0);
        assertThat(info.transientDeselect()).isGreaterThanOrEqualTo(0);
        assertThat(info.transientReset()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldHaveReadableToString() {
        APDUResponse response = session.send(0x80, 0x01);
        MemoryInfo info = MemoryInfo.from(response);
        assertThat(info.toString()).contains("persistent=");
        assertThat(info.toString()).contains("transientDeselect=");
        assertThat(info.toString()).contains("transientReset=");
    }

    @Test
    void shouldRejectUnsupportedIns() {
        APDUResponse response = session.send(0x80, 0xFF);
        assertThat(response.sw()).isEqualTo(0x6D00);
    }
}
