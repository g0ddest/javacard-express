package name.velikodniy.jcexpress.pace;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyPairGenerator;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;

/**
 * Standard domain parameters for PACE (BSI TR-03110, Table A.3).
 *
 * <p>Provides both Brainpool curves (mandatory for German eID) and NIST curves
 * (common in non-EU passports). Each constant maps to an EC curve with
 * pre-computed {@link ECParameterSpec}.</p>
 */
public enum PaceParameterId {

    // Brainpool curves (RFC 5639)
    /** brainpoolP256r1 (parameterId 8). */
    BRAINPOOL_P256R1(8, "brainpoolP256r1", 256),
    /** brainpoolP384r1 (parameterId 10). */
    BRAINPOOL_P384R1(10, "brainpoolP384r1", 384),
    /** brainpoolP512r1 (parameterId 12). */
    BRAINPOOL_P512R1(12, "brainpoolP512r1", 512),

    // NIST curves
    /** NIST P-256 / secp256r1 (parameterId 13). */
    NIST_P256(13, "secp256r1", 256),
    /** NIST P-384 / secp384r1 (parameterId 14). */
    NIST_P384(14, "secp384r1", 384),
    /** NIST P-521 / secp521r1 (parameterId 15). */
    NIST_P521(15, "secp521r1", 521);

    private final int id;
    private final String curveName;
    private final int bitSize;
    private volatile ECParameterSpec cachedSpec;

    PaceParameterId(int id, String curveName, int bitSize) {
        this.id = id;
        this.curveName = curveName;
        this.bitSize = bitSize;
    }

    /**
     * Returns the BSI parameter ID value (for MSE:Set AT tag 0x84).
     *
     * @return the parameter ID
     */
    public int id() {
        return id;
    }

    /**
     * Returns the JCE/RFC curve name.
     *
     * @return the curve name
     */
    public String curveName() {
        return curveName;
    }

    /**
     * Returns the curve bit size.
     *
     * @return bit size
     */
    public int bitSize() {
        return bitSize;
    }

    /**
     * Returns the EC parameter specification for this curve.
     *
     * <p>NIST curves use the JCE provider. Brainpool curves use hardcoded
     * parameters from RFC 5639, falling back to JCE if available.</p>
     *
     * @return the EC parameter spec
     * @throws PaceException if the curve parameters cannot be loaded
     */
    public ECParameterSpec ecParameterSpec() {
        ECParameterSpec spec = cachedSpec;
        if (spec == null) {
            spec = loadSpec();
            cachedSpec = spec;
        }
        return spec;
    }

    /**
     * Looks up a PaceParameterId by its BSI numeric ID.
     *
     * @param id the parameter ID
     * @return the matching enum constant
     * @throws IllegalArgumentException if no constant matches
     */
    public static PaceParameterId fromId(int id) {
        for (PaceParameterId p : values()) {
            if (p.id == id) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown PACE parameter ID: " + id);
    }

    private ECParameterSpec loadSpec() {
        // Try JCE first (works for NIST and some JVMs support Brainpool)
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec(curveName));
            return params.getParameterSpec(ECParameterSpec.class);
        } catch (Exception e) {
            // Fall back to hardcoded for Brainpool
        }

