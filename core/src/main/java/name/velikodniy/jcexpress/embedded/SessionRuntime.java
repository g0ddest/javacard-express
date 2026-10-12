package name.velikodniy.jcexpress.embedded;

import com.licel.jcardsim.base.LoadFile;
import com.licel.jcardsim.base.Module;
import com.licel.jcardsim.base.SimulatorRuntime;
import com.licel.jcardsim.utils.AIDUtil;
import javacard.framework.AID;
import javacard.framework.Applet;
import javacard.framework.ISOException;
import javacard.framework.SystemException;
import name.velikodniy.jcexpress.embedded.gp.CardApplication;
import name.velikodniy.jcexpress.embedded.gp.GlobalPlatformCard;
import name.velikodniy.jcexpress.embedded.gp.GlobalPlatformHost;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The jCardSim runtime of one {@link EmbeddedSession}, with deletion that behaves like on a card and a report of
 * what the applets throw.
 *
 * <p>For every applet installed from a class, jCardSim creates a load file whose AID it numbers by the count of
 * the load files it created, and keeps it when the applet is deleted. So the AID of a deleted applet could not
 * be installed again ({@code SystemException}), and after a deletion a new number could repeat an existing one.
 * This runtime numbers load files without repetition and forgets the load file of a deleted applet. A deleted
 * applet that was selected is deselected first (jCardSim removes it before it checks the selection), and no
 * applet is selected afterwards.</p>
 *
 * <p>jCardSim answers an exception that escapes an applet's {@code process} with '6F00' and one of {@code select}
 * with '6999', and reports one of the install method as a bare {@code SystemException} (JCRE 3.0.5 chapter 3: a
 * card does not pass them on either). This runtime keeps the exception for the session to note
 * ({@link #takeFailure()}): {@code transmitCommand} gets the applet through {@link #getApplet(AID)}, which hands out
 * a {@link ReportingApplet}, and the install method is called here, with the behaviour of jCardSim's
 * {@code installApplet} otherwise.</p>
 *
 * <p>The card has a GlobalPlatform environment ({@link #globalPlatform()}), which the applets reach through
 * {@code org.globalplatform.GPSystem}. This runtime tells it when an install method starts and how it ends (the
 * application is registered in the state INSTALLED while the method runs and SELECTABLE afterwards, as after
 * INSTALL [for install and make selectable], GPCS v2.3.1 11.5.2.1), when an applet is deleted, when an applet is
 * deselected (which jCardSim also does before it selects one) and when the card is reset.</p>
 */
final class SessionRuntime extends SimulatorRuntime implements GlobalPlatformHost {

    private final Map<Applet, ReportingApplet> reporting = new IdentityHashMap<>();
    private final GlobalPlatformCard globalPlatform = GlobalPlatformCard.withTestKeys();
    private int nextLoadFile;
    private AppletFailure failure;
    /** The AID under which the application being installed is in the GlobalPlatform Registry. */
    private name.velikodniy.jcexpress.AID installing;

    @Override
    public GlobalPlatformCard globalPlatform() {
        return globalPlatform;
    }

    @Override
    public void loadApplet(AID aid, Class<? extends Applet> appletClass) {
        if (generatedLoadFileAIDs.containsKey(aid)) {
            SystemException.throwIt(SystemException.ILLEGAL_AID);
        }
        byte[] number = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) (nextLoadFile >> 8), (byte) nextLoadFile};
        nextLoadFile = (nextLoadFile + 1) & 0xFFFF;
        AID loadFile = AIDUtil.create(number);
        generatedLoadFileAIDs.put(aid, loadFile);
        loadLoadFile(new LoadFile(loadFile, loadFile, appletClass));
    }

    /**
     * Installs like jCardSim (the install method must call {@code register} once; an {@code ISOException} of it is
     * passed on, any other failure becomes {@code SystemException.ILLEGAL_AID}), and keeps what the install method
     * threw for {@link #takeFailure()}. The GlobalPlatform Registry has the application from the start of the
     * install method, with the privileges of the install parameters, and loses it again when the method fails.
     */
    @Override
    public void installApplet(AID loadFileAID, AID moduleAID, AID appletAID, byte[] bArray, short bOffset,
                              byte bLength) {
        activateSimulatorRuntimeInstance();
        Class<? extends Applet> appletClass = appletClass(loadFileAID, moduleAID);
        Method install = installMethod(appletClass);
        byte[] privileges = privileges(bArray, bOffset, bLength);
        name.velikodniy.jcexpress.AID application = GlobalPlatformCard.aidOf(appletAID);
        globalPlatform.installStarted(application, privileges);
        installing = application;
        boolean installed = false;
        try {
            invokeInstall(install, appletClass, appletAID, privileges, bArray, bOffset, bLength);
            installed = true;
        } finally {
            if (installing != null && installed) {
                globalPlatform.installSucceeded(installing, CardApplication.SELECTABLE);
            } else if (installing != null) {
                globalPlatform.installFailed(installing);
            }
            installing = null;
        }
    }

    private static Method installMethod(Class<? extends Applet> appletClass) {
        try {
            return appletClass.getMethod("install", byte[].class, short.class, byte.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class does not provide install method");
        }
    }

    private void invokeInstall(Method install, Class<? extends Applet> appletClass, AID appletAID, byte[] privileges,
                               byte[] bArray, short bOffset, byte bLength) {
        AtomicInteger registrations = new AtomicInteger();
        registrationCallback.set((applet, aid) -> register(applet, aid == null ? appletAID : aid, registrations,
                privileges));
        try {
            install.invoke(null, bArray, bOffset, bLength);
        } catch (InvocationTargetException e) {
            throw installFailure(e.getCause(), appletClass);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new SystemException(SystemException.ILLEGAL_AID);
        } finally {
            registrationCallback.set(null);
        }
        if (registrations.get() != 1) {
            throw new SystemException(SystemException.ILLEGAL_AID);
        }
    }

    /**
     * The Privileges of the install parameters, {@code [Li][instance AID][Lp][Privileges][La][...]} (GPCS v2.3.1
     * Annex A.1, Installation): one or three bytes; none when the field is empty or the parameters are not coded
     * this way.
     */
    private static byte[] privileges(byte[] bArray, short bOffset, byte bLength) {
        int end = bOffset + (bLength & 0xFF);
        int field = bOffset + 1 + (bOffset < bArray.length ? bArray[bOffset] & 0xFF : 0);
        if (field >= end || end > bArray.length) {
            return new byte[0];
        }
        int length = bArray[field] & 0xFF;
        boolean coded = (length == 1 || length == 3) && field + 1 + length <= end;
        return coded ? Arrays.copyOfRange(bArray, field + 1, field + 1 + length) : new byte[0];
    }

    private Class<? extends Applet> appletClass(AID loadFileAID, AID moduleAID) {
        LoadFile loadFile = loadFiles.get(loadFileAID);
        if (loadFile == null) {
            throw new IllegalArgumentException("LoadFile AID not found " + AIDUtil.toString(loadFileAID));
        }
        Module module = loadFile.getModule(moduleAID);
        if (module == null) {
            throw new IllegalArgumentException("Module AID not found " + AIDUtil.toString(moduleAID));
        }
        return module.getAppletClass();
    }

    /**
     * Registers an applet once per install method (Java Card API {@code Applet.register}). An applet that
     * registers under another AID than the one being installed is in the GlobalPlatform Registry under that AID.
     */
    private void register(Applet applet, AID aid, AtomicInteger registrations, byte[] privileges) {
        if (registrations.incrementAndGet() != 1) {
            throw new SystemException(SystemException.ILLEGAL_AID);
        }
        name.velikodniy.jcexpress.AID registered = GlobalPlatformCard.aidOf(aid);
        if (!registered.equals(installing)) {
            globalPlatform.installFailed(installing);
            installing = null;
            globalPlatform.installStarted(registered, privileges);
            installing = registered;
        }
        applets.put(aid, new ApplicationInstance(aid, applet));
    }

    /** jCardSim's answer to a failed install method, with what the method threw as its cause. */
    private RuntimeException installFailure(Throwable cause, Class<? extends Applet> appletClass) {
        if (cause instanceof ISOException isoException) {
            return isoException;
        }
        SystemException reported = new SystemException(SystemException.ILLEGAL_AID);
        if (cause != null) {
            failure = new AppletFailure(cause, appletClass);
            reported.initCause(cause);
        }
        return reported;
    }

    /** Hands out the applet wrapped, so that what it throws in {@code transmitCommand} is reported. */
    @Override
    protected Applet getApplet(AID aid) {
        Applet applet = super.getApplet(aid);
        return applet == null ? null
                : reporting.computeIfAbsent(applet, real -> ReportingApplet.wrap(real, this::failed));
    }

    /** Compares with the applet itself: {@code selectingApplet()} of the applet asks with {@code this}. */
    @Override
    public boolean isAppletSelecting(Object aThis) {
        return selecting && aThis != null && aThis == super.getApplet(getAID());
    }

    private void failed(AppletFailure appletFailure) {
        failure = appletFailure;
    }

    /**
     * Returns and forgets what an applet threw since the last call.
     *
     * @return the failure, or empty
     */
    Optional<AppletFailure> takeFailure() {
        Optional<AppletFailure> taken = Optional.ofNullable(failure);
        failure = null;
        return taken;
    }

    /**
     * Deselects the selected applet as selecting another application does (its {@code deselect()} runs, an open
     * transaction is aborted, CLEAR_ON_DESELECT memory is cleared); no applet is selected afterwards.
     *
     * @return the AID of the applet that was selected, or null when none was
     */
    AID deselectCurrent() {
        AID current = currentAID;
        if (current != null) {
            deselect(lookupApplet(current));
            currentAID = null;
        }
        return current;
    }

    @Override
    protected void deleteApplet(AID aid) {
        if (currentAID != null && AIDUtil.comparator().compare(currentAID, aid) == 0) {
            deselect(lookupApplet(aid));
            currentAID = null;
        }
        ApplicationInstance instance = lookupApplet(aid);
        if (instance != null) {
            reporting.remove(instance.getApplet());
        }
        super.deleteApplet(aid);
        forgetLoadFile(aid);
        globalPlatform.unregister(GlobalPlatformCard.aidOf(aid));
    }

    /**
     * Deselects like jCardSim, which calls this also before it selects an applet; the Application Session ends
     * here, and with it a secure channel session (GPCS v2.3.1 10.2.3), after the applet's {@code deselect()} ran.
     */
    @Override
    protected void deselect(ApplicationInstance applicationInstance) {
        try {
            super.deselect(applicationInstance);
        } finally {
            globalPlatform.applicationSessionEnded();
        }
    }

    /** Resets like jCardSim and reports the card reset, which ends a secure channel session (GPCS v2.3.1 10.2.3). */
    @Override
    public void reset() {
        super.reset();
        globalPlatform.cardReset();
    }

    /**
     * Forgets the load file created for an applet AID, so that the AID can be installed again.
     *
     * @param aid the applet AID
     */
    void forgetLoadFile(AID aid) {
        AID loadFile = generatedLoadFileAIDs.remove(aid);
        if (loadFile != null) {
            loadFiles.remove(loadFile);
        }
    }
}
