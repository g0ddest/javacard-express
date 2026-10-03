package name.velikodniy.jcexpress.embedded;

import javacard.framework.AID;
import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISOException;
import javacard.framework.Shareable;
import javacardx.apdu.ExtendedLength;

import java.util.function.Consumer;

/**
 * The applet as jCardSim's runtime calls it in {@code transmitCommand}: every call goes to the applet, and an
 * exception other than {@code ISOException} that escapes its {@code process} or {@code select} is reported before
 * jCardSim catches it and answers '6F00' or '6999' (JCRE 3.0.5 chapter 3: the methods process and select).
 * {@link SessionRuntime} hands it out instead of the applet; an applet that implements {@link ExtendedLength} gets a
 * wrapper that does too, because jCardSim accepts extended length commands only for such applets.
 */
class ReportingApplet extends Applet {

    private final Applet applet;
    private final Consumer<AppletFailure> failures;

    private ReportingApplet(Applet applet, Consumer<AppletFailure> failures) {
        this.applet = applet;
        this.failures = failures;
    }

    /**
     * Wraps an applet.
     *
     * @param applet   the applet
     * @param failures receives what the applet throws
     * @return the wrapper
     */
    static ReportingApplet wrap(Applet applet, Consumer<AppletFailure> failures) {
        return applet instanceof ExtendedLength ? new Extended(applet, failures)
                : new ReportingApplet(applet, failures);
    }

    @Override
    public void process(APDU apdu) {
        try {
            applet.process(apdu);
        } catch (Throwable failure) {
            report(failure);
            throw failure;
        }
    }

    @Override
    public boolean select() {
        try {
            return applet.select();
        } catch (Throwable failure) {
            report(failure);
            throw failure;
        }
    }

    @Override
    public void deselect() {
        applet.deselect();
    }

    @Override
    public Shareable getShareableInterfaceObject(AID clientAID, byte parameter) {
        return applet.getShareableInterfaceObject(clientAID, parameter);
    }

    private void report(Throwable failure) {
        if (!(failure instanceof ISOException)) {
            failures.accept(new AppletFailure(failure, applet.getClass()));
        }
    }

    /** The wrapper of an applet that accepts extended length commands. */
    private static final class Extended extends ReportingApplet implements ExtendedLength {
        Extended(Applet applet, Consumer<AppletFailure> failures) {
            super(applet, failures);
        }
    }
}
