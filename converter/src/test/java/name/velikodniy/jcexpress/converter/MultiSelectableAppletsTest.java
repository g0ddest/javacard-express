package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.testutil.CapInspector;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import name.velikodniy.jcexpress.converter.translate.OracleVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JCVM 3.1 §2.2.5: "Applets that implement the javacard.framework.MultiSelectable interface are called
 * multiselectable applets ... All applets within a CAP file shall be multiselectable, or none shall be."
 * An applet implements the interface directly, through a superclass of the package or of an imported
 * package (its export entry lists the implemented interfaces, §5.7 {@code interfaces[]}), or through an
 * interface that extends it.
 */
class MultiSelectableAppletsTest {

    @TempDir
    Path classes;

    @TempDir
    Path work;

    private static final String SELECT_METHODS = """
                public boolean select(boolean appInstAlreadyActive) { return true; }
                public void deselect(boolean appInstStillActive) { }
            """;

    private static String applet(String pkg, String name, String declaration, String body) {
        return "package " + pkg + ";\nimport javacard.framework.*;\npublic class " + name + " " + declaration
                + " {\n    public static void install(byte[] b, short o, byte l) { new " + name
                + "().register(); }\n    public void process(APDU apdu) { }\n" + body + "}\n";
    }

    private static String plain(String pkg, String name) {
        return applet(pkg, name, "extends Applet", "");
    }

    private static String multi(String pkg, String name) {
        return applet(pkg, name, "extends Applet implements MultiSelectable", SELECT_METHODS);
    }

    private Converter.Builder convert(String pkg, String aid, String... applets) {
        Converter.Builder b = Converter.builder().classesDirectory(classes).packageName(pkg).packageAid(aid);
        for (int i = 0; i < applets.length; i++) {
            b.applet(pkg + "." + applets[i], aid + "0" + (i + 1));
        }
        return b;
    }

    @Test
    void aMultiselectableAndAPlainAppletInOneCapFileAreRejected_2_2_5() {
        JavaSources.compile(classes, Map.of(
                "com.acme.mix.Multi", multi("com.acme.mix", "Multi"),
                "com.acme.mix.Plain", plain("com.acme.mix", "Plain")));

        assertThatThrownBy(() -> convert("com.acme.mix", "A000000FFE80", "Multi", "Plain").build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("§2.2.5")
                .hasMessageContaining("multiselectable: com.acme.mix.Multi")
                .hasMessageContaining("not multiselectable: com.acme.mix.Plain");
    }

    @Test
    void multiSelectableInheritedFromAnAbstractBaseClassCounts_2_2_5() {
        JavaSources.compile(classes, Map.of(
                "com.acme.inh.Base", """
                        package com.acme.inh;
                        import javacard.framework.*;
                        public abstract class Base extends Applet implements MultiSelectable {
                        """ + SELECT_METHODS + "}\n",
                "com.acme.inh.Sub", applet("com.acme.inh", "Sub", "extends Base", ""),
                "com.acme.inh.Plain", plain("com.acme.inh", "Plain")));

        assertThatThrownBy(() -> convert("com.acme.inh", "A000000FFE81", "Sub", "Plain").build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("§2.2.5")
                .hasMessageContaining("multiselectable: com.acme.inh.Sub")
                .hasMessageContaining("not multiselectable: com.acme.inh.Plain");
    }

    @Test
    void multiSelectableThroughAnInterfaceOfThePackageCounts_2_2_5() {
        JavaSources.compile(classes, Map.of(
                "com.acme.itf.Channels", """
                        package com.acme.itf;
                        public interface Channels extends javacard.framework.MultiSelectable { }
                        """,
                "com.acme.itf.Via", applet("com.acme.itf", "Via", "extends Applet implements Channels",
                        SELECT_METHODS),
                "com.acme.itf.Plain", plain("com.acme.itf", "Plain")));

        assertThatThrownBy(() -> convert("com.acme.itf", "A000000FFE82", "Via", "Plain").build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("§2.2.5");
    }

    @Test
    void multiSelectableInheritedFromAnImportedBaseClassCounts_2_2_5() throws Exception {
        JavaSources.compile(classes, Map.of(
                "com.acme.mslib.MsBase", """
                        package com.acme.mslib;
                        import javacard.framework.*;
                        public abstract class MsBase extends Applet implements MultiSelectable {
                            protected MsBase() { }
                        """ + SELECT_METHODS + "}\n",
                "com.acme.mscli.Client", applet("com.acme.mscli", "Client", "extends com.acme.mslib.MsBase", ""),
                "com.acme.mscli.Plain", plain("com.acme.mscli", "Plain")));
        ConverterResult library = Converter.builder().classesDirectory(classes).packageName("com.acme.mslib")
                .packageAid("A000000FFE83").generateExport(true).build().convert();
        Path exp = Files.write(work.resolve("mslib.exp"), library.exportFile());

        assertThatThrownBy(() -> convert("com.acme.mscli", "A000000FFE84", "Client", "Plain")
                .importExportFile(exp).build().convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("§2.2.5")
                .hasMessageContaining("multiselectable: com.acme.mscli.Client");
    }

    @Test
    void allAppletsMultiselectableIsAccepted_2_2_5() throws Exception {
        Map<String, String> sources = new HashMap<>();
        sources.put("com.acme.all.Base", """
                package com.acme.all;
                import javacard.framework.*;
                public abstract class Base extends Applet implements MultiSelectable {
                """ + SELECT_METHODS + "}\n");
        sources.put("com.acme.all.First", multi("com.acme.all", "First"));
        sources.put("com.acme.all.Second", applet("com.acme.all", "Second", "extends Base", ""));
        JavaSources.compile(classes, sources);

        ConverterResult result = convert("com.acme.all", "A000000FFE85", "First", "Second").build().convert();

        assertThat(CapInspector.applets(result.capFile())).hasSize(2);
        if (OracleVerifier.available()) {
            String out = OracleVerifier.verify(result.capFile());
            assertThat(out).as("verifycap:%n%s", out).contains("0 errors");
        }
    }

    @Test
    void noAppletMultiselectableIsAccepted_2_2_5() throws Exception {
        JavaSources.compile(classes, Map.of(
                "com.acme.none.One", plain("com.acme.none", "One"),
                "com.acme.none.Two", plain("com.acme.none", "Two")));

        ConverterResult result = convert("com.acme.none", "A000000FFE86", "One", "Two").build().convert();

        assertThat(CapInspector.applets(result.capFile())).hasSize(2);
    }
}
