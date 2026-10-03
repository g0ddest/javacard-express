package name.velikodniy.jcexpress.container.applets;

/** Top-level helper class used by {@link HelperApplet}. */
public class Helper {

    private final byte[] data = {0x11, 0x22, 0x33};

    /** @return the helper data */
    public byte[] data() {
        return data;
    }
}
