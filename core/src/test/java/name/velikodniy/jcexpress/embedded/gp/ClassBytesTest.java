package name.velikodniy.jcexpress.embedded.gp;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The class byte in the secure channel: the classes of the two initiation commands (GlobalPlatform Card
 * Specification v2.3 Amendment D v1.1.2 Tables 7-2 and 7-5, coding of GlobalPlatform Card Specification v2.3.1
 * 11.1.4), the class byte of the C-MAC input (Amendment D 6.2.4) and the class byte of the unwrapped command.
 */
class ClassBytesTest {

    @ParameterizedTest
    @CsvSource({"80,true,false", "83,true,false", "84,false,true", "87,false,true", "88,false,false", "8C,false,false",
        "90,false,false", "94,false,false", "00,false,false", "04,false,false", "C0,true,false", "CF,true,false",
        "E0,false,true", "EF,false,true", "B0,false,false", "FF,false,false"})
    void initiationCommandsHaveTheirClasses_amdD_Table_7_2_and_7_5(String cla, boolean initializeUpdate,
                                                                    boolean externalAuthenticate) {
        int value = Integer.parseInt(cla, 16);

        assertThat(ClassBytes.initializeUpdate(value)).isEqualTo(initializeUpdate);
        assertThat(ClassBytes.externalAuthenticate(value)).isEqualTo(externalAuthenticate);
    }

    @ParameterizedTest
    @CsvSource({"84,84,80", "85,84,81", "87,84,83", "8C,84,80", "08,04,00", "04,04,00", "94,94,90", "E0,84,C0",
        "E5,84,C5", "60,04,40"})
    void theMacCoversChannelZeroWithGlobalPlatformSecureMessagingAndUnwrappingKeepsTheChannel_amdD_6_2_4(
            String cla, String forMac, String unwrapped) {
        int value = Integer.parseInt(cla, 16);

        assertThat(ClassBytes.secureMessaging(value)).isTrue();
        assertThat(ClassBytes.forMac(value)).isEqualTo(Integer.parseInt(forMac, 16));
        assertThat(ClassBytes.withoutSecureMessaging(value)).isEqualTo(Integer.parseInt(unwrapped, 16));
    }
}
