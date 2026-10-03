package javacard.framework.service;

import java.rmi.Remote;

/**
 * Base class for objects that a Java Card RMI service exposes to the terminal. A remote object is reachable
 * only while it is exported.
 */
@SuppressWarnings({"java:S1172", "java:S112"}) // API stubs: params are contractual, RuntimeException is intentional
public class CardRemoteObject implements Remote {

    /**
     * Creates the object and exports it.
     */
    public CardRemoteObject() {
        throw new RuntimeException("stub");
    }

    /**
     * Makes a remote object reachable through remote method invocation.
     *
     * @param obj the object to export
     * @throws SecurityException if {@code obj} does not belong to the caller's context
     */
    public static void export(Remote obj) throws SecurityException {
        throw new RuntimeException("stub");
    }

    /**
     * Makes a remote object unreachable through remote method invocation.
     *
     * @param obj the object to unexport
     * @throws SecurityException if {@code obj} does not belong to the caller's context
     */
    public static void unexport(Remote obj) throws SecurityException {
        throw new RuntimeException("stub");
    }
}
