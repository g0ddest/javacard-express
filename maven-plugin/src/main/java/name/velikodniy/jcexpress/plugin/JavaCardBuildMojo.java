package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.converter.Converter;
import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.converter.ConverterResult;
import name.velikodniy.jcexpress.converter.JavaCardVersion;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Converts the compiled classes of one Java package into a CAP file, and into an export file
 * when the package is exported (JCVM 3.1 chapters 4 to 6).
 * <p>
 * The goal runs in the {@code process-classes} phase once it is bound with an {@code <execution>}
 * (a default phase alone does not make Maven run a goal): code outside the Java Card subset fails
 * the build before the tests run, and the CAP file, the export file and the build descriptor are
 * in the classes directory when the tests and the other modules of a reactor need them
 * ({@code classesOutput}). Uses the built-in clean-room converter — no Oracle SDK or proprietary
 * tools required. Maven must run on JDK 25+.
 * <p>
 * Before converting, the goal selects the package (one per CAP file), finds or checks the
 * applets, assigns AIDs that keep the package RID, decides about the export, and collects the
 * export files of imported packages; problems are reported with the source lines they come from.
 * <p>
 * Minimal configuration example (auto-discovers applets):
 * <pre>{@code
 * <plugin>
 *   <groupId>name.velikodniy</groupId>
 *   <artifactId>javacard-express-maven-plugin</artifactId>
 *   <version>${jcexpress.version}</version>
 *   <executions>
 *     <execution>
 *       <goals>
 *         <goal>build</goal>
 *       </goals>
 *     </execution>
 *   </executions>
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
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true
)
public class JavaCardBuildMojo extends AbstractMojo {

    private final MavenProjectHelper projectHelper;

    /** Directory containing the compiled .class files to convert. */
    @Parameter(property = "javacard.classesDirectory", defaultValue = "${project.build.outputDirectory}")
    private File classesDirectory;

    /** Output directory for the generated .cap and .exp files. */
    @Parameter(property = "javacard.outputDirectory", defaultValue = "${project.build.directory}")
    private File outputDirectory;

    /** Base name of the generated files: {@code <finalName>[-<classifier>].cap} and {@code .exp}. */
    @Parameter(property = "javacard.finalName", defaultValue = "${project.build.finalName}")
    private String finalName;

    /**
     * Classifier of the generated files, both in their names and as attached artifacts. Needed
     * when one module builds several CAP files (one plugin execution per package).
     */
    @Parameter(property = "javacard.classifier")
    private String classifier;

    /**
     * Attach the CAP file (type {@code cap}) and the export file (type {@code exp}) to the project,
     * so that {@code install}/{@code deploy} publish them and other modules can depend on them.
     */
    @Parameter(property = "javacard.attach", defaultValue = "true")
    private boolean attach;

    /**
     * Package AID as hex string (e.g. "A00000006212"), 5 to 16 bytes (JCVM 3.1 &sect;4.2.1). If
     * omitted, a development AID {@code F0 || SHA-1(package name)[0..6]} is used (with a warning).
     * Applet AIDs without a configured {@code <aid>} are this AID followed by the applet's 1-based
     * position, so they keep the package RID (&sect;4.2.2.2, &sect;6.6).
     */
    @Parameter(property = "javacard.packageAid")
    private String packageAid;

    /**
     * The Java package to convert (dot notation). A CAP file holds one package (JCVM 3.1
     * &sect;4.1.2). May be omitted when all classes are in one package; with classes in several
     * packages the build fails unless this names one of them.
     */
    @Parameter(property = "javacard.packageName")
    private String packageName;

    /**
     * Package version {@code <major>.<minor>}, each number 0 to 255 (JCVM 3.1 &sect;4.5); written to
     * the Header component (&sect;6.4).
     */
    @Parameter(property = "javacard.packageVersion", defaultValue = "1.0")
    private String packageVersion;

    /**
     * The applets to register, each with {@code <className>} and an optional {@code <aid>}. If
     * omitted, every non-abstract direct or indirect subclass of {@code javacard.framework.Applet}
     * of the package that declares {@code static install(byte[], short, byte)} is registered
     * (JCVM 3.1 &sect;6.6). Configured classes that are not such applets fail the build.
     */
    @Parameter
    private List<AppletConfig> applets;

    /** Enable 32-bit integer support (ACC_INT flag of the Header component, JCVM 3.1 &sect;6.4). */
    @Parameter(property = "javacard.supportInt32", defaultValue = "false")
    private boolean supportInt32;

