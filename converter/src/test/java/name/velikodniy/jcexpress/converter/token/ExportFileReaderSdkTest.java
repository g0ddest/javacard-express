package name.velikodniy.jcexpress.converter.token;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.testutil.OracleSdkExports;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Black-box interoperability check: every API export file shipped with the Oracle Java Card
 * kits 2.1.2-3.2.0 (formats 2.1, 2.2 and 2.3) must be readable by {@link ExportFileReader}.
 * Skipped when the kits are not installed locally.
 */
@EnabledIf("name.velikodniy.jcexpress.converter.testutil.OracleSdkExports#allAvailable")
class ExportFileReaderSdkTest {

    @ParameterizedTest
    @EnumSource(JavaCardVersion.class)
    void readsEveryApiExportFileOfTheKit(JavaCardVersion version) throws IOException {
        Map<String, byte[]> files = OracleSdkExports.read(version);
        assertThat(files).isNotEmpty();

        for (var entry : files.entrySet()) {
            ExportFile ef = ExportFileReader.read(entry.getValue());
            String expectedPackage = entry.getKey().substring(0, entry.getKey().indexOf("/javacard/"));
            assertThat(ef.packageName()).as(entry.getKey()).isEqualTo(expectedPackage);
            assertThat(ef.majorVersion()).as("package major of " + entry.getKey()).isEqualTo(1);
            assertThat(ef.formatMajor()).as(entry.getKey()).isEqualTo(2);
            assertThat(ef.classes()).as(entry.getKey()).isNotEmpty();
        }
    }
}
