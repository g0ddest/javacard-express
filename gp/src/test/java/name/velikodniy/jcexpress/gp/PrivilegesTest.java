package name.velikodniy.jcexpress.gp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link Privileges}.
 */
class PrivilegesTest {

    @Test
    void securityDomainBitDetected() {
        assertThat(Privileges.isSecurityDomain(0x80)).isTrue();
        assertThat(Privileges.isSecurityDomain(0xA0)).isTrue(); // SD + DM
    }

    @Test
    void nonDomainNotDetected() {
        assertThat(Privileges.isSecurityDomain(0x00)).isFalse();
        assertThat(Privileges.isSecurityDomain(0x20)).isFalse(); // DM only
    }

    @Test
    void delegatedManagementDetected() {
        assertThat(Privileges.hasDelegatedManagement(0xA0)).isTrue(); // SD + DM
        assertThat(Privileges.hasDelegatedManagement(0x20)).isTrue(); // DM only
    }

    @Test
    void delegatedManagementNotDetectedWhenAbsent() {
        assertThat(Privileges.hasDelegatedManagement(0x80)).isFalse(); // SD only
        assertThat(Privileges.hasDelegatedManagement(0x00)).isFalse();
    }

    @Test
    void describeNone() {
        assertThat(Privileges.describe(0x00)).isEqualTo("none");
    }

    @Test
    void describeSingleBit() {
        assertThat(Privileges.describe(0x80)).isEqualTo("SECURITY_DOMAIN");
        assertThat(Privileges.describe(0x20)).isEqualTo("DELEGATED_MANAGEMENT");
        assertThat(Privileges.describe(0x01)).isEqualTo("MANDATED_DAP");
    }

    @Test
    void describeMultipleBits() {
        assertThat(Privileges.describe(0xA0))
                .isEqualTo("SECURITY_DOMAIN | DELEGATED_MANAGEMENT");
        assertThat(Privileges.describe(0xFF))
                .isEqualTo("SECURITY_DOMAIN | DAP_VERIFICATION | DELEGATED_MANAGEMENT"
                        + " | CARD_LOCK | CARD_TERMINATE | CARD_RESET"
                        + " | CVM_MANAGEMENT | MANDATED_DAP");
    }
}
