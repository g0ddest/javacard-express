package name.velikodniy.jcexpress.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import name.velikodniy.jcexpress.converter.JavaCardVersion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JavaCardBuildMojoTest {

    @TempDir
    Path tempDir;

    // ── Helpers ──

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = JavaCardBuildMojo.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = JavaCardBuildMojo.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void invokeValidation(AppletConfig ac) throws Exception {
        Method method = JavaCardBuildMojo.class.getDeclaredMethod("validateAppletConfig", AppletConfig.class);
        method.setAccessible(true);
        try {
            method.invoke(null, ac);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception ex) throw ex;
            throw new RuntimeException(e.getCause());
        }
    }

    private static String invokeGenerateAppletAid(JavaCardBuildMojo mojo, String className) throws Exception {
        Method method = JavaCardBuildMojo.class.getDeclaredMethod("generateAppletAid", String.class);
        method.setAccessible(true);
        try {
            return (String) method.invoke(mojo, className);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception ex) throw ex;
            throw new RuntimeException(e.getCause());
        }
    }

    private static String invokeBytesToHex(byte[] bytes) throws Exception {
        Method method = JavaCardBuildMojo.class.getDeclaredMethod("bytesToHex", byte[].class);
        method.setAccessible(true);
        return (String) method.invoke(null, (Object) bytes);
    }

    private static boolean invokeIsAppletClass(JavaCardBuildMojo mojo, Path classFile) throws Exception {
        Method method = JavaCardBuildMojo.class.getDeclaredMethod("isAppletClass", Path.class);
        method.setAccessible(true);
        try {
            return (boolean) method.invoke(mojo, classFile);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception ex) throw ex;
            throw new RuntimeException(e.getCause());
        }
    }

    /**
     * Creates a minimal valid .class file using the JDK ClassFile API.
     */
    private byte[] createMinimalAppletClass(String className, String superClass) {
        return java.lang.classfile.ClassFile.of().build(
                java.lang.constant.ClassDesc.ofInternalName(className),
                cb -> cb.withSuperclass(java.lang.constant.ClassDesc.ofInternalName(superClass))
        );
    }

    // ══════════════════════════════════════════════════════════════
    // Package Name Resolution
    // ══════════════════════════════════════════════════════════════

    @Nested
    class PackageNameResolution {

        @Test
        void shouldAutoDetectPackageName() throws IOException {
            Path pkg = tempDir.resolve("com/example");
            Files.createDirectories(pkg);
            Files.write(pkg.resolve("MyApplet.class"), new byte[]{(byte) 0xCA, (byte) 0xFE});

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            String detected = mojo.resolvePackageName(tempDir);

            assertThat(detected).isEqualTo("com.example");
        }

        @Test
        void shouldReturnExplicitPackageName() throws Exception {
            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            setField(mojo, "packageName", "com.custom.pkg");

            String resolved = mojo.resolvePackageName(tempDir);
            assertThat(resolved).isEqualTo("com.custom.pkg");
        }

        @Test
        void shouldReturnNullWhenNoClassFiles() {
            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            String detected = mojo.resolvePackageName(tempDir);

            assertThat(detected).isNull();
        }

        @Test
        void shouldReturnNullForEmptyDirectory() throws IOException {
            Path emptyDir = tempDir.resolve("empty");
            Files.createDirectories(emptyDir);

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            assertThat(mojo.resolvePackageName(emptyDir)).isNull();
        }

        @Test
        void shouldAutoDetectDeeplyNestedPackage() throws IOException {
            Path pkg = tempDir.resolve("org/example/deep/nested");
            Files.createDirectories(pkg);
            Files.write(pkg.resolve("Foo.class"), new byte[]{(byte) 0xCA});

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            assertThat(mojo.resolvePackageName(tempDir)).isEqualTo("org.example.deep.nested");
        }

        @Test
        void explicitPackageNameTakesPriority() throws Exception {
            // Even if classesDir has files, explicit packageName wins
            Path pkg = tempDir.resolve("org/other");
            Files.createDirectories(pkg);
            Files.write(pkg.resolve("X.class"), new byte[]{0});

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            setField(mojo, "packageName", "com.explicit");

            assertThat(mojo.resolvePackageName(tempDir)).isEqualTo("com.explicit");
        }

        @Test
        void blankPackageNameFallsBackToAutoDetect() throws Exception {
            Path pkg = tempDir.resolve("com/auto");
            Files.createDirectories(pkg);
            Files.write(pkg.resolve("A.class"), new byte[]{1});

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            setField(mojo, "packageName", "   ");

            assertThat(mojo.resolvePackageName(tempDir)).isEqualTo("com.auto");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Applet Discovery
    // ══════════════════════════════════════════════════════════════

    @Nested
    class AppletDiscovery {

        @Test
        void shouldDiscoverAppletsByDefault() throws Exception {
            Path pkg = tempDir.resolve("com/example");
            Files.createDirectories(pkg);

            byte[] classBytes = createMinimalAppletClass("com/example/TestApplet", "javacard/framework/Applet");
            Files.write(pkg.resolve("TestApplet.class"), classBytes);

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            var discovered = mojo.discoverApplets(tempDir, "com.example");

            assertThat(discovered).hasSize(1);
            assertThat(discovered.getFirst().getClassName()).isEqualTo("com.example.TestApplet");
            assertThat(discovered.getFirst().getAid()).isNotBlank();
        }

        @Test
        void shouldNotDiscoverNonAppletClasses() throws Exception {
            Path pkg = tempDir.resolve("com/example");
            Files.createDirectories(pkg);

            byte[] classBytes = createMinimalAppletClass("com/example/Helper", "java/lang/Object");
            Files.write(pkg.resolve("Helper.class"), classBytes);

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            var discovered = mojo.discoverApplets(tempDir, "com.example");

            assertThat(discovered).isEmpty();
        }

        @Test
        void shouldDiscoverMultipleApplets() throws Exception {
            Path pkg = tempDir.resolve("com/example");
            Files.createDirectories(pkg);

            Files.write(pkg.resolve("AppletA.class"),
                    createMinimalAppletClass("com/example/AppletA", "javacard/framework/Applet"));
            Files.write(pkg.resolve("AppletB.class"),
                    createMinimalAppletClass("com/example/AppletB", "javacard/framework/Applet"));
            Files.write(pkg.resolve("Util.class"),
                    createMinimalAppletClass("com/example/Util", "java/lang/Object"));

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            var discovered = mojo.discoverApplets(tempDir, "com.example");

            assertThat(discovered).hasSize(2);
            List<String> classNames = discovered.stream()
                    .map(AppletConfig::getClassName).sorted().toList();
            assertThat(classNames).containsExactly("com.example.AppletA", "com.example.AppletB");
        }

        @Test
        void shouldReturnEmptyWhenPackageDirMissing() {
            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            var discovered = mojo.discoverApplets(tempDir, "com.nonexistent");
            assertThat(discovered).isEmpty();
        }

        @Test
        void shouldReturnEmptyWhenNoClassFilesInPackage() throws IOException {
            Path pkg = tempDir.resolve("com/example");
            Files.createDirectories(pkg);
            // No .class files

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            var discovered = mojo.discoverApplets(tempDir, "com.example");
            assertThat(discovered).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // isAppletClass
    // ══════════════════════════════════════════════════════════════

    @Nested
    class IsAppletClassTests {

        @Test
        void recognizesDirectAppletSubclass() throws Exception {
            Path classFile = tempDir.resolve("TestApplet.class");
            Files.write(classFile, createMinimalAppletClass("TestApplet", "javacard/framework/Applet"));

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            assertThat(invokeIsAppletClass(mojo, classFile)).isTrue();
        }

        @Test
        void rejectsNonAppletClass() throws Exception {
            Path classFile = tempDir.resolve("Helper.class");
            Files.write(classFile, createMinimalAppletClass("Helper", "java/lang/Object"));

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            assertThat(invokeIsAppletClass(mojo, classFile)).isFalse();
        }

        @Test
        void handlesInvalidClassFile() throws Exception {
            Path classFile = tempDir.resolve("Broken.class");
            Files.write(classFile, new byte[]{0x00, 0x01, 0x02, 0x03});

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            assertThat(invokeIsAppletClass(mojo, classFile)).isFalse();
        }

        @Test
        void handlesNonexistentFile() throws Exception {
            Path classFile = tempDir.resolve("Missing.class");

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            assertThat(invokeIsAppletClass(mojo, classFile)).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Applet Config Validation
    // ══════════════════════════════════════════════════════════════

    @Nested
    class AppletConfigValidation {

        @Test
        void shouldValidateAppletConfig() {
            AppletConfig empty = new AppletConfig();
            assertThatThrownBy(() -> invokeValidation(empty))
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessageContaining("className");

            AppletConfig noAid = new AppletConfig();
            noAid.setClassName("com.example.Applet");
            assertThatThrownBy(() -> invokeValidation(noAid))
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessageContaining("AID is required");

            AppletConfig tooShort = new AppletConfig();
            tooShort.setClassName("com.example.Applet");
            tooShort.setAid("A0B1");
            assertThatThrownBy(() -> invokeValidation(tooShort))
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessageContaining("Invalid AID length");
        }

        @Test
        void shouldAcceptValidAppletConfig() {
            AppletConfig valid = new AppletConfig();
            valid.setClassName("com.example.WalletApplet");
            valid.setAid("A0000000621201");

            assertThatNoException().isThrownBy(() -> invokeValidation(valid));
        }

        @Test
        void shouldRejectTooLongAid() {
            AppletConfig tooLong = new AppletConfig();
            tooLong.setClassName("com.example.Applet");
            // 17 bytes = 34 hex chars (max is 16 bytes = 32 hex)
            tooLong.setAid("A0000000621201020304050607080910FF11");
            assertThatThrownBy(() -> invokeValidation(tooLong))
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessageContaining("Invalid AID length");
        }

        @Test
        void shouldAcceptMinLengthAid() {
            AppletConfig minLen = new AppletConfig();
            minLen.setClassName("com.example.App");
            minLen.setAid("A000000062"); // 5 bytes = 10 hex chars = minimum
            assertThatNoException().isThrownBy(() -> invokeValidation(minLen));
        }

        @Test
        void shouldAcceptMaxLengthAid() {
            AppletConfig maxLen = new AppletConfig();
            maxLen.setClassName("com.example.App");
            maxLen.setAid("A0000000620102030405060708091011"); // 16 bytes = 32 hex chars
            assertThatNoException().isThrownBy(() -> invokeValidation(maxLen));
        }

        @Test
        void shouldAcceptAidWithSpacesAndColons() {
            AppletConfig ac = new AppletConfig();
            ac.setClassName("com.example.App");
            ac.setAid("A0:00:00:00:62:12:01");
            assertThatNoException().isThrownBy(() -> invokeValidation(ac));
        }

        @Test
        void shouldRejectBlankClassName() {
            AppletConfig blank = new AppletConfig();
            blank.setClassName("   ");
            assertThatThrownBy(() -> invokeValidation(blank))
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessageContaining("className");
        }

        @Test
        void shouldRejectBlankAid() {
            AppletConfig blankAid = new AppletConfig();
            blankAid.setClassName("com.example.App");
            blankAid.setAid("   ");
            assertThatThrownBy(() -> invokeValidation(blankAid))
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessageContaining("AID is required");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // AppletConfig toString
    // ══════════════════════════════════════════════════════════════

    @Nested
    class AppletConfigTests {

        @Test
        void toStringContainsClassAndAid() {
            AppletConfig ac = new AppletConfig();
            ac.setClassName("com.example.Test");
            ac.setAid("A00000006212");

            assertThat(ac.toString()).contains("com.example.Test")
                    .contains("A00000006212");
        }

        @Test
        void gettersAndSetters() {
            AppletConfig ac = new AppletConfig();
            assertThat(ac.getClassName()).isNull();
            assertThat(ac.getAid()).isNull();

            ac.setClassName("foo.Bar");
            ac.setAid("AABBCC");
            assertThat(ac.getClassName()).isEqualTo("foo.Bar");
            assertThat(ac.getAid()).isEqualTo("AABBCC");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Version Parsing
    // ══════════════════════════════════════════════════════════════

    @Nested
    class VersionParsing {

        @Test
        void shouldParseAllJavaCardVersionStrings() {
            assertThat(JavaCardBuildMojo.parseJavaCardVersion("2.1.2")).isEqualTo(JavaCardVersion.V2_1_2);
            assertThat(JavaCardBuildMojo.parseJavaCardVersion("2.1")).isEqualTo(JavaCardVersion.V2_1_2);
            assertThat(JavaCardBuildMojo.parseJavaCardVersion("2.2.1")).isEqualTo(JavaCardVersion.V2_2_1);
            assertThat(JavaCardBuildMojo.parseJavaCardVersion("2.2.2")).isEqualTo(JavaCardVersion.V2_2_2);
            assertThat(JavaCardBuildMojo.parseJavaCardVersion("2.2")).isEqualTo(JavaCardVersion.V2_2_2);
            assertThat(JavaCardBuildMojo.parseJavaCardVersion("3.0.3")).isEqualTo(JavaCardVersion.V3_0_3);
            assertThat(JavaCardBuildMojo.parseJavaCardVersion("3.0.4")).isEqualTo(JavaCardVersion.V3_0_4);
            assertThat(JavaCardBuildMojo.parseJavaCardVersion("3.0.5")).isEqualTo(JavaCardVersion.V3_0_5);
            assertThat(JavaCardBuildMojo.parseJavaCardVersion("3.0")).isEqualTo(JavaCardVersion.V3_0_5);
            assertThat(JavaCardBuildMojo.parseJavaCardVersion("3.1.0")).isEqualTo(JavaCardVersion.V3_1_0);
            assertThat(JavaCardBuildMojo.parseJavaCardVersion("3.1")).isEqualTo(JavaCardVersion.V3_1_0);
            assertThat(JavaCardBuildMojo.parseJavaCardVersion("3.2.0")).isEqualTo(JavaCardVersion.V3_2_0);
            assertThat(JavaCardBuildMojo.parseJavaCardVersion("3.2")).isEqualTo(JavaCardVersion.V3_2_0);
        }

        @Test
        void shouldDefaultToJc305WhenNull() {
            assertThat(JavaCardBuildMojo.parseJavaCardVersion(null)).isEqualTo(JavaCardVersion.V3_0_5);
        }

        @Test
        void shouldDefaultToJc305ForUnknownVersion() {
            assertThat(JavaCardBuildMojo.parseJavaCardVersion("9.9.9")).isEqualTo(JavaCardVersion.V3_0_5);
            assertThat(JavaCardBuildMojo.parseJavaCardVersion("")).isEqualTo(JavaCardVersion.V3_0_5);
        }

        @Test
        void shouldHandleWhitespace() {
            assertThat(JavaCardBuildMojo.parseJavaCardVersion("  3.0.5  ")).isEqualTo(JavaCardVersion.V3_0_5);
            assertThat(JavaCardBuildMojo.parseJavaCardVersion(" 2.2.2 ")).isEqualTo(JavaCardVersion.V2_2_2);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // generateAppletAid (via reflection)
    // ══════════════════════════════════════════════════════════════

    @Nested
    class GenerateAppletAidTests {

        @Test
        void generatesNonBlankAid() throws Exception {
            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            String aid = invokeGenerateAppletAid(mojo, "com.example.TestApplet");
            assertThat(aid).isNotBlank();
        }

        @Test
        void aidStartsWithF0() throws Exception {
            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            String aid = invokeGenerateAppletAid(mojo, "com.example.TestApplet");
            assertThat(aid).startsWith("F0");
        }

        @Test
        void aidIs9Bytes() throws Exception {
            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            String aid = invokeGenerateAppletAid(mojo, "com.example.TestApplet");
            // 9 bytes = 18 hex chars
            assertThat(aid).hasSize(18);
        }

        @Test
        void aidIsDeterministic() throws Exception {
            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            String aid1 = invokeGenerateAppletAid(mojo, "com.example.MyApplet");
            String aid2 = invokeGenerateAppletAid(mojo, "com.example.MyApplet");
            assertThat(aid1).isEqualTo(aid2);
        }

        @Test
        void differentClassesDifferentAids() throws Exception {
            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            String aid1 = invokeGenerateAppletAid(mojo, "com.example.AppletA");
            String aid2 = invokeGenerateAppletAid(mojo, "com.example.AppletB");
            assertThat(aid1).isNotEqualTo(aid2);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // bytesToHex (via reflection)
    // ══════════════════════════════════════════════════════════════

    @Nested
    class BytesToHexTests {

        @Test
        void emptyArray() throws Exception {
            assertThat(invokeBytesToHex(new byte[0])).isEmpty();
        }

        @Test
        void singleByte() throws Exception {
            assertThat(invokeBytesToHex(new byte[]{(byte) 0xAB})).isEqualTo("AB");
        }

        @Test
        void multipleBytes() throws Exception {
            assertThat(invokeBytesToHex(new byte[]{0x01, 0x02, (byte) 0xFF})).isEqualTo("0102FF");
        }

        @Test
        void zeroByte() throws Exception {
            assertThat(invokeBytesToHex(new byte[]{0x00})).isEqualTo("00");
        }

        @Test
        void allZeros() throws Exception {
            assertThat(invokeBytesToHex(new byte[]{0, 0, 0})).isEqualTo("000000");
        }

        @Test
        void uppercaseOutput() throws Exception {
            assertThat(invokeBytesToHex(new byte[]{(byte) 0xab, (byte) 0xcd})).isEqualTo("ABCD");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Skip flag
    // ══════════════════════════════════════════════════════════════

    @Nested
    class SkipFlagTests {

        @Test
        void skipTrueSkipsExecution() throws Exception {
            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            setField(mojo, "skip", true);
            setField(mojo, "classesDirectory", tempDir.toFile());
            setField(mojo, "outputDirectory", tempDir.toFile());
            // Should return immediately without doing anything
            assertThatNoException().isThrownBy(mojo::execute);
        }

        @Test
        void skipDefaultIsFalse() throws Exception {
            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            assertThat((boolean) getField(mojo, "skip")).isFalse();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Configuration Parameters (via reflection)
    // ══════════════════════════════════════════════════════════════

    @Nested
    class ConfigurationParameters {

        @Test
        void packageAid() throws Exception {
            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            assertThat(getField(mojo, "packageAid")).isNull();

            setField(mojo, "packageAid", "A00000006212");
            assertThat(getField(mojo, "packageAid")).isEqualTo("A00000006212");
        }

        @Test
        void packageName() throws Exception {
            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            assertThat(getField(mojo, "packageName")).isNull();

            setField(mojo, "packageName", "com.test");
            assertThat(getField(mojo, "packageName")).isEqualTo("com.test");
        }

        @Test
        void packageVersion() throws Exception {
            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            // Default from annotation is "1.0"
            setField(mojo, "packageVersion", "2.1");
            assertThat(getField(mojo, "packageVersion")).isEqualTo("2.1");
        }

        @Test
        void supportInt32() throws Exception {
            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            assertThat((boolean) getField(mojo, "supportInt32")).isFalse();

            setField(mojo, "supportInt32", true);
            assertThat((boolean) getField(mojo, "supportInt32")).isTrue();
        }

        @Test
        void generateExport() throws Exception {
            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            // default is true per annotation
            setField(mojo, "generateExport", false);
            assertThat((boolean) getField(mojo, "generateExport")).isFalse();
        }

        @Test
        void oracleCompatibility() throws Exception {
            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            assertThat((boolean) getField(mojo, "oracleCompatibility")).isFalse();

            setField(mojo, "oracleCompatibility", true);
            assertThat((boolean) getField(mojo, "oracleCompatibility")).isTrue();
        }

        @Test
        void javaCardVersionStr() throws Exception {
            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            setField(mojo, "javaCardVersionStr", "2.2.2");
            assertThat(getField(mojo, "javaCardVersionStr")).isEqualTo("2.2.2");
        }

        @Test
        void appletsListCanBeSet() throws Exception {
            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            assertThat(getField(mojo, "applets")).isNull();

            List<AppletConfig> applets = new ArrayList<>();
            AppletConfig ac = new AppletConfig();
            ac.setClassName("com.example.App");
            ac.setAid("A0000000621201");
            applets.add(ac);
            setField(mojo, "applets", applets);

            @SuppressWarnings("unchecked")
            List<AppletConfig> result = (List<AppletConfig>) getField(mojo, "applets");
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getClassName()).isEqualTo("com.example.App");
        }

        @Test
        void importExportFiles() throws Exception {
            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            assertThat(getField(mojo, "importExportFiles")).isNull();

            List<File> files = new ArrayList<>();
            files.add(tempDir.resolve("api.exp").toFile());
            setField(mojo, "importExportFiles", files);

            @SuppressWarnings("unchecked")
            List<File> result = (List<File>) getField(mojo, "importExportFiles");
            assertThat(result).hasSize(1);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Execute: Error Cases
    // ══════════════════════════════════════════════════════════════

    @Nested
    class ExecuteErrorCases {

        @Test
        void missingClassesDirectoryWarnsAndReturns() throws Exception {
            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            Path noSuchDir = tempDir.resolve("nonexistent");
            setField(mojo, "classesDirectory", noSuchDir.toFile());
            setField(mojo, "outputDirectory", tempDir.toFile());
            setField(mojo, "skip", false);
            // Should not throw, just warn
            assertThatNoException().isThrownBy(mojo::execute);
        }

        @Test
        void cannotDeterminePackageNameThrows() throws Exception {
            // Create empty classes directory (no .class files)
            Path classesDir = tempDir.resolve("classes");
            Files.createDirectories(classesDir);

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            setField(mojo, "classesDirectory", classesDir.toFile());
            setField(mojo, "outputDirectory", tempDir.toFile());
            setField(mojo, "skip", false);

            assertThatThrownBy(mojo::execute)
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessageContaining("Cannot determine package name");
        }

        @Test
        void importExportFileNotFoundThrows() throws Exception {
            Path classesDir = tempDir.resolve("classes/com/example");
            Files.createDirectories(classesDir);
            Files.write(classesDir.resolve("App.class"),
                    createMinimalAppletClass("com/example/App", "javacard/framework/Applet"));

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            setField(mojo, "classesDirectory", tempDir.resolve("classes").toFile());
            setField(mojo, "outputDirectory", tempDir.toFile());
            setField(mojo, "skip", false);
            setField(mojo, "packageAid", "A00000006212");
            setField(mojo, "javaCardVersionStr", "3.0.5");
            setField(mojo, "packageVersion", "1.0");
            setField(mojo, "generateExport", true);
            setField(mojo, "oracleCompatibility", false);
            setField(mojo, "supportInt32", false);

            List<File> imports = new ArrayList<>();
            imports.add(new File("/nonexistent/file.exp"));
            setField(mojo, "importExportFiles", imports);

            // We need a MavenProject mock for the execute path
            MavenProject project = new MavenProject();
            project.setArtifactId("test-applet");
            project.setVersion("1.0");
            setField(mojo, "project", project);

            assertThatThrownBy(mojo::execute)
                    .isInstanceOf(MojoExecutionException.class)
                    .hasMessageContaining("Export file not found");
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Execute: With Explicit Applets
    // ══════════════════════════════════════════════════════════════

    @Nested
    class ExecuteWithExplicitApplets {

        @Test
        void executesWithExplicitAppletConfig() throws Exception {
            // Create a classes directory with a real .class file
            Path classesDir = tempDir.resolve("classes/com/example");
            Files.createDirectories(classesDir);
            Files.write(classesDir.resolve("TestApplet.class"),
                    createMinimalAppletClass("com/example/TestApplet", "javacard/framework/Applet"));

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            setField(mojo, "classesDirectory", tempDir.resolve("classes").toFile());
            setField(mojo, "outputDirectory", tempDir.resolve("output").toFile());
            setField(mojo, "skip", false);
            setField(mojo, "packageAid", "A00000006212");
            setField(mojo, "packageName", "com.example");
            setField(mojo, "packageVersion", "1.0");
            setField(mojo, "javaCardVersionStr", "3.0.5");
            setField(mojo, "supportInt32", false);
            setField(mojo, "generateExport", true);
            setField(mojo, "oracleCompatibility", false);

            List<AppletConfig> applets = new ArrayList<>();
            AppletConfig ac = new AppletConfig();
            ac.setClassName("com.example.TestApplet");
            ac.setAid("A0000000621201");
            applets.add(ac);
            setField(mojo, "applets", applets);

            MavenProject project = new MavenProject();
            project.setArtifactId("test-applet");
            project.setVersion("1.0");
            setField(mojo, "project", project);

            // Execute - should produce CAP file
            mojo.execute();

            Path outDir = tempDir.resolve("output");
            assertThat(outDir.resolve("test-applet-1.0.cap")).exists();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Execute: Auto-discovery path
    // ══════════════════════════════════════════════════════════════

    @Nested
    class ExecuteAutoDiscovery {

        @Test
        void executesWithAutoDiscoveredApplets() throws Exception {
            Path classesDir = tempDir.resolve("classes/com/example");
            Files.createDirectories(classesDir);
            Files.write(classesDir.resolve("MyApplet.class"),
                    createMinimalAppletClass("com/example/MyApplet", "javacard/framework/Applet"));

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            setField(mojo, "classesDirectory", tempDir.resolve("classes").toFile());
            setField(mojo, "outputDirectory", tempDir.resolve("out").toFile());
            setField(mojo, "skip", false);
            setField(mojo, "packageAid", "A00000006212");
            setField(mojo, "packageVersion", "1.0");
            setField(mojo, "javaCardVersionStr", "3.0.5");
            setField(mojo, "supportInt32", false);
            setField(mojo, "generateExport", true);
            setField(mojo, "oracleCompatibility", false);
            // applets is null -> auto-discover

            MavenProject project = new MavenProject();
            project.setArtifactId("my-applet");
            project.setVersion("0.1");
            setField(mojo, "project", project);

            mojo.execute();

            assertThat(tempDir.resolve("out/my-applet-0.1.cap")).exists();
        }

        @Test
        void executesWithNoAppletsFoundWarns() throws Exception {
            // Package dir has only non-applet class files
            Path classesDir = tempDir.resolve("classes/com/example");
            Files.createDirectories(classesDir);
            Files.write(classesDir.resolve("Helper.class"),
                    createMinimalAppletClass("com/example/Helper", "java/lang/Object"));

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            setField(mojo, "classesDirectory", tempDir.resolve("classes").toFile());
            setField(mojo, "outputDirectory", tempDir.resolve("out2").toFile());
            setField(mojo, "skip", false);
            setField(mojo, "packageAid", "A00000006212");
            setField(mojo, "packageVersion", "1.0");
            setField(mojo, "javaCardVersionStr", "3.0.5");
            setField(mojo, "supportInt32", false);
            setField(mojo, "generateExport", true);
            setField(mojo, "oracleCompatibility", false);

            MavenProject project = new MavenProject();
            project.setArtifactId("test");
            project.setVersion("1.0");
            setField(mojo, "project", project);

            // Should execute without throwing (warns about no applets)
            assertThatNoException().isThrownBy(mojo::execute);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Execute: Package version parsing
    // ══════════════════════════════════════════════════════════════

    @Nested
    class PackageVersionParsing {

        @Test
        void parsesVersionWithDot() throws Exception {
            Path classesDir = tempDir.resolve("classes/com/example");
            Files.createDirectories(classesDir);
            Files.write(classesDir.resolve("TestApplet.class"),
                    createMinimalAppletClass("com/example/TestApplet", "javacard/framework/Applet"));

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            setField(mojo, "classesDirectory", tempDir.resolve("classes").toFile());
            setField(mojo, "outputDirectory", tempDir.resolve("out3").toFile());
            setField(mojo, "skip", false);
            setField(mojo, "packageAid", "A00000006212");
            setField(mojo, "packageVersion", "2.3");
            setField(mojo, "javaCardVersionStr", "3.0.5");
            setField(mojo, "supportInt32", false);
            setField(mojo, "generateExport", true);
            setField(mojo, "oracleCompatibility", false);

            MavenProject project = new MavenProject();
            project.setArtifactId("test");
            project.setVersion("1.0");
            setField(mojo, "project", project);

            // Execute to exercise the version parsing code path
            assertThatNoException().isThrownBy(mojo::execute);
        }

        @Test
        void handlesNullPackageVersion() throws Exception {
            Path classesDir = tempDir.resolve("classes/com/example");
            Files.createDirectories(classesDir);
            Files.write(classesDir.resolve("TestApplet.class"),
                    createMinimalAppletClass("com/example/TestApplet", "javacard/framework/Applet"));

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            setField(mojo, "classesDirectory", tempDir.resolve("classes").toFile());
            setField(mojo, "outputDirectory", tempDir.resolve("out4").toFile());
            setField(mojo, "skip", false);
            setField(mojo, "packageAid", "A00000006212");
            setField(mojo, "packageVersion", null);
            setField(mojo, "javaCardVersionStr", "3.0.5");
            setField(mojo, "supportInt32", false);
            setField(mojo, "generateExport", true);
            setField(mojo, "oracleCompatibility", false);

            MavenProject project = new MavenProject();
            project.setArtifactId("test");
            project.setVersion("1.0");
            setField(mojo, "project", project);

            assertThatNoException().isThrownBy(mojo::execute);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Execute: packageAid auto-generation
    // ══════════════════════════════════════════════════════════════

    @Nested
    class PackageAidAutoGeneration {

        @Test
        void executesWithNullPackageAid() throws Exception {
            Path classesDir = tempDir.resolve("classes/com/example");
            Files.createDirectories(classesDir);
            Files.write(classesDir.resolve("TestApplet.class"),
                    createMinimalAppletClass("com/example/TestApplet", "javacard/framework/Applet"));

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            setField(mojo, "classesDirectory", tempDir.resolve("classes").toFile());
            setField(mojo, "outputDirectory", tempDir.resolve("out5").toFile());
            setField(mojo, "skip", false);
            // packageAid is null -> auto-generate from package name
            setField(mojo, "packageVersion", "1.0");
            setField(mojo, "javaCardVersionStr", "3.0.5");
            setField(mojo, "supportInt32", false);
            setField(mojo, "generateExport", true);
            setField(mojo, "oracleCompatibility", false);

            MavenProject project = new MavenProject();
            project.setArtifactId("auto-aid");
            project.setVersion("1.0");
            setField(mojo, "project", project);

            mojo.execute();
            assertThat(tempDir.resolve("out5/auto-aid-1.0.cap")).exists();
        }

        @Test
        void executesWithBlankPackageAid() throws Exception {
            Path classesDir = tempDir.resolve("classes/com/example");
            Files.createDirectories(classesDir);
            Files.write(classesDir.resolve("TestApplet.class"),
                    createMinimalAppletClass("com/example/TestApplet", "javacard/framework/Applet"));

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            setField(mojo, "classesDirectory", tempDir.resolve("classes").toFile());
            setField(mojo, "outputDirectory", tempDir.resolve("out6").toFile());
            setField(mojo, "skip", false);
            setField(mojo, "packageAid", "   ");
            setField(mojo, "packageVersion", "1.0");
            setField(mojo, "javaCardVersionStr", "3.0.5");
            setField(mojo, "supportInt32", false);
            setField(mojo, "generateExport", true);
            setField(mojo, "oracleCompatibility", false);

            MavenProject project = new MavenProject();
            project.setArtifactId("blank-aid");
            project.setVersion("1.0");
            setField(mojo, "project", project);

            mojo.execute();
            assertThat(tempDir.resolve("out6/blank-aid-1.0.cap")).exists();
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Execute: Export file generation
    // ══════════════════════════════════════════════════════════════

    @Nested
    class ExportFileGeneration {

        @Test
        void generatesExportFile() throws Exception {
            Path classesDir = tempDir.resolve("classes/com/example");
            Files.createDirectories(classesDir);
            Files.write(classesDir.resolve("TestApplet.class"),
                    createMinimalAppletClass("com/example/TestApplet", "javacard/framework/Applet"));

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            setField(mojo, "classesDirectory", tempDir.resolve("classes").toFile());
            setField(mojo, "outputDirectory", tempDir.resolve("outexp").toFile());
            setField(mojo, "skip", false);
            setField(mojo, "packageAid", "A00000006212");
            setField(mojo, "packageVersion", "1.0");
            setField(mojo, "javaCardVersionStr", "3.0.5");
            setField(mojo, "supportInt32", false);
            setField(mojo, "generateExport", true);
            setField(mojo, "oracleCompatibility", false);

            MavenProject project = new MavenProject();
            project.setArtifactId("exp-test");
            project.setVersion("1.0");
            setField(mojo, "project", project);

            mojo.execute();

            Path capPath = tempDir.resolve("outexp/exp-test-1.0.cap");
            assertThat(capPath).exists();
            assertThat(Files.size(capPath)).isGreaterThan(0);
        }

        @Test
        void noExportWhenDisabled() throws Exception {
            Path classesDir = tempDir.resolve("classes/com/example");
            Files.createDirectories(classesDir);
            Files.write(classesDir.resolve("TestApplet.class"),
                    createMinimalAppletClass("com/example/TestApplet", "javacard/framework/Applet"));

            JavaCardBuildMojo mojo = new JavaCardBuildMojo();
            setField(mojo, "classesDirectory", tempDir.resolve("classes").toFile());
            setField(mojo, "outputDirectory", tempDir.resolve("outnoexp").toFile());
            setField(mojo, "skip", false);
            setField(mojo, "packageAid", "A00000006212");
            setField(mojo, "packageVersion", "1.0");
            setField(mojo, "javaCardVersionStr", "3.0.5");
            setField(mojo, "supportInt32", false);
            setField(mojo, "generateExport", false);
            setField(mojo, "oracleCompatibility", false);

            MavenProject project = new MavenProject();
            project.setArtifactId("noexp-test");
            project.setVersion("1.0");
            setField(mojo, "project", project);

            mojo.execute();

            Path capPath = tempDir.resolve("outnoexp/noexp-test-1.0.cap");
            assertThat(capPath).exists();
        }
    }
}
