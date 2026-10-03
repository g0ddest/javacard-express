package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.input.ClassInfo;
import name.velikodniy.jcexpress.converter.input.FieldInfo;
import name.velikodniy.jcexpress.converter.input.PackageInfo;
import name.velikodniy.jcexpress.converter.token.TokenAssigner;
import name.velikodniy.jcexpress.converter.token.TokenMap;
import name.velikodniy.jcexpress.converter.translate.JcvmConstantPool;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests of {@link ReferenceResolver} rules that do not need a whole package.
 */
class ReferenceResolverTest {

    private final JcvmConstantPool cp = new JcvmConstantPool();

    private ReferenceResolver resolver() {
        TokenMap tokens = TokenAssigner.assign(new PackageInfo("p", List.of()));
        return new ReferenceResolver(tokens,
                new ArrayList<>(BuiltinExports.allBuiltinImports(0, JavaCardVersion.V3_0_5)), cp, List.of());
    }

    @Test
    void methodsAreLinkedByNameAndDescriptorNeverByNameOnly_5_9() {
        ReferenceResolver resolver = resolver();

        // javacard.security.Signature.getLength() returns short; a 'byte' variant does not exist
        assertThatThrownBy(() -> resolver.resolveMethodRef("javacard/security/Signature", "getLength", "()B",
                ReferenceResolver.InvokeKind.VIRTUAL))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Signature.getLength()B")
                .hasMessageContaining("getLength()S");
        assertThat(cp.size()).isZero();
    }

    @Test
    void exactDescriptorMatchIsLinked() {
        int index = resolver().resolveMethodRef("javacard/security/Signature", "getLength", "()S",
                ReferenceResolver.InvokeKind.VIRTUAL);

        assertThat(cp.entries().get(index).tag()).isEqualTo(JcvmConstantPool.TAG_VIRTUAL_METHODREF);
    }

    @Test
    void internalClassRefIsAnOffsetOnlyKnownAfterTheClassComponent_6_8_1() {
        ReferenceResolver resolver = resolver();

        assertThatThrownBy(() -> resolver.resolveClassRefDirect("p/C"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unresolvableInternalReferenceIsAnErrorNotAPlaceholder_6_8() {
        ClassInfo c = new ClassInfo("p/C", "java/lang/Object", List.of(), 0x0001, List.of(),
                List.of(new FieldInfo("X", "S", 0x0008, null)));
        ReferenceResolver resolver = new ReferenceResolver(
                TokenAssigner.assign(new PackageInfo("p", List.of(c))),
                new ArrayList<>(BuiltinExports.allBuiltinImports(0, JavaCardVersion.V3_0_5)), cp, List.of(c));
        resolver.resolveFieldRef("p/C", "X", "S", true);

        // the static field image offset of p/C.X is missing: no placeholder may survive
        assertThatThrownBy(() -> resolver.patchInternalRefs(Map.of("p/C", 0), Map.of(), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("p/C:X");
    }
}
