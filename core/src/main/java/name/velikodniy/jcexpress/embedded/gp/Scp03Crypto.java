package name.velikodniy.jcexpress.embedded.gp;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.crypto.CryptoException;
import name.velikodniy.jcexpress.crypto.CryptoUtil;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * The cryptographic operations of the card side of SCP03 (GlobalPlatform Card Specification v2.3 Amendment D
 * v1.1.2), on top of the AES primitives of {@link CryptoUtil}: data derivation (4.1.5), the card challenge
 * (6.2.2.1), MACs (6.2.4, 6.2.5) and the encryption of command and response data (6.2.6, 6.2.7).
 */
final class Scp03Crypto {

    /** AES block length in bytes. */
    static final int BLOCK = 16;
    /** Length of the C-MAC and R-MAC that are transmitted (Amendment D 6.2.4). */
    static final int MAC = 8;
    /** Data derivation constants (Amendment D Table 4-1). */
    static final byte CARD_CRYPTOGRAM = 0x00;
    static final byte HOST_CRYPTOGRAM = 0x01;
    static final byte CARD_CHALLENGE = 0x02;
    static final byte S_ENC = 0x04;
    static final byte S_MAC = 0x06;
    static final byte S_RMAC = 0x07;

    private Scp03Crypto() {
    }

    /**
     * Derives data with the KDF of Amendment D 4.1.5 (NIST SP 800-108 in counter mode with AES-CMAC).
     *
     * @param key      the key to derive from
     * @param constant the derivation constant
     * @param bits     the number of bits to derive
     * @param context  the context
     * @return the derived bytes
     */
    static byte[] derive(byte[] key, byte constant, int bits, byte[] context) {
        return CryptoUtil.deriveSCP03SessionKey(key, context, constant, bits);
    }

    /**
     * Computes a pseudo-random card challenge (Amendment D 6.2.2.1): derived from Key-ENC with the context
     * {@code sequence counter || AID of the application that uses the secure channel}.
     *
     * @param keyEnc      the static Key-ENC
     * @param sequence    the 3-byte sequence counter, already incremented
     * @param application the application that invokes the secure channel, or null when none is known
     * @return the 8-byte card challenge
     */
    static byte[] cardChallenge(byte[] keyEnc, byte[] sequence, AID application) {
        byte[] aid = application == null ? new byte[0] : application.toBytes();
        return derive(keyEnc, CARD_CHALLENGE, 64, concat(sequence, aid));
    }

    /**
     * Computes the full 16-byte AES-CMAC over the concatenation of the parts.
     *
     * @param key   the MAC key
     * @param parts the message parts
     * @return the MAC
     */
    static byte[] mac(byte[] key, byte[]... parts) {
        return CryptoUtil.aesCmac(key, concat(parts));
    }

    /**
     * Decrypts a command data field (Amendment D 6.2.6): AES-CBC with S-ENC, the ICV is the encrypted counter
     * block, and the padding of 4.1.4 ('80' and zeros) is removed.
     *
     * @param sEnc    the session key S-ENC
     * @param counter the encryption counter of the command
     * @param data    the encrypted data field, a multiple of 16 bytes
     * @return the plain data, or null when the padding is not there
     */
    static byte[] decryptCommand(byte[] sEnc, int counter, byte[] data) {
        byte[] icv = CryptoUtil.aesEcbEncrypt(sEnc, counterBlock(counter, false));
        try {
            return CryptoUtil.unpad80(CryptoUtil.aesCbcDecrypt(sEnc, data, icv));
        } catch (CryptoException e) {
            return null;
        }
    }

    /**
     * Encrypts a response data field (Amendment D 6.2.7): padded as in 4.1.4, AES-CBC with S-ENC, the ICV is the
     * encrypted counter block of the command with its most significant byte set to '80'.
     *
     * @param sEnc    the session key S-ENC
     * @param counter the encryption counter of the command this response answers
     * @param data    the plain response data
     * @return the encrypted data field
     */
    static byte[] encryptResponse(byte[] sEnc, int counter, byte[] data) {
        byte[] icv = CryptoUtil.aesEcbEncrypt(sEnc, counterBlock(counter, true));
        return CryptoUtil.aesCbcEncrypt(sEnc, CryptoUtil.pad80(data, BLOCK), icv);
    }

    /** The encryption counter left padded with zeros to a block (6.2.6); for responses the first byte is '80'. */
    private static byte[] counterBlock(int counter, boolean response) {
        byte[] block = new byte[BLOCK];
        block[12] = (byte) (counter >> 24);
        block[13] = (byte) (counter >> 16);
        block[14] = (byte) (counter >> 8);
        block[15] = (byte) counter;
        if (response) {
            block[0] = (byte) 0x80;
        }
        return block;
    }

    /**
     * Returns the first 8 bytes of a MAC, the part that is transmitted.
     *
     * @param mac the full MAC
     * @return its 8 most significant bytes
     */
    static byte[] truncated(byte[] mac) {
        return Arrays.copyOf(mac, MAC);
    }

    /**
     * Concatenates byte arrays.
     *
     * @param parts the arrays
     * @return one array with all bytes
     */
    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
