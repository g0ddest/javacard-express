package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.check.Violation;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Java Card RMI (JCVM 3.1 §2.2.6) is not supported, and a package with remote classes or interfaces is
 * rejected instead of being converted without its remote information.
 *
 * <p>§2.2.6.1: "A class is remote if it or any of its superclasses implements a remote interface"; a
 * remote interface is {@code java.rmi.Remote} or extends it. The Class component flags such types with
 * ACC_REMOTE, which "must be one if and only if the class or interface satisfies the requirements defined
 * in 2.2.6.1" (§6.9.2.1 Table 6-11), and describes them with {@code interface_name_info} and
 * {@code remote_interface_info} (§6.9.2.6), structures of CAP format 2.2. javacard-express writes
 * neither, so the Java Card RE could not dispatch remote calls to such a CAP file.
 */
class RemoteTypesTest {

    @TempDir
    Path classes;

    private static final Map<String, String> PURSE = Map.of(
            "com.acme.rmi.Purse", """
                    package com.acme.rmi;
                    import java.rmi.Remote;
                    import java.rmi.RemoteException;
                    public interface Purse extends Remote {
                        short getBalance() throws RemoteException;
                    }
                    """,
            "com.acme.rmi.PurseImpl", """
                    package com.acme.rmi;
                    import java.rmi.RemoteException;
                    import javacard.framework.service.CardRemoteObject;
                    public class PurseImpl extends CardRemoteObject implements Purse {
                        private short balance = 100;
                        public short getBalance() throws RemoteException { return balance; }
                    }
                    """,
            "com.acme.rmi.RmiApplet", """
                    package com.acme.rmi;
                    import javacard.framework.*;
                    import javacard.framework.service.Dispatcher;
                    import javacard.framework.service.RMIService;
                    public class RmiApplet extends Applet {
                        private final Dispatcher dispatcher = new Dispatcher((short) 1);
                        private RmiApplet() {
                            dispatcher.addService(new RMIService(new PurseImpl()), Dispatcher.PROCESS_COMMAND);
                        }
                        public static void install(byte[] b, short o, byte l) { new RmiApplet().register(); }
                        public void process(APDU apdu) { dispatcher.process(apdu); }
                    }
                    """);

    @Test
    void anRmiAppletPackageIsRejected_2_2_6() {
        JavaSources.compile(classes, PURSE);

        ConverterException e = catchThrowableOfType(ConverterException.class, () -> Converter.builder()
                .classesDirectory(classes).packageName("com.acme.rmi").packageAid("A000000FFE90")
                .applet("com.acme.rmi.RmiApplet", "A000000FFE9001").build().convert());

        assertThat(e).isNotNull().hasMessageContaining("Java Card RMI").hasMessageContaining("§2.2.6");
        assertThat(e.violations()).extracting(Violation::className)
                .containsExactlyInAnyOrder("com/acme/rmi/Purse", "com/acme/rmi/PurseImpl");
        assertThat(e.violations()).allMatch(v -> v.message().contains("§6.9.2.6"));
    }

    @Test
    void aClassIsRemoteThroughAnImportedSuperclass_2_2_6_1() {
        // CardRemoteObject implements java.rmi.Remote (its export entry lists it in interfaces[], §5.7)
        JavaSources.compile(classes, Map.of("com.acme.rmo.Obj", """
                package com.acme.rmo;
                public class Obj extends javacard.framework.service.CardRemoteObject { }
                """));

        ConverterException e = catchThrowableOfType(ConverterException.class, () -> Converter.builder()
                .classesDirectory(classes).packageName("com.acme.rmo").packageAid("A000000FFE91")
                .build().convert());

        assertThat(e).isNotNull();
        assertThat(e.violations()).extracting(Violation::className).containsExactly("com/acme/rmo/Obj");
    }

    @Test
    void aLibraryWithARemoteInterfaceIsRejected_2_2_6_1() {
        JavaSources.compile(classes, Map.of("com.acme.rif.Counter", """
                package com.acme.rif;
                public interface Counter extends java.rmi.Remote {
                    short next() throws java.rmi.RemoteException;
                }
                """));

        ConverterException e = catchThrowableOfType(ConverterException.class, () -> Converter.builder()
                .classesDirectory(classes).packageName("com.acme.rif").packageAid("A000000FFE92")
                .generateExport(true).build().convert());

        assertThat(e).isNotNull();
        assertThat(e.violations()).extracting(Violation::className).containsExactly("com/acme/rif/Counter");
    }

    @Test
    void usingTheServiceFrameworkWithoutRemoteTypesIsAccepted() throws Exception {
        JavaSources.compile(classes, Map.of("com.acme.svc.SvcApplet", """
                package com.acme.svc;
                import javacard.framework.*;
                import javacard.framework.service.Dispatcher;
                public class SvcApplet extends Applet {
                    private final Dispatcher dispatcher = new Dispatcher((short) 1);
                    public static void install(byte[] b, short o, byte l) { new SvcApplet().register(); }
                    public void process(APDU apdu) { dispatcher.process(apdu); }
                }
                """));

        ConverterResult result = Converter.builder().classesDirectory(classes).packageName("com.acme.svc")
                .packageAid("A000000FFE93").applet("com.acme.svc.SvcApplet", "A000000FFE9301").build().convert();

        assertThat(result.capFile()).isNotEmpty();
    }
}
