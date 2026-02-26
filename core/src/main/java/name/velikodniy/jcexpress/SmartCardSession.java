package name.velikodniy.jcexpress;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.pin.PinSession;

/**
 * Main interface for interacting with a smart card (real or simulated).
 *
 * <p>Provides a unified API for installing applets, sending APDU commands,
 * and managing the card lifecycle. Implementations include embedded (jCardSim)
 * and container (Docker) backends.</p>
 */
public interface SmartCardSession extends AutoCloseable {

    // === Lifecycle ===

    /**
     * Installs and selects an applet. AID is generated automatically from the class name.
     *
     * @param appletClass the applet class to install
     */
    void install(Class<? extends Applet> appletClass);

    /**
     * Installs and selects an applet with an explicit AID.
     *
     * @param appletClass the applet class to install
     * @param aid         the AID to assign
     */
    void install(Class<? extends Applet> appletClass, AID aid);

    /**
     * Installs and selects an applet with an explicit AID and install parameters.
     *
     * @param appletClass   the applet class to install
     * @param aid           the AID to assign
     * @param installParams installation parameters
     */
    void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams);

    /**
     * Selects a previously installed applet by class (uses the AID assigned during install).
     *
     * @param appletClass the applet class to select
     */
    void select(Class<? extends Applet> appletClass);

    /**
     * Selects a previously installed applet by AID.
     *
     * @param aid the AID of the applet to select
     */
    void select(AID aid);

    /**
     * Resets the card (equivalent to removing and reinserting the card).
     */
    void reset();

    // === APDU ===

    /**
     * Sends an APDU with CLA and INS only (P1=P2=0, no data).
     *
     * @param cla the CLA byte
     * @param ins the INS byte
     * @return the response
     */
    APDUResponse send(int cla, int ins);

    /**
     * Sends an APDU with CLA, INS, P1, and P2 (no data).
     *
     * @param cla the CLA byte
     * @param ins the INS byte
     * @param p1  the P1 byte
     * @param p2  the P2 byte
     * @return the response
     */
    APDUResponse send(int cla, int ins, int p1, int p2);

    /**
     * Sends an APDU with CLA, INS, P1, P2, and data.
     *
     * @param cla  the CLA byte
     * @param ins  the INS byte
     * @param p1   the P1 byte
     * @param p2   the P2 byte
     * @param data the command data
     * @return the response
     */
    APDUResponse send(int cla, int ins, int p1, int p2, byte[] data);

    /**
     * Sends an APDU with CLA, INS, P1, P2, data, and Le.
     *
     * @param cla  the CLA byte
     * @param ins  the INS byte
     * @param p1   the P1 byte
     * @param p2   the P2 byte
     * @param data the command data (may be null)
     * @param le   the expected response length
     * @return the response
     */
    APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le);

    /**
     * Sends raw APDU bytes and returns raw response bytes.
     *
     * @param rawApdu the raw APDU bytes
     * @return the raw response bytes (including SW)
     */
    byte[] transmit(byte[] rawApdu);

    // === Decorators ===

    /**
     * Wraps this session with APDU logging.
     *
     * @return a logging-enabled session
     */
    default LoggingSession logged() {
        return LoggingSession.wrap(this);
    }

    /**
     * Wraps this session with APDU logging.
     *
     * @param printToLog true to also print exchanges to java.util.logging
     * @return a logging-enabled session
     */
    default LoggingSession logged(boolean printToLog) {
        return LoggingSession.wrap(this, printToLog);
    }

    /**
     * Creates a PIN helper on this session.
     *
     * @return a PIN session for verify/change/unblock operations
     */
    default PinSession pin() {
        return PinSession.on(this);
    }

    /**
     * Closes this session and releases resources.
     */
    @Override
    void close();
}
