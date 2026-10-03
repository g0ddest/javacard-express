package name.velikodniy.jcexpress.converter.resolve;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ExportedTypes}: what token assignment and the Class component learn about imported
 * types from the export files of the imported packages (JCVM 3.1 §5.7).
 */
class ExportedTypesTest {

    private static final ExportedTypes API_305 =
            new ExportedTypes(BuiltinExports.allBuiltinImports(0, JavaCardVersion.V3_0_5));

    @Test
    void superinterfacesOfAnImportedInterfaceAreTheInterfacesOfItsExportEntry_5_7() {
        // §5.7 interfaces[]: every public superinterface, direct and indirect
        assertThat(API_305.superInterfaces("javacard/security/ECPrivateKey")).containsExactlyInAnyOrder(
                "javacard/security/Key", "javacard/security/PrivateKey", "javacard/security/ECKey");
        assertThat(API_305.superInterfaces("javacard/security/PrivateKey"))
                .containsExactly("javacard/security/Key");
    }

    @Test
    void typesWithoutSuperinterfacesAndUnknownTypesHaveNone_5_7() {
        assertThat(API_305.superInterfaces("javacard/security/Key")).isEmpty();
        assertThat(API_305.superInterfaces("javacard/framework/Applet")).isEmpty();
        assertThat(API_305.superInterfaces("com/acme/Unknown")).isEmpty();
    }
}
