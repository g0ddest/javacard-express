package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.gp.CAPFile;
import name.velikodniy.jcexpress.livecard.live.TestApplet;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The conversion options of {@link AppletPackage} reach the converter: int support and the Export component show
 * in the Header component's flags (JCVM 3.0.5 6.4: ACC_INT '01', ACC_EXPORT '02').
 */
class AppletPackageTest {

    private static final int ACC_INT = 0x01;
    private static final int ACC_EXPORT = 0x02;
    private final LiveCardConfig config = LiveCardConfig.of(Map.of());

    @Test
    void defaultConversionHasNeitherIntSupportNorExportComponent() throws ConverterException {
        AppletPackage pkg = TestApplet.HELLO.pkg(config);

        assertThat(pkg.intSupport()).isFalse();
        assertThat(pkg.exportComponent()).isFalse();
        assertThat(pkg.exportPath()).isEmpty();
        assertThat(headerFlags(pkg) & (ACC_INT | ACC_EXPORT)).isZero();
    }

    @Test
    void intSupportSetsAccInt() throws ConverterException {
        assertThat(headerFlags(TestApplet.INT_OPS.pkg(config)) & ACC_INT).isEqualTo(ACC_INT);
    }

    @Test
    void exportComponentSetsAccExport() throws ConverterException {
        assertThat(headerFlags(TestApplet.SIO_SERVER.pkg(config)) & ACC_EXPORT).isEqualTo(ACC_EXPORT);
    }

    @Test
    void withersKeepTheOtherSettings() {
        AppletPackage pkg = TestApplet.HELLO.pkg(config).withIntSupport().withExportComponent()
                .withExportPath(Path.of("a")).withExportPath(Path.of("b")).withVersion(2, 1);

        assertThat(pkg.intSupport()).isTrue();
        assertThat(pkg.exportComponent()).isTrue();
        assertThat(pkg.exportPath()).containsExactly(Path.of("a"), Path.of("b"));
        assertThat(pkg.majorVersion()).isEqualTo(2);
        assertThat(pkg.modules()).hasSize(1);
    }

    /** The flags byte of the Header component: tag, size (2), magic (4), minor, major, flags. */
    private int headerFlags(AppletPackage pkg) throws ConverterException {
        byte[] cap = pkg.converter(config.javaCardVersion()).build().convert().capFile();
        return CAPFile.from(cap).code()[9] & 0xFF;
    }
}
