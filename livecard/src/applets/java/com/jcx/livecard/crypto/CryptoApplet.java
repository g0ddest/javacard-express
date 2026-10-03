package com.jcx.livecard.crypto;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;
import javacard.security.AESKey;
import javacard.security.CryptoException;
import javacard.security.DESKey;
import javacard.security.ECKey;
import javacard.security.ECPublicKey;
import javacard.security.HMACKey;
import javacard.security.KeyBuilder;
import javacard.security.KeyPair;
import javacard.security.MessageDigest;
import javacard.security.RSAPublicKey;
import javacard.security.RandomData;
import javacard.security.Signature;
import javacardx.crypto.Cipher;

/**
 * Crypto API probe for real cards. Every algorithm is selected through the API constants of the stubs. Its
 * objects (KeyBuilder.buildKey, getInstance, init) are created once, on first use, in a method of their own, so
 * a card that lacks an algorithm fails only that algorithm's commands. The test asks for the objects first (INS
 * '1F'): a CryptoException there is answered with SW '6F0R', R being its reason code, and only NO_SUCH_ALGORITHM
 * ('6F03') is reported as "not supported by this card"; any other answer ('6F00' for an exception that is not a
 * CryptoException, another reason) and a wrong result of the computation are failures.
 *
 * <pre>
 * INS 1F, P1 = INS of an algorithm: create its objects only, no data; P1 1A: an algorithm no card has (code 7F)
 * INS 10 AES-128 ECB  (FIPS-197 C.1 key/plaintext)    INS 15 16 random bytes
 * INS 11 AES-128 CBC  (zero IV, same block)           INS 16 ECDSA P-256 / SHA-256 of "abc": W || len || sig
 * INS 12 SHA-256("abc")                               INS 17 RSA-2048 PKCS#1 SHA-256 of "abc": P1 0 generate,
 * INS 13 SHA-1("abc")                                        1 modulus, 2 signature, 3 public exponent
 * INS 14 2-key 3DES ECB of 0011223344556677           INS 18 HMAC-SHA-256 (RFC 4231 test case 2)
 *                                                     INS 19 AES-128 CBC ISO 9797-1 M2 of "abc"
 * </pre>
 *
 * <p>Every case is a call from a short dispatcher. The applet avoided exception handlers and {@code array.length}
 * as a workaround for converter defects of an earlier snapshot (one-byte branches in long methods, wrong catch
 * types, {@code arraylength} not translated) that are fixed now: ConverterFeaturesLiveTest covers these constructs
 * on the card (LIVE_CARD_TESTING.md, "Known issues"), and {@code allocate} catches CryptoException.</p>
 */
