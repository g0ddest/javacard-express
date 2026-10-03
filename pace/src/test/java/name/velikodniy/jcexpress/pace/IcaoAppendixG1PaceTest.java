package name.velikodniy.jcexpress.pace;

import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.crypto.CryptoUtil;
import name.velikodniy.jcexpress.sm.SMKeys;
import name.velikodniy.jcexpress.sm.SMSession;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.util.Iterator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Known-answer test: ICAO Doc 9303 Part 11 (8th ed.), Appendix G.1 "ECDH based example" (PACE with ECDH Generic
 * Mapping, AES-128 session keys, brainpoolP256r1, MRZ password of App. G).
 *
 * <p>All keys, intermediate values and APDUs below are copied from the public ICAO worked example. With the
 * terminal's ephemeral private keys of the example, {@link PaceSession} must reproduce the terminal side of the
 * transcript byte for byte.</p>
 */
class IcaoAppendixG1PaceTest {

    private static final ECParameterSpec BP256 = PaceParameterId.BRAINPOOL_P256R1.ecParameterSpec();
    private static final byte[] OID = PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128.oidBytes();

    private static final String K = "7E2D2A41C74EA0B38CD36F863939BFA8E9032AAD";
    private static final String K_PI = "89DED1B26624EC1E634C1989302849DD";
    private static final String NONCE_S = "3F00C4D39D153F2B2A214A078D899B22";
    private static final String NONCE_Z = "95A3A016522EE98D01E76CB6B98B42C3";
    private static final BigInteger SK_MAP_IFD =
            hex("7F4EF07B9EA82FD78AD689B38D0BC78CF21F249D953BC46F4C6E19259C010F99");
    private static final String PK_MAP_IFD = "047ACF3EFC982EC45565A4B155129EFBC74650DCBFA6362D896FC70262E0C2CC5E"
            + "544552DCB6725218799115B55C9BAA6D9F6BC3A9618E70C25AF71777A9C4922D";
    private static final String PK_MAP_IC = "04824FBA91C9CBE26BEF53A0EBE7342A3BF178CEA9F45DE0B70AA601651FBA3F57"
            + "30D8C879AAA9C9F73991E61B58F4D52EB87A0A0C709A49DC63719363CCD13C54";
    private static final String H = "0460332EF2450B5D247EF6D3868397D398852ED6E8CAF6FFEEF6BF85CA57057FD5"
            + "0840CA7415BAF3E43BD414D35AA4608B93A2CAF3A4E3EA4E82C9C13D03EB7181";
    private static final String G_HAT = "048CED63C91426D4F0EB1435E7CB1D74A46723A0AF21C89634F65A9AE87A9265E2"
            + "8C879506743F8611AC33645C5B985C80B5F09A0B83407C1B6A4D857AE76FE522";
    private static final BigInteger SK_DH_IFD =
            hex("A73FB703AC1436A18E0CFA5ABB3F7BEC7A070E7A6788486BEE230C4A22762595");
    private static final String PK_DH_IFD = "042DB7A64C0355044EC9DF190514C625CBA2CEA48754887122F3A5EF0D5EDD301C"
            + "3556F3B3B186DF10B857B58F6A7EB80F20BA5DC7BE1D43D9BF850149FBB36462";
    private static final String PK_DH_IC = "049E880F842905B8B3181F7AF7CAA9F0EFB743847F44A306D2D28C1D9EC65DF6DB"
            + "7764B22277A2EDDC3C265A9F018F9CB852E111B768B326904B59A0193776F094";
    private static final String SHARED_SECRET = "28768D20701247DAE81804C9E780EDE582A9996DB4A315020B2733197DB84925";
    private static final String KS_ENC = "F5F0E35C0D7161EE6724EE513A0D9A7F";
    private static final String KS_MAC = "FE251C7858B356B24514B3BD5F4297D1";
    private static final String T_IFD = "C2B0BD78D94BA866";
    private static final String T_IC = "3ABB9674BCE93C08";

    @Test
    void mrzPasswordEncodingAndPasswordKey() {
        byte[] k = PaceMrz.encodeMrzPassword("T22000129", "640812", "101031");

        assertThat(Hex.encode(k)).isEqualTo(K);
        assertThat(Hex.encode(PaceMrz.kdf(k, 3, 16))).isEqualTo(K_PI);
    }

    @Test
    void nonceIsDecryptedWithThePasswordKeyAndZeroIv() {
        assertThat(Hex.encode(CryptoUtil.aesCbcDecrypt(Hex.decode(K_PI), Hex.decode(NONCE_Z)))).isEqualTo(NONCE_S);
    }

    @Test
    void genericMappingAddsTheEcdhPointHToTheScaledGenerator() {
        // 4.4.3.3.1: G^ = s * G + H, H = KA(SK_Map,IFD, PK_Map,IC) is a point (not its x-coordinate times G)
        ECPoint pkMapIc = PaceCrypto.decodePoint(Hex.decode(PK_MAP_IC), BP256.getCurve());

        ECPoint mapped = PaceCrypto.mapNonceGeneric(hex(NONCE_S), SK_MAP_IFD, pkMapIc, BP256);

        assertThat(encode(PaceCrypto.scalarMultiply(SK_MAP_IFD, pkMapIc, BP256.getCurve()))).isEqualTo(H);
        assertThat(encode(mapped)).isEqualTo(G_HAT);
    }

