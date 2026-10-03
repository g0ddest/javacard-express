package name.velikodniy.jcexpress.converter.cap;

import name.velikodniy.jcexpress.converter.Converter;
import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.TestFixtures;
import name.velikodniy.jcexpress.converter.capcheck.CapImage;
import name.velikodniy.jcexpress.converter.capcheck.ClassComponentView;
import name.velikodniy.jcexpress.converter.capcheck.ClassComponentView.ClassEntry;
import name.velikodniy.jcexpress.converter.capcheck.ClassComponentView.ImplementedInterface;
import name.velikodniy.jcexpress.converter.capcheck.ClassComponentView.InterfaceEntry;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.ClassDescriptor;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.MethodDescriptor;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Class component (JCVM 3.1 §6.9) of the converter's test packages, parsed with the layout of
 * the specification ({@code class_info_compact}: the four u1 table items precede both tables).
 * Expected values are derived from the rules of §6.9.2 and §4.3.7; the Descriptor component
 * (§6.14) is used to identify methods by token.
 */
class ClassComponentTest {

    private static final int FRAMEWORK_SHAREABLE = 0x8002; // javacard.framework is package token 0 or 1

    @TempDir
    Path probe;

    private static CapImage convert(String label) throws ConverterException {
        return CapImage.parse(TestFixtures.applet(label).convert().capFile());
    }

    private static CapImage convert(String label, JavaCardVersion version) throws ConverterException {
        return CapImage.parse(TestFixtures.applet(label).builder(version).build().convert().capFile());
    }

    /** Method offset of the method with the given token declared by the class at {@code classRef}. */
    private static int method(CapImage cap, int classRef, int token) {
        ClassDescriptor cd = cap.descriptor().byClassRef(classRef).orElseThrow();
        return cd.methods().stream().filter(MethodDescriptor::isVirtual).filter(m -> m.token() == token)
                .findFirst().orElseThrow(() -> new AssertionError("no token " + token)).methodOffset();
    }

    // ── §6.9.2 field order ──

    @Test
    void classInfoUsesTheFieldOrderOfTheSpecification_6_9_2() throws Exception {
        CapImage cap = convert("TestApplet");
        ClassEntry applet = cap.classComponent().classes().getFirst();

        assertThat(applet.publicBase()).isEqualTo(7);
        assertThat(applet.publicCount()).isEqualTo(1);
        assertThat(applet.packageBase()).isZero();
        assertThat(applet.packageCount()).isZero();
        assertThat(applet.publicTable()).containsExactly(method(cap, applet.offset(), 7));
    }

    @Test
    void deprecatedOracleCompatibilityOptionDoesNotChangeTheOutput() throws Exception {
        TestFixtures.Fixture fx = TestFixtures.applet("InheritanceApplet");
        byte[] defaults = fx.builder(JavaCardVersion.V3_0_5).build().convert().capFile();
        @SuppressWarnings("deprecation")
        byte[] compat = fx.builder(JavaCardVersion.V3_0_5).oracleCompatibility(true).build().convert().capFile();

        assertThat(CapImage.parse(compat).requireBody(CapImage.TAG_CLASS))
                .isEqualTo(CapImage.parse(defaults).requireBody(CapImage.TAG_CLASS));
    }

    // ── §6.9.2.3 method table base/count ──

    @Test
    void tablesStartAtTheFirstOverrideAndRunToTheLargestInheritedToken_6_9_2_3() throws Exception {
        CapImage cap = convert("ChainApplet");
        List<ClassEntry> c = cap.classComponent().classes(); // A, B, C, ChainApplet, D, E
        ClassEntry a = c.get(0);
        ClassEntry b = c.get(1);
        ClassEntry cc = c.get(2);
        ClassEntry e = c.get(5);

        assertThat(List.of(a.publicBase(), a.publicCount())).containsExactly(1, 2);
        assertThat(List.of(b.publicBase(), b.publicCount())).containsExactly(1, 2);
        assertThat(b.publicTable()).containsExactly(method(cap, b.offset(), 1), method(cap, a.offset(), 2));
        assertThat(List.of(e.publicBase(), e.publicCount())).containsExactly(1, 3);
        assertThat(e.publicTable()).containsExactly(method(cap, e.offset(), 1),
                method(cap, cc.offset(), 2), method(cap, cc.offset(), 3));
    }

    @Test
    void emptyTableBaseIsSuperclassBasePlusCount_6_9_2_3() throws Exception {
        CapImage chain = convert("ChainApplet");
        ClassEntry d = chain.classComponent().classes().get(4);
        assertThat(List.of(d.publicBase(), d.publicCount())).containsExactly(4, 0);

        List<ClassEntry> noMeth = convert("NoMethApplet").classComponent().classes(); // Holder, MyException, applet
        assertThat(List.of(noMeth.get(0).publicBase(), noMeth.get(0).publicCount()))
                .as("Holder extends Object (equals = token 0)").containsExactly(1, 0);
        assertThat(List.of(noMeth.get(1).publicBase(), noMeth.get(1).publicCount()))
                .as("MyException extends CardRuntimeException (tokens 0..2)").containsExactly(3, 0);
    }

