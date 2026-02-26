package name.velikodniy.jcexpress.pace;

import name.velikodniy.jcexpress.crypto.CryptoUtil;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;
import java.util.Arrays;

/**
 * Low-level elliptic curve operations for PACE.
 *
 * <p>Provides EC point arithmetic (addition, scalar multiplication),
 * ECDH key agreement, Generic Mapping, and authentication token computation.</p>
 *
 * <p>Point arithmetic uses affine coordinates with BigInteger. This is not
 * side-channel resistant but is appropriate for a testing framework.</p>
 */
public final class PaceCrypto {

    private PaceCrypto() {
    }

    /**
     * Decodes an uncompressed EC point ({@code 0x04 || x || y}).
     *
     * @param encoded the encoded point bytes
     * @param curve   the elliptic curve
     * @return the decoded EC point
     * @throws PaceException if the encoding is invalid
     */
    public static ECPoint decodePoint(byte[] encoded, EllipticCurve curve) {
        if (encoded.length == 0 || encoded[0] != 0x04) {
            throw new PaceException("Expected uncompressed EC point (0x04 prefix), got 0x"
                    + (encoded.length > 0 ? String.format("%02X", encoded[0] & 0xFF) : "empty"));
        }
        int fieldSize = fieldSize(curve);
        if (encoded.length != 1 + 2 * fieldSize) {
            throw new PaceException("Invalid EC point length: expected " + (1 + 2 * fieldSize)
                    + ", got " + encoded.length);
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(encoded, 1, 1 + fieldSize));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(encoded, 1 + fieldSize, encoded.length));
        return new ECPoint(x, y);
    }

    /**
     * Encodes an EC point as uncompressed ({@code 0x04 || x || y}).
     *
     * @param point     the EC point
     * @param fieldSize the field size in bytes
     * @return the encoded point bytes
     */
    public static byte[] encodePoint(ECPoint point, int fieldSize) {
        byte[] encoded = new byte[1 + 2 * fieldSize];
        encoded[0] = 0x04;
        byte[] x = toFixedLength(point.getAffineX(), fieldSize);
        byte[] y = toFixedLength(point.getAffineY(), fieldSize);
        System.arraycopy(x, 0, encoded, 1, fieldSize);
        System.arraycopy(y, 0, encoded, 1 + fieldSize, fieldSize);
        return encoded;
    }

    /**
     * Adds two EC points on the given curve.
     *
     * @param p     first point
     * @param q     second point
     * @param curve the elliptic curve
     * @return P + Q
     */
    public static ECPoint pointAdd(ECPoint p, ECPoint q, EllipticCurve curve) {
        if (p.equals(ECPoint.POINT_INFINITY)) return q;
        if (q.equals(ECPoint.POINT_INFINITY)) return p;

        BigInteger prime = ((ECFieldFp) curve.getField()).getP();
        BigInteger px = p.getAffineX();
        BigInteger py = p.getAffineY();
        BigInteger qx = q.getAffineX();
        BigInteger qy = q.getAffineY();

        BigInteger lambda;
        if (px.equals(qx)) {
            if (py.equals(qy)) {
                // Point doubling: lambda = (3*x^2 + a) / (2*y) mod p
                BigInteger num = px.modPow(BigInteger.TWO, prime)
                        .multiply(BigInteger.valueOf(3))
                        .add(curve.getA())
                        .mod(prime);
                BigInteger den = py.multiply(BigInteger.TWO).modInverse(prime);
                lambda = num.multiply(den).mod(prime);
            } else {
                // P + (-P) = infinity
                return ECPoint.POINT_INFINITY;
            }
        } else {
            // lambda = (qy - py) / (qx - px) mod p
            BigInteger num = qy.subtract(py).mod(prime);
            BigInteger den = qx.subtract(px).modInverse(prime);
            lambda = num.multiply(den).mod(prime);
        }

        // rx = lambda^2 - px - qx mod p
        BigInteger rx = lambda.modPow(BigInteger.TWO, prime)
                .subtract(px).subtract(qx).mod(prime);
        // ry = lambda * (px - rx) - py mod p
        BigInteger ry = lambda.multiply(px.subtract(rx)).subtract(py).mod(prime);

        return new ECPoint(rx, ry);
    }

    /**
     * Computes scalar multiplication k * P using double-and-add.
     *
     * @param k     the scalar
     * @param p     the EC point
     * @param curve the elliptic curve
     * @return k * P
     */
    public static ECPoint scalarMultiply(BigInteger k, ECPoint p, EllipticCurve curve) {
        if (k.signum() == 0 || p.equals(ECPoint.POINT_INFINITY)) {
            return ECPoint.POINT_INFINITY;
        }
        // Reduce k modulo the curve order if needed (caller should ensure positive k)
        ECPoint result = ECPoint.POINT_INFINITY;
        ECPoint addend = p;

        BigInteger scalar = k;
        while (scalar.signum() > 0) {
            if (scalar.testBit(0)) {
                result = pointAdd(result, addend, curve);
            }
            addend = pointAdd(addend, addend, curve);
            scalar = scalar.shiftRight(1);
        }
        return result;
    }

    /**
     * Performs ECDH key agreement using JCE for standard generators.
     *
     * @param privateKey  the local private key
     * @param publicPoint the remote public point
     * @param params      the EC parameters
     * @return the shared secret (x-coordinate of the shared point)
     * @throws PaceException if key agreement fails
     */
    public static byte[] ecdh(ECPrivateKey privateKey, ECPoint publicPoint, ECParameterSpec params) {
        // Use manual scalar multiplication to avoid JCE key encoding issues
        ECPoint sharedPoint = scalarMultiply(privateKey.getS(), publicPoint, params.getCurve());
        return toFixedLength(sharedPoint.getAffineX(), fieldSize(params.getCurve()));
    }

    /**
     * Generates a random EC key pair on the given parameters using JCE.
     *
     * @param params the EC parameters
     * @return the generated key pair
     * @throws PaceException if key generation fails
     */
    public static KeyPair generateKeyPair(ECParameterSpec params) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(params);
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new PaceException("EC key pair generation failed", e);
        }
    }

    /**
     * Generates a key pair on a custom generator G' (for PACE Step 3).
     *
     * <p>Since JCE doesn't support custom generators, this generates a random
     * scalar k and computes the public key as k * G'.</p>
     *
     * @param generator the custom generator point G'
     * @param params    the original EC parameters (for curve and order)
     * @return a key pair where the public key point is on G'
     * @throws PaceException if key generation fails
     */
    public static KeyPair generateKeyPairOnGenerator(ECPoint generator, ECParameterSpec params) {
        try {
            // Generate a random scalar using a standard key pair
            KeyPair temp = generateKeyPair(params);
            ECPrivateKey tempPriv = (ECPrivateKey) temp.getPrivate();
            BigInteger k = tempPriv.getS();

            // Compute public key: k * G'
            ECPoint pubPoint = scalarMultiply(k, generator, params.getCurve());

            // Build key objects
            java.security.KeyFactory kf = java.security.KeyFactory.getInstance("EC");
            java.security.spec.ECPrivateKeySpec privSpec = new java.security.spec.ECPrivateKeySpec(k, params);
            java.security.spec.ECPublicKeySpec pubSpec = new java.security.spec.ECPublicKeySpec(pubPoint, params);
            return new KeyPair(kf.generatePublic(pubSpec), kf.generatePrivate(privSpec));
        } catch (PaceException e) {
            throw e;
        } catch (Exception e) {
            throw new PaceException("Key pair generation on custom generator failed", e);
        }
    }

    /**
     * Performs Generic Mapping: G' = s * G + H.
     *
     * <p>Where s is the decrypted nonce, G is the base generator,
     * and H is the ECDH shared point from the mapping step.</p>
     *
     * @param nonce       the decrypted nonce as BigInteger
     * @param generator   the base generator G
     * @param sharedPoint the ECDH shared point H
     * @param curve       the elliptic curve
     * @return the mapped generator G'
     */
    public static ECPoint genericMapping(BigInteger nonce, ECPoint generator,
                                          ECPoint sharedPoint, EllipticCurve curve) {
        ECPoint sG = scalarMultiply(nonce, generator, curve);
        return pointAdd(sG, sharedPoint, curve);
    }

    /**
     * Computes the PACE authentication token.
     *
     * <p>token = AES-CMAC(K_mac, OID || publicKey), truncated to 8 bytes.</p>
     *
     * @param macKey          the MAC key
     * @param oidBytes        the DER-encoded OID bytes
     * @param publicKeyEncoded the encoded public key point (uncompressed)
     * @return 8-byte authentication token
     */
    public static byte[] authToken(byte[] macKey, byte[] oidBytes, byte[] publicKeyEncoded) {
        // Build the MAC input: 7F49 [L] (06 [L] OID || 86 [L] pubKey)
        // This is the ephemeral public key info per BSI TR-03110
        byte[] oidTlv = buildTlv(0x06, oidBytes);
        byte[] pkTlv = buildTlv(0x86, publicKeyEncoded);

        byte[] innerData = new byte[oidTlv.length + pkTlv.length];
        System.arraycopy(oidTlv, 0, innerData, 0, oidTlv.length);
        System.arraycopy(pkTlv, 0, innerData, oidTlv.length, pkTlv.length);

        byte[] macInput = buildTlv(0x7F49, innerData);

        byte[] fullMac = CryptoUtil.aesCmac(macKey, macInput);
        return Arrays.copyOf(fullMac, 8);
    }

    /**
     * Returns the field size in bytes for the given curve.
     *
     * @param curve the elliptic curve
     * @return field size in bytes
     */
    public static int fieldSize(EllipticCurve curve) {
        return (((ECFieldFp) curve.getField()).getP().bitLength() + 7) / 8;
    }

    /**
     * Converts a BigInteger to a fixed-length unsigned byte array.
     */
    static byte[] toFixedLength(BigInteger value, int length) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == length) {
            return bytes;
        }
        if (bytes.length > length) {
            // Strip leading zero byte(s)
            return Arrays.copyOfRange(bytes, bytes.length - length, bytes.length);
        }
        // Pad with leading zeros
        byte[] padded = new byte[length];
        System.arraycopy(bytes, 0, padded, length - bytes.length, bytes.length);
        return padded;
    }

    /**
     * Builds a simple TLV (tag + BER length + value).
     */
    private static byte[] buildTlv(int tag, byte[] value) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        // Write tag
        if (tag > 0xFFFF) {
            out.write((tag >> 16) & 0xFF);
            out.write((tag >> 8) & 0xFF);
            out.write(tag & 0xFF);
        } else if (tag > 0xFF) {
            out.write((tag >> 8) & 0xFF);
            out.write(tag & 0xFF);
        } else {
            out.write(tag & 0xFF);
        }
        // Write BER length
        if (value.length <= 0x7F) {
            out.write(value.length);
        } else if (value.length <= 0xFF) {
            out.write(0x81);
            out.write(value.length);
        } else {
            out.write(0x82);
            out.write((value.length >> 8) & 0xFF);
            out.write(value.length & 0xFF);
        }
        out.writeBytes(value);
        return out.toByteArray();
    }
}
