package name.velikodniy.jcexpress;

/**
 * Well-known Application Identifiers (AIDs) for common smart card applications.
 *
 * <p>These constants provide convenient access to standard AIDs without
 * memorizing hex values.</p>
 */
public final class WellKnownAIDs {

    private WellKnownAIDs() {
    }

    // ePassport / ICAO
    /** eMRTD (ePassport) application — ICAO 9303 */
    public static final AID MRTD = AID.fromHex("A0000002471001");

    // Payment
    /** Visa credit/debit */
    public static final AID VISA = AID.fromHex("A0000000031010");

    /** Mastercard credit/debit */
    public static final AID MASTERCARD = AID.fromHex("A0000000041010");

    /** American Express */
    public static final AID AMEX = AID.fromHex("A000000025010104");

    // GlobalPlatform
    /** GlobalPlatform Issuer Security Domain (ISD) */
    public static final AID GP_ISD = AID.fromHex("A000000151000000");
}
