package javacard.framework;

/**
 * Provides a callback for applet uninstall notification.
 */
public interface AppletEvent {

    /**
     * Called by the runtime when the applet is being uninstalled.
     */
    void uninstall();
}
