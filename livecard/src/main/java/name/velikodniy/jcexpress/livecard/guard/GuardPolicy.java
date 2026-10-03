package name.velikodniy.jcexpress.livecard.guard;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * What the {@link ApduGuard} needs to know about the run: the AID prefix that marks everything the tests own,
 * the Issuer Security Domain, and the static SCP03 Key-MAC that lets the guard verify the secure channel
 * handshake on its own.
 *
 * <p>Everything under the prefix may be created, changed and deleted (leftovers of earlier runs included), so the
 * policy itself refuses prefixes that could reach foreign content ({@link #requireSafePrefix}): the prefix never
 * overlaps the ISD or the {@linkplain #RESERVED_RIDS RIDs} of GlobalPlatform, Visa/OpenPlatform and the Java Card
 * API packages, and it must be a proprietary, unregistered AID (first half byte {@code 'F'}, ISO/IEC 7816-5 and
 * ISO/IEC 7816-4:2005 8.2.1.2) unless the registered RID it starts with is named explicitly.</p>
 *
 * @param aidPrefix     every AID the tests may create, change or delete starts with these bytes (4-13 bytes)
 * @param isdAid        the AID of the Issuer Security Domain (the only Security Domain accepted in INSTALL
 *                      [for load])
 * @param staticMacKey  the static SCP03 Key-MAC (16, 24 or 32 bytes), GlobalPlatform Amendment D 6.2.1
 * @param registeredRid the registered RID (5 bytes) that a prefix outside the proprietary category starts with,
 *                      named explicitly by the user; empty for a proprietary prefix
 */
public record GuardPolicy(byte[] aidPrefix, byte[] isdAid, byte[] staticMacKey, byte[] registeredRid) {

    /**
     * RIDs that no prefix may overlap, whatever the user names: GlobalPlatform ({@code A000000151}, the ISD and
     * its Security Domain packages), Visa/OpenPlatform ({@code A000000003}) and the Java Card API packages
     * ({@code A000000062}).
     */
    public static final List<String> RESERVED_RIDS = List.of("A000000151", "A000000003", "A000000062");

    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    private static final int RID_LENGTH = 5;

    /**
     * Validates and copies the policy values.
     *
     * @throws IllegalArgumentException if the prefix is unsafe ({@link #requireSafePrefix}), the ISD AID is not
     *                                  5-16 bytes, or the key is not an AES key
     */
    public GuardPolicy {
        aidPrefix = aidPrefix.clone();
        isdAid = isdAid.clone();
        staticMacKey = staticMacKey.clone();
        registeredRid = registeredRid.clone();
        if (isdAid.length < 5 || isdAid.length > 16) {
            throw new IllegalArgumentException("ISD AID must be 5-16 bytes, got " + isdAid.length);
        }
        requireSafePrefix(aidPrefix, isdAid, registeredRid);
        if (staticMacKey.length != 16 && staticMacKey.length != 24 && staticMacKey.length != 32) {
            throw new IllegalArgumentException("SCP03 keys are AES-128/192/256, got " + staticMacKey.length
                    + " bytes");
        }
    }

    /**
     * Creates a policy for a proprietary prefix (first half byte {@code 'F'}), without a registered RID.
     *
     * @param aidPrefix    every AID the tests may create, change or delete starts with these bytes
     * @param isdAid       the AID of the Issuer Security Domain
     * @param staticMacKey the static SCP03 Key-MAC
     * @throws IllegalArgumentException if a value is invalid, see the canonical constructor
     */
    public GuardPolicy(byte[] aidPrefix, byte[] isdAid, byte[] staticMacKey) {
        this(aidPrefix, isdAid, staticMacKey, new byte[0]);
    }

    /**
     * Checks that a prefix can only reach the tests' own content: 4-13 bytes, no overlap with the ISD or a
     * {@linkplain #RESERVED_RIDS reserved RID} (even when named), and either a proprietary AID (first half byte
     * {@code 'F'}) or under the explicitly named registered RID, which must be 5 bytes (ISO/IEC 7816-5).
     *
     * @param aidPrefix     the prefix
     * @param isdAid        the Issuer Security Domain AID
     * @param registeredRid the registered RID the prefix starts with, or empty
     * @throws IllegalArgumentException with the reason if the prefix is not safe
     */
    public static void requireSafePrefix(byte[] aidPrefix, byte[] isdAid, byte[] registeredRid) {
        if (aidPrefix.length < 4 || aidPrefix.length > 13) {
            throw new IllegalArgumentException("AID prefix must be 4-13 bytes, got " + aidPrefix.length);
        }
        for (String reserved : RESERVED_RIDS) {
            if (overlaps(aidPrefix, HEX.parseHex(reserved))) {
                throw new IllegalArgumentException("AID prefix " + HEX.formatHex(aidPrefix) + " overlaps the reserved"
                        + " RID " + reserved + " (GlobalPlatform, Visa/OpenPlatform and Java Card API content)");
            }
        }
        if (overlaps(aidPrefix, isdAid)) {
            throw new IllegalArgumentException("AID prefix " + HEX.formatHex(aidPrefix)
                    + " overlaps the Issuer Security Domain " + HEX.formatHex(isdAid));
        }
        requireCategory(aidPrefix, registeredRid);
    }

    /** A proprietary prefix, or one under the registered RID the user named (ISO/IEC 7816-5). */
    private static void requireCategory(byte[] aidPrefix, byte[] registeredRid) {
        if (registeredRid.length == 0) {
            if ((aidPrefix[0] & 0xF0) != 0xF0) {
                throw new IllegalArgumentException("AID prefix " + HEX.formatHex(aidPrefix) + " is not a proprietary"
                        + " AID (first half byte 'F', ISO/IEC 7816-5); everything under the prefix is deleted as a"
                        + " leftover of earlier runs, so a prefix under a registered RID needs that RID named"
                        + " explicitly in the setting registeredRid (system property jcx.livecard.registeredRid)");
            }
            return;
        }
        if (registeredRid.length != RID_LENGTH) {
            throw new IllegalArgumentException("registeredRid must be a 5-byte RID (ISO/IEC 7816-5), got "
                    + registeredRid.length + " bytes");
        }
        if (!startsWith(aidPrefix, registeredRid)) {
            throw new IllegalArgumentException("AID prefix " + HEX.formatHex(aidPrefix) + " does not start with"
                    + " registeredRid " + HEX.formatHex(registeredRid));
        }
    }

    /**
     * Returns whether an AID belongs to the tests, i.e. starts with the prefix.
     *
     * @param aid the AID (may be shorter than the prefix)
     * @return true if {@code aid} starts with {@link #aidPrefix()}
     */
    public boolean owns(byte[] aid) {
        return aid != null && startsWith(aid, aidPrefix);
    }

    /**
     * Returns whether an AID is the Issuer Security Domain's.
     *
     * @param aid the AID
     * @return true if {@code aid} equals {@link #isdAid()}
     */
    public boolean isIssuerSecurityDomain(byte[] aid) {
        return Arrays.equals(aid, isdAid);
    }

    /**
     * Returns the AID prefix.
     *
     * @return a copy of the prefix that every AID the tests may create, change or delete starts with
     */
    @Override
    public byte[] aidPrefix() {
        return aidPrefix.clone();
    }

    /**
     * Returns the Issuer Security Domain AID.
     *
     * @return a copy of the ISD AID
     */
    @Override
    public byte[] isdAid() {
        return isdAid.clone();
    }

    /**
     * Returns the static SCP03 Key-MAC.
     *
     * @return a copy of the key
     */
    @Override
    public byte[] staticMacKey() {
        return staticMacKey.clone();
    }

    /**
     * Returns the registered RID named for a prefix outside the proprietary category.
     *
     * @return a copy of the RID, empty for a proprietary prefix
     */
    @Override
    public byte[] registeredRid() {
        return registeredRid.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GuardPolicy(byte[] prefix, byte[] isd, byte[] mac, byte[] rid)
                && Arrays.equals(prefix, aidPrefix) && Arrays.equals(isd, isdAid) && Arrays.equals(mac, staticMacKey)
                && Arrays.equals(rid, registeredRid);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(aidPrefix) + Arrays.hashCode(isdAid);
    }

    /** Never prints the key. */
    @Override
    public String toString() {
        return "GuardPolicy[aidPrefix=" + HEX.formatHex(aidPrefix) + ", isd=" + HEX.formatHex(isdAid)
                + (registeredRid.length == 0 ? "" : ", registeredRid=" + HEX.formatHex(registeredRid))
                + ", staticMacKey=(" + staticMacKey.length + " bytes)]";
    }

    static boolean startsWith(byte[] value, byte[] prefix) {
        return value.length >= prefix.length && Arrays.equals(value, 0, prefix.length, prefix, 0, prefix.length);
    }

    private static boolean overlaps(byte[] a, byte[] b) {
        return startsWith(a, b) || startsWith(b, a);
    }
}
