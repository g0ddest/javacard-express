package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CardInfo}: the INITIALIZE UPDATE response layouts of GPCS v2.3.1 Table E-8 (SCP02) and
 * Amendment D Table 7-3 (SCP03), with responses of real cards.
 */
class CardInfoTest {

    @Test
    void table_e_8_scp02ResponseOfARealCard() {
        CardInfo info = CardInfo.parse(ScpTranscript.named("SCP02_real_card_i15_session1").initUpdate());

        assertThat(info.scpVersion()).isEqualTo(2);
        assertThat(info.keyVersion()).isEqualTo(0xFF);
        assertThat(info.diversificationHex()).isEqualTo("00005300000105000078");
        assertThat(Hex.encode(info.sequenceCounter())).isEqualTo("009C");
        assertThat(Hex.encode(info.cardChallenge())).isEqualTo("F65B8D459133");
        assertThat(Hex.encode(info.cardCryptogram())).isEqualTo("6273F9DBDB60709F");
        assertThat(info.implementationOption()).isEqualTo(-1);
        assertThat(info.toString()).contains("keyVersion=255").contains("scp=2").doesNotContain("i=");
    }

    @Test
    void table_7_3_scp03ResponseOfARealJcop4Card() {
        CardInfo info = CardInfo.parse(ScpTranscript.named("SCP03_real_JCOP4_i70").initUpdate());

        assertThat(info.scpVersion()).isEqualTo(3);
        assertThat(info.keyVersion()).isEqualTo(0x01);
        assertThat(info.implementationOption()).isEqualTo(0x70);
        assertThat(Hex.encode(info.cardChallenge())).isEqualTo("734ECDCA19E446A3");
        assertThat(Hex.encode(info.cardCryptogram())).isEqualTo("0BC253BCE97DB991");
        assertThat(Hex.encode(info.sequenceCounter())).isEqualTo("000436");
        assertThat(info.toString()).contains("i=70");
    }

    @Test
    void table_7_3_theDeprecatedPseudoRandomFlagNoLongerChangesTheLayout() {
        byte[] response = ScpTranscript.named("SCP03_AES128_i00_level01").initUpdate();

        @SuppressWarnings("deprecation")
        CardInfo info = CardInfo.parse(response, true);

        assertThat(info).isEqualTo(CardInfo.parse(response));
        assertThat(info.sequenceCounter()).isEmpty();
    }

    @Test
    void protocolCanBeForcedForNonCompliantCards() {
        byte[] response = ScpTranscript.named("SCP02_real_card_i15_session1").initUpdate().clone();
        response[11] = 0x01;

        assertThat(CardInfo.parse(response, 2).scpVersion()).isEqualTo(2);
        assertThatThrownBy(() -> CardInfo.parse(response))
                .isInstanceOf(GPException.class)
                .hasMessageContaining("Unsupported Secure Channel Protocol");
    }

    @Test
    void malformedResponsesAreRejected() {
        assertThatThrownBy(() -> CardInfo.parse(new byte[11])).isInstanceOf(GPException.class)
                .hasMessageContaining("too short");
        byte[] scp02 = new byte[29];
        scp02[11] = 0x02;
        assertThatThrownBy(() -> CardInfo.parse(scp02)).isInstanceOf(GPException.class)
                .hasMessageContaining("Table E-8");
    }
}
