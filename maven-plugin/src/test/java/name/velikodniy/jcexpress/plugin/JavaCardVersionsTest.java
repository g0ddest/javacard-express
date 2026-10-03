package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The accepted spellings of {@code javaCardVersion}; anything else is an error, never a silent
 * fallback to 3.0.5.
 */
class JavaCardVersionsTest {

    @ParameterizedTest
    @CsvSource({
            "2.1.2, V2_1_2", "2.1, V2_1_2", "2.2.1, V2_2_1", "2.2.2, V2_2_2", "2.2, V2_2_2",
            "3.0.3, V3_0_3", "3.0.4, V3_0_4", "3.0.5, V3_0_5", "3.0, V3_0_5",
            "3.1.0, V3_1_0", "3.1, V3_1_0", "3.2.0, V3_2_0", "3.2, V3_2_0", "' 2.2.2 ', V2_2_2"})
    void supportedVersions(String text, JavaCardVersion expected) throws MojoExecutionException {
        assertThat(JavaCardVersions.parse(text)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"9.9.9", "", "3", "3.0.6", "3.3", "v3.0.5", "3.0.5-SNAPSHOT"})
    void otherValuesAreRejected(String text) {
        assertThatThrownBy(() -> JavaCardVersions.parse(text)).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("'" + text + "'")
                .hasMessageContaining("2.1.2, 2.2.1, 2.2.2, 3.0.3, 3.0.4, 3.0.5, 3.1.0, 3.2.0");
    }

    @Test
    void displayNameIsTheVersionNumber() {
        assertThat(JavaCardVersions.display(JavaCardVersion.V3_0_5)).isEqualTo("3.0.5");
        assertThat(JavaCardVersions.display(JavaCardVersion.V2_1_2)).isEqualTo("2.1.2");
    }
}
