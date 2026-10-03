package name.velikodniy.jcexpress.plugin;

/**
 * Configuration entry for a single Java Card applet in the Maven plugin configuration:
 * <pre>{@code
 * <applets>
 *   <applet>
 *     <className>com.example.WalletApplet</className>
 *     <aid>A0000000621201</aid>
 *   </applet>
 * </applets>
 * }</pre>
 *
 * <p>The {@code <aid>} is optional: without it the applet gets the package AID followed by the
 * applet's 1-based position in the list, so that its RID equals the package RID
 * (JCVM 3.1 &sect;4.2.2.2).
 */
public class AppletConfig {

    private String className;
    private String aid;

    /**
     * Returns the applet class.
     *
     * @return fully qualified class name (dot notation)
     */
    public String getClassName() {
        return className;
    }

    /**
     * Sets the applet class.
     *
     * @param className fully qualified class name (dot notation)
     */
    public void setClassName(String className) {
        this.className = className;
    }

    /**
     * Returns the applet AID.
     *
     * @return the AID as hex string (e.g. "A0000000621201"), or {@code null} to derive it
     */
    public String getAid() {
        return aid;
    }

    /**
     * Sets the applet AID.
     *
     * @param aid the AID as hex string; spaces and colons between bytes are allowed
     */
    public void setAid(String aid) {
        this.aid = aid;
    }

    @Override
    public String toString() {
        return className + " [" + (aid == null ? "derived AID" : aid) + "]";
    }
}