    /**
     * Whether to generate the Export component in the CAP file and write the export file
     * ({@code .exp}). When not configured, the package is exported if it has something to export
     * (JCVM 3.1 &sect;6.13): a library package exports its public classes and interfaces, an applet
     * package only its public shareable interfaces; an applet package without them is not exported.
     */
    @Parameter(property = "javacard.generateExport")
    private Boolean generateExport;

    /**
     * Former Oracle compatibility mode of the Class component.
     *
     * @deprecated The Class component is written as JCVM 3.1 &sect;6.9 specifies; the value is
     *             only passed on to the converter's deprecated option of the same name and the
     *             parameter will be removed.
     */
    @Deprecated
    @Parameter(property = "javacard.oracleCompatibility", defaultValue = "false")
    private boolean oracleCompatibility;

    /**
     * Target Java Card platform version. Controls the CAP format version in the Header
     * component (JCVM 3.1 &sect;6.4) and the versions of the imported API packages
     * (&sect;6.7). Valid values: 2.1.2, 2.2.1, 2.2.2, 3.0.3, 3.0.4, 3.0.5 (default), 3.1.0, 3.2.0;
     * any other value fails the build. The old element name {@code <javaCardVersionStr>} is
     * accepted as an alias.
     */
    @Parameter(property = "javacard.version", alias = "javaCardVersionStr", defaultValue = "3.0.5")
    private String javaCardVersion;

    /**
     * Export files ({@code .exp}) of imported packages, one by one. Export files are also taken
     * from dependencies of type {@code exp}, from {@code <exportPath>}, and from dependency jars
     * that contain them at {@code <package directory>/javacard/<name>.exp} (JCVM 3.1 &sect;5.2).
     */
    @Parameter
    private List<File> importExportFiles;

    /**
     * Directories and jars searched for the export files of imported packages, each at
     * {@code <package directory>/javacard/<last package name component>.exp} (JCVM 3.1 &sect;5.1,
     * &sect;5.2), e.g. the {@code api_export_files} directory of a Java Card development kit or a
     * directory of GlobalPlatform export files.
     */
    @Parameter
    private List<File> exportPath;

    /**
     * Also write the CAP file and the export file into the classes directory, at
     * {@code <package directory>/javacard/<name>.cap} and {@code .exp} (JCVM 3.1 &sect;5.2), with the build
     * descriptor {@code META-INF/javacard/<package>.properties} (package, AIDs, conversion settings,
     * {@code groupId:artifactId}). They go into the jar: modules and projects that import the package find its
     * export file on their class path (no dependency of type {@code exp}), and the card test backends of
     * {@code javacard-express-core} convert the package with the settings of this build.
     */
    @Parameter(property = "javacard.classesOutput", defaultValue = "true")
    private boolean classesOutput;

    /** Skip the goal. */
    @Parameter(property = "javacard.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Fail the build when the project's {@code javacard-express-api} or {@code javacard-express-core}
     * dependency has another version than this plugin: the converter in the plugin, the API stubs
     * javac compiles against and the toolkit the tests run on must come from one release.
     */
    @Parameter(property = "javacard.checkVersions", defaultValue = "true")
    private boolean checkVersions;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** How Maven started this execution (lifecycle or command line) and the plugin version. */
    @Parameter(defaultValue = "${mojoExecution}", readonly = true)
    private MojoExecution mojoExecution;

    /** The {@code -D} properties of the command line. */
    @Parameter(defaultValue = "${session.userProperties}", readonly = true)
    private Properties userProperties;

    /**
     * Creates the Mojo; Maven injects the project helper used to attach the generated files.
     *
     * @param projectHelper Maven's project helper
     */
    @Inject
    public JavaCardBuildMojo(MavenProjectHelper projectHelper) {
        this.projectHelper = projectHelper;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping JavaCard build (javacard.skip=true)");
            return;
        }
        if ("pom".equals(project.getPackaging())) {
            getLog().info("Skipping JavaCard build: " + project.getArtifactId() + " has packaging pom, no classes");
            return;
        }
        Invocation invocation = Invocation.of(mojoExecution);
        if (checkVersions) {
            ToolkitVersions.check(project.getDependencies(), invocation.pluginVersion());
        }
        Path classesDir = classesDirectory.toPath();
        if (!Files.isDirectory(classesDir)) {
            noClasses(invocation);
            return;
        }
        List<Path> classPath = compileClassPath();
        try (ClassIndex index = ClassIndex.scan(classesDir, classPath)) {
            build(classesDir, index, classPath, invocation);
        }
    }

