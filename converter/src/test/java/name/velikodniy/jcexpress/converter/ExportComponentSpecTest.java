package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.capcheck.CapImage;
import name.velikodniy.jcexpress.converter.capcheck.CapImage.ClassExport;
import name.velikodniy.jcexpress.converter.capcheck.ClassComponentView.InterfaceEntry;
import name.velikodniy.jcexpress.converter.capcheck.ClassComponentView.TypeEntry;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.ClassDescriptor;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.FieldDescriptor;
import name.velikodniy.jcexpress.converter.capcheck.DescriptorView.MethodDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Export component contents (JCVM 3.1 §6.13): library packages export their public classes and
 * interfaces with public/protected static members, application packages only their public
 * shareable interfaces; an index into {@code class_exports} is the class token, and an empty
 * component is omitted together with the ACC_EXPORT flag (§6.4).
 */
class ExportComponentSpecTest {

    private static final int ACC_EXPORT = 0x02;

    @Test
    void libraryExportsEveryPublicClassAndInterfaceInTokenOrder_6_13() throws Exception {
        CapImage cap = CapImage.parse(TestFixtures.LIBRARY.convert().capFile());
        List<ClassExport> exports = cap.exports();

        // LibService (shareable interface), LibBase, LibUtil; package-visible Hidden is not exported
        assertThat(exports).hasSize(3);
        for (int token = 0; token < exports.size(); token++) {
            int classRef = exports.get(token).classOffset();
            assertThat(cap.descriptor().byClassRef(classRef)).as("export entry %d", token)
                    .hasValueSatisfying(cd -> assertThat(cd.isPublic()).isTrue())
                    .map(ClassDescriptor::token).contains(token);
        }
        assertThat(cap.headerFlags() & ACC_EXPORT).isNotZero();
    }

    @Test
    void libraryClassExportsListPublicStaticMembersByToken_6_13() throws Exception {
        CapImage cap = CapImage.parse(TestFixtures.LIBRARY.convert().capFile());
        ClassExport libUtil = cap.exports().get(2);
        ClassDescriptor descriptor = cap.descriptor().byClassRef(libUtil.classOffset()).orElseThrow();

        // public static short counter (token 0), public static byte[] shared (token 1);
        // the package-visible static field 'internal' is not exported
        assertThat(libUtil.staticFieldOffsets()).containsExactlyElementsOf(descriptor.fields().stream()
                .filter(FieldDescriptor::isStatic).filter(FieldDescriptor::isExternallyVisible)
                .sorted(Comparator.comparingInt(FieldDescriptor::token))
                .map(FieldDescriptor::staticOffset).toList());
        assertThat(libUtil.staticFieldOffsets()).hasSize(2);
        // public constructor (token 0) and public static twice(short) (token 1); hidden() is not exported
        assertThat(libUtil.staticMethodOffsets()).containsExactlyElementsOf(descriptor.methods().stream()
                .filter(m -> m.isStatic() || m.isInit()).filter(m -> m.token() != 0xFF)
                .sorted(Comparator.comparingInt(MethodDescriptor::token))
                .map(MethodDescriptor::methodOffset).toList());
        assertThat(libUtil.staticMethodOffsets()).hasSize(2);
    }

    @Test
    void applicationPackageExportsOnlyPublicShareableInterfaces_6_13() throws Exception {
        CapImage cap = CapImage.parse(TestFixtures.applet("ShareApplet").convert().capFile());
        List<ClassExport> exports = cap.exports();

        // IBase and IExt; neither the non-shareable public interface AStatus nor the applet class
        assertThat(exports).hasSize(2);
        for (ClassExport export : exports) {
            TypeEntry entry = cap.classComponent().at(export.classOffset()).orElseThrow();
            assertThat(entry).isInstanceOf(InterfaceEntry.class);
            assertThat(entry.isShareable()).isTrue();
            assertThat(export.staticFieldOffsets()).isEmpty();
            assertThat(export.staticMethodOffsets()).isEmpty();
        }
        assertThat(cap.headerFlags() & ACC_EXPORT).isNotZero();
    }

    @Test
    void applicationPackageWithoutShareableInterfacesHasNoExportComponent_6_13() throws Exception {
        CapImage cap = CapImage.parse(TestFixtures.applet("HelperApplet").convert().capFile());

        assertThat(cap.has(CapImage.TAG_EXPORT)).as("class_count must be greater than zero").isFalse();
        assertThat(cap.headerFlags() & ACC_EXPORT).isZero();
    }

    @Test
    void explicitGenerateExportFalseOmitsTheExportComponent() throws Exception {
        CapImage cap = CapImage.parse(TestFixtures.LIBRARY.builder(JavaCardVersion.V3_0_5)
                .generateExport(false).build().convert().capFile());

        assertThat(cap.has(CapImage.TAG_EXPORT)).isFalse();
        assertThat(cap.headerFlags() & ACC_EXPORT).isZero();
    }

    /**
     * §6.2: the Export component is included if classes of other packages may import elements of the
     * package; §6.13: a compact CAP file with an Export component makes its package public. A library
     * package has nothing but its exported elements, so without the component no package on the card could
     * use it: the default must export it.
     */
    @Test
    void libraryPackageGetsTheExportComponentByDefault_6_2_6_13() throws Exception {
        byte[] byDefault = withoutExportSetting(TestFixtures.LIBRARY).build().convert().capFile();
        CapImage cap = CapImage.parse(byDefault);

        assertThat(cap.has(CapImage.TAG_EXPORT)).isTrue();
        assertThat(cap.headerFlags() & ACC_EXPORT).isNotZero();
        assertThat(byDefault).isEqualTo(TestFixtures.LIBRARY.convert().capFile());
    }

    /** Public shareable interfaces of an applet package are exported by default as well (§6.13). */
    @Test
    void appletPackageWithShareableInterfacesGetsTheExportComponentByDefault_6_13() throws Exception {
        TestFixtures.Fixture share = TestFixtures.applet("ShareApplet");
        byte[] byDefault = withoutExportSetting(share).build().convert().capFile();

        assertThat(CapImage.parse(byDefault).exports()).hasSize(2);
        assertThat(byDefault).isEqualTo(share.convert().capFile());
    }

    @Test
    void appletPackageWithoutShareableInterfacesHasNoExportComponentByDefault_6_13() throws Exception {
        CapImage cap = CapImage.parse(withoutExportSetting(TestFixtures.applet("HelperApplet")).build().convert()
                .capFile());

        assertThat(cap.has(CapImage.TAG_EXPORT)).isFalse();
        assertThat(cap.headerFlags() & ACC_EXPORT).isZero();
    }

    /** The fixture's conversion settings without a {@code generateExport} call. */
    private static Converter.Builder withoutExportSetting(TestFixtures.Fixture fixture) {
        Converter.Builder b = Converter.builder().classesDirectory(TestFixtures.CLASSES_DIR)
                .packageName(fixture.pkg()).packageAid(fixture.pkgAid()).packageVersion(1, 0)
                .supportInt32(fixture.int32());
        if (fixture.applet() != null) {
            b.applet(fixture.applet(), fixture.appletAid());
        }
        return b;
    }
}
