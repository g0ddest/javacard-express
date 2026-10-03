package javacard.framework.service;

import java.rmi.Remote;

import javacard.framework.APDU;

/**
 * Service that implements Java Card remote method invocation: it answers the SELECT command with a reference to
 * an initial remote object and executes the method invocation commands sent by the terminal.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class RMIService extends BasicService implements RemoteService {

    /** Default INS byte of the remote method invocation command. */
    public static final byte DEFAULT_RMI_INVOKE_INSTRUCTION = 0x38;

    /**
     * Creates the service with the object whose reference is returned on SELECT.
     *
     * @param initialObject the initial remote object
     * @throws NullPointerException if {@code initialObject} is {@code null}
     */
    public RMIService(Remote initialObject) throws NullPointerException {
        throw new RuntimeException("stub");
    }

    /**
     * Changes the INS byte used for remote method invocation commands.
     *
     * @param ins the new instruction byte
     */
    public void setInvokeInstructionByte(byte ins) {
        throw new RuntimeException("stub");
    }

    /**
     * Handles SELECT and remote method invocation commands.
     *
     * @param apdu the command being processed
     * @return {@code true} if the command was handled by this service
     */
    @Override
    public boolean processCommand(APDU apdu) {
        throw new RuntimeException("stub");
    }
}
