package name.velikodniy.jcexpress.api.conformance;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Known answers for the constants of the {@code org.globalplatform} stubs, taken from the GlobalPlatform Card
 * Specification v2.3.1, a source that is independent of every implementation of the API (the class-file comparison
 * of {@link GlobalPlatformApiReferenceTest} needs a jar that the build does not have).
 *
 * <p>The values are read from the {@code ConstantValue} attributes of the compiled stubs, which is exactly what
 * javac copies into an applet (JLS 13.1). Only the constants whose coding the Card Specification gives are listed.
 * It does not give the values of {@code GPSystem.GLOBAL_SERVICE_IDENTIFIER}, of the {@code CARD_INFO} constants,
 * of the service families of its amendments ('86' and above), of the {@code CVM} formats and results, or of the
 * modes and statuses of the administration and authority interfaces; these are covered by the reference
 * comparison alone.</p>
 */
class GlobalPlatformKnownAnswerTest {

    private static final String PACKAGE = "org/globalplatform/";

    private static Map<String, ApiSurface.Type> types;

    @BeforeAll
    static void readStubs() {
        types = ApiSurface.read(ApiLocations.stubClasses(), ApiSurface.GLOBALPLATFORM_PACKAGE)
                .inPackages(List.of("org/globalplatform"));
        assertThat(types).as("org.globalplatform in the stubs").isNotEmpty();
    }

    /**
     * Life cycle state coding of GPCS v2.3.1 section 11.1.1: Table 11-4 (Application: INSTALLED '03', SELECTABLE
     * '07', LOCKED is bit b8), Table 11-5 (Security Domain: PERSONALIZED '0F') and Table 11-6 (card: OP_READY '01',
     * INITIALIZED '07', SECURED '0F', CARD_LOCKED '7F', TERMINATED 'FF').
     *
     * @param constant name of the constant in {@code GPSystem}
     * @param expected value defined by the Card Specification, in hexadecimal
     */
    @ParameterizedTest(name = "GPSystem.{0} = {1}")
    @CsvSource({
        "APPLICATION_INSTALLED, 03", "APPLICATION_SELECTABLE, 07", "APPLICATION_LOCKED, 80",
        "SECURITY_DOMAIN_PERSONALIZED, 0F",
        "CARD_OP_READY, 01", "CARD_INITIALIZED, 07", "CARD_SECURED, 0F", "CARD_LOCKED, 7F", "CARD_TERMINATED, FF"
    })
    void lifeCycleStates_gpcs231_11_1_1(String constant, String expected) {
        assertThat(hex("GPSystem", constant)).as("GPSystem." + constant).isEqualTo(expected);
    }

    /**
     * Current Security Level coding of GPCS v2.3.1 Table 10-1 (section 10.6): AUTHENTICATED is b8,
     * ANY_AUTHENTICATED b7, R_ENCRYPTION b6, R_MAC b5, C_DECRYPTION b2, C_MAC b1, NO_SECURITY_LEVEL '00'.
     *
     * @param constant name of the constant in {@code SecureChannel}
     * @param expected value defined by the Card Specification, in hexadecimal
     */
    @ParameterizedTest(name = "SecureChannel.{0} = {1}")
    @CsvSource({
        "AUTHENTICATED, 80", "ANY_AUTHENTICATED, 40", "R_ENCRYPTION, 20", "R_MAC, 10", "C_DECRYPTION, 02",
        "C_MAC, 01", "NO_SECURITY_LEVEL, 00"
    })
    void securityLevels_gpcs231_table10_1(String constant, String expected) {
        assertThat(hex("SecureChannel", constant)).as("SecureChannel." + constant).isEqualTo(expected);
    }

