package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.testutil.CapInspector;
import name.velikodniy.jcexpress.converter.testutil.CapInspector.PackageRef;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JCVM 3.1 §6.7: the Import component lists every package referenced by the CAP file, including
 * packages whose classes are named only by class_info/interface_info (§6.9: superclass and
 * implemented interfaces) or by type descriptors (§6.14), not by any bytecode - and no package that
 * the CAP file does not reference (java.lang excepted, which is always imported for Java Card
 * 2.2.2 and later, like Oracle's converter does).
 */
class StructuralImportsTest {

    private static final String APDU_PACKAGE = "A0000000620209";
    private static final String APDU_UTIL_PACKAGE = "A000000062020901";
    private static final String JAVA_LANG = "A0000000620001";

    @TempDir
    Path classes;

    private ConverterResult convert(String probe) throws ConverterException {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com.acme.probe.Probe", probe);
        // the stub jar lacks these optional 3.0.5 API types
        sources.put("javacardx.apdu.ExtendedLength", "package javacardx.apdu; public interface ExtendedLength { }");
        sources.put("javacardx.apdu.util.APDUUtil",
                "package javacardx.apdu.util; public class APDUUtil { private APDUUtil() { } }");
        JavaSources.compile(classes, sources);
        return Converter.builder()
                .classesDirectory(classes)
                .packageName("com.acme.probe")
                .packageAid("A000000FFE01")
                .applet("com.acme.probe.Probe", "A000000FFE0101")
                .build()
                .convert();
    }

    @Test
    void packageOfAnInterfaceThatIsOnlyImplementedIsImported_jcvm31_6_7() throws Exception {
        ConverterResult result = convert("""
                package com.acme.probe;
                import javacard.framework.*;
                public class Probe extends Applet implements javacardx.apdu.ExtendedLength {
                    public static void install(byte[] b, short o, byte l) { new Probe().register(); }
                    public void process(APDU apdu) { }
                }
                """);

        assertThat(CapInspector.imports(result.capFile())).contains(new PackageRef(1, 0, APDU_PACKAGE));
    }

    @Test
    void packageOfAClassThatOnlyAppearsInADescriptorIsImported_jcvm31_6_14() throws Exception {
        ConverterResult result = convert("""
                package com.acme.probe;
                import javacard.framework.*;
                public class Probe extends Applet {
                    private javacardx.apdu.util.APDUUtil unused;
                    public static void install(byte[] b, short o, byte l) { new Probe().register(); }
                    public void process(APDU apdu) { }
                }
                """);

        assertThat(CapInspector.imports(result.capFile())).contains(new PackageRef(1, 0, APDU_UTIL_PACKAGE));
    }

    @ParameterizedTest
    @EnumSource(JavaCardVersion.class)
    void libraryThatUsesNoFrameworkClassDoesNotImportTheFramework_jcvm31_6_7(JavaCardVersion version)
            throws Exception {
        JavaSources.compile(classes, Map.of("com.acme.math.MathLib", """
                package com.acme.math;
                public class MathLib {
                    public static short add(short a, short b) { return (short) (a + b); }
                }
                """));

        ConverterResult result = Converter.builder()
                .classesDirectory(classes)
                .packageName("com.acme.math")
                .packageAid("A000000FFE30")
                .javaCardVersion(version)
                .build()
                .convert();

        // java.lang only (MathLib extends Object); javacard.framework would be a dependency on a package the
        // library never uses, e.g. framework 1.6 for a 3.0.5 build that could otherwise load on a 3.0.4 card
        assertThat(CapInspector.imports(result.capFile())).containsExactly(new PackageRef(1, 0, JAVA_LANG));
    }
}
