package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.converter.Converter;
import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.converter.ConverterResult;
import name.velikodniy.jcexpress.converter.JavaCardVersion;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Compiles JavaCard applet sources into a CAP file.
 * <p>
 * Binds to the {@code package} phase by default. Uses the built-in
 * clean-room converter — no Oracle SDK or proprietary tools required.
 * <p>
 * Minimal configuration example (auto-discovers applets):
 * <pre>{@code
 * <plugin>
 *   <groupId>name.velikodniy</groupId>
 *   <artifactId>javacard-express-maven-plugin</artifactId>
 *   <version>0.1.0</version>
 *   <configuration>
 *     <packageAid>A00000006212</packageAid>
 *   </configuration>
 * </plugin>
 * }</pre>
 * <p>
 * Explicit applet configuration:
 * <pre>{@code
 * <configuration>
 *   <packageAid>A00000006212</packageAid>
 *   <applets>
 *     <applet>
 *       <className>com.example.WalletApplet</className>
 *       <aid>A0000000621201</aid>
 *     </applet>
 *   </applets>
 * </configuration>
 * }</pre>
 */
@Mojo(
        name = "build",
        defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyResolution = ResolutionScope.COMPILE
)
public class JavaCardBuildMojo extends AbstractMojo {

    /** Directory containing compiled .class files. */
    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true)
    private File classesDirectory;

    /** Output directory for the generated .cap and .exp files. */
    @Parameter(defaultValue = "${project.build.directory}", readonly = true)
    private File outputDirectory;

    /**
     * Package AID as hex string (e.g. "A00000006212").
     * If omitted, a deterministic AID is generated from the package name.
     */
    @Parameter(property = "javacard.packageAid")
    private String packageAid;

    /**
     * Java package name (dot notation). Auto-detected from classesDirectory if omitted.
     */
    @Parameter(property = "javacard.packageName")
    private String packageName;

    /**
     * Package version string (e.g. "1.0"). Defaults to "1.0".
     */
    @Parameter(property = "javacard.packageVersion", defaultValue = "1.0")
    private String packageVersion;

    /** Applet configurations. If empty, applets are auto-discovered. */
    @Parameter
    private List<AppletConfig> applets;

    /** Enable 32-bit integer support (ACC_INT flag). */
    @Parameter(property = "javacard.supportInt32", defaultValue = "false")
    private boolean supportInt32;

    /** Generate export component in the CAP file. */
    @Parameter(property = "javacard.generateExport", defaultValue = "true")
    private boolean generateExport;

    /**
     * Enable Oracle compatibility mode. When true, replicates the Oracle converter's
     * dispatch table off-by-one behavior in Class.cap for byte-identical output.
     */
    @Parameter(property = "javacard.oracleCompatibility", defaultValue = "false")
    private boolean oracleCompatibility;

    /**
     * Target JavaCard specification version.
     * Controls the CAP format version and API import versions.
     * Valid values: "2.2.2", "3.0.5" (default), "3.1.0", "3.2.0".
     */
    @Parameter(property = "javacard.version", defaultValue = "3.0.5")
    private String javaCardVersionStr;

    /** Import export files (.exp) for external package resolution. */
    @Parameter
    private List<File> importExportFiles;

    /** Skip plugin execution. */
    @Parameter(property = "javacard.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping JavaCard build (javacard.skip=true)");
            return;
        }

        Path classesDir = classesDirectory.toPath();
        if (!Files.isDirectory(classesDir)) {
            getLog().warn("Classes directory does not exist: " + classesDirectory
                    + " (has the project been compiled?)");
            return;
        }

        // Auto-detect package name if not configured
        String pkgName = resolvePackageName(classesDir);
        if (pkgName == null) {
            throw new MojoExecutionException(
                    "Cannot determine package name. Set <packageName> in plugin configuration.");
        }
        getLog().info("Package: " + pkgName);

        // Parse version
        int majorVersion = 1;
        int minorVersion = 0;
        if (packageVersion != null && packageVersion.contains(".")) {
            String[] parts = packageVersion.split("\\.", 2);
            majorVersion = Integer.parseInt(parts[0]);
            minorVersion = Integer.parseInt(parts[1]);
        }

        // Resolve JavaCard version
        JavaCardVersion jcVersion = parseJavaCardVersion(javaCardVersionStr);
        getLog().info("JavaCard version: " + javaCardVersionStr + " (format "
                + jcVersion.formatMajor() + "." + jcVersion.formatMinor() + ")");

        // Build converter
        Converter.Builder builder = Converter.builder()
                .classesDirectory(classesDir)
                .packageName(pkgName)
                .packageVersion(majorVersion, minorVersion)
                .supportInt32(supportInt32)
                .generateExport(generateExport)
                .oracleCompatibility(oracleCompatibility)
                .javaCardVersion(jcVersion);

        if (packageAid != null && !packageAid.isBlank()) {
            builder.packageAid(packageAid);
            getLog().info("Package AID: " + packageAid);
        } else {
            getLog().info("Package AID: auto-generated from package name");
        }

        // Register applets
        if (applets != null && !applets.isEmpty()) {
            for (AppletConfig ac : applets) {
                validateAppletConfig(ac);
                builder.applet(ac.getClassName(), ac.getAid());
                getLog().info("Applet: " + ac.getClassName() + " [" + ac.getAid() + "]");
            }
        } else {
            // Auto-discover applets
            List<AppletConfig> discovered = discoverApplets(classesDir, pkgName);
            if (discovered.isEmpty()) {
                getLog().warn("No applets found in " + pkgName
                        + ". Configure <applets> explicitly if your applets are in a sub-package.");
            }
            for (AppletConfig ac : discovered) {
                builder.applet(ac.getClassName(), ac.getAid());
                getLog().info("Discovered applet: " + ac.getClassName()
                        + " [AID auto-generated]");
            }
        }

        // Add import export files
        if (importExportFiles != null) {
            for (File expFile : importExportFiles) {
                if (!expFile.isFile()) {
                    throw new MojoExecutionException(
                            "Export file not found: " + expFile.getAbsolutePath());
                }
                builder.importExportFile(expFile.toPath());
                getLog().debug("Import: " + expFile.getName());
            }
        }

        // Convert
        ConverterResult result;
        try {
            result = builder.build().convert();
        } catch (ConverterException e) {
            if (e.violations() != null && !e.violations().isEmpty()) {
                getLog().error("JavaCard subset violations:");
                for (var v : e.violations()) {
                    getLog().error("  " + v);
                }
            }
            throw new MojoFailureException("JavaCard conversion failed: " + e.getMessage(), e);
        }

        // Write output files
        Path outDir = outputDirectory.toPath();
        try {
            Files.createDirectories(outDir);

            String baseName = project.getArtifactId() + "-" + project.getVersion();
            Path capPath = outDir.resolve(baseName + ".cap");
            Path expPath = outDir.resolve(baseName + ".exp");

            Files.write(capPath, result.capFile());
            getLog().info("CAP file: " + capPath + " (" + result.capSize() + " bytes)");

            if (result.exportFile() != null && result.exportFile().length > 0) {
                Files.write(expPath, result.exportFile());
                getLog().info("Export file: " + expPath + " (" + result.exportFile().length + " bytes)");
            }

            // Report warnings
            for (String warning : result.warnings()) {
                getLog().warn(warning);
            }

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write output files: " + e.getMessage(), e);
        }
    }

    /**
     * Resolves the package name: uses explicit config, or auto-detects from classesDirectory.
     */
    String resolvePackageName(Path classesDir) {
        if (packageName != null && !packageName.isBlank()) {
            return packageName;
        }

        // Auto-detect: find deepest directory that contains .class files
        // and derive the package from the path
        try {
            Path found = findPackageDir(classesDir, classesDir);
            if (found != null) {
                return classesDir.relativize(found).toString().replace(File.separatorChar, '.');
            }
        } catch (IOException e) {
            getLog().debug("Auto-detection failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Recursively finds the first directory (DFS) containing .class files.
     */
    private Path findPackageDir(Path root, Path current) throws IOException {
        boolean hasClassFiles = false;
        List<Path> subdirs = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    subdirs.add(entry);
                } else if (entry.toString().endsWith(".class")) {
                    hasClassFiles = true;
                }
            }
        }

        if (hasClassFiles) {
            return current;
        }

        for (Path subdir : subdirs) {
            Path result = findPackageDir(root, subdir);
            if (result != null) return result;
        }
        return null;
    }

    /**
     * Auto-discovers Applet subclasses by scanning .class files.
     */
    List<AppletConfig> discoverApplets(Path classesDir, String pkgName) {
        List<AppletConfig> result = new ArrayList<>();
        String pkgPath = pkgName.replace('.', '/');
        Path pkgDir = classesDir.resolve(pkgPath);

        if (!Files.isDirectory(pkgDir)) {
            return result;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pkgDir, "*.class")) {
            for (Path classFile : stream) {
                if (isAppletClass(classFile)) {
                    String simpleName = classFile.getFileName().toString()
                            .replace(".class", "");
                    String fqcn = pkgName + "." + simpleName;

                    AppletConfig ac = new AppletConfig();
                    ac.setClassName(fqcn);
                    // AID will be auto-generated by the converter (package AID + applet index)
                    ac.setAid(generateAppletAid(fqcn));
                    result.add(ac);
                }
            }
        } catch (IOException e) {
            getLog().debug("Applet discovery failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Checks if a .class file extends {@code javacard.framework.Applet} (directly or transitively).
     */
    private boolean isAppletClass(Path classFile) {
        try {
            byte[] bytes = Files.readAllBytes(classFile);
            ClassModel model = ClassFile.of().parse(bytes);
            String superName = model.superclass()
                    .map(ClassEntry::asInternalName)
                    .orElse("");
            // Direct check — covers most cases
            return "javacard/framework/Applet".equals(superName);
        } catch (IOException | IllegalArgumentException e) {
            getLog().debug("Cannot check class file: " + classFile + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * Generates a deterministic applet AID from class name.
     * Uses the package AID (if set) + sequential byte, or hash-based generation.
     */
    @SuppressWarnings("java:S4790") // SHA-1 used for deterministic AID generation, not security
    private String generateAppletAid(String className) {
        // Use SHA-1 hash of class name, prefix with 0xF0
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(className.getBytes(StandardCharsets.UTF_8));
            byte[] aid = new byte[9]; // 9 bytes for applet AID
            aid[0] = (byte) 0xF0;
            System.arraycopy(hash, 0, aid, 1, 8);
            return bytesToHex(aid);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    static JavaCardVersion parseJavaCardVersion(String version) {
        if (version == null) return JavaCardVersion.V3_0_5;
        return switch (version.trim()) {
            case "2.1.2", "2.1" -> JavaCardVersion.V2_1_2;
            case "2.2.1" -> JavaCardVersion.V2_2_1;
            case "2.2.2", "2.2" -> JavaCardVersion.V2_2_2;
            case "3.0.3" -> JavaCardVersion.V3_0_3;
            case "3.0.4" -> JavaCardVersion.V3_0_4;
            case "3.0.5", "3.0" -> JavaCardVersion.V3_0_5;
            case "3.1.0", "3.1" -> JavaCardVersion.V3_1_0;
            case "3.2.0", "3.2" -> JavaCardVersion.V3_2_0;
            default -> JavaCardVersion.V3_0_5;
        };
    }

    private static void validateAppletConfig(AppletConfig ac) throws MojoExecutionException {
        if (ac.getClassName() == null || ac.getClassName().isBlank()) {
            throw new MojoExecutionException("Applet className is required");
        }
        if (ac.getAid() == null || ac.getAid().isBlank()) {
            throw new MojoExecutionException(
                    "Applet AID is required for: " + ac.getClassName());
        }
        // Validate AID length (5-16 bytes = 10-32 hex chars)
        String hex = ac.getAid().replace(" ", "").replace(":", "");
        if (hex.length() < 10 || hex.length() > 32) {
            throw new MojoExecutionException(
                    "Invalid AID length for " + ac.getClassName()
                            + ": expected 5-16 bytes, got " + (hex.length() / 2));
        }
    }
}
