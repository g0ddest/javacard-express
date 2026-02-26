package name.velikodniy.jcexpress.pace;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link PaceCrypto}.
 */
class PaceCryptoTest {

    private ECParameterSpec ecParams() {
        return PaceParameterId.NIST_P256.ecParameterSpec();
    }

    @Test
    void encodeDecodeShouldRoundTrip() {
        ECParameterSpec params = ecParams();
        ECPoint g = params.getGenerator();
        int fieldSize = PaceCrypto.fieldSize(params.getCurve());

        byte[] encoded = PaceCrypto.encodePoint(g, fieldSize);
        ECPoint decoded = PaceCrypto.decodePoint(encoded, params.getCurve());

        assertThat(decoded.getAffineX()).isEqualTo(g.getAffineX());
        assertThat(decoded.getAffineY()).isEqualTo(g.getAffineY());
    }

    @Test
    void decodeInvalidPrefixShouldThrow() {
        assertThatThrownBy(() -> PaceCrypto.decodePoint(new byte[]{0x02, 0x01}, ecParams().getCurve()))
                .isInstanceOf(PaceException.class)
                .hasMessageContaining("0x04");
    }

    @Test
    void decodeEmptyShouldThrow() {
        assertThatThrownBy(() -> PaceCrypto.decodePoint(new byte[0], ecParams().getCurve()))
                .isInstanceOf(PaceException.class);
    }

    @Test
    void pointAddShouldBeCommutative() {
        ECParameterSpec params = ecParams();
        EllipticCurve curve = params.getCurve();
        ECPoint g = params.getGenerator();

        // 2G and 3G
        ECPoint g2 = PaceCrypto.scalarMultiply(BigInteger.TWO, g, curve);
        ECPoint g3 = PaceCrypto.scalarMultiply(BigInteger.valueOf(3), g, curve);

        ECPoint sum1 = PaceCrypto.pointAdd(g2, g3, curve);
        ECPoint sum2 = PaceCrypto.pointAdd(g3, g2, curve);

        assertThat(sum1.getAffineX()).isEqualTo(sum2.getAffineX());
        assertThat(sum1.getAffineY()).isEqualTo(sum2.getAffineY());

        // Should equal 5G
        ECPoint g5 = PaceCrypto.scalarMultiply(BigInteger.valueOf(5), g, curve);
        assertThat(sum1.getAffineX()).isEqualTo(g5.getAffineX());
        assertThat(sum1.getAffineY()).isEqualTo(g5.getAffineY());
    }

    @Test
    void pointAddIdentityShouldReturnSame() {
        ECParameterSpec params = ecParams();
        ECPoint g = params.getGenerator();

        ECPoint result = PaceCrypto.pointAdd(ECPoint.POINT_INFINITY, g, params.getCurve());
        assertThat(result.getAffineX()).isEqualTo(g.getAffineX());
        assertThat(result.getAffineY()).isEqualTo(g.getAffineY());

        result = PaceCrypto.pointAdd(g, ECPoint.POINT_INFINITY, params.getCurve());
        assertThat(result.getAffineX()).isEqualTo(g.getAffineX());
        assertThat(result.getAffineY()).isEqualTo(g.getAffineY());
    }

    @Test
    void scalarMultiplyByOneShouldReturnSame() {
        ECParameterSpec params = ecParams();
        ECPoint g = params.getGenerator();

        ECPoint result = PaceCrypto.scalarMultiply(BigInteger.ONE, g, params.getCurve());
        assertThat(result.getAffineX()).isEqualTo(g.getAffineX());
        assertThat(result.getAffineY()).isEqualTo(g.getAffineY());
    }

    @Test
    void scalarMultiplyByOrderShouldReturnInfinity() {
        ECParameterSpec params = ecParams();
        ECPoint g = params.getGenerator();
        BigInteger n = params.getOrder();

        ECPoint result = PaceCrypto.scalarMultiply(n, g, params.getCurve());
        assertThat(result).isEqualTo(ECPoint.POINT_INFINITY);
    }

    @Test
    void ecdhShouldBeSymmetric() {
        ECParameterSpec params = ecParams();
        KeyPair kp1 = PaceCrypto.generateKeyPair(params);
        KeyPair kp2 = PaceCrypto.generateKeyPair(params);

        byte[] secret1 = PaceCrypto.ecdh(
                (ECPrivateKey) kp1.getPrivate(),
                ((ECPublicKey) kp2.getPublic()).getW(),
                params);

        byte[] secret2 = PaceCrypto.ecdh(
                (ECPrivateKey) kp2.getPrivate(),
                ((ECPublicKey) kp1.getPublic()).getW(),
                params);

        assertThat(secret1).isEqualTo(secret2);
    }

    @Test
    void genericMappingShouldProduceNewGenerator() {
        ECParameterSpec params = ecParams();
        ECPoint g = params.getGenerator();
        EllipticCurve curve = params.getCurve();

        // Use a nonce of 42 and a shared point of 7*G
        BigInteger nonce = BigInteger.valueOf(42);
        ECPoint sharedPoint = PaceCrypto.scalarMultiply(BigInteger.valueOf(7), g, curve);

        ECPoint mapped = PaceCrypto.genericMapping(nonce, g, sharedPoint, curve);

        // G' = 42*G + 7*G = 49*G
        ECPoint expected = PaceCrypto.scalarMultiply(BigInteger.valueOf(49), g, curve);
        assertThat(mapped.getAffineX()).isEqualTo(expected.getAffineX());
        assertThat(mapped.getAffineY()).isEqualTo(expected.getAffineY());
    }

    @Test
    void generateKeyPairShouldBeOnCurve() {
        ECParameterSpec params = ecParams();
        KeyPair kp = PaceCrypto.generateKeyPair(params);

        ECPublicKey pub = (ECPublicKey) kp.getPublic();
        ECPrivateKey priv = (ECPrivateKey) kp.getPrivate();

        assertThat(pub.getW()).isNotEqualTo(ECPoint.POINT_INFINITY);
        assertThat(priv.getS()).isPositive();
        assertThat(priv.getS()).isLessThan(params.getOrder());
    }

    @Test
    void generateKeyPairOnGeneratorShouldBeOnCurve() {
        ECParameterSpec params = ecParams();
        EllipticCurve curve = params.getCurve();
        // Use 5*G as a "custom" generator
        ECPoint customG = PaceCrypto.scalarMultiply(BigInteger.valueOf(5), params.getGenerator(), curve);

        KeyPair kp = PaceCrypto.generateKeyPairOnGenerator(customG, params);
        ECPublicKey pub = (ECPublicKey) kp.getPublic();
        ECPrivateKey priv = (ECPrivateKey) kp.getPrivate();

        // Verify: pub = priv.s * customG
        ECPoint expected = PaceCrypto.scalarMultiply(priv.getS(), customG, curve);
        assertThat(pub.getW().getAffineX()).isEqualTo(expected.getAffineX());
        assertThat(pub.getW().getAffineY()).isEqualTo(expected.getAffineY());
    }

    @Test
    void authTokenShouldComputeAesCmac() {
        byte[] macKey = new byte[16]; // zero key
        byte[] oid = PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128.oidBytes();
        byte[] pubKey = new byte[65]; // dummy uncompressed point
        pubKey[0] = 0x04;

        byte[] token = PaceCrypto.authToken(macKey, oid, pubKey);

        // Token should be 8 bytes (truncated AES-CMAC)
        assertThat(token).hasSize(8);
        // Should be deterministic
        assertThat(PaceCrypto.authToken(macKey, oid, pubKey)).isEqualTo(token);
    }
}
