package name.velikodniy.jcexpress.pace;

import java.math.BigInteger;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;

/**
 * Provider-independent elliptic-curve key objects for the ephemeral PACE keys.
 *
 * <p>PACE only needs the raw scalar and point (ICAO Doc 9303-11, 4.4.3.3.1 and 9.6), so the keys are plain value
 * holders that implement the JCA interfaces for API compatibility. They have no encoded form
 * ({@code getEncoded()} returns {@code null}, as the {@link java.security.Key} contract allows), which keeps them
 * usable on curves the installed JCA provider does not support.</p>
 */
final class EcKeys {

    private EcKeys() {
    }

    /**
     * An EC public key: a point W on the curve of {@code params}.
     *
     * @param w      the public point
     * @param params the domain parameters
     */
    record PublicKey(ECPoint w, ECParameterSpec params) implements ECPublicKey {

        @Override
        public ECPoint getW() {
            return w;
        }

        @Override
        public ECParameterSpec getParams() {
            return params;
        }

        @Override
        public String getAlgorithm() {
            return "EC";
        }

        @Override
        public String getFormat() {
            return null;
        }

        @Override
        public byte[] getEncoded() {
            return null;
        }
    }

    /**
     * An EC private key: the scalar s.
     *
     * @param s      the private scalar, {@code 1 <= s < n}
     * @param params the domain parameters
     */
    record PrivateKey(BigInteger s, ECParameterSpec params) implements ECPrivateKey {

        @Override
        public BigInteger getS() {
            return s;
        }

        @Override
        public ECParameterSpec getParams() {
            return params;
        }

        @Override
        public String getAlgorithm() {
            return "EC";
        }

        @Override
        public String getFormat() {
            return null;
        }

        @Override
        public byte[] getEncoded() {
            return null;
        }

        /** Does not reveal the scalar. */
        @Override
        public String toString() {
            return "EcKeys.PrivateKey[s=<redacted>]";
        }
    }
}