    @Test
    void methodsInheritedFromAnImportedPackageAreMarkedFFFF_6_9_2_3() throws Exception {
        CapImage cap = convert("ChainApplet");
        ClassEntry applet = cap.classComponent().classes().get(3);

        assertThat(List.of(applet.publicBase(), applet.publicCount())).containsExactly(4, 4);
        assertThat(applet.publicTable().get(1)).as("token 5 not overridden").isEqualTo(0xFFFF);
    }

    // ── §6.9.2.3 package_virtual_method_table ──

    @Test
    void packageVisibleMethodsAreDispatchedThroughThePackageTable_6_9_2_3() throws Exception {
        CapImage cap = convert("PkgVirtApplet");
        List<ClassEntry> c = cap.classComponent().classes(); // Base, PkgVirtApplet, Sub, Sub2
        ClassEntry base = c.get(0);
        ClassEntry sub = c.get(2);
        ClassEntry sub2 = c.get(3);

        assertThat(List.of(base.publicBase(), base.publicCount(), base.packageBase(), base.packageCount()))
                .containsExactly(1, 1, 0, 2);
        assertThat(base.packageTable()).containsExactly(method(cap, base.offset(), 0x80),
                method(cap, base.offset(), 0x81));
        assertThat(sub.packageTable()).containsExactly(method(cap, sub.offset(), 0x80),
                method(cap, base.offset(), 0x81), method(cap, sub.offset(), 0x82));
        assertThat(List.of(sub2.publicBase(), sub2.publicCount(), sub2.packageBase(), sub2.packageCount()))
                .containsExactly(2, 0, 3, 0);
    }

    @Test
    void packageTableBaseIsZeroWhenTheSuperclassIsImported_6_9_2_3() throws Exception {
        CapImage cap = convert("HelperApplet");
        ClassEntry applet = cap.classComponent().classes().get(1); // Counter, HelperApplet

        assertThat(List.of(applet.packageBase(), applet.packageCount())).containsExactly(0, 2);
        assertThat(applet.packageTable()).containsExactly(method(cap, applet.offset(), 0x80),
                method(cap, applet.offset(), 0x81));
    }

    // ── §6.9.2.3 instance layout ──

    @Test
    void declaredInstanceSizeCountsCellsAndReferenceBlockIsContiguous_6_9_2_3() throws Exception {
        ClassEntry fields = convert("FieldsApplet").classComponent().classes().getFirst();

        assertThat(fields.declaredInstanceSize()).as("10 fields, two of them int").isEqualTo(12);
        assertThat(fields.firstReferenceToken()).isEqualTo(3);
        assertThat(fields.referenceCount()).isEqualTo(4);
    }

    // ── §6.9.2.1, §6.9.2.2, §6.9.2.5 interfaces ──

    @Test
    void internalInterfaceReferencesAreComponentOffsets_6_8_1() throws Exception {
        ClassComponentView cc = convert("OrderApplet").classComponent();
        InterfaceEntry zSuper = cc.interfaces().get(0);
        InterfaceEntry aSub = cc.interfaces().get(1);
        ClassEntry applet = cc.classes().getFirst();

        assertThat(aSub.superinterfaces()).containsExactly(zSuper.offset());
        assertThat(applet.interfaces()).extracting(ImplementedInterface::interfaceRef)
                .containsExactly(zSuper.offset(), aSub.offset());
    }

    @Test
    void internalInterfaceReferencesSkipTheSignaturePoolInCap23_6_9() throws Exception {
        ClassComponentView cc = convert("OrderApplet", JavaCardVersion.V3_2_0).classComponent();

        assertThat(cc.signaturePoolLength()).isZero();
        assertThat(cc.interfaces().get(0).offset()).isEqualTo(2);
        assertThat(cc.interfaces().get(1).superinterfaces()).containsExactly(2);
    }

    @Test
    void interfaceIndexCoversInheritedInterfaceMethods_6_9_2_5() throws Exception {
        CapImage cap = convert("OrderApplet");
        ClassEntry applet = cap.classComponent().classes().getFirst();
        int z = applet.interfaces().get(0).index().getFirst();
        int aSubZ = applet.interfaces().get(1).index().get(0);
        int aSubA = applet.interfaces().get(1).index().get(1);

        assertThat(aSubZ).as("ASub token 0 is the inherited z()").isEqualTo(z);
        assertThat(List.of(z, aSubA)).doesNotHaveDuplicates().allMatch(t -> t >= 8);
    }

