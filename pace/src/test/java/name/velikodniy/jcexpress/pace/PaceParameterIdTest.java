package name.velikodniy.jcexpress.pace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link PaceParameterId}: the standardized domain parameter IDs of ICAO Doc 9303-11 Table 12 and the
 * built-in curve parameters (FIPS 186-4 D.1.2, RFC 5639).
 */
class PaceParameterIdTest {

    @Test
    void idsFollowIcaoTable12() {
        assertThat(PaceParameterId.NIST_P192.id()).isEqualTo(8);
        assertThat(PaceParameterId.BRAINPOOL_P192R1.id()).isEqualTo(9);
        assertThat(PaceParameterId.NIST_P224.id()).isEqualTo(10);
        assertThat(PaceParameterId.BRAINPOOL_P224R1.id()).isEqualTo(11);
        assertThat(PaceParameterId.NIST_P256.id()).isEqualTo(12);
        assertThat(PaceParameterId.BRAINPOOL_P256R1.id()).isEqualTo(13);
        assertThat(PaceParameterId.BRAINPOOL_P320R1.id()).isEqualTo(14);
        assertThat(PaceParameterId.NIST_P384.id()).isEqualTo(15);
        assertThat(PaceParameterId.BRAINPOOL_P384R1.id()).isEqualTo(16);
        assertThat(PaceParameterId.BRAINPOOL_P512R1.id()).isEqualTo(17);
        assertThat(PaceParameterId.NIST_P521.id()).isEqualTo(18);
    }

    @Test
    void fromIdResolvesThePaceInfoOfIcaoAppendixG1() {
        // PACEInfo 3012060A04007F000702020402020201020201 0D: "Brainpool P256r1 Standardized Domain Parameters"
        assertThat(PaceParameterId.fromId(0x0D)).isEqualTo(PaceParameterId.BRAINPOOL_P256R1);
        assertThat(PaceParameterId.fromId(12)).isEqualTo(PaceParameterId.NIST_P256);
    }

    @ParameterizedTest
    @EnumSource(PaceParameterId.class)
    void fromIdRoundTrips(PaceParameterId parameterId) {
        assertThat(PaceParameterId.fromId(parameterId.id())).isEqualTo(parameterId);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 7, 19, 31, 99})
    void fromIdRejectsDhGroupsAndReservedIds(int id) {
        assertThatThrownBy(() -> PaceParameterId.fromId(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(id));
    }

    @ParameterizedTest
    @EnumSource(PaceParameterId.class)
    void generatorLiesOnTheCurveAndHasTheStatedOrder(PaceParameterId parameterId) {
        ECParameterSpec spec = parameterId.ecParameterSpec();
        BigInteger p = ((ECFieldFp) spec.getCurve().getField()).getP();
        ECPoint g = spec.getGenerator();
        BigInteger lhs = g.getAffineY().pow(2).mod(p);
        BigInteger rhs = g.getAffineX().pow(3).add(spec.getCurve().getA().multiply(g.getAffineX()))
                .add(spec.getCurve().getB()).mod(p);

        assertThat(lhs).isEqualTo(rhs);
        assertThat(PaceCrypto.scalarMultiply(spec.getOrder(), g, spec.getCurve())).isEqualTo(ECPoint.POINT_INFINITY);
        assertThat(spec.getOrder().isProbablePrime(64)).isTrue();
        assertThat(spec.getCofactor()).isEqualTo(1);
        assertThat(p.bitLength()).isEqualTo(parameterId.bitSize());
    }

    @ParameterizedTest
    @EnumSource(value = PaceParameterId.class, names = {"NIST_P256", "NIST_P384", "NIST_P521"})
    void nistCurvesMatchTheJdkProvider(PaceParameterId parameterId) throws Exception {
        AlgorithmParameters jdk = AlgorithmParameters.getInstance("EC");
        jdk.init(new ECGenParameterSpec(parameterId.curveName()));
        ECParameterSpec expected = jdk.getParameterSpec(ECParameterSpec.class);
        ECParameterSpec actual = parameterId.ecParameterSpec();

        assertThat(actual.getCurve()).isEqualTo(expected.getCurve());
        assertThat(actual.getGenerator()).isEqualTo(expected.getGenerator());
        assertThat(actual.getOrder()).isEqualTo(expected.getOrder());
    }

    @Test
    void brainpoolP256r1MatchesTheDomainParametersListedInIcaoAppendixG1() {
        ECParameterSpec spec = PaceParameterId.BRAINPOOL_P256R1.ecParameterSpec();

        assertThat(((ECFieldFp) spec.getCurve().getField()).getP()).isEqualTo(
                hex("A9FB57DBA1EEA9BC3E660A909D838D726E3BF623D52620282013481D1F6E5377"));
        assertThat(spec.getCurve().getA()).isEqualTo(
                hex("7D5A0975FC2C3057EEF67530417AFFE7FB8055C126DC5C6CE94A4B44F330B5D9"));
        assertThat(spec.getCurve().getB()).isEqualTo(
                hex("26DC5C6CE94A4B44F330B5D9BBD77CBF958416295CF7E1CE6BCCDC18FF8C07B6"));
        assertThat(spec.getGenerator()).isEqualTo(new ECPoint(
                hex("8BD2AEB9CB7E57CB2C4B482FFC81B7AFB9DE27E1E3BD23C23A4453BD9ACE3262"),
                hex("547EF835C3DAC4FD97F8461A14611DC9C27745132DED8E545C1D54C72F046997")));
        assertThat(spec.getOrder()).isEqualTo(
                hex("A9FB57DBA1EEA9BC3E660A909D838D718C397AA3B561A6F7901E0E82974856A7"));
    }

    @Test
    void ecParameterSpecShouldBeCached() {
        ECParameterSpec spec1 = PaceParameterId.NIST_P256.ecParameterSpec();
        ECParameterSpec spec2 = PaceParameterId.NIST_P256.ecParameterSpec();
        assertThat(spec1).isSameAs(spec2);
    }

    private static BigInteger hex(String value) {
        return new BigInteger(value, 16);
    }
}
