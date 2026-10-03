package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.crypto.CryptoUtil;
import name.velikodniy.jcexpress.scp.KeyInfo;
import name.velikodniy.jcexpress.scp.SCP03;
import name.velikodniy.jcexpress.scp.SecureChannel;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Builds the key data fields of the PUT KEY command (GPCS v2.3.1 11.8.2.3, Format 1 / basic format).
 *
 * <p>The key type comes from the key, not from the secure channel (Table 11-16: '80' DES, '88' AES); the
 * key component value is encrypted with the data encryption key of the current secure channel:
 * 3DES-ECB with the SCP02 session DEK (E.4.7) or AES-CBC with zero ICV and the static SCP03 Key-DEK
 * (Amendment D 6.2.8). A key that is not a multiple of the DEK block size is right-padded and
 * preceded by its clear length (Table 11-70); AES keys always use that format, DES keys that need no
 * padding use the plain encrypted block (Table 11-71). The 3-byte key check value is appended
 * (Table 11-68).</p>
 */
final class PutKeyData {

    private PutKeyData() {
    }

    /**
     * Encodes one key data field: key type, BER length, key component block, KCV length and KCV.
     *
     * @param key     the clear key value
     * @param type    the key type of the key being loaded
     * @param channel the secure channel whose DEK encrypts the key
     * @return the key data field
     */
    static byte[] keyDataField(byte[] key, KeyInfo.KeyType type, SecureChannel channel) {
        validate(key, type);
        boolean aesDek = channel instanceof SCP03;
        int blockSize = aesDek ? 16 : 8;
        boolean aligned = key.length % blockSize == 0;
        byte[] clear = aligned ? key.clone() : CryptoUtil.pad80(key, blockSize);
        byte[] dek = channel.dek();
        byte[] encrypted = aesDek ? CryptoUtil.aesCbcEncrypt(dek, clear) : CryptoUtil.des3EcbEncrypt(dek, clear);
        Arrays.fill(dek, (byte) 0);
        Arrays.fill(clear, (byte) 0);

        ByteArrayOutputStream block = new ByteArrayOutputStream();
        if (type == KeyInfo.KeyType.AES || !aligned) {
            block.write(key.length);      // Table 11-70: length of the clear key component value
        }
        block.writeBytes(encrypted);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(type.code());
        InstallParams.writeBerValue(out, block.toByteArray());
        byte[] kcv = KeyInfo.kcv(key, type);
        out.write(kcv.length);
        out.writeBytes(kcv);
        return out.toByteArray();
    }

    private static void validate(byte[] key, KeyInfo.KeyType type) {
        boolean valid = type == KeyInfo.KeyType.AES
                ? key.length == 16 || key.length == 24 || key.length == 32
                : key.length == 16 || key.length == 24;
        if (!valid) {
            throw new GPException(type + " key of " + key.length + " bytes is not supported (GPCS v2.3.1"
                    + " Table 11-16: DES 16/24 bytes, AES 16/24/32 bytes)");
        }
    }
}