        // Try via KeyPairGenerator
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec(curveName));
            // Generate a throwaway pair to extract params
            return ((java.security.interfaces.ECPublicKey) kpg.generateKeyPair().getPublic()).getParams();
        } catch (Exception e) {
            // Fall back to hardcoded
        }

        // Hardcoded Brainpool curves from RFC 5639
        return switch (this) {
            case BRAINPOOL_P256R1 -> brainpoolP256r1();
            case BRAINPOOL_P384R1 -> brainpoolP384r1();
            case BRAINPOOL_P512R1 -> brainpoolP512r1();
            default -> throw new PaceException("Cannot load EC parameters for " + curveName);
        };
    }

    // ── Brainpool hardcoded curves (RFC 5639) ──

    private static ECParameterSpec brainpoolP256r1() {
        BigInteger p = new BigInteger("A9FB57DBA1EEA9BC3E660A909D838D726E3BF623D52620282013481D1F6E5377", 16);
        BigInteger a = new BigInteger("7D5A0975FC2C3057EEF67530417AFFE7FB8055C126DC5C6CE94A4B44F330B5D9", 16);
        BigInteger b = new BigInteger("26DC5C6CE94A4B44F330B5D9BBD77CBF958416295CF7E1CE6BCCDC18FF8C07B6", 16);
        BigInteger gx = new BigInteger("8BD2AEB9CB7E57CB2C4B482FFC81B7AFB9DE27E1E3BD23C23A4453BD9ACE3262", 16);
        BigInteger gy = new BigInteger("547EF835C3DAC4FD97F8461A14611DC9C27745132DED8E545C1D54C72F046997", 16);
        BigInteger n = new BigInteger("A9FB57DBA1EEA9BC3E660A909D838D718C397AA3B561A6F7901E0E82974856A7", 16);
        return new ECParameterSpec(
                new EllipticCurve(new ECFieldFp(p), a, b),
                new ECPoint(gx, gy), n, 1);
    }

    private static ECParameterSpec brainpoolP384r1() {
        BigInteger p = new BigInteger("8CB91E82A3386D280F5D6F7E50E641DF152F7109ED5456B412B1DA197FB71123ACD3A729901D1A71874700133107EC53", 16);
        BigInteger a = new BigInteger("7BC382C63D8C150C3C72080ACE05AFA0C2BEA28E4FB22787139165EFBA91F90F8AA5814A503AD4EB04A8C7DD22CE2826", 16);
        BigInteger b = new BigInteger("04A8C7DD22CE28268B39B55416F0447C2FB77DE107DCD2A62E880EA53EEB62D57CB4390295DBC9943AB78696FA504C11", 16);
        BigInteger gx = new BigInteger("1D1C64F068CF45FFA2A63A81B7C13F6B8847A3E77EF14FE3DB7FCAFE0CBD10E8E826E03436D646AAEF87B2E247D4AF1E", 16);
        BigInteger gy = new BigInteger("8ABE1D7520F9C2A45CB1EB8E95CFD55262B70B29FEEC5864E19C054FF99129280E4646217791811142820341263C5315", 16);
        BigInteger n = new BigInteger("8CB91E82A3386D280F5D6F7E50E641DF152F7109ED5456B31F166E6CAC0425A7CF3AB6AF6B7FC3103B883202E9046565", 16);
        return new ECParameterSpec(
                new EllipticCurve(new ECFieldFp(p), a, b),
                new ECPoint(gx, gy), n, 1);
    }

    private static ECParameterSpec brainpoolP512r1() {
        BigInteger p = new BigInteger("AADD9DB8DBE9C48B3FD4E6AE33C9FC07CB308DB3B3C9D20ED6639CCA703308717D4D9B009BC66842AECDA12AE6A380E62881FF2F2D82C68528AA6056583A48F3", 16);
        BigInteger a = new BigInteger("7830A3318B603B89E2327145AC234CC594CBDD8D3DF9610399603F6114838FC8D8648990F3AD460692B11D09B7B63D58B0BBE71789E46C0B4AF6D1C5ACD8E6C9", 16);
        BigInteger b = new BigInteger("3DF91610A83441CAEA9863BC2DED5D5AA8253AA10A2EF1C98B9AC8B57F1117A72BF2C7B9E7C1AC4D77FC94CADC083E67984050B75EBAE5DD2809BD638016F723", 16);
        BigInteger gx = new BigInteger("81AEE4BDD82ED9645A21322E9C4C6A9385ED9F70B5D916C1B43B62EEF4D0098EFF3B1F78E2D0D48D50D1687B93B97D5F7C6D5047406A5E688B352209BCB9F822", 16);
        BigInteger gy = new BigInteger("7DDE385D566332ECC0EABFA9CF7822FDF209F70024A57B1AA000C55B881F8111B2DCDE494A5F485E5BCA4BD88A2763AED1CA2B2FA8F0540678CD1E0F3AD80892", 16);
        BigInteger n = new BigInteger("AADD9DB8DBE9C48B3FD4E6AE33C9FC07CB308DB3B3C9D20ED6639CCA70330870553E5C414CA92619418661197FAC10471DB1D381085DDADDB58796829CA90069", 16);
        return new ECParameterSpec(
                new EllipticCurve(new ECFieldFp(p), a, b),
                new ECPoint(gx, gy), n, 1);
    }
}
