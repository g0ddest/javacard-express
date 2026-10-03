package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AppletInfo}: life cycle states (GPCS v2.3.1 11.1.1, Table 11-4) and privileges
 * (11.1.2, Table 11-7).
 */
class AppletInfoTest {

    private static final byte[] AID = Hex.decode("A000000003");

    @Test
    void table_11_4_selectableAndInstalledStates() {
        assertThat(new AppletInfo(AID, 0x07, 0x00).isSelectable()).isTrue();
        assertThat(new AppletInfo(AID, 0x03, 0x00).isSelectable()).isFalse();
    }

    @Test
    void table_11_4_lockedStateHasBit8Set() {
        AppletInfo info = new AppletInfo(AID, 0x83, 0x00);

        assertThat(info.isLocked()).isTrue();
        assertThat(info.isSelectable()).isFalse();
    }

    @Test
    void table_11_4_personalizedAndTerminatedStates() {
        assertThat(new AppletInfo(AID, 0x0F, 0x00).isPersonalized()).isTrue();
        assertThat(new AppletInfo(AID, 0x07, 0x00).isPersonalized()).isFalse();
        assertThat(new AppletInfo(AID, 0xFF, 0x00).isTerminated()).isTrue();
        assertThat(new AppletInfo(AID, 0x07, 0x00).isTerminated()).isFalse();
        assertThat(new AppletInfo(AID, 0x07, 0x00).lifeCycleDescription()).contains("SELECTABLE");
    }

    @Test
    void table_11_7_privilegesOfByte1() {
        assertThat(new AppletInfo(AID, 0x07, 0x80).isSecurityDomain()).isTrue();
        assertThat(new AppletInfo(AID, 0x07, 0x00).isSecurityDomain()).isFalse();
        assertThat(new AppletInfo(AID, 0x07, 0xA0).hasDelegatedManagement()).isTrue();
        assertThat(new AppletInfo(AID, 0x07, 0x80).hasDelegatedManagement()).isFalse();
        assertThat(new AppletInfo(AID, 0x07, 0xA0).privilegeDescription())
                .isEqualTo("SECURITY_DOMAIN | DELEGATED_MANAGEMENT");
        assertThat(new AppletInfo(AID, 0x07, 0x00).privilegeDescription()).isEqualTo("none");
    }

    @Test
    void table_11_36_allRegistryFieldsAreKept() {
        AppletInfo info = new AppletInfo(Hex.decode("A000000003000000"), 0x07, 0x9E, Hex.decode("9EFF80"),
                Hex.decode("A0000000620001"), Hex.decode("A000000003000000"), Hex.decode("0100"),
                List.of(Hex.decode("A000000151535041")));

        assertThat(info.toString()).contains("A000000003000000").contains("9EFF80");
        assertThat(info).isEqualTo(new AppletInfo(Hex.decode("A000000003000000"), 0x07, 0x9E,
                Hex.decode("9EFF80"), Hex.decode("A0000000620001"), Hex.decode("A000000003000000"),
                Hex.decode("0100"), List.of(Hex.decode("A000000151535041"))));
        assertThat(info).isNotEqualTo(new AppletInfo(Hex.decode("A000000003000000"), 0x07, 0x9E));
    }

    @Test
    void compatibilityConstructorKeepsTheSingleByteOfPrivileges() {
        AppletInfo info = new AppletInfo(Hex.decode("A0000000031010"), 0x07, 0x00);

        assertThat(info.toString()).contains("A0000000031010");
        assertThat(info.privilegeBytes()).containsExactly(0x00);
        assertThat(info.executableModuleAids()).isEmpty();
    }
}
