package name.velikodniy.jcexpress.pace;

import java.security.spec.ECParameterSpec;

/**
 * Standardized elliptic-curve domain parameters for PACE (ICAO Doc 9303-11, 9.5.1, Table 12; BSI TR-03110-3
 * uses the same identifiers).
 *
 * <p>The numeric {@link #id()} is the value a chip lists in the {@code parameterId} of its PACEInfo
 * (EF.CardAccess) and the value the terminal sends in tag {@code 0x84} of MSE:Set AT. The curve parameters are
 * built in (see FIPS 186-4 D.1.2 and RFC 5639), so every curve works independently of the JCA provider. The
 * modular-exponentiation groups 0-2 of Table 12 belong to DH-based PACE, which is not implemented.</p>
 */
public enum PaceParameterId {

    /** NIST P-192 (secp192r1), ID 8. */
    NIST_P192(8, "secp192r1", 192, StandardCurves.NIST_P192),
    /** brainpoolP192r1, ID 9. */
    BRAINPOOL_P192R1(9, "brainpoolP192r1", 192, StandardCurves.BRAINPOOL_P192R1),
    /** NIST P-224 (secp224r1), ID 10. */
    NIST_P224(10, "secp224r1", 224, StandardCurves.NIST_P224),
    /** brainpoolP224r1, ID 11. */
    BRAINPOOL_P224R1(11, "brainpoolP224r1", 224, StandardCurves.BRAINPOOL_P224R1),
    /** NIST P-256 (secp256r1), ID 12. */
    NIST_P256(12, "secp256r1", 256, StandardCurves.NIST_P256),
    /** brainpoolP256r1, ID 13 (the curve of the ICAO App. G.1 example and of most European documents). */
    BRAINPOOL_P256R1(13, "brainpoolP256r1", 256, StandardCurves.BRAINPOOL_P256R1),
    /** brainpoolP320r1, ID 14. */
    BRAINPOOL_P320R1(14, "brainpoolP320r1", 320, StandardCurves.BRAINPOOL_P320R1),
    /** NIST P-384 (secp384r1), ID 15. */
    NIST_P384(15, "secp384r1", 384, StandardCurves.NIST_P384),
    /** brainpoolP384r1, ID 16. */
    BRAINPOOL_P384R1(16, "brainpoolP384r1", 384, StandardCurves.BRAINPOOL_P384R1),
    /** brainpoolP512r1, ID 17. */
    BRAINPOOL_P512R1(17, "brainpoolP512r1", 512, StandardCurves.BRAINPOOL_P512R1),
    /** NIST P-521 (secp521r1), ID 18. */
    NIST_P521(18, "secp521r1", 521, StandardCurves.NIST_P521);

    private final int id;
    private final String curveName;
    private final int bitSize;
    private final StandardCurves.CurveParameters parameters;
    private volatile ECParameterSpec cachedSpec;

    PaceParameterId(int id, String curveName, int bitSize, StandardCurves.CurveParameters parameters) {
        this.id = id;
        this.curveName = curveName;
        this.bitSize = bitSize;
        this.parameters = parameters;
    }

    /**
     * Returns the standardized domain parameter ID (ICAO Doc 9303-11 Table 12), as used in PACEInfo and in tag
     * {@code 0x84} of MSE:Set AT.
     *
     * @return the parameter ID
     */
    public int id() {
        return id;
    }

    /**
     * Returns the curve name (SEC 2 / RFC 5639 naming, as used by JCA providers).
     *
     * @return the curve name
     */
    public String curveName() {
        return curveName;
    }

    /**
     * Returns the curve size in bits.
     *
     * @return bit size
     */
    public int bitSize() {
        return bitSize;
    }

    /**
     * Returns the EC parameter specification of this curve.
     *
     * @return the EC parameter spec (cached)
     */
    public ECParameterSpec ecParameterSpec() {
        ECParameterSpec spec = cachedSpec;
        if (spec == null) {
            spec = parameters.toSpec();
            cachedSpec = spec;
        }
        return spec;
    }

    /**
     * Looks up the domain parameters by their standardized ID (ICAO Doc 9303-11 Table 12), e.g. the
     * {@code parameterId} of a PACEInfo.
     *
     * @param id the parameter ID
     * @return the matching enum constant
     * @throws IllegalArgumentException if the ID is not a standardized elliptic curve (0-2 are DH groups,
     *                                  3-7 and 19-31 are reserved)
     */
    public static PaceParameterId fromId(int id) {
        for (PaceParameterId p : values()) {
            if (p.id == id) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown PACE parameter ID: " + id
                + " (ICAO 9303-11 Table 12 defines elliptic curves for IDs 8-18)");
    }
}
