package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.testutil.CapInspector;
import name.velikodniy.jcexpress.converter.testutil.CapInspector.PackageRef;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * The Import component records, for every imported API package, the package version of the
 * target platform's API (JCVM 3.1 §4.5.2, §6.7): a CAP file only links if that version is not
 * newer than the one on the card. Expected values are the package versions declared by the API
 * export files of each Java Card release (also checked against the kits, when installed, by
 * {@code BuiltinExportsSdkComparisonTest}).
 */
class ImportVersionsPerTargetTest {

    private static final String LANG = "A0000000620001";
    private static final String FRAMEWORK = "A0000000620101";
    private static final String SECURITY = "A0000000620102";
    private static final String CRYPTO = "A0000000620201";

    @TempDir
    static Path classes;

    /** Uses java.lang, javacard.framework, javacard.security and javacardx.crypto of Java Card 2.1.2. */
    @BeforeAll
    static void compileProbe() {
        JavaSources.compile(classes, Map.of("com.acme.imports.CoreApi", """
                package com.acme.imports;
                import javacard.framework.*;
                import javacard.security.RandomData;
                import javacardx.crypto.Cipher;
                public class CoreApi extends Applet {
                    private final RandomData random = RandomData.getInstance(RandomData.ALG_PSEUDO_RANDOM);
                    private final Cipher cipher = Cipher.getInstance(Cipher.ALG_DES_CBC_NOPAD, false);
                    public static void install(byte[] b, short o, byte l) { new CoreApi().register(); }
                    public void process(APDU apdu) {
                        byte[] buf = apdu.getBuffer();
                        try {
                            Util.arrayFillNonAtomic(buf, (short) 0, (short) 8, (byte) 0);
                        } catch (ArithmeticException e) {
                            ISOException.throwIt(ISO7816.SW_UNKNOWN);
                        }
                    }
                }
                """));
    }

    static Stream<Arguments> releases() {
        // target, java.lang, javacard.framework, javacard.security, javacardx.crypto
        return Stream.of(
                arguments(JavaCardVersion.V2_1_2, "1.0", "1.0", "1.1", "1.1"),
                arguments(JavaCardVersion.V2_2_1, "1.0", "1.2", "1.2", "1.2"),
                arguments(JavaCardVersion.V2_2_2, "1.0", "1.3", "1.3", "1.3"),
                arguments(JavaCardVersion.V3_0_3, "1.0", "1.4", "1.4", "1.4"),
                arguments(JavaCardVersion.V3_0_4, "1.0", "1.5", "1.5", "1.5"),
                arguments(JavaCardVersion.V3_0_5, "1.0", "1.6", "1.6", "1.6"),
                arguments(JavaCardVersion.V3_1_0, "1.0", "1.8", "1.7", "1.7"),
                arguments(JavaCardVersion.V3_2_0, "1.0", "1.9", "1.8", "1.8"));
    }

    private static PackageRef ref(String version, String aid) {
        String[] v = version.split("\\.");
        return new PackageRef(Integer.parseInt(v[0]), Integer.parseInt(v[1]), aid);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("releases")
    void importComponentRecordsTheApiPackageVersionsOfTheTarget_jcvm31_4_5_2(
            JavaCardVersion target, String lang, String framework, String security, String crypto) throws Exception {
        ConverterResult result = Converter.builder()
                .classesDirectory(classes).packageName("com.acme.imports").packageAid("A000000FFE30")
                .applet("com.acme.imports.CoreApi", "A000000FFE3001")
                .javaCardVersion(target)
                .build().convert();

        assertThat(CapInspector.imports(result.capFile())).containsExactlyInAnyOrder(
                ref(lang, LANG), ref(framework, FRAMEWORK), ref(security, SECURITY), ref(crypto, CRYPTO));
    }
}
