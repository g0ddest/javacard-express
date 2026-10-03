package name.velikodniy.jcexpress.livecard.thirdparty;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.livecard.AppletPackage;
import name.velikodniy.jcexpress.livecard.LiveCardConfig;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Real-world open-source applets the live suite runs as test subjects. Their sources stay upstream: git submodules
 * under {@code livecard/third_party} pin the commits, and nothing of them is copied into this repository. Before a
 * test deploys one, {@link #classes()} copies its sources to {@code target/third-party}, preprocesses PivApplet as
 * its build does ({@link Jpp}) and compiles them for Java 8 against the API stubs ({@link AppletCompiler}); the
 * deployment converts them with this project's converter like the test applets.
 *
 * <p>A test that needs an applet whose submodule is not initialized is aborted with the command that initializes
 * it ({@link #requireInitialized()}), so the build works without the submodules and without network access.</p>
 */
public enum ThirdPartyApplet {

    /** NIST SP 800-73-4 PIV card application by Alex Wilson (MPL-2.0); build defines of its build.xml. */
    PIV("PivApplet", "https://github.com/arekinath/PivApplet", "MPL-2.0", "5cb14a9e8d16e92fbad73dcad86a219a9210554f",
            "net.cooperi.pivapplet.PivApplet", "0122", "012201", Set.of("PIV_SUPPORT_RSA", "PIV_SUPPORT_EC",
            "PIV_SUPPORT_ECCP384", "PIV_SUPPORT_AES", "PIV_SUPPORT_3DES", "YKPIV_ATTESTATION", "APPLET_EXTLEN")),
    /** OpenPGP card 3.4 application by ANSSI (GPL-2.0-or-later). */
    SMART_PGP("SmartPGP", "https://github.com/ANSSI-FR/SmartPGP", "GPL-2.0-or-later",
            "da52ec4d6baa2b8f5bf8de35e7932572bb96b161", "fr.anssi.smartpgp.SmartPGPApplet", "0123", "012301", null);

    /** System property set by the build: the directory with the submodules ({@code livecard/third_party}). */
    public static final String ROOT_PROPERTY = "jcx.livecard.test.thirdParty";
    /** System property set by the build: the module's build directory ({@code livecard/target}). */
    public static final String BUILD_PROPERTY = "jcx.livecard.test.buildDirectory";
    private static final Map<ThirdPartyApplet, Path> BUILT = new EnumMap<>(ThirdPartyApplet.class);

    private final String directory;
    private final String url;
    private final String license;
    private final String pinnedCommit;
    private final String className;
    private final String packageSuffix;
    private final String moduleSuffix;
    private final Set<String> defines;

    ThirdPartyApplet(String directory, String url, String license, String pinnedCommit, String className,
                     String packageSuffix, String moduleSuffix, Set<String> defines) {
        this.directory = directory;
        this.url = url;
        this.license = license;
        this.pinnedCommit = pinnedCommit;
        this.className = className;
        this.packageSuffix = packageSuffix;
        this.moduleSuffix = moduleSuffix;
        this.defines = defines;
    }

    /**
     * Returns the command that initializes the submodule, to run in the project root.
     *
     * @return e.g. {@code git submodule update --init livecard/third_party/PivApplet}
     */
    public String initCommand() {
        return "git submodule update --init livecard/third_party/" + directory;
    }

    /**
     * Returns where the submodule is checked out.
     *
     * @return {@code livecard/third_party/<name>}
     */
    public Path submodule() {
        return root().resolve(directory);
    }

    /**
     * Returns whether the submodule is initialized (its sources are there).
     *
     * @return true if the applet's main source file exists
     */
    public boolean initialized() {
        return initializedIn(root());
    }

    /**
     * Aborts the calling test (JUnit assumption) unless the submodule is initialized; the message is the command
     * that initializes it.
     */
    public void requireInitialized() {
        requireInitializedIn(root());
    }

    /**
     * Aborts the calling test (JUnit assumption) unless the submodule is at the pinned commit without local
     * changes: only the pinned upstream code was validated, and a case loads it onto a card (an untracked source
     * file would be compiled and loaded too).
     */
    public void requirePinnedCommit() {
        requirePinnedCommitIn(root());
    }

    void requirePinnedCommitIn(Path root) {
        Path repository = root.resolve(directory);
        String head = git(repository, "rev-parse", "HEAD").map(String::strip).orElse("an unknown commit");
        Assumptions.assumeTrue(head.equals(pinnedCommit), () -> directory + " is at " + head + ", not at the pinned"
                + " commit " + pinnedCommit + "; to run this test, run in the project root: git submodule update "
                + "livecard/third_party/" + directory);
        List<String> changes = localChanges(repository);
        Assumptions.assumeTrue(changes.isEmpty(), () -> directory + " has local changes " + changes + "; only the"
                + " pinned upstream code is loaded onto a card (git -C livecard/third_party/" + directory
                + " status)");
    }

    /**
     * Returns the local changes of a git working tree ({@code git status --porcelain}, untracked files included).
     *
     * @param repository the working tree
     * @return one line per changed file; a single explanatory line if git cannot tell
     */
    static List<String> localChanges(Path repository) {
        return git(repository, "status", "--porcelain").map(out -> out.lines().filter(line -> !line.isBlank()).toList())
                .orElse(List.of("(git status failed in " + repository + ")"));
    }

    /**
     * Runs git in a directory.
     *
     * @param directory where git runs ({@code git -C})
     * @param arguments the git arguments
     * @return the standard output, or empty if git is not available or fails
     */
    static Optional<String> git(Path directory, String... arguments) {
        List<String> command = new ArrayList<>(List.of("git", "-C", directory.toString()));
        command.addAll(List.of(arguments));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
            process.getOutputStream().close();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.getErrorStream().readAllBytes();
            return process.waitFor() == 0 ? Optional.of(output) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * Returns the applet class.
     *
     * @return the fully qualified class name
     */
    public String className() {
        return className;
    }

    /**
     * Returns the package AID under the configured prefix.
     *
     * @param config the settings
     * @return the load file AID
     */
    public AID packageAid(LiveCardConfig config) {
        return config.aid(packageSuffix);
    }

    /**
     * Returns the applet (module) AID under the configured prefix.
     *
     * @param config the settings
     * @return the module AID, also the instance AID
     */
    public AID moduleAid(LiveCardConfig config) {
        return config.aid(moduleSuffix);
    }

    /**
     * Returns the package description for {@link name.velikodniy.jcexpress.livecard.LiveCard#deploy}, building the
     * classes first if this JVM has not built them yet.
     *
     * @param config the settings
     * @return the package with its applet
     */
    public AppletPackage pkg(LiveCardConfig config) {
        return AppletPackage.of(classes(), packageName(), packageAid(config)).withApplet(className, moduleAid(config));
    }

    /**
     * Returns a description for transcripts and reports: name, URL, the checked out and the pinned commit, license.
     *
     * @return the description
     */
    public String describe() {
        String checkedOut = checkedOutCommit().orElse("unknown commit");
        return directory + " (" + url + " @ " + checkedOut + (checkedOut.equals(pinnedCommit) ? "" : ", pinned "
                + pinnedCommit) + ", " + license + ")";
    }

    /**
     * Returns the compiled classes: on the first call in this JVM the sources are copied (PivApplet preprocessed)
     * and compiled into {@code target/third-party/<name>/classes}.
     *
     * @return the classes directory
     * @throws org.opentest4j.TestAbortedException if the submodule is not initialized
     */
    public Path classes() {
        synchronized (BUILT) {
            Path built = BUILT.get(this);
            if (built == null) {
                requireInitialized();
                built = build(submodule().resolve("src"),
                        Path.of(System.getProperty(BUILD_PROPERTY, "target"), "third-party", directory));
                BUILT.put(this, built);
            }
            return built;
        }
    }

    /**
     * Finds the third-party applet whose module AID (under any prefix) is the given AID.
     *
     * @param moduleAid the module AID, uppercase hex
     * @return the applet, or empty
     */
    public static Optional<ThirdPartyApplet> byModuleAid(String moduleAid) {
        return Arrays.stream(values()).filter(applet -> moduleAid.endsWith(applet.moduleSuffix)).findFirst();
    }

    /**
     * Returns the classes directories built in this JVM, for simulators that run the applets.
     *
     * @return the directories
     */
    public static List<Path> builtClasses() {
        synchronized (BUILT) {
            return List.copyOf(BUILT.values());
        }
    }

    boolean initializedIn(Path root) {
        return Files.isRegularFile(root.resolve(directory).resolve("src")
                .resolve(className.replace('.', '/') + ".java"));
    }

    void requireInitializedIn(Path root) {
        Assumptions.assumeTrue(initializedIn(root), () -> directory + " (" + url + ", " + license + ") is a git"
                + " submodule that is not initialized here; to run this test, run in the project root: "
                + initCommand());
    }

    private String packageName() {
        return className.substring(0, className.lastIndexOf('.'));
    }

    private Path build(Path upstream, Path work) {
        Path sources = work.resolve("src");
        Path classes = work.resolve("classes");
        try {
            AppletCompiler.deleteTree(work);
            if (defines != null) {
                Jpp.processTree(upstream, sources, defines);
            } else {
                AppletCompiler.copyTree(upstream, sources);
            }
            AppletCompiler.compile(sources, classes);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot build " + directory + " in " + work, e);
        }
        return classes;
    }

    /** The commit checked out in the submodule (detached HEAD after {@code git submodule update}). */
    private Optional<String> checkedOutCommit() {
        try {
            Path dotGit = submodule().resolve(".git");
            Path gitDir = Files.isDirectory(dotGit) ? dotGit : dotGit.getParent()
                    .resolve(Files.readString(dotGit).strip().substring("gitdir:".length()).strip()).normalize();
            return Optional.of(Files.readString(gitDir.resolve("HEAD")).strip());
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    private static Path root() {
        return Path.of(System.getProperty(ROOT_PROPERTY, "third_party"));
    }
}
