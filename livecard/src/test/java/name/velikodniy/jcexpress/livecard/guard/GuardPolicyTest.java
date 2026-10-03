package name.velikodniy.jcexpress.livecard.guard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static name.velikodniy.jcexpress.livecard.guard.GuardHarness.HEX;
import static name.velikodniy.jcexpress.livecard.guard.GuardHarness.TEST_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The guard's own prefix rule: everything under the prefix may be created, changed and deleted, so the policy
 * itself (not only the settings parser) refuses prefixes that could reach foreign content. The prefix must be a
 * proprietary AID (first half byte 'F', ISO/IEC 7816-5) unless a registered RID is named explicitly, and it never
 * overlaps the ISD or the RIDs of GlobalPlatform, Visa/OpenPlatform and the Java Card API packages.
 */
class GuardPolicyTest {

    private static final byte[] ISD = HEX.parseHex("A000000151000000");
    private static final byte[] NONE = new byte[0];

    private static GuardPolicy policy(String prefix, String registeredRid) {
        return new GuardPolicy(HEX.parseHex(prefix), ISD, TEST_KEY, HEX.parseHex(registeredRid));
    }

    @ParameterizedTest(name = "prefix {0}")
    @ValueSource(strings = {"A0000001515350", "A000000062", "A000000003", "A0000000035350", "A0000001"})
    void prefixesUnderReservedRidsAreRefused(String prefix) {
        assertThatThrownBy(() -> new GuardPolicy(HEX.parseHex(prefix), ISD, TEST_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved RID");
    }

    /** Even an explicit opt-in cannot name a reserved RID. */
    @ParameterizedTest(name = "prefix {0}")
    @ValueSource(strings = {"A0000001515350", "A00000006201", "A00000000301"})
    void reservedRidsCannotBeOptedIn(String prefix) {
        assertThatThrownBy(() -> policy(prefix, prefix.substring(0, 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserved RID");
    }

    /** Leftover removal deletes everything under the prefix: a registered RID needs an explicit opt-in. */
    @ParameterizedTest(name = "prefix {0}")
    @ValueSource(strings = {"D276000124", "A000000308", "A000000396", "A0000003", "A0000005", "00112233"})
    void prefixesOutsideTheProprietaryCategoryNeedTheRegisteredRidNamed(String prefix) {
        assertThatThrownBy(() -> new GuardPolicy(HEX.parseHex(prefix), ISD, TEST_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a proprietary AID")
                .hasMessageContaining("registeredRid");
    }

    @Test
    void namedRegisteredRidAllowsPrefixesUnderIt() {
        GuardPolicy policy = policy("A00000030801", "A000000308");

        assertThat(policy.owns(HEX.parseHex("A0000003080101"))).isTrue();
        assertThat(policy.owns(HEX.parseHex("A0000003080201"))).isFalse();
        assertThat(policy.registeredRid()).isEqualTo(HEX.parseHex("A000000308"));
        assertThat(policy.toString()).contains("registeredRid=A000000308");
    }

    @ParameterizedTest(name = "prefix {0}, registeredRid {1}")
    @CsvSource({
        "F04A4358, A000000308",
        "A00000030801, A000000396",
        "A00000030801, A0000003",
        "A00000030801, A00000030801",
    })
    void registeredRidMustBeFiveBytesAndStartThePrefix(String prefix, String registeredRid) {
        assertThatThrownBy(() -> policy(prefix, registeredRid)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("registeredRid");
    }

    @Test
    void proprietaryPrefixesNeedNoOptIn() {
        assertThat(new GuardPolicy(HEX.parseHex("F04A4358"), ISD, TEST_KEY).registeredRid()).isEqualTo(NONE);
        GuardPolicy longPrefix = new GuardPolicy(HEX.parseHex("F0010203040506"), ISD, TEST_KEY);

        assertThat(longPrefix.owns(HEX.parseHex("F00102030405060701"))).isTrue();
    }

    @Test
    void prefixMustNotOverlapTheIsd() {
        assertThatThrownBy(() -> new GuardPolicy(HEX.parseHex("F04A4358"), HEX.parseHex("F04A435800000000"), TEST_KEY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlaps the Issuer Security Domain");
    }

    /** With such a policy refused, DELETE (related objects) of the GlobalPlatform SSD package can never pass. */
    @Test
    void deleteOfTheGlobalPlatformPackageNeverPasses() {
        GuardHarness harness = new GuardHarness();
        harness.selectIsd();
        harness.guard.writeAccess(true);

        assertThat(harness.allows("80E40080094F07A0000001515350")).isFalse();
    }
}
