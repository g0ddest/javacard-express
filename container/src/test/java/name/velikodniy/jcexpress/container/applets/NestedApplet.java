package name.velikodniy.jcexpress.container.applets;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.Util;

/** Applet using a class nested two levels deep ({@code Outer.Deeper}); INS 00 returns {@code 0A0B}. */
public class NestedApplet extends Applet {

    static final class Outer {
        static final class Deeper {
            private Deeper() {
            }

            static short value() {
                return (short) 0x0A0B;
            }
        }

        private Outer() {
        }
    }

    /**
     * Applet installation entry point.
     *
     * @param bArray  install parameters
     * @param bOffset offset
     * @param bLength length
     */
    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new NestedApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buffer = apdu.getBuffer();
        Util.setShort(buffer, (short) 0, Outer.Deeper.value());
        apdu.setOutgoingAndSend((short) 0, (short) 2);
    }
}
