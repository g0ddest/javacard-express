package name.velikodniy.jcexpress.idea;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/** Sources and jars the tests check with: the toolkit's own javac check and API stubs, as a project has them. */
final class Applets {

    static final String PACKAGE = "com.example.wallet";

    static final String VALID = """
            package com.example.wallet;

            import javacard.framework.APDU;
            import javacard.framework.Applet;
            import javacard.framework.ISO7816;
            import javacard.framework.ISOException;
            import javacard.framework.Util;

            public class WalletApplet extends Applet {

                private short balance;

                public static void install(byte[] bArray, short bOffset, byte bLength) {
                    new WalletApplet().register();
                }

                public void process(APDU apdu) {
                    if (selectingApplet()) {
                        return;
                    }
                    byte[] buffer = apdu.getBuffer();
                    Util.setShort(buffer, (short) 0, balance);
                    apdu.setOutgoingAndSend((short) 0, (short) 2);
                }
            }
            """;

    /** The valid applet with a {@code long} field (JCVM 3.1 §2.2.1.3). */
    static final String WITH_LONG = VALID.replace("    private short balance;\n",
            "    private short balance;\n    private long total;\n");

    /** The valid applet calling {@code Util.arrayFill}, which Java Card 3.0.5 brought (JCVM 3.1 §4.5.2). */
    static final String WITH_ARRAY_FILL = VALID.replace("        Util.setShort(buffer, (short) 0, balance);\n",
            "        Util.arrayFill(buffer, (short) 0, (short) 2, (byte) 0);\n"
                    + "        Util.setShort(buffer, (short) 0, balance);\n");

    private Applets() {
    }

    /** The javac check and the converter: the processor path of a project's main compilation. */
    static List<String> processorPath() {
        return paths("jcx.javacCheck");
    }

    /** The Java Card API stubs: the class path of a project's main compilation. */
    static List<String> apiStubs() {
        return paths("jcx.apiStubs");
    }

    private static List<String> paths(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("System property " + property + " is not set (the Gradle build sets it)");
        }
        return Arrays.stream(value.split(File.pathSeparator)).filter(path -> !path.isBlank()).toList();
    }
}