    /**
     * Without compiled classes a bound execution only warns (a module may have no sources), but a goal
     * named on the command line has nothing to do: {@code mvn javacard-express:build} after
     * {@code mvn clean} reported success without a CAP file.
     */
    private void noClasses(Invocation invocation) throws MojoFailureException {
        if (invocation.commandLine()) {
            throw new MojoFailureException("javacard-express:build converts compiled classes, but " + classesDirectory
                    + " does not exist. Run mvn package (the goal runs in the process-classes phase when the POM binds"
                    + " it, as in the Quick Start), or compile first: mvn compile javacard-express:build");
        }
        getLog().warn("Classes directory does not exist: " + classesDirectory + " (has the project been compiled?)");
    }

    private void build(Path classesDir, ClassIndex index, List<Path> classPath, Invocation invocation)
            throws MojoExecutionException, MojoFailureException {
        String pkgName = new PackageSelection(index, classesDir).select(packageName, getLog());
        getLog().info("Package: " + pkgName);
        Converter.Builder builder = converter(classesDir, pkgName);
        Aid pkgAid = resolvePackageAid(pkgName);
        builder.packageAid(pkgAid.bytes());
        List<AppletAids.Assigned> assigned = registerApplets(builder, index, pkgName, pkgAid);
        boolean appletPackage = !assigned.isEmpty();
        boolean export = ExportPolicy.decide(generateExport, pkgName, appletPackage,
                exportableTypes(index, pkgName, appletPackage), getLog());
        builder.generateExport(export);
        BuildOutputs outputs = BuildOutputs.of(outputDirectory.toPath(), finalName, classifier);
        OutputClaims.claim(project, invocation, outputs.capFile(), attach, outputs.classifier());
        new ExportFileLookup(outputs.workDirectory(), getLog())
                .collect(importExportFiles, project.getArtifacts(), exportPath, classPath,
                        importedPackages(index, pkgName))
                .forEach(builder::importExportFile);
        ConverterResult result = convert(builder, index, pkgName);
        outputs.write(result, export, getLog());
        if (attach) {
            outputs.attach(project, projectHelper, export);
        }
        if (classesOutput) {
            writeIntoClasses(classesDir, invocation, result, descriptor(pkgName, pkgAid, export, assigned,
                    invocation));
        }
    }

    /** What the build descriptor in the classes directory says about this build. */
    private ClassesOutput.Descriptor descriptor(String pkgName, Aid pkgAid, boolean export,
                                                List<AppletAids.Assigned> assigned, Invocation invocation)
            throws MojoExecutionException {
        return new ClassesOutput.Descriptor(pkgName, pkgAid, PackageVersion.parse(packageVersion).toString(),
                JavaCardVersions.display(JavaCardVersions.parse(javaCardVersion)), supportInt32, export, assigned,
                project.getGroupId() + ":" + project.getArtifactId(), invocation.pluginVersion());
    }

    private void writeIntoClasses(Path classesDir, Invocation invocation, ConverterResult result,
                                  ClassesOutput.Descriptor descriptor) throws MojoExecutionException {
        Optional<String> writer = OutputClaims.classesWriter(project, invocation, descriptor.packageName());
        if (writer.isPresent()) {
            getLog().info("Classes directory: execution '" + writer.get() + "' already wrote the files of package "
                    + descriptor.packageName() + "; this execution leaves them");
            return;
        }
        new ClassesOutput(classesDir, descriptor).write(result, getLog());
    }

    /** The converter with the package and target platform settings. */
    @SuppressWarnings("deprecation") // oracleCompatibility is passed on while the parameter exists
    private Converter.Builder converter(Path classesDir, String pkgName) throws MojoExecutionException {
        PackageVersion version = PackageVersion.parse(packageVersion);
        JavaCardVersion jcVersion = JavaCardVersions.parse(javaCardVersion);
        getLog().info("Target: Java Card " + JavaCardVersions.display(jcVersion) + " (CAP format "
                + jcVersion.formatMajor() + "." + jcVersion.formatMinor() + ")");
        JavaCardVersions.reportIgnoredCommandLine(userProperties, javaCardVersion, jcVersion, getLog());
        Converter.Builder builder = Converter.builder()
                .classesDirectory(classesDir)
                .packageName(pkgName)
                .packageVersion(version.major(), version.minor())
                .supportInt32(supportInt32)
                .javaCardVersion(jcVersion);
        if (oracleCompatibility) {
            getLog().warn("<oracleCompatibility> is deprecated and will be removed: the Class component is"
                    + " written as JCVM 3.1 §6.9 specifies. Remove the parameter from the configuration.");
            builder.oracleCompatibility(true);
        }
        return builder;
    }

