package name.velikodniy.jcexpress.tlv;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tag constants against the specifications that define them.
 */
class TagsTest {

    /** GlobalPlatform Card Specification 2.3.1, Appendix H.2, Tables H-1 and H-2. */
    @Test
    void cardRecognitionDataTagsFollowTableH1() {
        assertThat(Tags.GP_CARD_DATA).isEqualTo(0x66);
        assertThat(Tags.GP_CARD_RECOGNITION_DATA).isEqualTo(0x73);
        assertThat(Tags.GP_OID).isEqualTo(0x06);
        assertThat(Tags.GP_CARD_MANAGEMENT_TYPE_VERSION).isEqualTo(0x60);
        assertThat(Tags.GP_CARD_IDENTIFICATION_SCHEME).isEqualTo(0x63);
        assertThat(Tags.GP_SECURE_CHANNEL_PROTOCOL).isEqualTo(0x64);
        assertThat(Tags.GP_CARD_CONFIG_DETAILS).isEqualTo(0x65);
        assertThat(Tags.GP_CARD_CHIP_DETAILS).isEqualTo(0x66);
        assertThat(Tags.GP_ISD_TRUST_POINT_CERTIFICATE_INFO).isEqualTo(0x67);
        assertThat(Tags.GP_ISD_CERTIFICATE_INFO).isEqualTo(0x68);
    }

    /** Application tags 0 to 8 ('60' to '68') are constructed (ISO/IEC 8825-1: b6 of the tag set). */
    @Test
    void applicationTagsOfTheRecognitionDataAreConstructed() {
        assertThat(Tags.isConstructed(Tags.GP_CARD_MANAGEMENT_TYPE_VERSION)).isTrue();
        assertThat(Tags.isConstructed(Tags.GP_SECURE_CHANNEL_PROTOCOL)).isTrue();
        assertThat(Tags.isConstructed(Tags.GP_OID)).isFalse();
    }
}
