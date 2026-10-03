package name.velikodniy.jcexpress.container.applets;

/** Applet whose superclass is a user class ({@link BaseApplet}); INS 00 returns {@code 1234}. */
public class DerivedApplet extends BaseApplet {

    /**
     * Applet installation entry point.
     *
     * @param bArray  install parameters
     * @param bOffset offset
     * @param bLength length
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new DerivedApplet().register();
    }

    @Override
    protected short value() {
        return (short) 0x1234;
    }
}
