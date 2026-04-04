package name.velikodniy.jcexpress.converter;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class JavaCardVersionExportFormatTest {

    @ParameterizedTest
    @EnumSource(JavaCardVersion.class)
    void allVersionsHaveExportFormat(JavaCardVersion version) {
        // All supported JC versions produce export format 2.1
        assertThat(version.exportFormatMajor()).isEqualTo(2);
        assertThat(version.exportFormatMinor()).isEqualTo(1);
    }
}
