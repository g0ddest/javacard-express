package name.velikodniy.jcexpress.converter.input;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PackageScannerTest {

    @Test
    void shouldScanPackageFromTestClasses() throws IOException {
        // The test output directory contains compiled com.example.TestApplet
        Path classesDir = Path.of("target/test-classes");
        PackageInfo pkg = PackageScanner.scan(classesDir, "com.example");

        assertThat(pkg.packageName()).isEqualTo("com.example");
        assertThat(pkg.internalName()).isEqualTo("com/example");
        assertThat(pkg.classes()).isNotEmpty();
        assertThat(pkg.classes())
                .extracting(ClassInfo::thisClass)
                .contains("com/example/TestApplet");
    }

    @Test
    void shouldThrowOnMissingPackage() {
        Path classesDir = Path.of("target/test-classes");

        assertThatThrownBy(() -> PackageScanner.scan(classesDir, "no.such.package"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not found");
    }
}
