package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.Converter;
import name.velikodniy.jcexpress.converter.ConverterResult;
import name.velikodniy.jcexpress.converter.testutil.CapInspector;
import name.velikodniy.jcexpress.converter.testutil.CapInspector.ExternalRef;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.ExportFileReader;
import name.velikodniy.jcexpress.converter.translate.OracleVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Members of an imported class named through an imported subclass. javac names the class used in
 * the source (JLS 13.1), e.g. {@code Sub.hello()} for a static method that {@code Sub} inherits
 * from {@code Base}; the export entry of {@code Sub} does not list it (JCVM 3.1 §5.8, §5.9 list the
 * members a class declares, plus inherited virtual methods). The constant pool entry must name the
 * class that defines a static field or method (§6.8.3) or declares an instance field (§6.8.2); a
 * virtual method keeps the named class, whose export entry lists the inherited method (§6.8.2).
 */
class InheritedImportedMemberTest {

    private static final String LIB_AID = "A000000FFE60";
    private static final String CRYPTO_AID = "A0000000620201";
    private static final String FRAMEWORK_AID = "A0000000620101";
    private static final int INSTANCE_FIELD_REF = 2;
    private static final int VIRTUAL_METHOD_REF = 3;
    private static final int STATIC_FIELD_REF = 5;
    private static final int STATIC_METHOD_REF = 6;

    @TempDir
    Path classes;

    @TempDir
    Path work;

    private static final Map<String, String> SOURCES = Map.of(
            "com.acme.base.Base", """
                    package com.acme.base;
                    public class Base {
                        public static short counter;
                        public short value;
                        public static void hello() { counter++; }
                        public void touch() { value++; }
                    }
                    """,
            "com.acme.base.Sub", """
                    package com.acme.base;
                    public class Sub extends Base {
                        public Sub() { }
                    }
                    """,
            "com.acme.client.Client", """
                    package com.acme.client;
                    import javacard.framework.*;
                    import javacardx.crypto.AEADCipher;
                    import com.acme.base.Sub;
                    public class Client extends Applet {
                        private final Sub s = new Sub();
                        public static void install(byte[] b, short o, byte l) { new Client().register(); }
                        public void process(APDU apdu) {
                            if (selectingApplet()) return;
                            Sub.hello();
                            s.value = Sub.counter;
                            s.touch();
                            AEADCipher a = (AEADCipher) AEADCipher.getInstance(AEADCipher.ALG_AES_GCM, false);
                            ISOException.throwIt((short) (s.value + a.getAlgorithm()));
                        }
                    }
                    """);

    @Test
    void membersInheritedByAnImportedSubclassReferenceTheirDeclaringClass_6_8_2_and_6_8_3() throws Exception {
        JavaSources.compile(classes, SOURCES);
        ConverterResult library = Converter.builder().classesDirectory(classes).packageName("com.acme.base")
                .packageAid(LIB_AID).generateExport(true).build().convert();
        Path libExp = Files.write(work.resolve("base.exp"), library.exportFile());

        ConverterResult client = Converter.builder().classesDirectory(classes).packageName("com.acme.client")
                .packageAid("A000000FFE61").applet("com.acme.client.Client", "A000000FFE6101")
                .importExportFile(libExp).build().convert();

        ExportFile lib = ExportFileReader.read(library.exportFile());
        ExportFile.ClassExport base = lib.findClass("Base");
        ExportFile.ClassExport sub = lib.findClass("Sub");
        assertThat(CapInspector.externalRefs(client.capFile())).contains(
                new ExternalRef(STATIC_METHOD_REF, LIB_AID, base.token(), methodToken(base, "hello")),
                new ExternalRef(STATIC_FIELD_REF, LIB_AID, base.token(), fieldToken(base, "counter")),
                new ExternalRef(INSTANCE_FIELD_REF, LIB_AID, base.token(), fieldToken(base, "value")),
                // virtual methods: the named class, whose export entry lists the inherited method
                new ExternalRef(VIRTUAL_METHOD_REF, LIB_AID, sub.token(), methodToken(sub, "touch")),
                // AEADCipher.getInstance is javacardx.crypto.Cipher.getInstance (class token 1, token 0)
                new ExternalRef(STATIC_METHOD_REF, CRYPTO_AID, 1, 0));
        if (OracleVerifier.available()) {
            String out = OracleVerifier.verify(client.capFile(), libExp);
            assertThat(out).as("verifycap:%n%s", out).contains("0 errors");
        }
    }

    @Test
    void theNearestSuperclassDeclarationWinsAcrossPackages_6_8_3() throws Exception {
        // ISOException.throwIt(short) hides CardRuntimeException.throwIt(short): MyEx.throwIt names
        // ISOException's method (JLS 15.12, JVMS 5.4.3.3), whatever the order of supers[] (§5.7)
        JavaSources.compile(classes, Map.of("com.acme.ext.MyEx", """
                package com.acme.ext;
                public class MyEx extends javacard.framework.ISOException {
                    public MyEx(short reason) { super(reason); }
                }
                """, "com.acme.xcli.XClient", """
                package com.acme.xcli;
                import javacard.framework.*;
                public class XClient extends Applet {
                    public static void install(byte[] b, short o, byte l) { new XClient().register(); }
                    public void process(APDU apdu) {
                        if (selectingApplet()) return;
                        com.acme.ext.MyEx.throwIt((short) 0x6A80);
                    }
                }
                """));
        ConverterResult library = Converter.builder().classesDirectory(classes).packageName("com.acme.ext")
                .packageAid("A000000FFE62").generateExport(true).build().convert();
        Path libExp = Files.write(work.resolve("ext.exp"), library.exportFile());

        ConverterResult client = Converter.builder().classesDirectory(classes).packageName("com.acme.xcli")
                .packageAid("A000000FFE63").applet("com.acme.xcli.XClient", "A000000FFE6301")
                .importExportFile(libExp).build().convert();

        // javacard.framework: ISOException is class 7, CardRuntimeException class 5; throwIt is token 1
        assertThat(CapInspector.externalRefs(client.capFile()))
                .contains(new ExternalRef(STATIC_METHOD_REF, FRAMEWORK_AID, 7, 1))
                .doesNotContain(new ExternalRef(STATIC_METHOD_REF, FRAMEWORK_AID, 5, 1));
        // only the declaring package is referenced (JCVM 3.1 §6.7)
        assertThat(CapInspector.imports(client.capFile()))
                .noneMatch(p -> p.aidHex().equals("A000000FFE62"));
    }

    private static int methodToken(ExportFile.ClassExport cls, String name) {
        return cls.methods().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow().token();
    }

    private static int fieldToken(ExportFile.ClassExport cls, String name) {
        return cls.fields().stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow().token();
    }
}
