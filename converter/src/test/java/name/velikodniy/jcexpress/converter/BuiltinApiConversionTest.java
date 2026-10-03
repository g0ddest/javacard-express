package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.testutil.CapInspector;
import name.velikodniy.jcexpress.converter.testutil.CapInspector.ExternalRef;
import name.velikodniy.jcexpress.converter.testutil.CapInspector.PackageRef;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conversion tests for everyday applet code that links against the built-in Java Card API:
 * the converter must know every public constructor and every inherited public method of an API
 * class (JCVM 3.1 §5.7 methods[]) and must import the API package versions of the target
 * platform (JCVM 3.1 §4.5.2, §6.7).
 */
class BuiltinApiConversionTest {

    private static final String FRAMEWORK = "A0000000620101";
    private static final String LANG = "A0000000620001";
    private static final String SECURITY = "A0000000620102";
    private static final String CRYPTO = "A0000000620201";

    private static final int TAG_CLASSREF = 1;
    private static final int TAG_VIRTUAL_METHODREF = 3;
    private static final int TAG_STATIC_METHODREF = 6;

    @TempDir
    Path classes;

    private ConverterResult convert(String source) throws ConverterException {
        JavaSources.compile(classes, Map.of("com.acme.probe.Probe", source));
        return Converter.builder()
                .classesDirectory(classes)
                .packageName("com.acme.probe")
                .packageAid("A000000FFE01")
                .applet("com.acme.probe.Probe", "A000000FFE0101")
                .build()
                .convert();
    }

    private static final String HEADER = """
            package com.acme.probe;
            import javacard.framework.*;
            public class Probe extends Applet {
                public static void install(byte[] b, short o, byte l) { new Probe().register(); }
            """;

    @Test
    void inheritedGetReasonOfAnApiExceptionUsesTheInheritedVirtualToken_jcvm31_5_7() throws Exception {
        ConverterResult result = convert(HEADER + """
                    public void process(APDU apdu) {
                        try { apdu.setIncomingAndReceive(); }
                        catch (APDUException e) { ISOException.throwIt(e.getReason()); }
                    }
                }
                """);

        // APDUException is class token 12 of javacard.framework; getReason()S keeps the token 1
        // it has in CardRuntimeException (JCVM 3.1 §4.3.7.6)
        assertThat(CapInspector.externalRefs(result.capFile()))
                .contains(new ExternalRef(TAG_VIRTUAL_METHODREF, FRAMEWORK, 12, 1));
    }

    @Test
    void constructorsOfApiExceptionsAreLinked_jcvm31_4_3_7_4() throws Exception {
        ConverterResult result = convert(HEADER + """
                    public void process(APDU apdu) {
                        byte p1 = apdu.getBuffer()[ISO7816.OFFSET_P1];
                        if (p1 == 1) throw new WrongPin();
                        if (p1 == 2) throw new NullPointerException();
                        if (p1 == 3) throw new Failure();
                    }
                }
                class WrongPin extends ISOException { WrongPin() { super((short) 0x6982); } }
                class Failure extends RuntimeException { }
                """);

        assertThat(CapInspector.externalRefs(result.capFile())).contains(
                // ISOException.<init>(S)V: static method token 0 of class token 7
                new ExternalRef(TAG_STATIC_METHODREF, FRAMEWORK, 7, 0),
                // new NullPointerException(): class token 7 of java.lang and its <init>()V
                new ExternalRef(TAG_CLASSREF, LANG, 7, -1),
                new ExternalRef(TAG_STATIC_METHODREF, LANG, 7, 0),
                // RuntimeException.<init>()V (class token 3)
                new ExternalRef(TAG_STATIC_METHODREF, LANG, 3, 0));
    }

    @ParameterizedTest(name = "{0}: security {1}, crypto {2}")
    @CsvSource({"V3_0_5, 1.6, 1.6", "V3_1_0, 1.7, 1.7", "V3_2_0, 1.8, 1.8"})
    void cryptoApiImportsUseTheVersionsOfTheTargetPlatform_jcvm31_4_5_2(
            JavaCardVersion version, String security, String crypto) throws Exception {
        ConverterResult result = Converter.builder()
                .classesDirectory(Path.of("target/test-classes"))
                .packageName("com.example.crypto")
                .packageAid("A00000006205")
                .applet("com.example.crypto.CryptoApplet", "A0000000620501")
                .javaCardVersion(version)
                .build()
                .convert();

        assertThat(CapInspector.imports(result.capFile()))
                .extracting(p -> p.aidHex() + " " + p.version())
                .contains(SECURITY + " " + security, CRYPTO + " " + crypto);
    }

    @Test
    void javaLangIsAlwaysImportedAsVersion10() throws Exception {
        ConverterResult result = convert(HEADER + """
                    public void process(APDU apdu) { }
                }
                """);

        assertThat(CapInspector.imports(result.capFile())).contains(new PackageRef(1, 0, LANG));
    }
}
