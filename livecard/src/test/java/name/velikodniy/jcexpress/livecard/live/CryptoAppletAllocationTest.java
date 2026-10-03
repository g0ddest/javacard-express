package name.velikodniy.jcexpress.livecard.live;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Only a missing algorithm makes a crypto case "not supported by this card": the CryptoApplet answers a
 * CryptoException from creating an algorithm's objects with SW '6F0R' (R = its reason code), and the live test
 * aborts only for NO_SUCH_ALGORITHM (3). Any other answer (another reason, '6F00' for an exception that is no
 * CryptoException, such as SystemException.NO_RESOURCE) fails, so a regression cannot hide as "not supported".
 * Offline, on jCardSim.
 */
class CryptoAppletAllocationTest {

    private static final AID CRYPTO = AID.fromHex("F04A4358010301");
    private EmbeddedSession session;

    @BeforeEach
    void install() {
        session = new EmbeddedSession();
        session.install(TestApplet.CRYPTO.load(), CRYPTO);
        session.select(CRYPTO);
    }

    @AfterEach
    void close() {
        session.close();
    }

    /** INS '1A' asks for an algorithm no card has (code 0x7F): the positive control of the mapping. */
    @Test
    void algorithmTheCardLacksAbortsWithTheReason() {
        assertThat(session.send(0x80, 0x1F, 0x1A, 0x00).sw()).isEqualTo(CryptoApiLiveTest.SW_NO_SUCH_ALGORITHM);
        assertThatThrownBy(() -> CryptoApiLiveTest.requireSupported(session, 0x1A, "probe"))
                .isInstanceOf(TestAbortedException.class)
                .hasMessageContaining("not supported by this card: probe (CryptoException.NO_SUCH_ALGORITHM");
    }

    @Test
    void supportedAlgorithmPasses() {
        CryptoApiLiveTest.requireSupported(session, 0x12, "SHA-256");
    }

    @Test
    void otherAnswersFail() {
        assertThatThrownBy(() -> CryptoApiLiveTest.requireSupported(session, 0x77, "unknown INS"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("SW 6A86");
        assertThat(CryptoApiLiveTest.explain(0x6F00)).contains("not a CryptoException");
        assertThat(CryptoApiLiveTest.explain(0x6F01)).contains("CryptoException reason 1 (ILLEGAL_VALUE)");
        assertThat(CryptoApiLiveTest.explain(0x6F05)).contains("CryptoException reason 5 (ILLEGAL_USE)");
    }
}
