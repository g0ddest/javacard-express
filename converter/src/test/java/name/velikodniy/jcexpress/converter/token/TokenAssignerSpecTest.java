package name.velikodniy.jcexpress.converter.token;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.FieldInfo;
import name.velikodniy.jcexpress.converter.input.MethodInfo;
import name.velikodniy.jcexpress.converter.input.PackageInfo;
import name.velikodniy.jcexpress.converter.input.PackageScanner;
import name.velikodniy.jcexpress.converter.resolve.BuiltinExports;
import name.velikodniy.jcexpress.converter.resolve.ExportedTypes;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Token assignment rules of JCVM 3.1 §4.3.7 checked on the converter's test packages
 * (compiled against the javacard-api stubs) with the built-in JC 3.0.5 API export data.
 */
class TokenAssignerSpecTest {

    private static final Path CLASSES = Path.of("target/test-classes");
    private static final int ACC_PUBLIC = 0x0001;
    private static final int ACC_PRIVATE = 0x0002;
    private static final int ACC_ABSTRACT = 0x0400;
    private static final int ACC_INTERFACE = 0x0200;

    private static TokenMap assign(String pkg) throws IOException {
        PackageInfo info = PackageScanner.scan(CLASSES, pkg);
        return TokenAssigner.assign(info,
                new ExportedTypes(BuiltinExports.allBuiltinImports(0, JavaCardVersion.V3_0_5)));
    }

    private static int virtualToken(TokenMap map, String cls, String name, String desc) {
        return map.findClass(cls).findVirtualMethod(name, desc).token();
    }

    private static List<String> order(TokenMap map) {
        return map.classes().stream().map(TokenMap.ClassEntry::internalName).toList();
    }

    // ── §4.3.7.2 class tokens ──

    @Test
    void packageVisibleClassHasNoClassToken_4_3_7_2() throws IOException {
        TokenMap map = assign("com.example.helper");

        assertThat(map.classToken("com/example/helper/Counter")).isEqualTo(TokenMap.NO_TOKEN);
        assertThat(map.classToken("com/example/helper/HelperApplet")).isZero();
    }

    @Test
    void shareableInterfacesGetTheLowestPublicClassTokens_4_3_7_2_and_6_13() throws IOException {
        TokenMap map = assign("com.example.share");

        assertThat(map.classToken("com/example/share/IBase")).isZero();
        assertThat(map.classToken("com/example/share/IExt")).isEqualTo(1);
        assertThat(map.classToken("com/example/share/AStatus")).isEqualTo(2);
        assertThat(map.classToken("com/example/share/ShareApplet")).isEqualTo(3);
    }

    @Test
    void publicClassTokensOfALibraryAreConsecutiveAndSkipPackageVisibleClasses() throws IOException {
        TokenMap map = assign("com.example.lib");

        List<Integer> tokens = map.classes().stream().map(TokenMap.ClassEntry::token)
                .filter(t -> t != TokenMap.NO_TOKEN).sorted().toList();
        assertThat(tokens).containsExactly(0, 1, 2);
        assertThat(map.classToken("com/example/lib/Hidden")).isEqualTo(TokenMap.NO_TOKEN);
    }

    // ── §6.9 component order ──

    @Test
    void superinterfacePrecedesSubinterfaceAndInterfacesPrecedeClasses_6_9() throws IOException {
        TokenMap map = assign("com.example.ifaceorder");

        assertThat(order(map)).containsExactly(
                "com/example/ifaceorder/ZSuper", "com/example/ifaceorder/ASub",
                "com/example/ifaceorder/OrderApplet");
    }

    @Test
    void superclassPrecedesSubclassOtherwiseByName_6_9() throws IOException {
        TokenMap map = assign("com.example.chain");

        assertThat(order(map)).containsExactly(
                "com/example/chain/A", "com/example/chain/B", "com/example/chain/C",
                "com/example/chain/ChainApplet", "com/example/chain/D", "com/example/chain/E");
    }

    // ── §4.3.7.6 virtual methods ──

