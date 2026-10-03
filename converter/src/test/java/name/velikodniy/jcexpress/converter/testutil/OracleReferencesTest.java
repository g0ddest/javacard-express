package name.velikodniy.jcexpress.converter.testutil;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Oracle reference files are looked up outside the source tree (they must never be committed) and
 * their absence skips a comparison test instead of failing the build.
 */
class OracleReferencesTest {

    @TempDir
    Path tmp;

    @AfterEach
    void clearProperty() {
        System.clearProperty(OracleReferences.PROPERTY);
    }

    @Test
    void referencesLiveInTheIgnoredBuildDirectoryNotInTheSourceTree() {
        assertThat(OracleReferences.directory().normalize().toString().replace('\\', '/'))
                .endsWith("build/oracle-refs")
                .doesNotContain("src/");
    }

    @Test
    void theDirectoryCanBeConfigured() throws Exception {
        Files.write(tmp.resolve("oracle-X.cap"), new byte[] {1, 2, 3});
        System.setProperty(OracleReferences.PROPERTY, tmp.toString());

        assertThat(OracleReferences.directory()).isEqualTo(tmp);
        assertThat(OracleReferences.find("oracle-X.cap")).contains(new byte[] {1, 2, 3});
        assertThat(OracleReferences.require("oracle-X.cap")).containsExactly(1, 2, 3);
    }

    @Test
    void aMissingReferenceSkipsTheTest() {
        System.setProperty(OracleReferences.PROPERTY, tmp.toString());

        assertThat(OracleReferences.find("oracle-missing.cap")).isEmpty();
        assertThatThrownBy(() -> OracleReferences.require("oracle-missing.cap"))
                .isInstanceOf(TestAbortedException.class)
                .hasMessageContaining("tools/oracle/generate-oracle-refs.sh");
    }
}
