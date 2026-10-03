package name.velikodniy.jcexpress.apdu;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.embedded.EmbeddedSession;
import name.velikodniy.jcexpress.model.CountingApplet;
import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link APDUCommand#select(AID)}: SELECT by DF name (ISO/IEC 7816-4:2005 7.1.1, P1 '04': select by DF name, P2 '00':
 * first or only occurrence, return FCI), with Le '00' so that the card returns its answer to SELECT (FCI or
 * application property template), which a test can assert.
 */
class APDUCommandSelectTest {

    private static final AID AID_1 = AID.fromHex("F0000000010203");

    @Test
    void selectsByDfNameAndAsksForTheWholeAnswer_iso7816_4_7_1_1() {
        APDUCommand select = APDUCommand.select(AID_1);

        assertThat(select.toString()).isEqualTo("00A4040007F000000001020300");
        assertThat(select).isEqualTo(new APDUCommand(0x00, 0xA4, 0x04, 0x00, AID_1.toBytes(), 256));
    }

    @Test
    void aNullAidIsRejected() {
        assertThatThrownBy(() -> APDUCommand.select(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("AID");
    }

    @Test
    void theAnswerToSelectCanBeAsserted() {
        try (EmbeddedSession card = new EmbeddedSession()) {
            card.install(CountingApplet.class, AID_1);

            APDUResponse fci = card.send(APDUCommand.select(AID_1));

            assertThat(fci).isSuccess().tlv().tag(0x6F).tag(0x84).hasValue(AID_1.toBytes());
        }
    }
}