    @Test
    void packageVisibleMethodsGetPrivateTokensWithHighBit_4_3_7_6() throws IOException {
        TokenMap map = assign("com.example.pkgvirt");
        String base = "com/example/pkgvirt/Base";
        String sub = "com/example/pkgvirt/Sub";

        assertThat(virtualToken(map, base, "calc", "(S)S")).isEqualTo(0x80);
        assertThat(virtualToken(map, base, "touch", "()V")).isEqualTo(0x81);
        assertThat(virtualToken(map, base, "pub", "()S")).isEqualTo(1);
        assertThat(virtualToken(map, sub, "calc", "(S)S")).as("override keeps the token").isEqualTo(0x80);
        assertThat(virtualToken(map, sub, "extra", "()S")).isEqualTo(0x82);
        assertThat(virtualToken(map, sub, "pub", "()S")).isEqualTo(1);
    }

    @Test
    void privateTokensStartAtZeroWhenTheSuperclassIsInAnotherPackage_4_3_7_6() throws IOException {
        TokenMap map = assign("com.example.helper");
        String applet = "com/example/helper/HelperApplet";

        assertThat(virtualToken(map, applet, "handleIncrement", "()V")).isEqualTo(0x80);
        assertThat(virtualToken(map, applet, "handleRead", "(Ljavacard/framework/APDU;[B)V"))
                .isEqualTo(0x81);
        assertThat(virtualToken(map, applet, "process", "(Ljavacard/framework/APDU;)V")).isEqualTo(7);
    }

    @Test
    void publicOrProtectedOverrideOfPackageVisibleMethodIsRejected_2_2_1_1() {
        assertThatThrownBy(() -> assign("com.example.pubover"))
                .isInstanceOf(TokenAssignmentException.class)
                .satisfies(e -> assertThat(((TokenAssignmentException) e).violations())
                        .extracting(v -> v.className() + " " + v.context())
                        .containsExactlyInAnyOrder("com/example/pubover/PSub touch()V",
                                "com/example/pubover/PSub val()S"));
    }

    // ── §4.3.7.7 interface methods ──

    @Test
    void interfaceMethodTokensStartAtZeroAndIncludeInheritedMethods_4_3_7_7() throws IOException {
        TokenMap map = assign("com.example.share");

        assertThat(map.findClass("com/example/share/IBase").virtualMethods())
                .containsExactly(new TokenMap.MethodEntry("get", "()S", 0));
        assertThat(map.findClass("com/example/share/IExt").virtualMethods())
                .containsExactly(new TokenMap.MethodEntry("get", "()S", 0),
                        new TokenMap.MethodEntry("set", "(S)V", 1));
    }

    @Test
    void everyMethodOfAnImplementedInterfaceNeedsAVirtualMethodOfTheClass_6_9_2_5() {
        MethodInfo run = new MethodInfo("run", "(S)S", ACC_PUBLIC | ACC_ABSTRACT, 0, 0, new byte[0], List.of());
        ClassInfo cmd = new ClassInfo("p/Cmd", "java/lang/Object", List.of(),
                ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT, List.of(run), List.of());
        ClassInfo base = new ClassInfo("p/Base", "java/lang/Object", List.of("p/Cmd"),
                ACC_PUBLIC | ACC_ABSTRACT, List.of(), List.of());

        assertThatThrownBy(() -> TokenAssigner.assign(new PackageInfo("p", List.of(cmd, base))))
                .isInstanceOf(TokenAssignmentException.class)
                .satisfies(e -> assertThat(((TokenAssignmentException) e).violations())
                        .extracting(v -> v.className() + " " + v.context())
                        .containsExactly("p/Base run(S)S"));
    }

