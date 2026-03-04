package javacard.framework;

/**
 * Identifies applets that can support concurrent selections.
 */
public interface MultiSelectable {

    /**
     * Called when the applet is selected.
     *
     * @param appInstAlreadyActive true if another instance of this applet is already active
     * @return true if selection succeeded
     */
    boolean select(boolean appInstAlreadyActive);

    /**
     * Called when the applet is deselected.
     *
     * @param appInstStillActive true if another instance of this applet remains active
     */
    void deselect(boolean appInstStillActive);
}
