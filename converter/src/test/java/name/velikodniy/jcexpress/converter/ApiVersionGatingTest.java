package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.testutil.CapInspector;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A CAP file can only link against the API of the platform it targets (JCVM 3.1 §4.3.5,
 * §4.5.2): references to API packages, classes or members that the target Java Card version does
 * not have must stop the conversion with an error that names the place of the reference and the
 * version that introduced the element.
 */
class ApiVersionGatingTest {

    private static final String EXTENDED_LENGTH = """
            package javacardx.apdu;
            public interface ExtendedLength { }
            """;

    @TempDir
    Path classes;

    private ConverterResult convert(JavaCardVersion version, String probe) throws ConverterException {
        return convert(version, probe, Map.of());
    }

    /**
     * Compiles the probe (plus API classes that the stub jar lacks, standing in for a newer API
     * jar than the target) and converts package {@code com.acme.probe}.
     */
    private ConverterResult convert(JavaCardVersion version, String probe, Map<String, String> api)
            throws ConverterException {
        Map<String, String> sources = new LinkedHashMap<>(api);
        sources.put("com.acme.probe.Probe", probe);
        sources.put("javacardx.apdu.ExtendedLength", EXTENDED_LENGTH);
        JavaSources.compile(classes, sources);
        return Converter.builder()
                .classesDirectory(classes)
                .packageName("com.acme.probe")
                .packageAid("A000000FFE01")
                .applet("com.acme.probe.Probe", "A000000FFE0101")
                .javaCardVersion(version)
                .build()
                .convert();
    }

    private static String applet(String body) {
        return """
                package com.acme.probe;
                import javacard.framework.*;
                public class Probe extends Applet {
                    public static void install(byte[] b, short o, byte l) { new Probe().register(); }
                    public void process(APDU apdu) {
                        byte[] buf = apdu.getBuffer();
                """ + body + """
                    }
                }
                """;
    }

    @Test
    void methodIntroducedLaterIsRejectedWithItsLocationAndVersion() {
        String probe = applet("        Util.arrayFill(buf, (short) 0, (short) 4, (byte) 0);\n");

        assertThatThrownBy(() -> convert(JavaCardVersion.V2_2_2, probe))
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("com.acme.probe.Probe.process(Ljavacard/framework/APDU;)V (Probe.java:7)")
                .hasMessageContaining("javacard.framework.Util.arrayFill([BSSB)S is not available in the "
                        + "Java Card 2.2.2 API (javacard.framework 1.3)")
                .hasMessageContaining("introduced in Java Card 3.0.5");
    }

    @Test
    void sameMethodLinksForTheVersionThatIntroducedIt() throws ConverterException {
        String probe = applet("        Util.arrayFill(buf, (short) 0, (short) 4, (byte) 0);\n");

        ConverterResult result = convert(JavaCardVersion.V3_0_5, probe);

        assertThat(CapInspector.imports(result.capFile()))
                .extracting(p -> p.aidHex() + " " + p.version())
                .contains("A0000000620101 1.6");
    }

    @Test
    void overloadIntroducedLaterIsNotReplacedByAnOlderOverload() {
        String signature = """
                package javacard.security;
                public abstract class Signature {
                    protected Signature() { }
                    public static final Signature getInstance(byte a, boolean e) { return null; }
                    public static final Signature getInstance(byte m, byte c, byte p, boolean e) { return null; }
                }
                """;
        String probe = applet("        javacard.security.Signature.getInstance((byte) 2, (byte) 1, (byte) 1, false);\n");

        assertThatThrownBy(() -> convert(JavaCardVersion.V2_2_2, probe,
                Map.of("javacard.security.Signature", signature)))
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("javacard.security.Signature.getInstance(BBBZ)Ljavacard/security/Signature;"
                        + " is not available in the Java Card 2.2.2 API (javacard.security 1.3)")
                .hasMessageContaining("introduced in Java Card 3.0.4");
    }

    @Test
    void packageIntroducedLaterIsRejected() {
        String probe = """
                package com.acme.probe;
                import javacard.framework.*;
                public class Probe extends Applet implements javacardx.apdu.ExtendedLength {
                    public static void install(byte[] b, short o, byte l) { new Probe().register(); }
                    public void process(APDU apdu) { }
                }
                """;

        assertThatThrownBy(() -> convert(JavaCardVersion.V2_2_1, probe))
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("com.acme.probe.Probe: package javacardx.apdu is not part of the "
                        + "Java Card 2.2.1 API (it was introduced in Java Card 2.2.2)");
    }

    @Test
    void inheritedApiMethodCalledThroughAnOwnClassIsCheckedToo() {
        String appletApi = """
                package javacard.framework;
                public abstract class Applet {
                    protected Applet() { }
                    protected final void register() { }
                    public abstract void process(APDU apdu);
                    protected static final boolean reSelectingApplet() { return false; }
                }
                """;
        String probe = """
                package com.acme.probe;
                import javacard.framework.*;
                public class Probe extends Base {
                    public static void install(byte[] b, short o, byte l) { new Probe().register(); }
                    public void process(APDU apdu) {
                        if (Probe.reSelectingApplet()) return;
                    }
                }
                abstract class Base extends Applet { }
                """;

        assertThatThrownBy(() -> convert(JavaCardVersion.V3_0_3, probe,
                Map.of("javacard.framework.Applet", appletApi)))
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("com.acme.probe.Probe.process(Ljavacard/framework/APDU;)V (Probe.java:6)")
                .hasMessageContaining("javacard.framework.Applet.reSelectingApplet()Z is not available in the "
                        + "Java Card 3.0.3 API")
                .hasMessageContaining("introduced in Java Card 3.0.4");
    }

    @Test
    void referenceToANonApiClassOfAnApiPackageIsRejected() {
        // a class that a non-standard API jar adds to javacard.framework has no entry in its export file
        String unofficial = """
                package javacard.framework;
                public class Unofficial { public static void touch() { } }
                """;
        String probe = applet("        Unofficial.touch();\n");

        assertThatThrownBy(() -> convert(JavaCardVersion.V3_0_5, probe,
                Map.of("javacard.framework.Unofficial", unofficial)))
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("com.acme.probe.Probe.process(Ljavacard/framework/APDU;)V (Probe.java:7)")
                .hasMessageContaining("javacard.framework.Unofficial is not available in the Java Card 3.0.5 API"
                        + " (javacard.framework 1.6)");
    }
}