    @Test
    void keyAgreementSessionKeysAndAuthenticationTokens() {
        ECPoint gHat = PaceCrypto.decodePoint(Hex.decode(G_HAT), BP256.getCurve());
        byte[] shared = PaceCrypto.ecdh(new EcKeys.PrivateKey(SK_DH_IFD, BP256),
                PaceCrypto.decodePoint(Hex.decode(PK_DH_IC), BP256.getCurve()), BP256);
        SMKeys keys = PaceMrz.deriveKeys(shared, 16);

        assertThat(encode(PaceCrypto.scalarMultiply(SK_DH_IFD, gHat, BP256.getCurve()))).isEqualTo(PK_DH_IFD);
        assertThat(Hex.encode(shared)).isEqualTo(SHARED_SECRET);
        assertThat(Hex.encode(keys.encKey())).isEqualTo(KS_ENC);
        assertThat(Hex.encode(keys.macKey())).isEqualTo(KS_MAC);
        assertThat(Hex.encode(PaceCrypto.authToken(Hex.decode(KS_MAC), OID, Hex.decode(PK_DH_IC)))).isEqualTo(T_IFD);
        assertThat(Hex.encode(PaceCrypto.authToken(Hex.decode(KS_MAC), OID, Hex.decode(PK_DH_IFD)))).isEqualTo(T_IC);
    }

    @Test
    void terminalSideOfTheTranscriptIsReproducedByteForByte() {
        ScriptedCard chip = transcript("0022C1A40F800A04007F00070202040202830101");

        PaceResult result = icaoSession(false).perform(chip);

        assertThat(chip.isFinished()).isTrue();
        assertThat(Hex.encode(result.encKey())).isEqualTo(KS_ENC);
        assertThat(Hex.encode(result.macKey())).isEqualTo(KS_MAC);
        assertThat(Hex.encode(result.termToken())).isEqualTo(T_IFD);
        assertThat(Hex.encode(result.cardToken())).isEqualTo(T_IC);
    }

    @Test
    void domainParameterIdIsSentAsTag84WhenRequested() {
        // 4.4.4.1: 0x84 carries the Table 12 ID (0x0D for brainpoolP256r1) if the chip offers several parameter sets
        ScriptedCard chip = transcript("0022C1A412800A04007F0007020204020283010184010D");

        icaoSession(true).perform(chip);

        assertThat(chip.isFinished()).isTrue();
    }

    @Test
    void sessionKeysProtectTheNextCommandWithAesSecureMessaging() {
        // SELECT of the eMRTD application under the App. G.1 session keys, SSC = 0 (9.8.7.3);
        // vector from sm/src/test/resources/icao-sm-vectors/aes-session.properties (icao_ref.py)
        ScriptedCard chip = transcript("0022C1A40F800A04007F00070202040202830101")
                .expect("0CA4040C1D871101752F676B09FAC86A87D632749A49C7CC8E08C18BA1FCE707BD9F00",
                        "990290008E08BEA7B381C494A0799000");
        SMSession secure = icaoSession(false).perform(chip).toSMSession(chip);

        assertThat(secure.send(0x00, 0xA4, 0x04, 0x0C, Hex.decode("A0000002471001")).sw()).isEqualTo(0x9000);
        assertThat(chip.isFinished()).isTrue();
    }

    private static PaceSession icaoSession(boolean includeParameterId) {
        return PaceSession.builder()
                .algorithm(PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128)
                .parameterId(PaceParameterId.BRAINPOOL_P256R1)
                .includeParameterId(includeParameterId)
                .mrzPassword("T22000129", "640812", "101031")
                .ephemeralKeySource(terminalKeys())
                .build();
    }

    /** The terminal's ephemeral private keys of App. G.1: SK_Map,IFD, then SK_DH,IFD. */
    static PaceSession.EphemeralKeySource terminalKeys() {
        Iterator<BigInteger> keys = List.of(SK_MAP_IFD, SK_DH_IFD).iterator();
        return params -> keys.next();
    }

    /** The App. G.1 exchange; MSE:Set AT is given because tag 0x84 is optional. */
    static ScriptedCard transcript(String mseSetAt) {
        return new ScriptedCard()
                .expect(mseSetAt, "9000")
                .expect("10860000027C0000", "7C1280" + "10" + NONCE_Z + "9000")
                .expect("1086000045" + "7C438141" + PK_MAP_IFD + "00", "7C438241" + PK_MAP_IC + "9000")
                .expect("1086000045" + "7C438341" + PK_DH_IFD + "00", "7C438441" + PK_DH_IC + "9000")
                .expect("008600000C7C0A8508" + T_IFD + "00", "7C0A8608" + T_IC + "9000");
    }

    private static String encode(ECPoint point) {
        return Hex.encode(PaceCrypto.encodePoint(point, 32));
    }

    private static BigInteger hex(String value) {
        return new BigInteger(value, 16);
    }
}
