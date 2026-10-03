package name.velikodniy.jcexpress.embedded;

import com.licel.jcardsim.base.LoadFile;
import com.licel.jcardsim.base.Module;
import com.licel.jcardsim.base.SimulatorRuntime;
import com.licel.jcardsim.utils.AIDUtil;
import javacard.framework.AID;
import javacard.framework.Applet;
import javacard.framework.ISOException;
import javacard.framework.SystemException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
 */
final class SessionRuntime extends SimulatorRuntime {

    private final Map<Applet, ReportingApplet> reporting = new IdentityHashMap<>();
    private int nextLoadFile;
    private AppletFailure failure;

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
     * threw for {@link #takeFailure()}.
     */
    @Override
    public void installApplet(AID loadFileAID, AID moduleAID, AID appletAID, byte[] bArray, short bOffset,
                              byte bLength) {
        activateSimulatorRuntimeInstance();
        Class<? extends Applet> appletClass = appletClass(loadFileAID, moduleAID);
        Method install;
        try {
            install = appletClass.getMethod("install", byte[].class, short.class, byte.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class does not provide install method");
        }
        AtomicInteger registrations = new AtomicInteger();
        registrationCallback.set((applet, aid) -> register(applet, aid == null ? appletAID : aid, registrations));
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

    /** Registers an applet once per install method (Java Card API {@code Applet.register}). */
    private void register(Applet applet, AID aid, AtomicInteger registrations) {
        if (registrations.incrementAndGet() != 1) {
            throw new SystemException(SystemException.ILLEGAL_AID);
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
