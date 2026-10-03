package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.capcheck.CapImage;
import name.velikodniy.jcexpress.converter.capcheck.CapImage.CpEntry;
import name.velikodniy.jcexpress.converter.capcheck.ClassComponentView.ClassEntry;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.ClassDescriptor;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.FieldDescriptor;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.MethodDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Constant pool entries of the kinds JCVM 3.1 §6.8 prescribes for super invocations and for
 * members that javac names through a subclass (JLS 13.1 qualifying type).
 */
class ConstantPoolReferencesTest {

    private static CapImage convert(String label) throws ConverterException {
        return CapImage.parse(TestFixtures.applet(label).convert().capFile());
    }

    private static List<CpEntry> entries(CapImage cap, int tag) {
        return cap.constantPool().stream().filter(e -> e.tag() == tag).toList();
    }

    @Test
    void superInvocationsAreSuperMethodrefsOfTheCallingClass_6_8_2_and_7_5_55() throws Exception {
        CapImage cap = convert("ChainApplet");
        List<ClassEntry> classes = cap.classComponent().classes(); // A, B, C, ChainApplet, D, E
        int chainApplet = classes.get(3).offset();
        int e = classes.get(5).offset();

        // super.select() / super.deselect() (Applet virtual tokens 6 and 4) in ChainApplet,
        // super.m() in E, which javac names D.m() although D does not declare m()
        assertThat(entries(cap, CpEntry.SUPER_METHODREF))
                .extracting(CpEntry::classRef, CpEntry::token)
                .containsExactlyInAnyOrder(tuple(chainApplet, 6), tuple(chainApplet, 4), tuple(e, 1));
    }

    @Test
    void staticMethodrefsReferToConstructorsStaticOrPrivateMethodsOnly_7_5_55() throws Exception {
        CapImage cap = convert("ChainApplet");
        List<Integer> internalTargets = entries(cap, CpEntry.STATIC_METHODREF).stream()
                .filter(entry -> !entry.isExternal()).map(CpEntry::internalOffset).toList();
        List<MethodDescriptor> targets = cap.descriptor().classes().stream()
                .flatMap(cd -> cd.methods().stream())
                .filter(m -> internalTargets.contains(m.methodOffset())).toList();

        assertThat(targets).hasSize(internalTargets.size())
                .allMatch(m -> m.isStatic() || m.isInit() || m.isPrivate());
    }

    @Test
    void inheritedStaticMembersNamedThroughASubclassReferToTheDeclaringClass_6_8_3() throws Exception {
        CapImage cap = convert("InheritStaticApplet");
        ClassDescriptor base = cap.descriptor().classes().getFirst(); // Base, InheritStaticApplet, Middle, Leaf
        List<Integer> baseStatics = base.fields().stream().filter(FieldDescriptor::isStatic)
                .map(FieldDescriptor::staticOffset).toList();
        int helper = base.methods().stream().filter(MethodDescriptor::isStatic).findFirst().orElseThrow()
                .methodOffset();

        assertThat(entries(cap, CpEntry.STATIC_FIELDREF)).extracting(CpEntry::internalOffset)
                .as("Leaf.baseStatic and Leaf.baseStaticArr are Base's fields")
                .containsExactlyInAnyOrderElementsOf(baseStatics);
        assertThat(entries(cap, CpEntry.STATIC_METHODREF)).filteredOn(entry -> !entry.isExternal())
                .extracting(CpEntry::internalOffset).contains(helper);
    }

    @Test
    void noInternalReferenceKeepsAPlaceholder() throws Exception {
        for (TestFixtures.Fixture fixture : TestFixtures.APPLETS) {
            CapImage cap = CapImage.parse(fixture.convert().capFile());
            int methodComponentSize = cap.requireBody(CapImage.TAG_METHOD).length;
            assertThat(cap.constantPool()).as(fixture.label())
                    .filteredOn(entry -> !entry.isExternal() && entry.tag() == CpEntry.STATIC_METHODREF)
                    .allMatch(entry -> entry.internalOffset() < methodComponentSize);
        }
    }
}
