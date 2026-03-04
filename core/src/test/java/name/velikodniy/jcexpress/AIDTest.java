package name.velikodniy.jcexpress;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AIDTest {

    @Test
    void shouldCreateFromHex() {
        AID aid = AID.fromHex("A0000000031010");
        assertThat(aid.toHex()).isEqualTo("A0000000031010");
        assertThat(aid.toBytes()).hasSize(7);
    }

    @Test
    void shouldCreateFromIntValues() {
        AID aid = AID.of(0xA0, 0x00, 0x00, 0x00, 0x03, 0x10, 0x10);
        assertThat(aid.toHex()).isEqualTo("A0000000031010");
    }

    @Test
    void shouldGenerateAutoAid() {
        AID aid = AID.auto(HelloWorldApplet.class);
        assertThat(aid.toBytes()).hasSize(8);
        assertThat(aid.toBytes()[0]).isEqualTo((byte) 0xF0);
    }

    @Test
    void shouldGenerateDeterministicAid() {
        AID first = AID.auto(HelloWorldApplet.class);
        AID second = AID.auto(HelloWorldApplet.class);
        assertThat(first).isEqualTo(second);
        assertThat(first.toHex()).isEqualTo(second.toHex());
    }

    @Test
    void shouldSupportEquality() {
        AID a = AID.fromHex("A0000000031010");
        AID b = AID.of(0xA0, 0x00, 0x00, 0x00, 0x03, 0x10, 0x10);
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    void shouldHaveReadableToString() {
        AID aid = AID.fromHex("A0000000031010");
        assertThat(aid.toString()).contains("A0000000031010");
    }
}