public class CryptoApplet extends Applet {
    private static final byte INS_ALLOCATE = 0x1F;
    /** SW '6F00' plus the reason of a CryptoException thrown while an algorithm's objects are created. */
    private static final short SW_CRYPTO_EXCEPTION = 0x6F00;
    /** An algorithm code that no card supports, for the control case INS 1F P1 1A. */
    private static final byte NO_ALGORITHM = 0x7F;
    private static final byte[] P256_P = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00, 0x00, 0x01,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
        (byte) 0xFF};
    private static final byte[] P256_A = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00, 0x00, 0x01,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
        (byte) 0xFC};
    private static final byte[] P256_B = {0x5A, (byte) 0xC6, 0x35, (byte) 0xD8, (byte) 0xAA, 0x3A, (byte) 0x93,
        (byte) 0xE7, (byte) 0xB3, (byte) 0xEB, (byte) 0xBD, 0x55, 0x76, (byte) 0x98, (byte) 0x86, (byte) 0xBC, 0x65,
        0x1D, 0x06, (byte) 0xB0, (byte) 0xCC, 0x53, (byte) 0xB0, (byte) 0xF6, 0x3B, (byte) 0xCE, 0x3C, 0x3E, 0x27,
        (byte) 0xD2, 0x60, 0x4B};
    private static final byte[] P256_G = {0x04, 0x6B, 0x17, (byte) 0xD1, (byte) 0xF2, (byte) 0xE1, 0x2C, 0x42, 0x47,
        (byte) 0xF8, (byte) 0xBC, (byte) 0xE6, (byte) 0xE5, 0x63, (byte) 0xA4, 0x40, (byte) 0xF2, 0x77, 0x03, 0x7D,
        (byte) 0x81, 0x2D, (byte) 0xEB, 0x33, (byte) 0xA0, (byte) 0xF4, (byte) 0xA1, 0x39, 0x45, (byte) 0xD8,
        (byte) 0x98, (byte) 0xC2, (byte) 0x96, 0x4F, (byte) 0xE3, 0x42, (byte) 0xE2, (byte) 0xFE, 0x1A, 0x7F,
        (byte) 0x9B, (byte) 0x8E, (byte) 0xE7, (byte) 0xEB, 0x4A, 0x7C, 0x0F, (byte) 0x9E, 0x16, 0x2B, (byte) 0xCE,
        0x33, 0x57, 0x6B, 0x31, 0x5E, (byte) 0xCE, (byte) 0xCB, (byte) 0xB6, 0x40, 0x68, 0x37, (byte) 0xBF, 0x51,
        (byte) 0xF5};
    private static final byte[] P256_N = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00, 0x00, 0x00,
        (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF,
        (byte) 0xBC, (byte) 0xE6, (byte) 0xFA, (byte) 0xAD, (byte) 0xA7, 0x17, (byte) 0x9E, (byte) 0x84, (byte) 0xF3,
        (byte) 0xB9, (byte) 0xCA, (byte) 0xC2, (byte) 0xFC, 0x63, 0x25, 0x51};
    private static final byte[] AES_KEY = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B,
        0x0C, 0x0D, 0x0E, 0x0F};
    private static final byte[] PLAINTEXT = {0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99,
        (byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD, (byte) 0xEE, (byte) 0xFF};
    private static final byte[] DES_KEY = {0x01, 0x23, 0x45, 0x67, (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF,
        (byte) 0xFE, (byte) 0xDC, (byte) 0xBA, (byte) 0x98, 0x76, 0x54, 0x32, 0x10};
    private static final byte[] ABC = {'a', 'b', 'c'};
    private static final byte[] HMAC_KEY = {'J', 'e', 'f', 'e'};
    private static final byte[] HMAC_DATA = {'w', 'h', 'a', 't', ' ', 'd', 'o', ' ', 'y', 'a', ' ', 'w', 'a', 'n', 't',
        ' ', 'f', 'o', 'r', ' ', 'n', 'o', 't', 'h', 'i', 'n', 'g', '?'};
    private static final short HMAC_KEY_LENGTH = 4;
    private static final short HMAC_DATA_LENGTH = 28;

    private AESKey aesKey;
    private Cipher aesEcb;
    private Cipher aesCbc;
    private Cipher aesCbcM2;
    private Cipher desEcb;
    private MessageDigest sha256;
    private MessageDigest sha1;
    private RandomData random;
    private Signature hmac;
    private KeyPair ecKeyPair;
    private Signature ecdsa;
    private KeyPair rsaKeyPair;
    private Signature rsa;
    private byte[] rsaOut;

    private CryptoApplet() {
        register();
    }

    /**
     * Installs the applet.
     *
     * @param bArray  install parameters
     * @param bOffset offset of the parameters
     * @param bLength length of the parameters
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new CryptoApplet();
    }

    /**
     * Dispatches the probe commands.
     *
     * @param apdu the command
     */
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        short length = buf[ISO7816.OFFSET_INS] == INS_ALLOCATE ? allocate(buf[ISO7816.OFFSET_P1]) : compute(buf);
        apdu.setOutgoingAndSend((short) 0, length);
    }

    /**
     * Creates the objects of one algorithm (P1 = its INS) and answers no data; a CryptoException is answered with
     * SW '6F0R' (R = its reason code, NO_SUCH_ALGORITHM = 3 when the card lacks the algorithm).
     */
    private short allocate(byte ins) {
        try {
            return create(ins);
        } catch (CryptoException e) {
            ISOException.throwIt((short) (SW_CRYPTO_EXCEPTION | e.getReason()));
            return 0;
        }
    }

    private short create(byte ins) {
        switch (ins) {
            case 0x10: aesEcb(); return 0;
            case 0x11: aesCbc(); return 0;
            case 0x12: sha256(); return 0;
            case 0x13: sha1(); return 0;
            case 0x14: desEcb(); return 0;
            case 0x15: random(); return 0;
            case 0x16: ecdsa(); return 0;
            case 0x17: rsa(); return 0;
            case 0x18: hmac(); return 0;
            case 0x19: aesCbcM2(); return 0;
            case 0x1A: Signature.getInstance(NO_ALGORITHM, false); return 0;
            default: ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2); return 0;
        }
    }

    private short compute(byte[] buf) {
        switch (buf[ISO7816.OFFSET_INS]) {
            case 0x10: return aesEcb().doFinal(PLAINTEXT, (short) 0, (short) 16, buf, (short) 0);
            case 0x11: return aesCbc().doFinal(PLAINTEXT, (short) 0, (short) 16, buf, (short) 0);
            case 0x12: return sha256().doFinal(ABC, (short) 0, (short) 3, buf, (short) 0);
            case 0x13: return sha1().doFinal(ABC, (short) 0, (short) 3, buf, (short) 0);
            case 0x14: return desEcb().doFinal(PLAINTEXT, (short) 0, (short) 8, buf, (short) 0);
            case 0x15: return randomBytes(buf);
            case 0x16: return signEcdsa(buf);
            case 0x17: return rsaPart(buf, buf[ISO7816.OFFSET_P1]);
            case 0x18: return hmac().sign(HMAC_DATA, (short) 0, HMAC_DATA_LENGTH, buf, (short) 0);
            case 0x19: return aesCbcM2().doFinal(ABC, (short) 0, (short) 3, buf, (short) 0);
            default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED); return 0;
        }
    }

    private AESKey aesKey() {
        if (aesKey == null) {
            AESKey key = (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
            key.setKey(AES_KEY, (short) 0);
            aesKey = key;
        }
        return aesKey;
    }

    private Cipher aesCipher(byte algorithm) {
        Cipher cipher = Cipher.getInstance(algorithm, false);
        cipher.init(aesKey(), Cipher.MODE_ENCRYPT);
        return cipher;
    }

    private Cipher aesEcb() {
        if (aesEcb == null) {
            aesEcb = aesCipher(Cipher.ALG_AES_BLOCK_128_ECB_NOPAD);
        }
        return aesEcb;
    }

    private Cipher aesCbc() {
        if (aesCbc == null) {
            aesCbc = aesCipher(Cipher.ALG_AES_BLOCK_128_CBC_NOPAD);
        }
        return aesCbc;
    }

    private Cipher aesCbcM2() {
        if (aesCbcM2 == null) {
            aesCbcM2 = aesCipher(Cipher.ALG_AES_CBC_ISO9797_M2);
        }
        return aesCbcM2;
    }

    private Cipher desEcb() {
        if (desEcb == null) {
            DESKey key = (DESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_DES, KeyBuilder.LENGTH_DES3_2KEY, false);
            key.setKey(DES_KEY, (short) 0);
            Cipher cipher = Cipher.getInstance(Cipher.ALG_DES_ECB_NOPAD, false);
            cipher.init(key, Cipher.MODE_ENCRYPT);
            desEcb = cipher;
        }
        return desEcb;
    }

    private MessageDigest sha256() {
        if (sha256 == null) {
            sha256 = MessageDigest.getInstance(MessageDigest.ALG_SHA_256, false);
        }
        return sha256;
    }

    private MessageDigest sha1() {
        if (sha1 == null) {
            sha1 = MessageDigest.getInstance(MessageDigest.ALG_SHA, false);
        }
        return sha1;
    }

    private RandomData random() {
        if (random == null) {
            random = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
        }
        return random;
    }

    private short randomBytes(byte[] buf) {
        random().generateData(buf, (short) 0, (short) 16);
        return 16;
    }

    private Signature hmac() {
        if (hmac == null) {
            HMACKey key = (HMACKey) KeyBuilder.buildKey(KeyBuilder.TYPE_HMAC, KeyBuilder.LENGTH_HMAC_SHA_256_BLOCK_64,
                    false);
            key.setKey(HMAC_KEY, (short) 0, HMAC_KEY_LENGTH);
            Signature signature = Signature.getInstance(Signature.ALG_HMAC_SHA_256, false);
            signature.init(key, Signature.MODE_SIGN);
            hmac = signature;
        }
        return hmac;
    }

    private static void p256(ECKey key) {
        key.setFieldFP(P256_P, (short) 0, (short) 32);
        key.setA(P256_A, (short) 0, (short) 32);
        key.setB(P256_B, (short) 0, (short) 32);
        key.setG(P256_G, (short) 0, (short) 65);
        key.setR(P256_N, (short) 0, (short) 32);
        key.setK((short) 1);
    }

    private Signature ecdsa() {
        if (ecdsa == null) {
            KeyPair pair = new KeyPair(KeyPair.ALG_EC_FP, KeyBuilder.LENGTH_EC_FP_256);
            p256((ECKey) pair.getPublic());
            p256((ECKey) pair.getPrivate());
            Signature signature = Signature.getInstance(Signature.ALG_ECDSA_SHA_256, false);
            ecKeyPair = pair;
            ecdsa = signature;
        }
        return ecdsa;
    }

    /** New P-256 key pair, then {@code W || length || signature of "abc"}. */
    private short signEcdsa(byte[] buf) {
        Signature signature = ecdsa();
        ecKeyPair.genKeyPair();
        signature.init(ecKeyPair.getPrivate(), Signature.MODE_SIGN);
        short w = ((ECPublicKey) ecKeyPair.getPublic()).getW(buf, (short) 0);
        short length = signature.sign(ABC, (short) 0, (short) 3, buf, (short) (w + 1));
        buf[w] = (byte) length;
        return (short) (w + 1 + length);
    }

    private Signature rsa() {
        if (rsa == null) {
            KeyPair pair = new KeyPair(KeyPair.ALG_RSA, KeyBuilder.LENGTH_RSA_2048);
            Signature signature = Signature.getInstance(Signature.ALG_RSA_SHA_256_PKCS1, false);
            rsaOut = new byte[528];
            rsaKeyPair = pair;
            rsa = signature;
        }
        return rsa;
    }

    /** P1 0: new key pair and signature (answers the modulus length); 1 modulus, 2 signature, 3 exponent. */
    private short rsaPart(byte[] buf, byte part) {
        if (part == 0) {
            return rsaGenerate(buf);
        }
        if (rsaOut == null || part < 1 || part > 3) {
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        }
        if (part == 3) {
            short length = rsaOut[512];
            Util.arrayCopyNonAtomic(rsaOut, (short) 513, buf, (short) 0, length);
            return length;
        }
        Util.arrayCopyNonAtomic(rsaOut, part == 1 ? (short) 0 : (short) 256, buf, (short) 0, (short) 256);
        return 256;
    }

    private short rsaGenerate(byte[] buf) {
        Signature signature = rsa();
        rsaKeyPair.genKeyPair();
        signature.init(rsaKeyPair.getPrivate(), Signature.MODE_SIGN);
        RSAPublicKey publicKey = (RSAPublicKey) rsaKeyPair.getPublic();
        short modulus = publicKey.getModulus(rsaOut, (short) 0);
        signature.sign(ABC, (short) 0, (short) 3, rsaOut, (short) 256);
        rsaOut[512] = (byte) publicKey.getExponent(rsaOut, (short) 513);
        Util.setShort(buf, (short) 0, modulus);
        return 2;
    }
}
