package name.velikodniy.jcexpress.converter;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The version numbers a build configuration may name, and the short forms of the Maven plugin. */
class JavaCardVersionParseTest {

    @ParameterizedTest
    @CsvSource({"2.1.2, V2_1_2", "2.1, V2_1_2", "2.2.1, V2_2_1", "2.2.2, V2_2_2", "2.2, V2_2_2", "3.0.3, V3_0_3",
            "3.0.4, V3_0_4", "3.0.5, V3_0_5", "3.0, V3_0_5", "' 3.0.4 ', V3_0_4", "3.1.0, V3_1_0", "3.1, V3_1_0",
            "3.2.0, V3_2_0", "3.2, V3_2_0"})
    void versionNumbersNameTheirPlatform(String text, JavaCardVersion version) {
        assertThat(JavaCardVersion.parse(text)).contains(version);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "3", "3.0.6", "V3_0_5", "3.0.5-SNAPSHOT"})
    void anythingElseIsNoPlatform(String text) {
        assertThat(JavaCardVersion.parse(text)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"V2_1_2", "V2_2_1", "V2_2_2", "V3_0_3", "V3_0_4", "V3_0_5", "V3_1_0", "V3_2_0"})
    void everyPlatformParsesFromItsSpecificationVersion(JavaCardVersion version) {
        assertThat(JavaCardVersion.parse(version.specVersion())).contains(version);
    }
}
