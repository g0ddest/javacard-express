package name.velikodniy.jcexpress.apdu;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Logical channel coding of CLA: ISO/IEC 7816-4:2005 5.1.1 Tables 2 and 3 (interindustry class) and
 * GlobalPlatform Card Specification 2.3.1 11.1.4 Tables 11-11 and 11-12 (proprietary class).
 */
class ClassByteTest {

    @Nested
    class Decoding {

        @ParameterizedTest(name = "CLA {0} -> channel {1}")
        @CsvSource({
                // ISO 7816-4 Table 2: 000x xxxx, channel in b2-b1
                "0x00, 0", "0x01, 1", "0x02, 2", "0x03, 3", "0x0C, 0", "0x0F, 3", "0x13, 3",
                // ISO 7816-4 Table 3: 01xx xxxx, channel = b4-b1 + 4
                "0x40, 4", "0x41, 5", "0x4F, 19", "0x60, 4", "0x7F, 19",
                // GP Table 11-11: 100x xxxx, channel in b2-b1
                "0x80, 0", "0x83, 3", "0x84, 0", "0x87, 3",
                // GP Table 11-12: 11xx xxxx, channel = b4-b1 + 4
                "0xC0, 4", "0xCF, 19", "0xE0, 4", "0xEF, 19",
        })
        void channelOfClassByte(int cla, int channel) {
            assertThat(ClassByte.codesChannel(cla)).isTrue();
            assertThat(ClassByte.channel(cla)).isEqualTo(channel);
        }

        /** 'FF' is invalid (ISO/IEC 7816-3), 001x xxxx is RFU, 101x xxxx is proprietary without GP coding. */
        @ParameterizedTest(name = "CLA {0} codes no channel")
        @ValueSource(ints = {0xFF, 0x20, 0x23, 0x3F, 0xA0, 0xA3, 0xBF})
        void classBytesWithoutChannelCodingReferToTheBasicChannel(int cla) {
            assertThat(ClassByte.codesChannel(cla)).isFalse();
            assertThat(ClassByte.channel(cla)).isZero();
        }

        @Test
        void interindustryClass() {
            assertThat(ClassByte.isInterindustry(0x00)).isTrue();
            assertThat(ClassByte.isInterindustry(0x4F)).isTrue();
            assertThat(ClassByte.isInterindustry(0x20)).isFalse();
            assertThat(ClassByte.isInterindustry(0x80)).isFalse();
            assertThat(ClassByte.isInterindustry(0xC1)).isFalse();
        }

        @Test
        void acceptsTheByteConstantsOfAnApplet() {
            assertThat(ClassByte.channel((byte) 0x83)).isEqualTo(3);
            assertThat(ClassByte.codesChannel((byte) 0xFF)).isFalse();
            assertThat(ClassByte.isInterindustry((byte) 0x80)).isFalse();
            assertThat(ClassByte.withChannel((byte) 0x80, 1)).isEqualTo(0x81);
            assertThat(ClassByte.withChannel((byte) 0xFF, 0)).isEqualTo(0xFF);
        }

        @Test
        void rejectsValuesThatAreNotAByte() {
            assertThatThrownBy(() -> ClassByte.channel(0x100)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ClassByte.channel(-129)).isInstanceOf(IllegalArgumentException.class);
        }

        /**
         * ISO 7816-4 Table 2: b4-b3 != 00 indicates SM; Table 3: b6 = 1; GP Tables 11-11/11-12: same bits
         * (b3 = '04' is the GlobalPlatform SM indication).
         */
        @ParameterizedTest(name = "CLA {0} indicates secure messaging: {1}")
        @CsvSource({
                "0x00, false", "0x03, false", "0x04, true", "0x08, true", "0x0C, true", "0x0D, true", "0x1C, true",
                "0x40, false", "0x4F, false", "0x60, true", "0x61, true", "0x7F, true",
                "0x80, false", "0x84, true", "0x87, true", "0xC0, false", "0xE0, true",
                "0x20, false", "0xA4, false", "0xFF, false",
        })
        void secureMessagingIndication(int cla, boolean sm) {
            assertThat(ClassByte.indicatesSecureMessaging(cla)).isEqualTo(sm);
        }
    }

    @Nested
    class Encoding {

        @ParameterizedTest(name = "withChannel({0}, {1}) = {2}")
        @CsvSource({
                // channels 0-3: Table 2 / Table 11-11, other bits kept
                "0x00, 0, 0x00", "0x00, 1, 0x01", "0x00, 3, 0x03",
                "0x03, 0, 0x00", "0x03, 1, 0x01",      // existing channel bits are replaced
                "0x0C, 2, 0x0E", "0x04, 1, 0x05",      // SM indication b4-b3 kept
                "0x10, 1, 0x11",                       // chaining b5 kept
                "0x80, 2, 0x82", "0x84, 3, 0x87",
                // channels 4-19: Table 3 / Table 11-12, b4-b1 = channel - 4
                "0x00, 4, 0x40", "0x00, 19, 0x4F", "0x01, 5, 0x41", "0x10, 4, 0x50",
                "0x80, 4, 0xC0", "0x83, 19, 0xCF",
                // secure messaging: b4-b3 != 00 (Table 2) <-> b6 = 1 (Table 3)
                "0x0C, 4, 0x60", "0x08, 7, 0x63", "0x84, 5, 0xE1",
                "0x60, 1, 0x09",                       // ISO: b6 = 1 means b4-b3 = 10 (header not processed)
                "0xE0, 1, 0x85",                       // GP: b6 = 1 -> GP secure messaging b4-b3 = 01
                // further coding to further coding
                "0x41, 6, 0x42", "0xC0, 19, 0xCF", "0x40, 0, 0x00", "0xC5, 2, 0x82",
        })
        void recodesTheChannel(int cla, int channel, int expected) {
            int encoded = ClassByte.withChannel(cla, channel);
            assertThat(encoded).isEqualTo(expected);
            assertThat(ClassByte.channel(encoded)).isEqualTo(channel);
        }

        @Test
        void classBytesWithoutChannelCodingStayOnTheBasicChannel() {
            assertThat(ClassByte.withChannel(0xFF, 0)).isEqualTo(0xFF);
            assertThat(ClassByte.withChannel(0xA0, 0)).isEqualTo(0xA0);
            assertThatThrownBy(() -> ClassByte.withChannel(0xA0, 1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("A0");
        }

        @Test
        void channelNumbersAboveNineteenAreRejected() {
            assertThatThrownBy(() -> ClassByte.withChannel(0x00, 20)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ClassByte.withChannel(0x00, -1)).isInstanceOf(IllegalArgumentException.class);
        }

        /** Proprietary class, channel 19, secure messaging and chaining would give 'FF', which is invalid. */
        @Test
        void neverProducesTheInvalidClassFF() {
            assertThatThrownBy(() -> ClassByte.withChannel(0x9C, 19))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("FF");
        }
    }
}
