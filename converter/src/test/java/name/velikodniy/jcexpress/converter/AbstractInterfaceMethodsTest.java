package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.input.PackageScanner;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import name.velikodniy.jcexpress.converter.token.ImportedTypes;
import name.velikodniy.jcexpress.converter.translate.CapView;
import name.velikodniy.jcexpress.converter.translate.OracleVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An abstract class may leave the methods of its interfaces to its subclasses without declaring them
 * (JLS 8.1.1.1, 9.4.1), but its {@code implemented_interface_info} maps every method of an implemented
 * interface to a virtual method token of the class (JCVM 3.1 §6.9.2.5). The converter declares such a
 * method {@code public abstract} in the abstract class, exactly as the source could (abstract methods are
 * represented like other methods in the Method, Class and Descriptor components, §6.9.2.3, §6.10, §6.14):
 * the CAP and export files equal those of the same package with the explicit declarations.
 */
class AbstractInterfaceMethodsTest {

    @TempDir
    Path implicit;

    @TempDir
    Path explicit;

    private static final String PING_PONG = """
            package com.acme.abi;
            public interface Iface {
                short ping(short x);
                short pong(short x);
            }
            """;

    private static final String IMPL = """
            package com.acme.abi;
            public class Impl extends Abs {
                public short ping(short x) { return (short) (x + x); }
            }
            """;

    private static final String APPLET = """
            package com.acme.abi;
            import javacard.framework.*;
            public class AbiApplet extends Applet {
                private final Iface viaInterface = new Impl();
                private final Abs viaClass = new Impl();
                public static void install(byte[] b, short o, byte l) { new AbiApplet().register(); }
                public void process(APDU apdu) {
                    if (selectingApplet()) return;
                    short r = viaInterface.ping((short) 3);
                    r += viaClass.ping((short) 4);
                    r += viaInterface.pong((short) 5);
                    ISOException.throwIt((short) (0x6A00 + r));
                }
            }
            """;

    private static Map<String, String> abiPackage(String absBody) {
        Map<String, String> sources = new HashMap<>();
        sources.put("com.acme.abi.Iface", PING_PONG);
        sources.put("com.acme.abi.Abs", """
                package com.acme.abi;
                public abstract class Abs implements Iface {
                    public short pong(short x) { return (short) (x + 1); }
                """ + absBody + "}\n");
        sources.put("com.acme.abi.Impl", IMPL);
        sources.put("com.acme.abi.AbiApplet", APPLET);
        return sources;
    }

    @Test
    void anUndeclaredInterfaceMethodIsDeclaredAbstractInTheAbstractClass_6_9_2_5() throws Exception {
        JavaSources.compile(implicit, abiPackage(""));
        JavaSources.compile(explicit, abiPackage("    public abstract short ping(short x);\n"));

        ConverterResult withoutDeclaration = abiApplet(implicit);
        ConverterResult withDeclaration = abiApplet(explicit);

        assertThat(withoutDeclaration.capFile()).isEqualTo(withDeclaration.capFile());
        assertThat(withoutDeclaration.exportFile()).isEqualTo(withDeclaration.exportFile());
        assertThat(CapView.parse(withoutDeclaration.capFile()).methods())
                .as("one abstract method_info (ACC_ABSTRACT, §6.10.1)").filteredOn(m -> (m.flags() & 0x4) != 0)
                .hasSize(1);
        verify(withoutDeclaration);
    }

    /** The MirandaApplet fixture: {@code Base implements Command} without declaring {@code run}. */
    @Test
    void theMirandaFixtureConvertsWithRunDeclaredAbstractInBase_6_9_2_5() throws Exception {
        ConverterResult result = TestFixtures.applet("MirandaApplet").convert();

        assertThat(CapView.parse(result.capFile()).methods())
                .as("Base.run as an abstract method_info (ACC_ABSTRACT, §6.10.1)")
                .filteredOn(m -> (m.flags() & 0x4) != 0).hasSize(1);
        verify(result);
    }

