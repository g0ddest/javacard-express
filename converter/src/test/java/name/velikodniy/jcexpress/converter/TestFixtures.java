package name.velikodniy.jcexpress.converter;

import java.nio.file.Path;
import java.util.List;

/**
 * Catalogue of the converter's test packages (test sources under {@code com.example}) with the
 * conversion parameters used by the spec-conformance tests.
 */
public final class TestFixtures {

    /** Directory with the compiled test fixtures. */
    public static final Path CLASSES_DIR = Path.of("target/test-classes");

    /**
     * A test package.
     *
     * @param label      short name used in test reports
     * @param pkg        Java package name
     * @param pkgAid     package AID (hex)
     * @param applet     applet class (dot notation) or {@code null} for a library package
     * @param appletAid  applet AID (hex) or {@code null}
     * @param int32      whether the package uses the int type (ACC_INT)
     */
    public record Fixture(String label, String pkg, String pkgAid, String applet, String appletAid,
                          boolean int32) {
        @Override
        public String toString() {
            return label;
        }

        /**
         * Creates a converter builder for this fixture in the default mode, with the Export
         * component enabled as the Maven plugin does by default: the converter emits it only when
         * the package has something to export (JCVM 3.1 §6.13).
         *
         * @param version target Java Card version
         * @return configured builder
         */
        public Converter.Builder builder(JavaCardVersion version) {
            Converter.Builder b = Converter.builder()
                    .classesDirectory(CLASSES_DIR)
                    .packageName(pkg)
                    .packageAid(pkgAid)
                    .packageVersion(1, 0)
                    .supportInt32(int32)
                    .generateExport(true)
                    .javaCardVersion(version);
            if (applet != null) {
                b.applet(applet, appletAid);
            }
            return b;
        }

        /**
         * Converts this fixture in the default mode for JC 3.0.5.
         *
         * @return conversion result
         * @throws ConverterException if conversion fails
         */
        public ConverterResult convert() throws ConverterException {
            return builder(JavaCardVersion.V3_0_5).build().convert();
        }
    }

    /** Applet packages that must convert into spec-conformant CAP files. */
    public static final List<Fixture> APPLETS = List.of(
            applet("TestApplet", "com.example", "A000000062010101", "TestApplet", false),
            applet("InheritanceApplet", "com.example.inherit", "A000000062060101", "InheritanceApplet", false),
            applet("InterfaceApplet", "com.example.iface", "A000000062040101", "InterfaceApplet", false),
            applet("ExceptionApplet", "com.example.exception", "A000000062050101", "ExceptionApplet", false),
            applet("MultiClassApplet", "com.example.multiclass", "A000000062030101", "MultiClassApplet", false),
            applet("CryptoApplet", "com.example.crypto", "A000000062070101", "CryptoApplet", false),
            applet("VisibilityApplet", "com.example.visibility", "A000000062010102", "VisibilityApplet", false),
            applet("ConcreteApplet", "com.example.abstract_", "A000000062080101", "ConcreteApplet", false),
            applet("ArrayOpsApplet", "com.example.arrayops", "A000000062090101", "ArrayOpsApplet", false),
            applet("IntOpsApplet", "com.example.intops", "A0000000620A0101", "IntOpsApplet", true),
            applet("MultiExceptionApplet", "com.example.multiexc", "A0000000620B0101", "MultiExceptionApplet", false),
            applet("StaticsApplet", "com.example.statics", "A0000000620C0101", "StaticsApplet", true),
            applet("PkgVirtApplet", "com.example.pkgvirt", "A0000000620D0101", "PkgVirtApplet", false),
            applet("HelperApplet", "com.example.helper", "A0000000620E0101", "HelperApplet", false),
            applet("NoMethApplet", "com.example.nometh", "A0000000620F0101", "NoMethApplet", false),
            applet("ChainApplet", "com.example.chain", "A000000062100101", "ChainApplet", false),
            applet("ShareApplet", "com.example.share", "A000000062110101", "ShareApplet", false),
            applet("OrderApplet", "com.example.ifaceorder", "A000000062120101", "OrderApplet", false),
            applet("FieldsApplet", "com.example.fields", "A000000062130101", "FieldsApplet", true),
            applet("ArrInitApplet", "com.example.arrinit", "A000000062140101", "ArrInitApplet", false),
            applet("InheritStaticApplet", "com.example.inheritstatic", "A000000062150101",
                    "InheritStaticApplet", false),
            applet("AbsImpl", "com.example.abs", "A000000062160101", "AbsImpl", false),
            applet("MirandaApplet", "com.example.miranda", "A0000000621B0101", "MirandaApplet", false));

    /** Library package (no applet) converted with the Export component. */
    public static final Fixture LIBRARY =
            new Fixture("Library", "com.example.lib", "A000000062170101", null, null, false);

    /** Packages the converter must reject: they break a rule of the Java Card language subset. */
    public static final List<Fixture> REJECTED = List.of(
            applet("PubOverApplet", "com.example.pubover", "A000000062180101", "PubOverApplet", false),
            applet("BadClinitApplet", "com.example.badclinit", "A000000062190101", "BadClinitApplet", false),
            applet("IfStatApplet", "com.example.ifstat", "A0000000621A0101", "IfStatApplet", false),
            applet("BlankFinalApplet", "com.example.blankfinal", "A0000000621C0101", "BlankFinalApplet", false));

    private TestFixtures() {}

    private static Fixture applet(String label, String pkg, String pkgAid, String appletSimple,
                                  boolean int32) {
        return new Fixture(label, pkg, pkgAid, pkg + "." + appletSimple, pkgAid + "01", int32);
    }

    /**
     * Finds an applet fixture by label.
     *
     * @param label fixture label
     * @return the fixture
     */
    public static Fixture applet(String label) {
        return java.util.stream.Stream.concat(APPLETS.stream(), REJECTED.stream())
                .filter(f -> f.label().equals(label)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown fixture " + label));
    }
}
