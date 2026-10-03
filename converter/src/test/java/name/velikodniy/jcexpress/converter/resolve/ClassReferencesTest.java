package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.resolve.ClassReferences.Kind;
import name.velikodniy.jcexpress.converter.resolve.ClassReferences.Reference;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the symbolic reference scan used for import lookup and link checks. */
class ClassReferencesTest {

    @TempDir
    Path dir;

    private List<Reference> scan(String source) throws Exception {
        JavaSources.compile(dir, Map.of("com.acme.p.C", source));
        return ClassReferences.scan(List.of(Files.readAllBytes(dir.resolve("com/acme/p/C.class"))));
    }

    @Test
    void collectsClassStructureDescriptorAndInstructionReferencesWithLocations() throws Exception {
        List<Reference> refs = scan("""
                package com.acme.p;
                import javacard.framework.*;
                public abstract class C extends Applet implements Shareable {
                    private OwnerPIN[] pins;
                    public void process(APDU apdu) {
                        try {
                            Util.setShort(apdu.getBuffer(), (short) 0, (short) 1);
                        } catch (APDUException e) {
                            ISOException.throwIt(e.getReason());
                        }
                    }
                }
                """);

        assertThat(refs).contains(
                new Reference(Kind.SUPERCLASS, "javacard/framework/Applet", null, null, "com.acme.p.C"),
                new Reference(Kind.INTERFACE, "javacard/framework/Shareable", null, null, "com.acme.p.C"),
                new Reference(Kind.TYPE, "javacard/framework/OwnerPIN", null, null, "com.acme.p.C.pins"),
                new Reference(Kind.STATIC_METHOD, "javacard/framework/Util", "setShort", "([BSS)S",
                        "com.acme.p.C.process(Ljavacard/framework/APDU;)V (C.java:7)"),
                new Reference(Kind.VIRTUAL_METHOD, "javacard/framework/APDUException", "getReason", "()S",
                        "com.acme.p.C.process(Ljavacard/framework/APDU;)V (C.java:9)"));
        assertThat(refs).anyMatch(r -> r.kind() == Kind.CLASS && r.owner().equals("javacard/framework/APDUException"));
        assertThat(ClassReferences.packages(refs, "com/acme/p")).containsExactly("javacard/framework");
    }

    @Test
    void arrayClassReferencesAreReducedToTheirElementClass() throws Exception {
        List<Reference> refs = scan("""
                package com.acme.p;
                public class C {
                    Object f(Object o) { return (javacard.framework.AID[]) o; }
                    byte[] g(Object o) { return (byte[]) o; }
                }
                """);

        assertThat(refs).anyMatch(r -> r.kind() == Kind.CLASS && r.owner().equals("javacard/framework/AID"))
                .noneMatch(r -> r.owner().startsWith("["));
    }
}
