package name.velikodniy.jcexpress.converter.translate;

import name.velikodniy.jcexpress.converter.Converter;
import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.converter.ConverterResult;

import java.nio.file.Path;
import java.util.HexFormat;

/**
 * A bytecode regression fixture: one Java Card package under {@code com.example.bytecode}
 * with one applet class, converted with its own package/applet AIDs.
 *
 * @param simplePackage last package name segment (e.g. {@code "arrlen"})
 * @param appletClass   simple name of the applet class
 * @param id            unique byte used in the fixture AIDs (RID F04A435843, test only)
 */
public record BytecodeFixture(String simplePackage, String appletClass, int id) {

    /** Compiled fixture classes produced by the Maven test-compile phase. */
    public static final Path TEST_CLASSES = Path.of("target/test-classes");

    /** Fully qualified package name. */
    public String packageName() {
        return "com.example.bytecode." + simplePackage;
    }

    /** Package AID (test-only RID F04A435843). */
    public String packageAid() {
        return "F04A435843" + HexFormat.of().toHexDigits((byte) id);
    }

    /** Converter builder preconfigured for this fixture. */
    public Converter.Builder builder(Path classes) {
        return Converter.builder()
                .classesDirectory(classes)
                .packageName(packageName())
                .packageAid(packageAid())
                .packageVersion(1, 0)
                .applet(packageName() + "." + appletClass, packageAid() + "01");
    }

    /** Converts the Maven-compiled fixture with the given Oracle compatibility setting. */
    public ConverterResult convert(boolean oracleCompatibility) throws ConverterException {
        return builder(TEST_CLASSES).oracleCompatibility(oracleCompatibility).build().convert();
    }

    @Override
    public String toString() {
        return simplePackage;
    }
}
