package name.velikodniy.jcexpress.pace;

import org.junit.jupiter.api.Test;

import java.security.spec.ECParameterSpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link PaceParameterId}.
 */
class PaceParameterIdTest {

    @Test
    void fromIdShouldReturnCorrectEnum() {
        assertThat(PaceParameterId.fromId(8)).isEqualTo(PaceParameterId.BRAINPOOL_P256R1);
        assertThat(PaceParameterId.fromId(13)).isEqualTo(PaceParameterId.NIST_P256);
        assertThat(PaceParameterId.fromId(15)).isEqualTo(PaceParameterId.NIST_P521);
    }

    @Test
    void fromIdInvalidShouldThrow() {
        assertThatThrownBy(() -> PaceParameterId.fromId(99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    void ecParameterSpecShouldReturnValidSpec() {
        for (PaceParameterId param : PaceParameterId.values()) {
            ECParameterSpec spec = param.ecParameterSpec();
            assertThat(spec).isNotNull();
            assertThat(spec.getOrder()).isPositive();
            assertThat(spec.getGenerator()).isNotNull();
            assertThat(spec.getCurve()).isNotNull();
        }
    }

    @Test
    void nistP256ShouldHaveCorrectBitSize() {
        assertThat(PaceParameterId.NIST_P256.bitSize()).isEqualTo(256);
        assertThat(PaceParameterId.NIST_P384.bitSize()).isEqualTo(384);
        assertThat(PaceParameterId.NIST_P521.bitSize()).isEqualTo(521);
    }

    @Test
    void brainpoolShouldHaveCorrectBitSize() {
        assertThat(PaceParameterId.BRAINPOOL_P256R1.bitSize()).isEqualTo(256);
        assertThat(PaceParameterId.BRAINPOOL_P384R1.bitSize()).isEqualTo(384);
        assertThat(PaceParameterId.BRAINPOOL_P512R1.bitSize()).isEqualTo(512);
    }

    @Test
    void ecParameterSpecShouldBeCached() {
        ECParameterSpec spec1 = PaceParameterId.NIST_P256.ecParameterSpec();
        ECParameterSpec spec2 = PaceParameterId.NIST_P256.ecParameterSpec();
        assertThat(spec1).isSameAs(spec2);
    }
}
