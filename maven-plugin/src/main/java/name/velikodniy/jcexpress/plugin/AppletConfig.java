package name.velikodniy.jcexpress.plugin;

/**
 * Configuration entry for a single JavaCard applet.
 * Used in the Maven plugin configuration:
 * <pre>{@code
 * <applets>
 *   <applet>
 *     <className>com.example.WalletApplet</className>
 *     <aid>A0000000621201</aid>
 *   </applet>
 * </applets>
 * }</pre>
 */
public class AppletConfig {

    private String className;
    private String aid;

    /** Fully qualified class name (dot notation). */
    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    /** Applet AID as hex string (e.g. "A0000000621201"). */
    public String getAid() {
        return aid;
    }

    public void setAid(String aid) {
        this.aid = aid;
    }

    @Override
    public String toString() {
        return className + " [" + aid + "]";
    }
}