    @Test
    void aLibraryExportsTheDeclaredMethod_5_9() throws Exception {
        JavaSources.compile(implicit, Map.of("com.acme.abl.Handler", """
                package com.acme.abl;
                public interface Handler { short handle(short x); short reset(); }
                """, "com.acme.abl.Base", """
                package com.acme.abl;
                public abstract class Base implements Handler {
                    public short reset() { return 0; }
                }
                """));
        JavaSources.compile(explicit, Map.of("com.acme.abl.Handler", """
                package com.acme.abl;
                public interface Handler { short handle(short x); short reset(); }
                """, "com.acme.abl.Base", """
                package com.acme.abl;
                public abstract class Base implements Handler {
                    public short reset() { return 0; }
                    public abstract short handle(short x);
                }
                """));

        ConverterResult withoutDeclaration = library(implicit);
        ConverterResult withDeclaration = library(explicit);

        assertThat(withoutDeclaration.capFile()).isEqualTo(withDeclaration.capFile());
        assertThat(withoutDeclaration.exportFile()).isEqualTo(withDeclaration.exportFile());
    }

    @Test
    void methodsOfSuperinterfacesAndImportedInterfacesAreDeclaredToo_4_3_7_7() throws Exception {
        String sup = """
                package com.acme.abs;
                public interface Basic { short first(); }
                """;
        String sub = """
                package com.acme.abs;
                public interface Extended extends Basic { short second(); }
                """;
        String impl = """
                package com.acme.abs;
                import javacard.framework.*;
                public class App extends Base {
                    public static void install(byte[] b, short o, byte l) { new App().register(); }
                    public void process(APDU apdu) { ISOException.throwIt((short) (first() + second())); }
                    public short first() { return 1; }
                    public short second() { return 2; }
                    public void uninstall() { }
                }
                """;
        JavaSources.compile(implicit, Map.of("com.acme.abs.Basic", sup, "com.acme.abs.Extended", sub,
                "com.acme.abs.Base", """
                        package com.acme.abs;
                        public abstract class Base extends javacard.framework.Applet
                                implements Extended, javacard.framework.AppletEvent { }
                        """, "com.acme.abs.App", impl));
        JavaSources.compile(explicit, Map.of("com.acme.abs.Basic", sup, "com.acme.abs.Extended", sub,
                "com.acme.abs.Base", """
                        package com.acme.abs;
                        public abstract class Base extends javacard.framework.Applet
                                implements Extended, javacard.framework.AppletEvent {
                            public abstract short first();
                            public abstract short second();
                            public abstract void uninstall();
                        }
                        """, "com.acme.abs.App", impl));

        ConverterResult withoutDeclaration = applet(implicit, "com.acme.abs", "A000000FFEA2", "com.acme.abs.App");
        ConverterResult withDeclaration = applet(explicit, "com.acme.abs", "A000000FFEA2", "com.acme.abs.App");

        assertThat(withoutDeclaration.capFile()).isEqualTo(withDeclaration.capFile());
        verify(withoutDeclaration);
    }

    @Test
    void aMethodInheritedFromAnImportedSuperclassImplementsTheInterface() throws Exception {
        // Applet.deselect() is a concrete public method: nothing is declared, Base must not re-abstract it
        JavaSources.compile(implicit, Map.of("com.acme.des.Deselectable", """
                package com.acme.des;
                public interface Deselectable { void deselect(); }
                """, "com.acme.des.Base", """
                package com.acme.des;
                public abstract class Base extends javacard.framework.Applet implements Deselectable { }
                """, "com.acme.des.App", """
                package com.acme.des;
                import javacard.framework.*;
                public class App extends Base {
                    public static void install(byte[] b, short o, byte l) { new App().register(); }
                    public void process(APDU apdu) { }
                }
                """));

        ConverterResult result = applet(implicit, "com.acme.des", "A000000FFEA3", "com.acme.des.App");

        assertThat(CapView.parse(result.capFile()).methods()).noneMatch(m -> (m.flags() & 0x4) != 0);
        verify(result);
    }

