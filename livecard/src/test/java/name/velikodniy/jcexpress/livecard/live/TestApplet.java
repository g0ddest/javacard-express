package name.velikodniy.jcexpress.livecard.live;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.livecard.AppletPackage;
import name.velikodniy.jcexpress.livecard.LiveCardConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The test applets of {@code src/applets/java} (compiled with {@code --release 8} against the API stubs into
 * {@code target/applet-classes}) and their AIDs below the configured prefix.
 */
public enum TestApplet {

    /** Static array initializer, echo, arithmetic, ISOException, persistent counter, transient memory. */
    HELLO("com.jcx.livecard.hello", "HelloApplet", "0101", "010101"),
    /** Keeps its install parameters ('C9') and reports its own instance AID. */
    PARAMS("com.jcx.livecard.params", "ParamsApplet", "0102", "010201"),
    /** Crypto API probe (AES, 3DES, SHA, HMAC, RNG, ECDSA P-256, RSA-2048). */
    CRYPTO("com.jcx.livecard.crypto", "CryptoApplet", "0103", "010301"),
    /** Virtual dispatch: abstract methods, override chain with super calls, package-private overrides. */
    DISPATCH("com.jcx.livecard.dispatch", "DispatchApplet", "0104", "010401"),
    /** Typed catches, custom CardRuntimeException, nested try/finally, rethrow. */
    EXCEPTIONS("com.jcx.livecard.exceptions", "ExceptionsApplet", "0105", "010501"),
    /** Array lengths, post-increment indexes, compound element updates, Util array methods, Object[]. */
    ARRAYS("com.jcx.livecard.arrays", "ArraysApplet", "0106", "010601"),
    /** Branches over more than 127 bytes, dense and sparse switches. */
    FLOW("com.jcx.livecard.flow", "FlowApplet", "0107", "010701"),
    /** Static fields with initializers, static final arrays, an inlined constant. */
    STATICS("com.jcx.livecard.statics", "StaticsApplet", "0108", "010801"),
    /** Transactions (abort, commit) and CLEAR_ON_DESELECT/CLEAR_ON_RESET memory. */
    TRANSACTIONS("com.jcx.livecard.transactions", "TransactionsApplet", "0109", "010901"),
    /** 256-byte responses, chunked sendBytes, receiveBytes of 255 data bytes. */
    APDU_IO("com.jcx.livecard.apduio", "ApduIoApplet", "010A", "010A01"),
    /** Shares the Ledger interface; converted with an Export component for {@link #SIO_CLIENT}. */
    SIO_SERVER("com.jcx.livecard.sioserver", "LedgerApplet", "010B", "010B01", Conversion.EXPORT_COMPONENT),
    /** Uses the Ledger of {@link #SIO_SERVER} through JCSystem.getAppletShareableInterfaceObject. */
    SIO_CLIENT("com.jcx.livecard.sioclient", "LedgerClientApplet", "010C", "010C01"),
    /** The optional int type; converted with int support. */
    INT_OPS("com.jcx.livecard.intops", "IntApplet", "010D", "010D01", Conversion.INT_SUPPORT);

    /** How a package is converted beyond the defaults. */
    private enum Conversion {
        /** Default conversion. */
        PLAIN,
        /** With int support. */
        INT_SUPPORT,
        /** With an Export component. */
        EXPORT_COMPONENT
    }

    /** System property set by the build: the compiled applet classes. */
    public static final String CLASSES_PROPERTY = "jcx.livecard.test.appletClasses";

    private final String packageName;
    private final String simpleName;
    private final String packageSuffix;
    private final String moduleSuffix;
    private final Conversion conversion;

    TestApplet(String packageName, String simpleName, String packageSuffix, String moduleSuffix) {
        this(packageName, simpleName, packageSuffix, moduleSuffix, Conversion.PLAIN);
    }

    TestApplet(String packageName, String simpleName, String packageSuffix, String moduleSuffix,
               Conversion conversion) {
        this.packageName = packageName;
        this.simpleName = simpleName;
        this.packageSuffix = packageSuffix;
        this.moduleSuffix = moduleSuffix;
        this.conversion = conversion;
    }

    /**
     * Returns the directory with the compiled applet classes.
     *
     * @return {@code target/applet-classes} of this module
     */
    public static Path classesDirectory() {
        Path directory = Path.of(System.getProperty(CLASSES_PROPERTY, "target/applet-classes"));
        if (!Files.isDirectory(directory.resolve("com/jcx/livecard"))) {
            throw new IllegalStateException("Test applets not compiled in " + directory.toAbsolutePath()
                    + "; build the module with Maven (execution compile-applets)");
        }
        return directory;
    }

    /**
     * Returns the fully qualified class name.
     *
     * @return the applet class name
     */
    public String className() {
        return packageName + "." + simpleName;
    }

    /**
     * Returns the package AID under the configured prefix.
     *
     * @param config the settings
     * @return the package (load file) AID
     */
    public AID packageAid(LiveCardConfig config) {
        return config.aid(packageSuffix);
    }

    /**
     * Returns the applet (module) AID under the configured prefix.
     *
     * @param config the settings
     * @return the module AID
     */
    public AID moduleAid(LiveCardConfig config) {
        return config.aid(moduleSuffix);
    }

    /**
     * Returns another AID in the applet's range, e.g. for a second instance.
     *
     * @param config    the settings
     * @param lastByte  the last byte replacing the module AID's last byte
     * @return the AID
     */
    public AID instanceAid(LiveCardConfig config, int lastByte) {
        return config.aid(moduleSuffix.substring(0, moduleSuffix.length() - 2) + String.format("%02X", lastByte));
    }

    /**
     * Returns the package description for {@link name.velikodniy.jcexpress.livecard.LiveCard#deploy}.
     *
     * @param config the settings
     * @return the package with its applet
     */
    public AppletPackage pkg(LiveCardConfig config) {
        AppletPackage pkg = AppletPackage.of(classesDirectory(), packageName, packageAid(config))
                .withApplet(simpleName, moduleAid(config));
        return switch (conversion) {
            case PLAIN -> pkg;
            case INT_SUPPORT -> pkg.withIntSupport();
            case EXPORT_COMPONENT -> pkg.withExportComponent();
        };
    }

    /**
     * Returns the test applets whose packages this one's package imports; they must be deployed first and their
     * {@link name.velikodniy.jcexpress.livecard.Deployment#exportPath()} added to this package's export path.
     *
     * @return the imported test applet packages
     */
    public List<TestApplet> imports() {
        return this == SIO_CLIENT ? List.of(SIO_SERVER) : List.of();
    }

    /**
     * Loads the applet class (for jCardSim; the class path holds jCardSim's {@code javacard.framework}).
     *
     * @return the applet class
     */
    public Class<? extends Applet> load() {
        try {
            return Class.forName(className()).asSubclass(Applet.class);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(className() + " is not on the test class path; the build adds "
                    + "target/applet-classes (surefire additionalClasspathElements)", e);
        }
    }

    /**
     * Finds the test applet whose module AID (under any prefix) is the given AID.
     *
     * @param moduleAid the module AID, uppercase hex
     * @return the applet, or empty
     */
    public static Optional<TestApplet> byModuleAid(String moduleAid) {
        return Arrays.stream(values()).filter(applet -> moduleAid.endsWith(applet.moduleSuffix)).findFirst();
    }
}