    /**
     * Privilege numbers of GPCS v2.3.1 Table 6-1 (section 6.6.1) and their bits in the three privilege bytes,
     * Tables 11-7 to 11-9 (section 11.1.2), which name number 10 Token Management. The number of a privilege is
     * the position of its bit, counted from b8 of byte 1.
     *
     * @param constant name of the constant in {@code GPRegistryEntry}
     * @param number   privilege number of Table 6-1
     * @param inByte   privilege byte that holds the bit, 1 to 3
     * @param bit      the bit of the privilege in that byte, b8 to b1
     */
    @ParameterizedTest(name = "GPRegistryEntry.{0} = {1}")
    @CsvSource({
        "PRIVILEGE_SECURITY_DOMAIN, 0, 1, 8", "PRIVILEGE_DAP_VERIFICATION, 1, 1, 7",
        "PRIVILEGE_DELEGATED_MANAGEMENT, 2, 1, 6", "PRIVILEGE_CARD_LOCK, 3, 1, 5",
        "PRIVILEGE_CARD_TERMINATE, 4, 1, 4", "PRIVILEGE_CARD_RESET, 5, 1, 3",
        "PRIVILEGE_CVM_MANAGEMENT, 6, 1, 2", "PRIVILEGE_MANDATED_DAP, 7, 1, 1",
        "PRIVILEGE_TRUSTED_PATH, 8, 2, 8", "PRIVILEGE_AUTHORIZED_MANAGEMENT, 9, 2, 7",
        "PRIVILEGE_TOKEN_VERIFICATION, 10, 2, 6", "PRIVILEGE_GLOBAL_DELETE, 11, 2, 5",
        "PRIVILEGE_GLOBAL_LOCK, 12, 2, 4", "PRIVILEGE_GLOBAL_REGISTRY, 13, 2, 3",
        "PRIVILEGE_FINAL_APPLICATION, 14, 2, 2", "PRIVILEGE_GLOBAL_SERVICE, 15, 2, 1",
        "PRIVILEGE_RECEIPT_GENERATION, 16, 3, 8", "PRIVILEGE_CIPHERED_LOAD_FILE_DATA_BLOCK, 17, 3, 7"
    })
    void privilegeNumbers_gpcs231_table6_1_and_11_1_2(String constant, int number, int inByte, int bit) {
        assertThat(byteConstant("GPRegistryEntry", constant)).as("GPRegistryEntry." + constant).isEqualTo(number);
        assertThat((inByte - 1) * 8 + (8 - bit)).as("bit position of privilege " + number).isEqualTo(number);
    }

    /**
     * Service family identifiers of GPCS v2.3.1 section 8.1.3: '81' GlobalPlatform Secure Channel, '82'
     * GlobalPlatform CVM, '83' Authority Service (CASD), '84' HTTP Administration, '85' HTTP Report, 'A0' USSM.
     * The API has the authority family under two names.
     *
     * @param constant name of the constant in {@code GPSystem}
     * @param expected value defined by the Card Specification, in hexadecimal
     */
    @ParameterizedTest(name = "GPSystem.{0} = {1}")
    @CsvSource({
        "FAMILY_SECURE_CHANNEL, 81", "FAMILY_CVM, 82", "FAMILY_AUHTORITY, 83", "FAMILY_AUTHORITY, 83",
        "FAMILY_HTTP_ADMINISTRATION, 84", "FAMILY_HTTP_REPORT, 85", "FAMILY_USSM, A0"
    })
    void serviceFamilies_gpcs231_8_1_3(String constant, String expected) {
        assertThat(hex("GPSystem", constant)).as("GPSystem." + constant).isEqualTo(expected);
    }

    /** GPCS v2.3.1 sections 8.2 and 8.2.2.1: the global PIN has the CVM identifier '11'. */
    @Test
    void globalPinIdentifier_gpcs231_8_2_2_1() {
        assertThat(hex("GPSystem", "CVM_GLOBAL_PIN")).isEqualTo("11");
    }

    /**
     * GPCS v2.3.1 section 8.2.2.1 assigns the CVM identifiers '01' to '10' to the PINs of ETSI TS 102 221; the
     * API names eight of them, which must be eight different identifiers of that range.
     */
    @Test
    void etsiPinIdentifiers_gpcs231_8_2_2_1() {
        Set<Integer> identifiers = new TreeSet<>();
        for (int pin = 1; pin <= 8; pin++) {
            identifiers.add(byteConstant("GPSystem", "CVM_ETSI_PIN_APP_" + pin));
        }
        assertThat(identifiers).hasSize(8).allMatch(identifier -> identifier >= 0x01 && identifier <= 0x10);
    }

    /**
     * Key access coding of GPCS v2.3.1 Table 11-19 (section 11.1.10): '00' the Security Domain and any associated
     * Application, '01' the Security Domain only, '02' the associated Applications but not the Security Domain.
     *
     * @param constant name of the constant in {@code GlobalService}
     * @param expected value defined by the Card Specification, in hexadecimal
     */
    @ParameterizedTest(name = "GlobalService.{0} = {1}")
    @CsvSource({"KEY_ACCESS_ANY, 00", "KEY_ACCESS_SECURITY_DOMAIN, 01", "KEY_ACCESS_APPLICATION, 02"})
    void keyAccess_gpcs231_table11_19(String constant, String expected) {
        assertThat(hex("GlobalService", constant)).as("GlobalService." + constant).isEqualTo(expected);
    }