    /**
     * JLS 8.1.1.1 lets an abstract class leave interface methods undeclared; the converter declares
     * them in the topmost abstract class that lacks them (subclasses inherit the declaration), never in
     * a concrete class, whose missing method stays an error of {@link TokenAssigner#assign}.
     */
    @Test
    void undeclaredInterfaceMethodsAreDeclaredOnceInTheTopmostAbstractClass_6_9_2_5() {
        MethodInfo run = new MethodInfo("run", "(S)S", ACC_PUBLIC | ACC_ABSTRACT, 0, 0, new byte[0], List.of());
        MethodInfo impl = new MethodInfo("run", "(S)S", ACC_PUBLIC, 1, 2,
                new byte[] {0x1B, (byte) 0xAC}, List.of());
        ClassInfo cmd = new ClassInfo("p/Cmd", "java/lang/Object", List.of(),
                ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT, List.of(run), List.of());
        ClassInfo base = new ClassInfo("p/Base", "java/lang/Object", List.of("p/Cmd"),
                ACC_PUBLIC | ACC_ABSTRACT, List.of(), List.of());
        ClassInfo mid = new ClassInfo("p/Mid", "p/Base", List.of("p/Cmd"), ACC_PUBLIC | ACC_ABSTRACT,
                List.of(), List.of());
        ClassInfo leaf = new ClassInfo("p/Leaf", "p/Mid", List.of(), ACC_PUBLIC, List.of(impl), List.of());
        ClassInfo concrete = new ClassInfo("p/Concrete", "java/lang/Object", List.of("p/Cmd"), ACC_PUBLIC,
                List.of(), List.of());

        var undeclared = TokenAssigner.undeclaredInterfaceMethods(
                new PackageInfo("p", List.of(leaf, mid, concrete, base, cmd)), ImportedTypes.of(n -> List.of()));

        assertThat(undeclared).containsOnlyKeys("p/Base");
        assertThat(undeclared.get("p/Base")).extracting(m -> m.name() + m.descriptor()).containsExactly("run(S)S");
    }

    @Test
    void interfaceMethodsInheritedFromTheSuperclassAreImplemented() {
        MethodInfo run = new MethodInfo("run", "(S)S", ACC_PUBLIC | ACC_ABSTRACT, 0, 0, new byte[0], List.of());
        MethodInfo impl = new MethodInfo("run", "(S)S", ACC_PUBLIC, 1, 2,
                new byte[] {0x1B, (byte) 0xAC}, List.of());
        ClassInfo cmd = new ClassInfo("p/Cmd", "java/lang/Object", List.of(),
                ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT, List.of(run), List.of());
        ClassInfo base = new ClassInfo("p/Base", "java/lang/Object", List.of(), ACC_PUBLIC, List.of(impl), List.of());
        ClassInfo sub = new ClassInfo("p/Sub", "p/Base", List.of("p/Cmd"), ACC_PUBLIC | ACC_ABSTRACT,
                List.of(), List.of());

        TokenMap map = TokenAssigner.assign(new PackageInfo("p", List.of(cmd, base, sub)));

        assertThat(map.findClass("p/Sub").findVirtualMethod("run", "(S)S").token())
                .isEqualTo(map.findClass("p/Base").findVirtualMethod("run", "(S)S").token());
    }

    @Test
    void interfaceWithCodeIsRejected() {
        MethodInfo body = new MethodInfo("m", "()V", ACC_PUBLIC, 0, 1, new byte[] {(byte) 0xB1}, List.of());
        ClassInfo iface = new ClassInfo("p/I", "java/lang/Object", List.of(),
                ACC_PUBLIC | ACC_INTERFACE | ACC_ABSTRACT, List.of(body), List.of());

        assertThatThrownBy(() -> TokenAssigner.assign(new PackageInfo("p", List.of(iface))))
                .isInstanceOf(TokenAssignmentException.class)
                .hasMessageContaining("p/I");
    }

    // ── §4.3.7.5 instance fields ──

    @Test
    void instanceFieldTokensFollowVisibilityAndTypeGroups_4_3_7_5() throws IOException {
        TokenMap map = assign("com.example.fields");

        assertThat(map.findClass("com/example/fields/FieldsApplet").instanceFields())
                .extracting(f -> f.name() + "=" + f.token())
                .containsExactly("pubS=0", "protI=1", "pubArr=3", "buf=4", "obj=5", "shorts=6",
                        "a=7", "counter=8", "flag=10", "last=11");
    }

    @Test
    void classNeedingMoreThan255InstanceFieldCellsIsRejected_4_3_7_5() {
        List<FieldInfo> fields = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            fields.add(new FieldInfo("f" + i, "S", ACC_PRIVATE, null));
        }
        ClassInfo big = new ClassInfo("p/Big", "java/lang/Object", List.of(), ACC_PUBLIC, List.of(), fields);

        assertThatThrownBy(() -> TokenAssigner.assign(new PackageInfo("p", List.of(big))))
                .isInstanceOf(TokenAssignmentException.class)
                .hasMessageContaining("p/Big");
    }
}
