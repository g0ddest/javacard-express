package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.Converter;
import name.velikodniy.jcexpress.converter.ConverterResult;
import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.testutil.CapInspector;
import name.velikodniy.jcexpress.converter.testutil.CapInspector.ExternalRef;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import name.velikodniy.jcexpress.converter.translate.CapView;
import name.velikodniy.jcexpress.converter.translate.OracleVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Methods of {@code java.lang.Object} called on a reference of an interface type.
 *
 * <p>The members of an interface include the public methods of {@code Object} (JLS 9.2), so for
 * {@code op.equals(x)} with {@code op} of an interface type javac emits
 * {@code invokeinterface Op.equals(Ljava/lang/Object;)Z}; when neither the interface nor its
 * superinterfaces declare the method, the Java VM resolves it to {@code Object.equals} (JVMS 5.4.3.4).
 * A Java Card interface has method tokens only for the methods it declares or inherits from its
 * superinterfaces (JCVM 3.1 §4.3.7.7, §5.7 {@code methods[]}), so the call is dispatched with
 * {@code invokevirtual} through virtual method token 0 of {@code java.lang.Object} (§7.5.57; the
 * method table of {@code Object} also serves arrays, §7.5.54). An interface that declares the method
 * itself keeps {@code invokeinterface}.
 */
class InterfaceObjectMethodTest {

    private static final String LANG_AID = "A0000000620001";
    private static final int VIRTUAL_METHOD_REF = 3;
    /** java.lang.Object is class token 0 of java.lang, equals(Object) its virtual method token 0. */
    private static final ExternalRef OBJECT_EQUALS = new ExternalRef(VIRTUAL_METHOD_REF, LANG_AID, 0, 0);

    @TempDir
    Path classes;

    private static final Map<String, String> INTERNAL_INTERFACE = Map.of(
            "com.acme.ieq.Op", """
                    package com.acme.ieq;
                    public interface Op { short apply(short x); }
                    """,
            "com.acme.ieq.Twice", """
                    package com.acme.ieq;
                    public class Twice implements Op {
                        public short apply(short x) { return (short) (x + x); }
                    }
                    """,
            "com.acme.ieq.EqApplet", """
                    package com.acme.ieq;
                    import javacard.framework.*;
                    public class EqApplet extends Applet {
                        private final Op op = new Twice();
                        private final Op other = new Twice();
                        public static void install(byte[] b, short o, byte l) { new EqApplet().register(); }
                        public void process(APDU apdu) {
                            if (selectingApplet()) return;
                            if (op.equals(other)) ISOException.throwIt((short) 0x6A80);
                            ISOException.throwIt(op.apply((short) 0x3500));
                        }
                    }
                    """);

    @Test
    void equalsOnAnInternalInterfaceIsInvokevirtualOfObject_7_5_57() throws Exception {
        JavaSources.compile(classes, INTERNAL_INTERFACE);

        ConverterResult result = convert("com.acme.ieq", "A000000FFE70", "com.acme.ieq.EqApplet",
                JavaCardVersion.V3_0_5);

        assertThat(CapInspector.externalRefs(result.capFile())).contains(OBJECT_EQUALS);
        List<String> process = processMethod(result);
        assertThat(process).anyMatch(l -> l.startsWith("invokevirtual"));
        // Op.apply keeps invokeinterface (nargs 2, interface method token 0)
        assertThat(process).filteredOn(l -> l.startsWith("invokeinterface")).hasSize(1)
                .allMatch(l -> l.startsWith("invokeinterface 2 #") && l.endsWith(" 0"));
        verify(result);
    }

    @ParameterizedTest
    @EnumSource(JavaCardVersion.class)
    void equalsOnAnInternalInterfaceConvertsForEveryTarget(JavaCardVersion version) throws Exception {
        JavaSources.compile(classes, INTERNAL_INTERFACE);

        ConverterResult result = convert("com.acme.ieq", "A000000FFE70", "com.acme.ieq.EqApplet", version);

        assertThat(CapInspector.externalRefs(result.capFile())).contains(OBJECT_EQUALS);
    }