    @Test
    void shareableInterfacesAndClassesCarryAccShareable_6_9_2_1() throws Exception {
        ClassComponentView cc = convert("ShareApplet").classComponent();
        assertThat(cc.interfaces()).hasSize(3); // AStatus, IBase, IExt

        assertThat(cc.interfaces().get(0).isShareable()).as("AStatus").isFalse();
        assertThat(cc.interfaces().get(1).isShareable()).as("IBase extends Shareable").isTrue();
        assertThat(cc.interfaces().get(2).isShareable()).as("IExt extends IBase").isTrue();
        assertThat(cc.classes().getFirst().isShareable()).as("ShareApplet implements IExt").isTrue();
    }

    @Test
    void interfaceListsContainTheWholeHierarchy_6_9_2_2_and_6_9_2_3() throws Exception {
        CapImage cap = convert("ShareApplet");
        ClassComponentView cc = cap.classComponent();
        InterfaceEntry aStatus = cc.interfaces().get(0);
        InterfaceEntry iBase = cc.interfaces().get(1);
        InterfaceEntry iExt = cc.interfaces().get(2);
        int shareable = iBase.superinterfaces().getFirst();

        assertThat(shareable & 0x8000).as("Shareable is imported").isNotZero();
        assertThat(shareable & 0xFF).isEqualTo(FRAMEWORK_SHAREABLE & 0xFF);
        assertThat(iExt.superinterfaces()).containsExactly(shareable, iBase.offset());
        assertThat(cc.classes().getFirst().interfaces()).extracting(ImplementedInterface::interfaceRef)
                .hasSize(6).containsSubsequence(shareable, iBase.offset(), iExt.offset())
                .contains(aStatus.offset());
    }

    @Test
    void superinterfacesOfImportedInterfacesArePartOfTheInterfaceLists_6_9_2_2_and_6_9_2_3()
            throws Exception {
        // WrappedKey extends the imported PrivateKey, which extends the imported Key (JCVM 3.1 §5.7)
        JavaSources.compile(probe, Map.of("com.acme.subkey.KeyApplet", """
                package com.acme.subkey;
                import javacard.framework.*;
                public class KeyApplet extends Applet {
                    public static void install(byte[] b, short o, byte l) { new KeyApplet().register(); }
                    public void process(APDU apdu) { }
                }
                """, "com.acme.subkey.WrappedKey", """
                package com.acme.subkey;
                public interface WrappedKey extends javacard.security.PrivateKey { }
                """, "com.acme.subkey.SoftKey", """
                package com.acme.subkey;
                class SoftKey implements WrappedKey {
                    public boolean isInitialized() { return false; }
                    public void clearKey() { }
                    public byte getType() { return 0; }
                    public short getSize() { return 0; }
                }
                """));
        ClassComponentView cc = CapImage.parse(Converter.builder().classesDirectory(probe)
                .packageName("com.acme.subkey").packageAid("A000000FFE40")
                .applet("com.acme.subkey.KeyApplet", "A000000FFE4001")
                .build().convert().capFile()).classComponent();

        InterfaceEntry wrappedKey = cc.interfaces().getFirst();
        // §6.9.2.2: direct and indirect superinterfaces; imported ones as (0x80|package)<<8|class token
        assertThat(wrappedKey.superinterfaces()).hasSize(2).allMatch(ref -> (ref & 0x8000) != 0)
                .extracting(ref -> ref & 0xFF).containsExactlyInAnyOrder(0, 2); // Key, PrivateKey
        // §6.9.2.3: the interfaces of a class include the superinterfaces of those interfaces
        ClassEntry softKey = cc.classes().stream()
                .filter(c -> c.interfaces().stream().anyMatch(i -> i.interfaceRef() == wrappedKey.offset()))
                .findFirst().orElseThrow();
        assertThat(softKey.interfaces()).extracting(ImplementedInterface::interfaceRef)
                .containsExactlyInAnyOrder(wrappedKey.offset(), wrappedKey.superinterfaces().get(0),
                        wrappedKey.superinterfaces().get(1));
        // §6.9.2.5: each index[] maps the four Key methods (interface tokens 0..3)
        assertThat(softKey.interfaces()).allMatch(i -> i.index().size() == 4);
    }

    @Test
    void interfacesAreNotPartOfTheMethodComponentAndHaveTokensFromZero_6_14_4() throws Exception {
        CapImage cap = convert("ShareApplet");
        DescriptorView d = cap.descriptor();

        for (ClassDescriptor cd : d.classes().stream().filter(ClassDescriptor::isInterface).toList()) {
            assertThat(cd.interfaces()).isEmpty();
            assertThat(cd.methods()).allMatch(m -> m.methodOffset() == 0 && m.bytecodeCount() == 0);
            assertThat(cd.methods()).extracting(MethodDescriptor::token)
                    .containsExactlyElementsOf(java.util.stream.IntStream.range(0, cd.methods().size())
                            .boxed().toList());
        }
    }
}