    /** Registers the applets with their AIDs; returns them (none for a library package). */
    private List<AppletAids.Assigned> registerApplets(Converter.Builder builder, ClassIndex index, String pkgName,
                                                      Aid pkgAid) throws MojoExecutionException {
        List<AppletAids.Assigned> assigned = AppletAids.assign(pkgAid, appletRequests(index, pkgName));
        for (AppletAids.Assigned applet : assigned) {
            builder.applet(applet.className(), applet.aid().bytes());
            getLog().info("Applet: " + applet.className() + " (AID " + applet.aid()
                    + (applet.derived() ? ", derived from the package AID)" : ")"));
        }
        if (applets == null || applets.isEmpty()) {
            AppletAids.orderWarning(assigned).ifPresent(getLog()::warn);
        }
        return assigned;
    }

    private ConverterResult convert(Converter.Builder builder, ClassIndex index, String pkgName)
            throws MojoFailureException {
        try {
            ConverterResult result = builder.build().convert();
            result.warnings().forEach(getLog()::warn);
            return result;
        } catch (ConverterException e) {
            List<Path> sourceRoots = project.getCompileSourceRoots().stream().map(Path::of).toList();
            throw new ConversionErrors(new SourceLocations(index, sourceRoots),
                    new PackageOrigins(index, project.getArtifacts()), pkgName, getLog()).report(e);
        }
    }

    /** Packages other than the converted one that its classes refer to (dot notation, sorted). */
    private static Set<String> importedPackages(ClassIndex index, String pkgName) {
        Set<String> packages = new TreeSet<>();
        index.classesOf(pkgName).forEach(c -> packages.addAll(c.referencedPackages()));
        packages.remove(pkgName);
        packages.remove("");
        return packages;
    }

    /**
     * Types the package can export (JCVM 3.1 &sect;6.13): public shareable interfaces of an applet
     * package, public classes and interfaces of a library package.
     */
    private static List<String> exportableTypes(ClassIndex index, String pkgName, boolean appletPackage) {
        return index.classesOf(pkgName).stream()
                .filter(ClassSummary::isPublic)
                .filter(c -> !c.isSynthetic())
                .filter(c -> !appletPackage || index.isShareableInterface(c))
                .map(ClassSummary::javaName)
                .toList();
    }

    private List<Path> compileClassPath() throws MojoExecutionException {
        try {
            Path own = classesDirectory.toPath().toAbsolutePath().normalize();
            return project.getCompileClasspathElements().stream()
                    .filter(Objects::nonNull)
                    .map(e -> Path.of(e).toAbsolutePath().normalize())
                    .filter(e -> !e.equals(own))
                    .toList();
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Cannot resolve the compile class path: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the configured package AID, or derives a development AID from the package name.
     */
    private Aid resolvePackageAid(String pkgName) throws MojoExecutionException {
        if (packageAid != null && !packageAid.isBlank()) {
            Aid aid = Aid.parse("packageAid", packageAid);
            getLog().info("Package AID: " + aid);
            return aid;
        }
        Aid aid = Aid.derivedFromPackageName(pkgName);
        getLog().warn("No <packageAid> configured: using the development AID " + aid + " derived from the"
                + " package name. AIDs starting with F are unregistered proprietary AIDs (ISO/IEC 7816-5);"
                + " configure <packageAid> with your registered RID for real cards.");
        return aid;
    }

    /**
     * Returns the applets to register: the configured ones (checked against the compiled classes),
     * or the discovered ones in class-name order.
     */
    private List<AppletAids.Request> appletRequests(ClassIndex index, String pkgName) throws MojoExecutionException {
        AppletClasses appletClasses = new AppletClasses(index, pkgName);
        if (applets == null || applets.isEmpty()) {
            List<String> discovered = appletClasses.discover(getLog());
            if (discovered.isEmpty()) {
                getLog().info("No applet classes in package " + pkgName + ": building a library package.");
            }
            return discovered.stream().map(name -> new AppletAids.Request(name, null)).toList();
        }
        List<AppletAids.Request> requests = new ArrayList<>();
        for (AppletConfig ac : applets) {
            if (ac.getClassName() == null || ac.getClassName().isBlank()) {
                throw new MojoExecutionException("Every <applet> in <applets> needs a <className>.");
            }
            requests.add(new AppletAids.Request(ac.getClassName().trim().replace('/', '.'), ac.getAid()));
        }
        List<String> classNames = requests.stream().map(AppletAids.Request::className).toList();
        appletClasses.check(classNames);
        appletClasses.reportUnlisted(classNames, getLog());
        return requests;
    }
}