    @Test
    void equalsOnAnImportedInterfaceIsInvokevirtualOfObject_7_5_57() throws Exception {
        JavaSources.compile(classes, Map.of("com.acme.keq.KeyApplet", """
                package com.acme.keq;
                import javacard.framework.*;
                import javacard.security.Key;
                import javacard.security.KeyBuilder;
                public class KeyApplet extends Applet {
                    private final Key a = KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
                    private final Key b = KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
                    public static void install(byte[] bb, short o, byte l) { new KeyApplet().register(); }
                    public void process(APDU apdu) {
                        if (selectingApplet()) return;
                        if (a.equals(b)) ISOException.throwIt((short) 0x6A80);
                        ISOException.throwIt(a.getSize());
                    }
                }
                """));

        ConverterResult result = convert("com.acme.keq", "A000000FFE72", "com.acme.keq.KeyApplet",
                JavaCardVersion.V3_0_5);

        assertThat(CapInspector.externalRefs(result.capFile())).contains(OBJECT_EQUALS);
        // Key.getSize keeps invokeinterface
        assertThat(processMethod(result)).filteredOn(l -> l.startsWith("invokeinterface")).hasSize(1);
        verify(result);
    }

    @Test
    void anInterfaceThatDeclaresEqualsKeepsInvokeinterface_4_3_7_7() throws Exception {
        JavaSources.compile(classes, Map.of(
                "com.acme.deq.Handler", """
                        package com.acme.deq;
                        public interface Handler {
                            boolean equals(Object o);
                            short handle(short x);
                        }
                        """,
                "com.acme.deq.Echo", """
                        package com.acme.deq;
                        public class Echo implements Handler {
                            public short handle(short x) { return x; }
                        }
                        """,
                "com.acme.deq.HandlerApplet", """
                        package com.acme.deq;
                        import javacard.framework.*;
                        public class HandlerApplet extends Applet {
                            private final Handler h = new Echo();
                            public static void install(byte[] b, short o, byte l) { new HandlerApplet().register(); }
                            public void process(APDU apdu) {
                                if (selectingApplet()) return;
                                if (h.equals(this)) ISOException.throwIt((short) 0x6A80);
                                ISOException.throwIt(h.handle((short) 0x6A81));
                            }
                        }
                        """));

        ConverterResult result = convert("com.acme.deq", "A000000FFE74", "com.acme.deq.HandlerApplet",
                JavaCardVersion.V3_0_5);

        assertThat(CapInspector.externalRefs(result.capFile())).doesNotContain(OBJECT_EQUALS);
        assertThat(processMethod(result)).filteredOn(l -> l.startsWith("invokeinterface")).hasSize(2);
        verify(result);
    }

    private ConverterResult convert(String pkg, String aid, String applet, JavaCardVersion version)
            throws Exception {
        return Converter.builder().classesDirectory(classes).packageName(pkg).packageAid(aid)
                .applet(applet, aid + "01").javaCardVersion(version).build().convert();
    }

    /** Disassembly of the applet's process method: the longest method of these small packages. */
    private static List<String> processMethod(ConverterResult result) throws Exception {
        return CapView.parse(result.capFile()).disassembledMethods().stream()
                .filter(m -> m.stream().anyMatch(l -> l.startsWith("invokevirtual") || l.startsWith("invokeinterface")))
                .max((x, y) -> Integer.compare(x.size(), y.size())).orElseThrow();
    }

    private static void verify(ConverterResult result) throws Exception {
        if (OracleVerifier.available()) {
            String out = OracleVerifier.verify(result.capFile());
            assertThat(out).as("verifycap:%n%s", out).contains("0 errors");
        }
    }
}
