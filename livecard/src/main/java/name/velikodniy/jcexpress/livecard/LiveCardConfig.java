package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.livecard.ConfigSources.Value;
import name.velikodniy.jcexpress.livecard.guard.GuardPolicy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Settings of a live-card run (see {@link ConfigSources} for where they come from and in which order).
 *
 * @param enabled         whether live-card tests may run at all (default {@code false}; from the settings sources
 *                        only the system property {@code jcx.livecard.enabled} can make it {@code true})
 * @param reader          substring of the PC/SC reader name, or null for the first reader with a card
 * @param keys            the ISD's static SCP03 keys (default: the GlobalPlatform test keys)
 * @param keyVersion      the Key Version Number for INITIALIZE UPDATE, 0 = the card's choice (default)
 * @param securityLevel   the security level of {@link LiveCard#gp()} sessions (default '01', C-MAC)
 * @param securityLevels  the levels the secure channel test authenticates at, in this order (default '01', '03';
 *                        '00' only when listed)
 * @param aidPrefix       hex prefix of every AID the tests create (default {@code F04A4358}); everything under it
 *                        may be deleted, see {@link GuardPolicy#requireSafePrefix}
 * @param registeredRid   the registered RID (5 bytes, hex) that a prefix outside the proprietary category
 *                        ({@code 'F'}, ISO/IEC 7816-5) starts with, named explicitly; null for a proprietary prefix
 *                        (default)
 * @param javaCardVersion the conversion target (default 3.0.4)
 * @param isd             the Issuer Security Domain AID (default {@code A000000151000000})
 * @param verifierSdk     an Oracle Java Card development kit whose off-card verifier every CAP must pass before
 *                        it is loaded; when the setting is not given, {@link #load(ConfigSources)} takes the kit of
 *                        the conversion target from {@code build/oracle-sdks} under the project root (jc303_kit,
 *                        jc304_kit or a jc305u*_kit), if there is one; null otherwise (then deployments are refused
 *                        unless {@code allowUnverifiedCaps})
 * @param allowUnverifiedCaps true for the setting {@code verifierSdk=none}: CAP files are loaded without off-card
 *                        verification, at the user's own risk (JCVM 3.1 §1.3 requires verification before loading)
 * @param transcriptDir   where APDU transcripts are written (default {@code target/livecard-transcripts})
 * @param maxAuthFailures failed authentications that abort the whole run (default 1, at most 3)
 */
public record LiveCardConfig(boolean enabled, String reader, CardKeys keys, int keyVersion, int securityLevel,
                             List<Integer> securityLevels, String aidPrefix, String registeredRid,
                             JavaCardVersion javaCardVersion, String isd, Path verifierSdk,
                             boolean allowUnverifiedCaps, Path transcriptDir, int maxAuthFailures) {

    /**
     * Validates the settings with the same rules as the settings sources, so that settings built in code cannot
     * bypass them, and normalizes the AIDs to uppercase hex.
     *
     * @throws IllegalArgumentException     if a value is invalid: an unsafe AID prefix
     *                                      ({@link GuardPolicy#requireSafePrefix}), an ISD AID that is not 5-16
     *                                      bytes, a key version outside 00-FF, an unknown security level, no or a
     *                                      repeated level in {@code securityLevels}, {@code maxAuthFailures}
     *                                      outside 1-3
     * @throws NullPointerException         if keys, the AIDs, the version or the transcript directory are null
     */
    public LiveCardConfig {
        Objects.requireNonNull(keys, "keys");
        Objects.requireNonNull(javaCardVersion, "javaCardVersion");
        Objects.requireNonNull(transcriptDir, "transcriptDir");
        isd = aid(Objects.requireNonNull(isd, "isd"), 5, 16);
        aidPrefix = aid(Objects.requireNonNull(aidPrefix, "aidPrefix"), 4, 13);
        registeredRid = registeredRid == null || registeredRid.isBlank() ? null : aid(registeredRid, 5, 5);
        GuardPolicy.requireSafePrefix(HEX.parseHex(aidPrefix), HEX.parseHex(isd), registeredRidBytes(registeredRid));
        securityLevels = checkedLevels(securityLevels);
        securityLevel(String.format("%02X", securityLevel));
        if (keyVersion < 0 || keyVersion > 0xFF) {
            throw new IllegalArgumentException("key version must be 00-FF, got " + keyVersion);
        }
        if (maxAuthFailures < 1 || maxAuthFailures > 3) {
            throw new IllegalArgumentException("maxAuthFailures must be 1-3, got " + maxAuthFailures);
        }
        if (verifierSdk != null && allowUnverifiedCaps) {
            throw new IllegalArgumentException("verifierSdk names a kit and is none (unverified CAP files) at once");
        }
    }

    /**
     * System property that allows live-card mode on a machine that looks like CI/CD (see {@link #load(ConfigSources)}).
     * It is read only from the system properties, never from settings files or environment variables.
     */
    public static final String ALLOW_CI_PROPERTY = ContinuousIntegration.ALLOW_PROPERTY;

    private static final HexFormat HEX = HexFormat.of().withUpperCase();
    /** The value of {@code verifierSdk} that loads CAP files without off-card verification. */
    private static final String NO_VERIFIER = "none";

    /**
     * The settings, with their names in files ({@link #key()}), as system properties and as environment
     * variables, and their defaults.
     */
    public enum Setting {
        /**
         * Live-card tests run only when true; switched on only by the system property, for one run (an environment
         * variable or a settings file that says anything but false is refused).
         */
        ENABLED("enabled", "false"),
        /** Substring of the reader name; empty = first reader with a card. */
        READER("reader", ""),
        /** {@code test}, one hex key, or {@code enc,mac,dek}. */
        KEYS("keys", "test"),
        /** Key Version Number (hex), 00 = card's choice. */
        KVN("kvn", "00"),
        /** Security level of {@link LiveCard#gp()} sessions (hex): 00, 01, 03, 11, 13 or 33. */
        SECURITY_LEVEL("securityLevel", "01"),
        /**
         * Levels the secure channel test authenticates at, comma separated hex; '00' only when listed, because a
         * card may reject it and count that EXTERNAL AUTHENTICATE as a failed attempt.
         */
        SECURITY_LEVELS("securityLevels", "01,03"),
        /** Hex prefix of every AID the tests create, change or delete. */
        AID_PREFIX("aidPrefix", "F04A4358"),
        /**
         * The registered RID (5 bytes, hex) a prefix outside the proprietary category starts with; empty = the
         * prefix must be proprietary (first half byte 'F', ISO/IEC 7816-5).
         */
        REGISTERED_RID("registeredRid", ""),
        /** Java Card version the applets are converted for, e.g. 3.0.4. */
        JAVA_CARD_VERSION("javaCardVersion", "3.0.4"),
        /** Issuer Security Domain AID (hex). */
        ISD("isd", "A000000151000000"),
        /**
         * Path of an Oracle Java Card development kit for off-card verification; {@code none} = load CAP files
         * unverified (explicit opt-out); empty = not set, deployments are refused.
         */
        VERIFIER_SDK("verifierSdk", ""),
        /** Directory for APDU transcripts. */
        TRANSCRIPT_DIR("transcriptDir", "target/livecard-transcripts"),
        /** Failed authentications that abort the run (1-3). */
        MAX_AUTH_FAILURES("maxAuthFailures", "1");

        private final String key;
        private final String defaultValue;

        Setting(String key, String defaultValue) {
            this.key = key;
            this.defaultValue = defaultValue;
        }

        /**
         * Returns the name used in settings files.
         *
         * @return e.g. {@code aidPrefix}
         */
        public String key() {
            return key;
        }

        /**
         * Returns the default value.
         *
         * @return the default as text
         */
        public String defaultValue() {
            return defaultValue;
        }

        /**
         * Returns the system property name.
         *
         * @return e.g. {@code jcx.livecard.aidPrefix}
         */
        public String systemProperty() {
            return "jcx.livecard." + key;
        }

        /**
         * Returns the environment variable name.
         *
         * @return e.g. {@code JCX_LIVECARD_AID_PREFIX}
         */
        public String environmentVariable() {
            return "JCX_LIVECARD_" + name();
        }

        /**
         * Finds a setting by its file name.
         *
         * @param key the name, e.g. {@code reader}
         * @return the setting, or empty if there is none with this name
         */
        public static Optional<Setting> byKey(String key) {
            return Arrays.stream(values()).filter(setting -> setting.key.equals(key)).findFirst();
        }
    }

    /**
     * Loads the settings from the standard sources of this JVM.
     *
     * @return the settings
     * @throws LiveCardException if a value is invalid or a settings file is unreadable or has unknown names
     */
    public static LiveCardConfig load() {
        return load(ConfigSources.standard());
    }

    /**
     * Loads the settings from the given sources.
     *
     * <p>CI/CD must never run tests against a live card: when the settings enable live-card mode and the
     * environment looks like CI ({@code CI=true}, {@code GITHUB_ACTIONS=true}, ...), loading fails unless the
     * system property {@value #ALLOW_CI_PROPERTY}{@code =true} overrides it.</p>
     *
     * @param sources the sources
     * @return the settings
     * @throws LiveCardException if a value is invalid, a settings file is unreadable or has unknown names, or
     *                           live-card mode is enabled in a CI environment without the override
     */
    public static LiveCardConfig load(ConfigSources sources) {
        return load(sources, Map.of());
    }

    /**
     * Loads the settings like {@link #load(ConfigSources)}, with defaults of a test run in place of the built-in
     * defaults: a setting that a source gives (system property, environment variable, settings file) keeps its
     * value. The {@code livecard} backend of {@code @JavaCardTest} passes the run's AID prefix and the Java Card
     * version of the build ({@code BuildDescriptor}).
     *
     * @param sources     the sources
     * @param runDefaults the defaults of the run
     * @return the settings
     * @throws LiveCardException as {@link #load(ConfigSources)}
     */
    public static LiveCardConfig load(ConfigSources sources, Map<Setting, String> runDefaults) {
        Map<Setting, Value> values = new EnumMap<>(sources.resolve());
        runDefaults.forEach((setting, text) -> {
            if (values.get(setting).origin().equals("default")) {
                values.put(setting, new Value(text, "the test run (default)"));
            }
        });
        LiveCardConfig config = parse(values);
        if (config.enabled()) {
            ContinuousIntegration.requireAllowed(sources);
        }
        if (config.verifierSdk() == null && !config.allowUnverifiedCaps()) {
            Optional<Path> kit = sources.projectRoot().flatMap(root -> VerifierKits.find(root, config.javaCardVersion()));
            if (kit.isPresent()) {
                return config.withVerifierSdk(kit.get());
            }
        }
        return config;
    }

    /**
     * Returns these settings with another off-card verifier kit.
     *
     * @param sdk the Oracle Java Card development kit
     * @return the settings
     */
    LiveCardConfig withVerifierSdk(Path sdk) {
        return new LiveCardConfig(enabled, reader, keys, keyVersion, securityLevel, securityLevels, aidPrefix,
                registeredRid, javaCardVersion, isd, sdk, false, transcriptDir, maxAuthFailures);
    }

    /**
     * Creates settings from explicit values; settings that are not given keep their defaults.
     *
     * @param settings values by file name (e.g. {@code "aidPrefix" -> "F04A4358"})
     * @return the settings
     * @throws LiveCardException if a name is unknown or a value invalid
     */
    public static LiveCardConfig of(Map<String, String> settings) {
        Map<Setting, Value> values = new EnumMap<>(Setting.class);
        for (Setting setting : Setting.values()) {
            values.put(setting, new Value(setting.defaultValue(), "default"));
        }
        settings.forEach((key, value) -> values.put(Setting.byKey(key).orElseThrow(
                () -> new LiveCardException("Unknown live-card setting '" + key + "'")), new Value(value, "argument")));
        return parse(values);
    }

    private static LiveCardConfig parse(Map<Setting, Value> v) {
        boolean enabled = convert(v.get(Setting.ENABLED), LiveCardConfig::bool);
        String reader = v.get(Setting.READER).text().isBlank() ? null : v.get(Setting.READER).text().strip();
        CardKeys keys = keys(v.get(Setting.KEYS));
        int kvn = convert(v.get(Setting.KVN), text -> hexByte(text, "a hex byte 00-FF"));
        int level = convert(v.get(Setting.SECURITY_LEVEL), LiveCardConfig::securityLevel);
        List<Integer> levels = convert(v.get(Setting.SECURITY_LEVELS), LiveCardConfig::securityLevels);
        String isd = convert(v.get(Setting.ISD), text -> aid(text, 5, 16));
        String rid = convert(v.get(Setting.REGISTERED_RID), text -> text.isBlank() ? null : aid(text, 5, 5));
        String prefix = convert(v.get(Setting.AID_PREFIX), text -> prefix(text, isd, rid));
        JavaCardVersion version = convert(v.get(Setting.JAVA_CARD_VERSION), LiveCardConfig::version);
        boolean unverified = v.get(Setting.VERIFIER_SDK).text().strip().equalsIgnoreCase(NO_VERIFIER);
        Path sdk = unverified ? null : convert(v.get(Setting.VERIFIER_SDK), LiveCardConfig::sdk);
        Path transcripts = convert(v.get(Setting.TRANSCRIPT_DIR), text -> Path.of(text.strip()));
        int maxAuth = convert(v.get(Setting.MAX_AUTH_FAILURES), LiveCardConfig::maxAuthFailures);
        return new LiveCardConfig(enabled, reader, keys, kvn, level, levels, prefix, rid, version, isd, sdk,
                unverified, transcripts, maxAuth);
    }

    private static <T> T convert(Value value, Function<String, T> parser) {
        try {
            return parser.apply(value.text());
        } catch (IllegalArgumentException e) {
            String shown = value.origin().startsWith("default") ? value.text() : "'" + value.text() + "'";
            throw new LiveCardException("Invalid live-card setting " + shown + " from " + value.origin() + ": "
                    + e.getMessage(), e);
        }
    }

    /**
     * Parses the keys setting; an invalid value is described by its shape only, never echoed, and without the
     * cause, so that no part of a key reaches a message or a stack trace.
     */
    private static CardKeys keys(Value value) {
        try {
            return CardKeys.parse(value.text());
        } catch (IllegalArgumentException e) {
            throw new LiveCardException("Invalid live-card setting keys (value not shown: " + shape(value.text())
                    + ") from " + value.origin() + ": " + e.getMessage());
        }
    }

    /** The shape of a keys value: the number of comma-separated parts and their lengths, no key material. */
    private static String shape(String text) {
        String[] parts = text.split(",", -1);
        List<String> lengths = new ArrayList<>();
        for (String part : parts) {
            String clean = part.replaceAll("\\s", "");
            lengths.add(clean.matches("([0-9A-Fa-f]{2})*") ? String.valueOf(clean.length() / 2)
                    : clean.length() + " characters (not hex)");
        }
        String joined = String.join(" and ", lengths);
        String unit = joined.endsWith(")") ? "" : " bytes";
        return parts.length == 1 ? joined + unit : parts.length + " comma-separated parts of " + joined + unit;
    }

    private static boolean bool(String text) {
        String value = text.strip().toLowerCase(Locale.ROOT);
        if (!value.equals("true") && !value.equals("false")) {
            throw new IllegalArgumentException("must be true or false");
        }
        return value.equals("true");
    }

    private static int hexByte(String text, String expected) {
        String value = text.strip();
        if (!value.matches("[0-9A-Fa-f]{1,2}")) {
            throw new IllegalArgumentException("must be " + expected);
        }
        return Integer.parseInt(value, 16);
    }

    private static int securityLevel(String text) {
        int level = hexByte(text, "a hex security level");
        if (level != 0x00 && level != 0x01 && level != 0x03 && level != 0x11 && level != 0x13 && level != 0x33) {
            throw new IllegalArgumentException("security level must be 00, 01, 03, 11, 13 or 33"
                    + " (Amendment D Table 7-6)");
        }
        return level;
    }

    private static List<Integer> securityLevels(String text) {
        List<Integer> levels = new ArrayList<>();
        for (String part : text.split(",", -1)) {
            levels.add(securityLevel(part));
        }
        return checkedLevels(levels);
    }

    /** At least one level, each a valid one, none twice. */
    private static List<Integer> checkedLevels(List<Integer> levels) {
        if (levels.isEmpty()) {
            throw new IllegalArgumentException("securityLevels must list at least one security level");
        }
        List<Integer> checked = new ArrayList<>();
        for (int level : levels) {
            securityLevel(String.format("%02X", level & 0xFF));
            if (level < 0 || level > 0xFF || checked.contains(level)) {
                throw new IllegalArgumentException(String.format("security level %02X is listed twice or out of"
                        + " range", level));
            }
            checked.add(level);
        }
        return List.copyOf(checked);
    }

    private static String aid(String text, int min, int max) {
        byte[] bytes = HEX.parseHex(text.strip());
        if (bytes.length < min || bytes.length > max) {
            throw new IllegalArgumentException("must be " + (min == max ? min : min + "-" + max) + " bytes of hex");
        }
        return HEX.formatHex(bytes);
    }

    private static String prefix(String text, String isd, String registeredRid) {
        String prefix = aid(text, 4, 13);
        GuardPolicy.requireSafePrefix(HEX.parseHex(prefix), HEX.parseHex(isd), registeredRidBytes(registeredRid));
        return prefix;
    }

    private static byte[] registeredRidBytes(String registeredRid) {
        return registeredRid == null ? new byte[0] : HEX.parseHex(registeredRid);
    }

    private static JavaCardVersion version(String text) {
        String value = text.strip();
        String name = value.toUpperCase(Locale.ROOT).startsWith("V") ? value.toUpperCase(Locale.ROOT)
                : "V" + value.replace('.', '_');
        try {
            return JavaCardVersion.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown Java Card version; use one of "
                    + Arrays.stream(JavaCardVersion.values()).map(LiveCardConfig::display).toList(), e);
        }
    }

    private static Path sdk(String text) {
        if (text.isBlank()) {
            return null;
        }
        Path path = Path.of(text.strip()).toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("not a directory: " + path);
        }
        return path;
    }

    private static int maxAuthFailures(String text) {
        int value;
        try {
            value = Integer.parseInt(text.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("must be a number 1-3", e);
        }
        if (value < 1 || value > 3) {
            throw new IllegalArgumentException("must be 1-3");
        }
        return value;
    }

    /**
     * Returns an AID under the configured prefix.
     *
     * @param suffixHex the bytes after the prefix, as hex
     * @return {@code aidPrefix || suffix}
     * @throws IllegalArgumentException if the result is not 5-16 bytes
     */
    public AID aid(String suffixHex) {
        return AID.fromHex(aidPrefix + suffixHex);
    }

    /**
     * Returns the AID prefix as bytes.
     *
     * @return a copy of the prefix bytes
     */
    public byte[] aidPrefixBytes() {
        return HEX.parseHex(aidPrefix);
    }

    /**
     * Returns the guard policy of these settings: prefix, ISD, the static Key-MAC and the named registered RID.
     *
     * @return the policy for the {@link name.velikodniy.jcexpress.livecard.guard.ApduGuard}
     */
    public GuardPolicy guardPolicy() {
        return new GuardPolicy(aidPrefixBytes(), HEX.parseHex(isd), keys.mac(), registeredRidBytes(registeredRid));
    }

    /**
     * Describes the settings for logs and transcripts, without key values.
     *
     * @return one line per setting group
     */
    public String describe() {
        return "enabled=" + enabled + ", reader=" + (reader == null ? "(first reader with a card)" : reader)
                + ", keys=" + keys + String.format(", kvn=%02X%s, securityLevel=%02X", keyVersion,
                keyVersion == 0 ? " (card's choice)" : "", securityLevel)
                + ", securityLevels=" + String.join(",", securityLevels.stream()
                .map(value -> String.format("%02X", value)).toList())
                + ", aidPrefix=" + aidPrefix + (registeredRid == null ? "" : ", registeredRid=" + registeredRid)
                + ", javaCardVersion=" + display(javaCardVersion) + ", isd=" + isd
                + ", verifierSdk=" + verifierDescription() + ", transcriptDir="
                + transcriptDir + ", maxAuthFailures=" + maxAuthFailures;
    }

    private String verifierDescription() {
        if (verifierSdk != null) {
            return verifierSdk.toString();
        }
        return allowUnverifiedCaps ? "none (CAP files are loaded unverified)" : "(not set: deployments are refused)";
    }

    /** Never prints key values. */
    @Override
    public String toString() {
        return "LiveCardConfig[" + describe() + "]";
    }

    /**
     * Returns a Java Card version as the settings write it.
     *
     * @param version the version
     * @return e.g. {@code 3.0.4}
     */
    public static String display(JavaCardVersion version) {
        return version.name().substring(1).replace('_', '.');
    }
}
