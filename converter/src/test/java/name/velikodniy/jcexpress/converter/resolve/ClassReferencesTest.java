package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.resolve.ClassReferences.Kind;
import name.velikodniy.jcexpress.converter.resolve.ClassReferences.Reference;
import name.velikodniy.jcexpress.converter.resolve.ClassReferences.Site;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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

        assertThat(withoutSites(refs)).contains(
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
    void everyReferenceKnowsItsClassMemberAndLine() throws Exception {
        List<Reference> refs = scan("""
                package com.acme.p;
                import javacard.framework.*;
                public abstract class C extends Applet implements Shareable {
                    private OwnerPIN[] pins;
                    abstract AID owner(APDU apdu);
                    public void process(APDU apdu) {
                        Util.setShort(apdu.getBuffer(), (short) 0, (short) 1);
                    }
                }
                """);

        assertThat(refs).extracting(Reference::owner, Reference::site).contains(
                tuple("javacard/framework/Applet", new Site("com/acme/p/C", "superclass", -1, false)),
                tuple("javacard/framework/Shareable", new Site("com/acme/p/C", "interface javacard/framework/Shareable",
                        -1, false)),
                tuple("javacard/framework/OwnerPIN", new Site("com/acme/p/C", "field pins", -1, false)),
                tuple("javacard/framework/AID", new Site("com/acme/p/C",
                        "owner(Ljavacard/framework/APDU;)Ljavacard/framework/AID;", -1, false)),
                tuple("javacard/framework/Util", new Site("com/acme/p/C", "process(Ljavacard/framework/APDU;)V", 7,
                        true)));
    }

    /**
     * The class a catch clause names is a reference at the line of the clause: javac gives the handler's first
     * instruction that line (JVMS §4.7.3, §4.7.12). The location in the conversion's messages stays the method's.
     */
    @Test
    void aCatchClauseReferenceIsAtTheLineOfItsHandler_jcvm31_4_3_5() throws Exception {
        List<Reference> refs = scan("""
                package com.acme.p;
                public class C {
                    short work(short s) {
                        short r = 0;
                        try {
                            r = (short) (s / 2);
                        } catch (NumberFormatException e) {
                            r = 1;
                        } catch (ArithmeticException e) {
                            r = 2;
                        }
                        return r;
                    }
                }
                """);

        assertThat(refs).filteredOn(r -> r.kind() == Kind.CLASS).extracting(Reference::owner, r -> r.site().line(),
                Reference::location).containsExactly(
                tuple("java/lang/NumberFormatException", 7, "com.acme.p.C.work(S)S"),
                tuple("java/lang/ArithmeticException", 9, "com.acme.p.C.work(S)S"));
    }

    private static List<Reference> withoutSites(List<Reference> refs) {
        return refs.stream().map(r -> new Reference(r.kind(), r.owner(), r.name(), r.descriptor(), r.location()))
                .toList();
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
