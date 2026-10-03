package name.velikodniy.jcexpress.converter;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Export file format per target (JCVM 3.1 §5.5): 2.1 for Java Card 2.1.2 to 3.0.5, whose kits
 * write and read 2.1 export files; 2.3 for Java Card 3.1 and 3.2, like their kits.
 */
class JavaCardVersionExportFormatTest {

    @ParameterizedTest
    @EnumSource(value = JavaCardVersion.class, names = {"V3_1_0", "V3_2_0"}, mode = EnumSource.Mode.EXCLUDE)
    void versionsUpTo3_0_5WriteExportFormat2_1(JavaCardVersion version) {
        assertThat(version.exportFormatMajor()).isEqualTo(2);
        assertThat(version.exportFormatMinor()).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = JavaCardVersion.class, names = {"V3_1_0", "V3_2_0"})
    void versions3_1AndLaterWriteExportFormat2_3(JavaCardVersion version) {
        assertThat(version.exportFormatMajor()).isEqualTo(2);
        assertThat(version.exportFormatMinor()).isEqualTo(3);
    }
}
