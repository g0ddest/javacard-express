package name.velikodniy.jcexpress.livecard.backend;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUHistory;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SelectException;
import name.velikodniy.jcexpress.SmartCardSession;

import java.util.function.Function;

/**
 * The session of a {@link GpTestCard}: the guarded session of the live-card harness, whose failed SELECT also says
 * what the test run knows about the AID (its instances, the AIDs the Maven plugin's build gave its applets).
 * Everything else is the guarded session's.
 */
final class GpTestSession implements SmartCardSession {

    private final SmartCardSession session;
    private final Function<AID, String> selectContext;

    /**
     * Creates the session.
     *
     * @param session       the guarded session
     * @param selectContext what the run knows about an AID that could not be selected, appended to the message
     */
    GpTestSession(SmartCardSession session, Function<AID, String> selectContext) {
        this.session = session;
        this.selectContext = selectContext;
    }

    /**
     * {@inheritDoc}
     *
     * @throws SelectException if the card does not select the AID; the message adds the run's instances and, for an
     *                         AID of the build, the run's AID of that applet
     */
    @Override
    public void select(AID aid) {
        try {
            session.select(aid);
        } catch (SelectException e) {
            SelectException explained = new SelectException(aid, e.sw(), e.getMessage() + selectContext.apply(aid));
            explained.setStackTrace(e.getStackTrace());
            throw explained;
        }
    }

    @Override
    public void select(Class<? extends Applet> appletClass) {
        session.select(appletClass);
    }

    @Override
    public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
        return session.send(cla, ins, p1, p2, data, le);
    }

    @Override
    public byte[] transmit(byte[] rawApdu) {
        return session.transmit(rawApdu);
    }

    @Override
    public void reset() {
        session.reset();
    }

    @Override
    public APDUHistory history() {
        return session.history();
    }

    @Override
    public void install(Class<? extends Applet> appletClass) {
        session.install(appletClass);
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid) {
        session.install(appletClass, aid);
    }

    @Override
    public void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {
        session.install(appletClass, aid, installParams);
    }

    /** Does nothing: the card closes the guarded session ({@link GpTestCard#close()}). */
    @Override
    public void close() {
        // closed with the card
    }
}