    @Test
    void aConcreteClassWithoutTheMethodIsStillRejected_6_9_2_5() throws Exception {
        // inconsistent class files: the interface gained a method after the class was compiled
        JavaSources.compile(implicit, Map.of("com.acme.inc.Op", """
                package com.acme.inc;
                public interface Op { short one(); }
                """, "com.acme.inc.Impl", """
                package com.acme.inc;
                public class Impl implements Op { public short one() { return 1; } }
                """));
        JavaSources.compile(implicit, Map.of("com.acme.inc.Op", """
                package com.acme.inc;
                public interface Op { short one(); short two(); }
                """));

        assertThatThrownBy(() -> library(implicit, "com.acme.inc", "A000000FFEA4"))
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("com/acme/inc/Impl")
                .hasMessageContaining("two()S")
                .hasMessageContaining("§6.9.2.5");
    }

    /**
     * Class files of every version get the declaration, e.g. version 45.3 of {@code javac -target 1.1}
     * toolchains: the version, the constant pool entries of the other members and their code are kept.
     */
    @Test
    void oldClassFileVersionsAreCompletedToo() {
        ClassDesc iface = ClassDesc.of("p.Cmd");
        ClassDesc abs = ClassDesc.of("p.Base");
        MethodTypeDesc run = MethodTypeDesc.of(ConstantDescs.CD_short, ConstantDescs.CD_short);
        byte[] ifaceBytes = ClassFile.of().build(iface, cb -> cb.withVersion(ClassFile.JAVA_1_VERSION, 3)
                .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT)
                .withMethod("run", run, ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT, mb -> { }));
        byte[] absBytes = ClassFile.of().build(abs, cb -> cb.withVersion(ClassFile.JAVA_1_VERSION, 3)
                .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT | ClassFile.ACC_SUPER)
                .withInterfaceSymbols(iface)
                .withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC,
                        b -> b.aload(0).invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME,
                                ConstantDescs.MTD_void).return_()));
        Map<String, byte[]> classFiles = new LinkedHashMap<>();
        classFiles.put("p/Cmd", ifaceBytes);
        classFiles.put("p/Base", absBytes);

        Map<String, byte[]> completed = AbstractMethodDeclarations.complete(
                PackageScanner.read("p", classFiles.values()), classFiles, ImportedTypes.of(n -> List.of()));

        ClassModel base = ClassFile.of().parse(completed.get("p/Base"));
        assertThat(base.majorVersion()).isEqualTo(ClassFile.JAVA_1_VERSION);
        assertThat(base.minorVersion()).isEqualTo(3);
        assertThat(base.methods()).extracting(m -> m.methodName().stringValue() + m.methodType().stringValue()
                        + " " + m.flags().flagsMask())
                .containsExactly("<init>()V 1", "run(S)S " + (ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT));
        assertThat(completed.get("p/Cmd")).isSameAs(ifaceBytes);
    }

    private static ConverterResult abiApplet(Path classes) throws Exception {
        return applet(classes, "com.acme.abi", "A000000FFEA0", "com.acme.abi.AbiApplet");
    }

    private static ConverterResult applet(Path classes, String pkg, String aid, String applet) throws Exception {
        return Converter.builder().classesDirectory(classes).packageName(pkg).packageAid(aid)
                .applet(applet, aid + "01").build().convert();
    }

    private static ConverterResult library(Path classes) throws Exception {
        return library(classes, "com.acme.abl", "A000000FFEA1");
    }

    private static ConverterResult library(Path classes, String pkg, String aid) throws Exception {
        return Converter.builder().classesDirectory(classes).packageName(pkg).packageAid(aid)
                .generateExport(true).build().convert();
    }

    private static void verify(ConverterResult result) throws Exception {
        if (OracleVerifier.available()) {
            String out = OracleVerifier.verify(result.capFile());
            assertThat(out).as("verifycap:%n%s", out).contains("0 errors");
        }
    }
}
