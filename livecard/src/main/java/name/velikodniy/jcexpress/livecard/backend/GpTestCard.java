package name.velikodniy.jcexpress.livecard.backend;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.InstallException;
import name.velikodniy.jcexpress.Mode;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.backend.AidScheme;
import name.velikodniy.jcexpress.backend.AppletDeclaration;
import name.velikodniy.jcexpress.backend.BuildDescriptor;
import name.velikodniy.jcexpress.backend.CardRequest;
import name.velikodniy.jcexpress.backend.TestCard;
import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.gp.GPException;
import name.velikodniy.jcexpress.livecard.AppletInstance;
import name.velikodniy.jcexpress.livecard.Deployment;
import name.velikodniy.jcexpress.livecard.LiveCard;
import name.velikodniy.jcexpress.livecard.LiveCardConfig;
import name.velikodniy.jcexpress.livecard.LiveCardException;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A {@link TestCard} on a GlobalPlatform card reached through the live-card harness: the simulated card or the
 * card in the reader. The first instance of a package converts the package with every applet of it
 * ({@link PackageLoad}: the declared ones, those of the build descriptor of the Maven plugin, and every other
 * applet class of the package, from every class path entry that holds it) and loads it; further instances of any
 * of these applets are installed from the loaded package; deleting an instance keeps the package; closing deletes
 * everything the run created and checks it with GET STATUS ({@link LiveCard#cleanup()}). Every command passes the
 * APDU guard. Instance AIDs must be under the run's prefix.
 *
 * <p>A package the Maven plugin built is converted with the settings of its {@link BuildDescriptor} (package
 * version, int support; the Java Card version is the run's, which defaults to the build's), under the run's AIDs;
 * the transcript names the AIDs of the build next to them, and a failed SELECT of an AID of the build says which
 * AID the run gave that applet. The history of the session labels the card content management ("install",
 * "load", "delete", the secure channel).</p>
 */
final class GpTestCard implements TestCard {

    /** The card does not have a package the CAP file imports, in the imported version or a newer minor one. */
    private static final int SW_IMPORTED_PACKAGE_NOT_FOUND = 0x6438;
    /** INSTALL error condition: incorrect parameters in the data field (GPCS v2.3.1 11.5.3.2). */
    private static final int SW_INCORRECT_PARAMETERS = 0x6A80;
    /**
     * How {@code GPSession} reports a refused INSTALL [for install] ({@code GPException} with the status word);
     * {@code FailingInstallOnEveryBackendTest} pins it.
     */
    private static final String INSTALL_REFUSED = "INSTALL [for install] failed";

    private final Mode mode;
    private final LiveCard live;
    private final Optional<Boolean> intSupport;
    private final Consumer<PackageLoad> beforeLoad;
    private final Map<String, Deployment> deployments = new HashMap<>();
    /** The applets each loaded package holds: module AIDs by class name. */
    private final Map<String, Map<String, AID>> modules = new HashMap<>();
    private final RunAids runAids = new RunAids();
    private final SmartCardSession session;

    /**
     * Creates the card.
     *
     * @param mode       the backend
     * @param live       the connected harness
     * @param intSupport whether packages are converted with int support, if the run says so (otherwise as the
     *                   build did, or without)
     * @param beforeLoad told about every package before it is loaded (the simulated card learns which classes
     *                   implement its applets and where they are)
     */
    GpTestCard(Mode mode, LiveCard live, Optional<Boolean> intSupport, Consumer<PackageLoad> beforeLoad) {
        this.mode = mode;
        this.live = live;
        this.intSupport = intSupport;
        this.beforeLoad = beforeLoad;
        this.session = new GpTestSession(live.session(), aid -> runAids.explain(aid, live.config().aidPrefix()));
    }

    /**
     * Writes the exchanges of a test class run into {@code <transcriptDir>/<test class>/card.txt}, like the
     * transcripts of the live suite (keys never appear in them).
     *
     * @param live    the connected harness
     * @param request the run
     */
    static void transcribe(LiveCard live, CardRequest request) {
        live.transcriptTo(Path.of(request.testClass().getName(), "card.txt"), request.testClass().getName());
    }

    /**
     * Returns the Java Card version the Maven plugin built the run's first built package for: the default
     * conversion target of the run.
     *
     * @param request the run
     * @return the version, e.g. {@code 3.0.5}, or empty if no declared applet comes from a package the plugin built
     */
    static Optional<String> buildVersion(CardRequest request) {
        return request.buildDescriptors().stream().findFirst().map(BuildDescriptor::javaCardVersion);
    }

    @Override
    public Mode mode() {
        return mode;
    }

    @Override
    public AidScheme aids() {
        return AidScheme.of(live.config().aidPrefix());
    }

    @Override
    public SmartCardSession session() {
        return session;
    }

    /**
     * {@inheritDoc}
     *
     * @throws LiveCardException if the instance AID is outside the run's prefix (nothing is sent), or the package
     *                           cannot be converted or uses other packages
     * @throws InstallException  if the card refuses INSTALL [for install], with the applet, the AID, the status word
     *                           and where the applet finds its install parameters
     */
    @Override
    public void install(AppletDeclaration applet, Collection<AppletDeclaration> declaredRun) {
        requireUnderPrefix(applet);
        live.note("install " + applet.appletClass().getName() + " as " + applet.instanceAid().toHex());
        InstanceRegistration.note(applet).ifPresent(live::note);
        try {
            createInstance(applet, declaredRun);
        } catch (GPException e) {
            if (e.statusWord() < 0 || !e.getMessage().startsWith(INSTALL_REFUSED)) {
                throw e;
            }
            String reason = String.format("the card refused INSTALL [for install] with SW %04X", e.statusWord())
                    + (e.statusWord() == SW_INCORRECT_PARAMETERS ? " (incorrect parameters in the data field, GPCS"
                    + " v2.3.1 11.5.3.2: the applet's install method failed or rejected its install parameters)" : "");
            throw new InstallException(applet.appletClass().getName(), applet.instanceAid(), applet.parameters(),
                    e.statusWord(), reason, e);
        }
        runAids.installed(applet);
    }

    private void createInstance(AppletDeclaration applet, Collection<AppletDeclaration> declaredRun) {
        AppletInstance instance = AppletInstance.of(applet.moduleAid()).as(applet.instanceAid())
                .withParameters(applet.parameters());
        String packageName = applet.packageName();
        Deployment deployment = deployments.get(packageName);
        if (deployment == null) {
            deployments.put(packageName, load(applet, declaredRun, instance));
            return;
        }
        Map<String, AID> loaded = modules.get(packageName);
        if (!loaded.containsValue(applet.moduleAid())) {
            throw new IllegalStateException(applet.appletClass().getName() + " cannot be installed: its package "
                    + packageName + " was loaded with the applets " + String.join(", ", loaded.keySet()) + " only."
                    + " The CAP file holds every applet of the package: those of the build descriptor of the Maven"
                    + " plugin where the package comes from a build with one (META-INF/javacard/" + packageName
                    + ".properties), and every concrete subclass of javacard.framework.Applet with a static"
                    + " install(byte[], short, byte) method elsewhere (JCVM 3.1 §6.6)");
        }
        live.install(deployment, instance);
    }

    /** Converts the package with every applet of it, loads it and creates the first instance. */
    private Deployment load(AppletDeclaration applet, Collection<AppletDeclaration> declaredRun,
                            AppletInstance instance) {
        try (PackageLoad load = PackageLoad.of(applet, declaredRun, aids(), intSupport)) {
            beforeLoad.accept(load);
            live.transcript().note("convert " + applet.packageName() + ": " + load.converted());
            load.build().ifPresent(build -> {
                runAids.built(build);
                describe(build, applet, load);
            });
            Deployment deployment = deploy(load, instance);
            modules.put(applet.packageName(), new LinkedHashMap<>(load.applets()));
            return deployment;
        }
    }

    private Deployment deploy(PackageLoad load, AppletInstance instance) {
        try {
            return live.deploy(load.pkg(), instance);
        } catch (LiveCardException e) {
            if (!(e.getCause() instanceof ConverterException)) {
                throw e;
            }
            throw new LiveCardException(e.getMessage() + "\n(the GlobalPlatform backends convert every applet of the"
                    + " package and the classes they use: " + load.converted() + ")", e);
        } catch (GPException e) {
            if (e.statusWord() != SW_IMPORTED_PACKAGE_NOT_FOUND) {
                throw e;
            }
            String version = LiveCardConfig.display(live.config().javaCardVersion());
            throw new IllegalStateException("The card refused to load " + load.pkg().packageName() + ", converted for"
                    + " Java Card " + version + ": it does not have a package the CAP file imports in that version (SW"
                    + " 6438). A card links a CAP file only when it has every imported API package with the same"
                    + " major and an equal or higher minor version (JCVM 3.1 §4.5.2): build for the card's Java Card"
                    + " version or an older one (<javaCardVersion> of javacard-express-maven-plugin, or the"
                    + " live-card setting javaCardVersion)", e);
        }
    }

    /** Notes in the transcript how the package is converted and which AIDs of the build it gets in this run. */
    private void describe(BuildDescriptor build, AppletDeclaration applet, PackageLoad load) {
        String version = LiveCardConfig.display(live.config().javaCardVersion());
        StringBuilder note = new StringBuilder("package ").append(build.packageName())
                .append(" built by ").append(build.project()).append(": converted for Java Card ").append(version)
                .append(version.equals(build.javaCardVersion()) ? " as the build" : " (the build: "
                        + build.javaCardVersion() + ")")
                .append(", package version ").append(build.packageVersion()).append("; AIDs of the build -> this run:"
                        + " package ").append(build.packageAid().toHex()).append(" -> ")
                .append(applet.packageAid().toHex());
        load.applets().forEach((className, moduleAid) -> {
            AID buildAid = build.applets().get(className);
            if (buildAid != null) {
                note.append(", ").append(className.substring(className.lastIndexOf('.') + 1)).append(' ')
                        .append(buildAid.toHex()).append(" -> ").append(moduleAid.toHex());
            }
        });
        live.transcript().note(note.toString());
    }

    /**
     * Refuses an instance AID outside the run's prefix before anything is sent: the harness creates, and deletes
     * as leftovers of earlier runs, only AIDs under the prefix.
     */
    private void requireUnderPrefix(AppletDeclaration applet) {
        String prefix = live.config().aidPrefix();
        String aid = applet.instanceAid().toHex();
        if (aid.startsWith(prefix)) {
            return;
        }
        throw new LiveCardException("Cannot install " + applet.appletClass().getName() + " as " + aid + ": " + aid
                + " is outside the run's AID prefix " + prefix + ". On the GlobalPlatform backends (simulated-gp,"
                + " livecard) tests create AIDs only under the prefix: the harness deletes what earlier runs left under"
                + " it and never touches anything else. Give the instance an AID under the prefix: card.aid(\"<suffix>\")"
                + " in card.install(...), or @InstallApplet(aid = \"<suffix>\"). The prefix is the setting"
                + " jcx.aidPrefix: a proprietary AID (first half byte F), or one under a registered RID that the"
                + " setting jcx.livecard.registeredRid names. An applet that needs a fixed AID outside such a prefix"
                + " runs on the embedded backend only: @EnabledOnBackend(Mode.EMBEDDED). Nothing was sent to the card");
    }

    @Override
    public void delete(AppletDeclaration applet) {
        APDUResponse response = live.delete(applet.instanceAid(), false);
        if (response.sw() != 0x9000) {
            throw new IllegalStateException(String.format("DELETE of %s answered %04X", applet, response.sw()));
        }
        runAids.deleted(applet);
    }

    @Override
    public void deselect() {
        live.session().select(AID.fromHex(live.config().isd()));
    }

    @Override
    public <T> Optional<T> resolve(Class<T> type) {
        return type == LiveCard.class ? Optional.of(type.cast(live)) : Optional.empty();
    }

    @Override
    public void close() {
        try {
            live.cleanup();
        } finally {
            live.close();
        }
    }
}
