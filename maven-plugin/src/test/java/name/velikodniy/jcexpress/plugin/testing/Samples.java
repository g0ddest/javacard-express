package name.velikodniy.jcexpress.plugin.testing;

/**
 * Java Card applet sources used by the plugin tests. They are compiled with javac against the
 * API stubs ({@link JavaSources}), so the plugin sees real class files with methods, constructors
 * and line numbers.
 */
public final class Samples {

    private Samples() {
    }

    /**
     * A small applet in package {@code com.example.hello}: {@code HelloApplet} answers INS 01.
     *
     * @return the sources
     */
    public static JavaSources helloApplet() {
        return JavaSources.create().add("com/example/hello/HelloApplet.java", """
                package com.example.hello;

                import javacard.framework.APDU;
                import javacard.framework.Applet;
                import javacard.framework.ISO7816;
                import javacard.framework.ISOException;
                import javacard.framework.Util;

                public class HelloApplet extends Applet {
                    public static void install(byte[] bArray, short bOffset, byte bLength) {
                        new HelloApplet().register();
                    }

                    public void process(APDU apdu) {
                        if (selectingApplet()) {
                            return;
                        }
                        byte[] buf = apdu.getBuffer();
                        if (buf[ISO7816.OFFSET_INS] != (byte) 0x01) {
                            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
                        }
                        Util.setShort(buf, (short) 0, (short) 0x4869);
                        apdu.setOutgoingAndSend((short) 0, (short) 2);
                    }
                }
                """);
    }

    /**
     * Two independent applets in package {@code com.example.multi}: {@code AlphaApplet} and
     * {@code BetaApplet}.
     *
     * @return the sources
     */
    public static JavaSources twoApplets() {
        JavaSources sources = JavaSources.create();
        for (String name : new String[] {"AlphaApplet", "BetaApplet"}) {
            sources.add("com/example/multi/" + name + ".java", """
                    package com.example.multi;

                    import javacard.framework.APDU;
                    import javacard.framework.Applet;

                    public class %1$s extends Applet {
                        public static void install(byte[] bArray, short bOffset, byte bLength) {
                            new %1$s().register();
                        }

                        public void process(APDU apdu) {
                        }
                    }
                    """.formatted(name));
        }
        return sources;
    }

    /**
     * A library package (no applets) {@code com.example.lib} with a public class.
     *
     * @return the sources
     */
    public static JavaSources libraryPackage() {
        return JavaSources.create().add("com/example/lib/ShortMath.java", """
                package com.example.lib;

                public class ShortMath {
                    public static short twice(short value) {
                        return (short) (value + value);
                    }

                    public short half(short value) {
                        return (short) (value >> 1);
                    }
                }
                """);
    }

    /**
     * An applet package {@code com.example.sio} that offers a shareable interface
     * ({@code Counter extends javacard.framework.Shareable}) to other applets.
     *
     * @return the sources
     */
    public static JavaSources shareableInterfacePackage() {
        return JavaSources.create()
                .add("com/example/sio/Counter.java", """
                        package com.example.sio;

                        import javacard.framework.Shareable;

                        public interface Counter extends Shareable {
                            short next();
                        }
                        """)
                .add("com/example/sio/CounterApplet.java", """
                        package com.example.sio;

                        import javacard.framework.AID;
                        import javacard.framework.APDU;
                        import javacard.framework.Applet;
                        import javacard.framework.Shareable;

                        public class CounterApplet extends Applet implements Counter {
                            private short value;

                            public static void install(byte[] bArray, short bOffset, byte bLength) {
                                new CounterApplet().register();
                            }

                            public void process(APDU apdu) {
                            }

                            public short next() {
                                value++;
                                return value;
                            }

                            public Shareable getShareableInterfaceObject(AID clientAid, byte parameter) {
                                return this;
                            }
                        }
                        """);
    }

    /**
     * Package {@code com.example.partial}: {@code GoodApplet} is a complete applet; the other two
     * concrete {@code Applet} subclasses cannot be installed: {@code NoInstallApplet} declares no
     * {@code install} method, {@code WrongInstallApplet} declares one with the wrong signature
     * {@code install(byte[], short, short)}.
     *
     * @return the sources
     */
    public static JavaSources incompleteApplets() {
        return JavaSources.create()
                .add("com/example/partial/GoodApplet.java", """
                        package com.example.partial;

                        import javacard.framework.APDU;
                        import javacard.framework.Applet;

                        public class GoodApplet extends Applet {
                            public static void install(byte[] bArray, short bOffset, byte bLength) {
                                new GoodApplet().register();
                            }

                            public void process(APDU apdu) {
                            }
                        }
                        """)
                .add("com/example/partial/NoInstallApplet.java", """
                        package com.example.partial;

                        import javacard.framework.APDU;
                        import javacard.framework.Applet;

                        public class NoInstallApplet extends Applet {
                            public void process(APDU apdu) {
                            }
                        }
                        """)
                .add("com/example/partial/WrongInstallApplet.java", """
                        package com.example.partial;

                        import javacard.framework.APDU;
                        import javacard.framework.Applet;

                        public class WrongInstallApplet extends Applet {
                            public static void install(byte[] bArray, short bOffset, short bLength) {
                                new WrongInstallApplet().register();
                            }

                            public void process(APDU apdu) {
                            }
                        }
                        """);
    }

    /**
     * A wallet package: an abstract base applet, two concrete applets (one extends the base),
     * and a helper class.
     *
     * @return the sources
     */
    public static JavaSources walletPackage() {
        return JavaSources.create()
                .add("com/example/wallet/BaseApplet.java", """
                        package com.example.wallet;

                        import javacard.framework.APDU;
                        import javacard.framework.Applet;
                        import javacard.framework.ISO7816;
                        import javacard.framework.ISOException;

                        public abstract class BaseApplet extends Applet {
                            public void process(APDU apdu) {
                                if (selectingApplet()) {
                                    return;
                                }
                                byte[] buf = apdu.getBuffer();
                                if (!dispatch(buf[ISO7816.OFFSET_INS], apdu)) {
                                    ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
                                }
                            }

                            protected abstract boolean dispatch(byte ins, APDU apdu);
                        }
                        """)
                .add("com/example/wallet/WalletApplet.java", """
                        package com.example.wallet;

                        import javacard.framework.APDU;

                        public class WalletApplet extends BaseApplet {
                            private short balance;

                            public static void install(byte[] bArray, short bOffset, byte bLength) {
                                new WalletApplet().register();
                            }

                            protected boolean dispatch(byte ins, APDU apdu) {
                                if (ins == (byte) 0x50) {
                                    balance = Amounts.add(balance, (short) 1);
                                    return true;
                                }
                                return false;
                            }
                        }
                        """)
                .add("com/example/wallet/LoyaltyApplet.java", """
                        package com.example.wallet;

                        import javacard.framework.APDU;
                        import javacard.framework.Applet;

                        public class LoyaltyApplet extends Applet {
                            public static void install(byte[] bArray, short bOffset, byte bLength) {
                                new LoyaltyApplet().register();
                            }

                            public void process(APDU apdu) {
                            }
                        }
                        """)
                .add("com/example/wallet/Amounts.java", """
                        package com.example.wallet;

                        final class Amounts {
                            private Amounts() {
                            }

                            static short add(short a, short b) {
                                return (short) (a + b);
                            }
                        }
                        """);
    }
}
