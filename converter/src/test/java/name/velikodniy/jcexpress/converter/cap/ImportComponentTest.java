package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.resolve.ImportedPackage;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Import component (JCVM 3.1 §6.7): {@code count} and one package_info (minor, major, AID) per
 * imported package; the count is between 0 and 128 (package tokens 0..127, §4.3.7.1).
 */
class ImportComponentTest {

    private static ImportedPackage pkg(int token, String aidHex, int major, int minor) {
        byte[] aid = HexFormat.of().parseHex(aidHex);
        return new ImportedPackage(token, aid, major, minor, new ExportFile("p" + token, aid, major, minor, List.of()));
    }

    @Test
    void packageInfoHoldsMinorThenMajorVersionAndTheAid_jcvm31_6_7() {
        byte[] component = ImportComponent.generate(List.of(
                pkg(0, "A0000000620101", 1, 6), pkg(1, "A0000000620001", 1, 0)));

        assertThat(HexFormat.of().withUpperCase().formatHex(component)).isEqualTo("040015" + "02"
                + "06" + "01" + "07" + "A0000000620101"
                + "00" + "01" + "07" + "A0000000620001");
    }

    @Test
    void moreThan128ImportedPackagesAreRejected_jcvm31_6_7() {
        List<ImportedPackage> imports = new ArrayList<>();
        for (int i = 0; i < 129; i++) {
            imports.add(pkg(i, String.format("A0000000FE%04X", i), 1, 0));
        }

        assertThatThrownBy(() -> ImportComponent.generate(imports))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("129 imported packages")
                .hasMessageContaining("at most 128");
    }
}
