package corpus.crypto;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.security.AESKey;
import javacard.security.CryptoException;
import javacard.security.DHPrivateKey;
import javacard.security.DHPublicKey;
import javacard.security.ECPrivateKey;
import javacard.security.ECPublicKey;
import javacard.security.InitializedMessageDigest;
import javacard.security.KeyAgreement;
import javacard.security.KeyBuilder;
import javacard.security.KeyPair;
import javacard.security.MessageDigest;
import javacard.security.RSAPublicKey;
import javacard.security.RandomData;
import javacard.security.Signature;
import javacardx.crypto.AEADCipher;
import javacardx.crypto.Cipher;

/**
 * Corpus applet: uses the cryptographic API added up to Java Card 3.0.5 (algorithm building blocks,
 * pre-computed hashes, AEAD ciphers, one-shot objects, shared-domain keys, plain ECDH).
 */
public class CryptoShowcase extends Applet {

    private final KeyPair ecPair;
    private final AESKey sessionKey;
    private final RSAPublicKey bigRsa;
    private final Signature ecdsa;
    private final Cipher aesCbc;
    private final KeyAgreement ecdh;
    private final byte[] scratch;

    private CryptoShowcase() {
        ecPair = new KeyPair(KeyPair.ALG_EC_FP, KeyBuilder.LENGTH_EC_FP_256);
        sessionKey = (AESKey) KeyBuilder.buildKey(KeyBuilder.ALG_TYPE_AES, JCSystem.MEMORY_TYPE_TRANSIENT_DESELECT,
                KeyBuilder.LENGTH_AES_256, false);
        bigRsa = (RSAPublicKey) KeyBuilder.buildKey(KeyBuilder.TYPE_RSA_PUBLIC, KeyBuilder.LENGTH_RSA_4096, false);
        ecdsa = Signature.getInstance(MessageDigest.ALG_SHA_256, Signature.SIG_CIPHER_ECDSA, Cipher.PAD_NULL, false);
        aesCbc = Cipher.getInstance(Cipher.CIPHER_AES_CBC, Cipher.PAD_PKCS5, false);
        ecdh = KeyAgreement.getInstance(KeyAgreement.ALG_EC_SVDP_DH_PLAIN, false);
        scratch = JCSystem.makeTransientByteArray((short) 256, JCSystem.CLEAR_ON_DESELECT);
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new CryptoShowcase();
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        short len = apdu.setIncomingAndReceive();
        try {
            short out = signHash(buf, len);
            out += sealData(buf, out);
            out += digestAndDerive(buf, out);
            apdu.setOutgoingAndSend((short) 0, out);
        } catch (CryptoException e) {
            ISOException.throwIt((short) (ISO7816.SW_UNKNOWN | e.getReason()));
        }
    }

    private short signHash(byte[] buf, short len) {
        ECPublicKey pub = (ECPublicKey) ecPair.getPublic();
        ECPrivateKey priv = (ECPrivateKey) ecPair.getPrivate();
        pub.copyDomainParametersFrom(priv);
        ecPair.genKeyPair();
        ecdsa.init(priv, Signature.MODE_SIGN);
        short sigLen = ecdsa.signPreComputedHash(buf, ISO7816.OFFSET_CDATA, MessageDigest.LENGTH_SHA_256,
                scratch, (short) 0);
        Signature.OneShot verifier = Signature.OneShot.open(MessageDigest.ALG_SHA_256, Signature.SIG_CIPHER_ECDSA,
                Cipher.PAD_NULL);
        try {
            verifier.init(pub, Signature.MODE_VERIFY);
            if (!verifier.verifyPreComputedHash(buf, ISO7816.OFFSET_CDATA, MessageDigest.LENGTH_SHA_256,
                    scratch, (short) 0, sigLen)) {
                ISOException.throwIt(ISO7816.SW_DATA_INVALID);
            }
        } finally {
            verifier.close();
        }
        return sigLen;
    }

    private short sealData(byte[] buf, short off) {
        RandomData.OneShot rng = RandomData.OneShot.open(RandomData.ALG_TRNG);
        try {
            rng.nextBytes(scratch, (short) 0, (short) 32);
        } finally {
            rng.close();
        }
        sessionKey.setKey(scratch, (short) 0);
        AEADCipher gcm = (AEADCipher) Cipher.getInstance(AEADCipher.ALG_AES_GCM, false);
        gcm.init(sessionKey, Cipher.MODE_ENCRYPT, scratch, (short) 0, (short) 12);
        gcm.updateAAD(buf, (short) 0, (short) 4);
        short n = gcm.doFinal(buf, ISO7816.OFFSET_CDATA, (short) 16, buf, off);
        n += gcm.retrieveTag(buf, (short) (off + n), (short) 16);
        aesCbc.init(sessionKey, Cipher.MODE_ENCRYPT, scratch, (short) 0, (short) 16);
        if (aesCbc.getPaddingAlgorithm() != Cipher.PAD_PKCS5 || bigRsa.getSize() != KeyBuilder.LENGTH_RSA_4096) {
            CryptoException.throwIt(CryptoException.ILLEGAL_USE);
        }
        Cipher.OneShot ecb = Cipher.OneShot.open(Cipher.CIPHER_AES_ECB, Cipher.PAD_NOPAD);
        try {
            ecb.init(sessionKey, Cipher.MODE_ENCRYPT);
            n += ecb.doFinal(scratch, (short) 0, (short) 16, buf, (short) (off + n));
        } finally {
            ecb.close();
        }
        return n;
    }

    private short digestAndDerive(byte[] buf, short off) {
        MessageDigest.OneShot sha3 = MessageDigest.OneShot.open(MessageDigest.ALG_SHA3_256);
        short n;
        try {
            n = sha3.doFinal(buf, (short) 0, off, buf, off);
        } finally {
            sha3.close();
        }
        InitializedMessageDigest resumed = MessageDigest.getInitializedMessageDigestInstance(MessageDigest.ALG_SHA,
                false);
        resumed.setInitialDigest(buf, off, MessageDigest.LENGTH_SHA, scratch, (short) 0, (short) 8);
        ecdh.init((ECPrivateKey) ecPair.getPrivate());
        n += ecdh.generateSecret(buf, (short) 0, (short) 65, buf, (short) (off + n));
        DHPublicKey dhPub = (DHPublicKey) KeyBuilder.buildKey(KeyBuilder.TYPE_DH_PUBLIC, KeyBuilder.LENGTH_DH_2048,
                false);
        DHPrivateKey dhPriv = (DHPrivateKey) KeyBuilder.buildKeyWithSharedDomain(KeyBuilder.ALG_TYPE_DH_PRIVATE,
                JCSystem.MEMORY_TYPE_PERSISTENT, dhPub, false);
        return (short) (n + dhPriv.getX(buf, (short) (off + n)));
    }
}