    /**
     * Key type coding of GPCS v2.3.1 Table 11-16 (section 11.1.8): '80' DES with the mode implicitly known, '88'
     * AES, '90' HMAC-SHA1, '91' HMAC-SHA1-160, 'A0' and 'A1' the public exponent and the modulus of an RSA public
     * key, 'A2' to 'A8' the components of an RSA private key (modulus, private exponent, Chinese Remainder P, Q,
     * PQ, DP1, DQ1), 'FF' the extended format.
     *
     * @param constant name of the constant in {@code GlobalService}
     * @param expected value defined by the Card Specification, in hexadecimal
     */
    @ParameterizedTest(name = "GlobalService.{0} = {1}")
    @CsvSource({
        "KEY_TYPE_DES, 80", "KEY_TYPE_AES, 88", "KEY_TYPE_HMAC_SHA1, 90", "KEY_TYPE_HMAC_SHA1_160, 91",
        "KEY_TYPE_RSA_PUBLIC_EXPONENT, A0", "KEY_TYPE_RSA_PUBLIC_MODULUS, A1", "KEY_TYPE_RSA_PRIVATE_MODULUS, A2",
        "KEY_TYPE_RSA_PRIVATE_EXPONENT, A3", "KEY_TYPE_RSA_PRIVATE_CRT_P, A4", "KEY_TYPE_RSA_PRIVATE_CRT_Q, A5",
        "KEY_TYPE_RSA_PRIVATE_CRT_PQ, A6", "KEY_TYPE_RSA_PRIVATE_CRT_DP1, A7", "KEY_TYPE_RSA_PRIVATE_CRT_DQ1, A8",
        "KEY_TYPE_EXTENDED, FF"
    })
    void keyTypes_gpcs231_table11_16(String constant, String expected) {
        assertThat(hex("GlobalService", constant)).as("GlobalService." + constant).isEqualTo(expected);
    }

    /**
     * GPCS v2.3.1 Table 11-16 keeps the key types '81' to '84' "reserved for historical reasons" without saying
     * what they were; the API names four DES and Triple DES key types, which must be these four values.
     */
    @Test
    void historicalDesKeyTypes_gpcs231_table11_16() {
        Set<Integer> values = new TreeSet<>();
        for (String name : List.of("KEY_TYPE_3DES", "KEY_TYPE_3DES_CBC", "KEY_TYPE_DES_ECB", "KEY_TYPE_DES_CBC")) {
            values.add(byteConstant("GlobalService", name) & 0xFF);
        }
        assertThat(values).containsExactly(0x81, 0x82, 0x83, 0x84);
    }

    /**
     * Key usage qualifier coding of GPCS v2.3.1 Table 11-17 (section 11.1.9, first byte): b8 verification and
     * encipherment, b7 computation and decipherment, b6 secure messaging in response data fields, b5 secure
     * messaging in command data fields, b4 confidentiality, b3 cryptographic checksum, b2 digital signature, b1
     * cryptographic authorization.
     *
     * @param constant name of the constant in {@code GlobalService}
     * @param expected value defined by the Card Specification, in hexadecimal
     */
    @ParameterizedTest(name = "GlobalService.{0} = {1}")
    @CsvSource({
        "KEY_USAGE_VERIFICATION_ENCIPHERMENT, 80", "KEY_USAGE_COMPUTATION_DECIPHERMENT, 40",
        "KEY_USAGE_SM_RESPONSE, 20", "KEY_USAGE_SM_COMMAND, 10", "KEY_USAGE_CONFIDENTIALITY, 08",
        "KEY_USAGE_CRYPTOGRAPHIC_CHECKSUM, 04", "KEY_USAGE_DIGITAL_SIGNATURE, 02",
        "KEY_USAGE_CRYPTOGRAPHIC_AUTHORIZATION, 01"
    })
    void keyUsage_gpcs231_table11_17(String constant, String expected) {
        assertThat(hex("GlobalService", constant)).as("GlobalService." + constant).isEqualTo(expected);
    }

    /**
     * GPCS v2.3.1 A.1: "GPRegistryEntry objects shall be implemented as Shareable Interface Objects", so the
     * interface extends {@code javacard.framework.Shareable}.
     */
    @Test
    void registryEntriesAreShareable_gpcs231_a_1() {
        ApiSurface.Type entry = type("GPRegistryEntry");
        assertThat(entry.isInterface()).as("GPRegistryEntry is an interface").isTrue();
        assertThat(entry.interfaces()).containsExactly("javacard/framework/Shareable");
    }

    private static String hex(String type, String constant) {
        return String.format("%02X", byteConstant(type, constant) & 0xFF);
    }

    /** The value of a constant of type {@code byte}, as javac copies it into an applet. */
    private static int byteConstant(String typeName, String name) {
        ApiSurface.Field field = type(typeName).fields().get(name);
        assertThat(field).as(typeName + "." + name).isNotNull();
        assertThat(field.descriptor()).as("type of " + typeName + "." + name).isEqualTo("B");
        assertThat(field.constant()).as("ConstantValue of " + typeName + "." + name).isInstanceOf(Integer.class);
        return (byte) (int) (Integer) field.constant();
    }

    private static ApiSurface.Type type(String simpleName) {
        ApiSurface.Type type = types.get(PACKAGE + simpleName);
        assertThat(type).as("org.globalplatform." + simpleName + " in the stubs").isNotNull();
        return type;
    }
}
