package com.example.crypto;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacard.security.CryptoException;
import javacard.security.Key;
import javacard.security.KeyBuilder;
import javacard.security.KeyPair;
import javacard.security.MessageDigest;
import javacard.security.RandomData;
import javacard.security.Signature;
import javacardx.crypto.Cipher;

/**
 * Test applet exercising all major crypto/framework built-in exports.
 * Generates external references for: Cipher, MessageDigest, Signature,
 * KeyBuilder, KeyPair, RandomData, JCSystem, Util, APDU, ISO7816,
 * ISOException, CryptoException.
 */
public class CryptoApplet extends Applet {

    private static final byte INS_DIGEST     = 0x10;
    private static final byte INS_ENCRYPT    = 0x20;
    private static final byte INS_SIGN       = 0x30;
    private static final byte INS_RANDOM     = 0x40;
    private static final byte INS_KEYGEN     = 0x50;

    private MessageDigest md;
    private Cipher cipher;
    private Signature sig;
    private RandomData rng;
    private byte[] tmp;

    protected CryptoApplet() {
        tmp = JCSystem.makeTransientByteArray((short) 128, JCSystem.CLEAR_ON_DESELECT);
        md = MessageDigest.getInstance(MessageDigest.ALG_SHA, false);
        cipher = Cipher.getInstance(Cipher.ALG_DES_CBC_NOPAD, false);
        sig = Signature.getInstance(Signature.ALG_DES_MAC8_NOPAD, false);
        rng = RandomData.getInstance(RandomData.ALG_SECURE_RANDOM);
        register();
    }

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new CryptoApplet();
    }

    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }

        byte[] buf = apdu.getBuffer();
        byte ins = buf[ISO7816.OFFSET_INS];

        switch (ins) {
            case INS_DIGEST:
                processDigest(apdu, buf);
                break;
            case INS_ENCRYPT:
                processEncrypt(apdu, buf);
                break;
            case INS_SIGN:
                processSign(apdu, buf);
                break;
            case INS_RANDOM:
                processRandom(apdu, buf);
                break;
            case INS_KEYGEN:
                processKeygen(apdu, buf);
                break;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void processDigest(APDU apdu, byte[] buf) {
        short len = apdu.setIncomingAndReceive();
        short off = ISO7816.OFFSET_CDATA;
        md.reset();
        md.update(buf, off, len);
        short dLen = md.doFinal(buf, off, (short) 0, tmp, (short) 0);
        apdu.setOutgoing();
        apdu.setOutgoingLength(dLen);
        Util.arrayCopyNonAtomic(tmp, (short) 0, buf, (short) 0, dLen);
        apdu.sendBytes((short) 0, dLen);
    }

    private void processEncrypt(APDU apdu, byte[] buf) {
        try {
            Key desKey = KeyBuilder.buildKey(KeyBuilder.TYPE_DES, KeyBuilder.LENGTH_DES, false);
            cipher.init(desKey, Cipher.MODE_ENCRYPT);
            short len = apdu.setIncomingAndReceive();
            short off = ISO7816.OFFSET_CDATA;
            short outLen = cipher.doFinal(buf, off, len, tmp, (short) 0);
            byte alg = cipher.getAlgorithm();
            tmp[outLen] = alg;
            cipher.update(buf, off, len, tmp, (short) 0);
            apdu.setOutgoing();
            apdu.setOutgoingLength(outLen);
            apdu.sendBytes((short) 0, outLen);
        } catch (CryptoException e) {
            ISOException.throwIt(e.getReason());
        }
    }

    private void processSign(APDU apdu, byte[] buf) {
        try {
            Key desKey = KeyBuilder.buildKey(KeyBuilder.TYPE_DES, KeyBuilder.LENGTH_DES, false);
            sig.init(desKey, Signature.MODE_SIGN);
            short len = apdu.setIncomingAndReceive();
            short off = ISO7816.OFFSET_CDATA;
            sig.update(buf, off, len);
            short sLen = sig.sign(buf, off, len, tmp, (short) 0);
            byte alg = sig.getAlgorithm();
            short sigLen = sig.getLength();
            Util.setShort(tmp, sLen, sigLen);
            apdu.setOutgoing();
            apdu.setOutgoingLength((short) (sLen + 2));
            Util.arrayCopy(tmp, (short) 0, buf, (short) 0, (short) (sLen + 2));
            apdu.sendBytes((short) 0, (short) (sLen + 2));
        } catch (CryptoException e) {
            ISOException.throwIt(e.getReason());
        }
    }

    private void processRandom(APDU apdu, byte[] buf) {
        short len = Util.getShort(buf, ISO7816.OFFSET_P1);
        rng.generateData(tmp, (short) 0, len);
        rng.setSeed(tmp, (short) 0, len);
        Util.arrayCopy(tmp, (short) 0, buf, (short) 0, len);
        apdu.setOutgoingAndSend((short) 0, len);
    }

    private void processKeygen(APDU apdu, byte[] buf) {
        try {
            KeyPair kp = new KeyPair(KeyPair.ALG_RSA, KeyBuilder.LENGTH_RSA_512);
            kp.genKeyPair();
            javacard.security.PublicKey pub = kp.getPublic();
            javacard.security.PrivateKey priv = kp.getPrivate();
            short size = pub.getSize();
            byte type = priv.getType();
            boolean init = pub.isInitialized();
            Util.setShort(buf, (short) 0, size);
            buf[2] = type;
            buf[3] = init ? (byte) 1 : (byte) 0;
            buf[4] = priv.isInitialized() ? (byte) 1 : (byte) 0;
            apdu.setOutgoingAndSend((short) 0, (short) 5);
        } catch (CryptoException e) {
            ISOException.throwIt(e.getReason());
        }
    }
}
