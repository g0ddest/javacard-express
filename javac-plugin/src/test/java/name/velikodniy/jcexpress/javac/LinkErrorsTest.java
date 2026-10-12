package name.velikodniy.jcexpress.javac;

import name.velikodniy.jcexpress.converter.JavaCardVersion;
import name.velikodniy.jcexpress.converter.resolve.GlobalPlatformApi;
import name.velikodniy.jcexpress.javac.Javac.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * References that the target platform cannot link (JCVM 3.1 §4.3.5, §4.5.2) are compile errors at the line of the
 * reference; references into other packages are left to the conversion, which has their export files.
 */
class LinkErrorsTest {

    @TempDir
    Path project;

    @Test
    void theJavaSeLibraryIsAnErrorAtTheCall_jcvm31_2_2_1_4() {
        Result result = Javac.compile(project, Map.of("p/Ledger.java", """
                package p;

                public class Ledger {
                    void clear(byte[] entries) {
                        java.util.Arrays.fill(entries, (byte) 0);
                    }
                }
                """));

        assertThat(result.errors()).singleElement().satisfies(e -> assertThat(e).startsWith("Ledger.java:5:9:")
                .contains("java.util", "not part of the Java Card platform"));
    }

    @Test
    void apiOfALaterPlatformIsAnErrorForTheTargetedOne_jcvm31_4_5_2() {
        Map<String, String> sources = Map.of("p/Ledger.java", """
                package p;

                import javacard.framework.Util;

                public class Ledger {
                    void clear(byte[] entries) {
                        Util.arrayFill(entries, (short) 0, (short) entries.length, (byte) 0);
                    }
                }
                """);

        assertThat(Javac.compile(project.resolve("a"), sources, "-Ajavacard.version=3.0.4").errors()).singleElement()
                .satisfies(e -> assertThat(e).startsWith("Ledger.java:7:").contains("introduced in Java Card 3.0.5"));
        assertThat(Javac.compile(project.resolve("b"), sources).success()).isTrue();
    }

    @Test
    void anAppletOfTheGlobalPlatformApiCompiles_gpcs_7_3_1() {
        Result result = Javac.compile(project, Map.of("p/Vault.java", """
                package p;

                import javacard.framework.APDU;
                import javacard.framework.Applet;
                import org.globalplatform.GPSystem;
                import org.globalplatform.SecureChannel;

                public class Vault extends Applet {
                    public static void install(byte[] bArray, short bOffset, byte bLength) {
                        new Vault().register();
                    }

                    public void process(APDU apdu) {
                        SecureChannel channel = GPSystem.getSecureChannel();
                        if (channel.getSecurityLevel() == SecureChannel.NO_SECURITY_LEVEL) {
                            GPSystem.setCardContentState((byte) 0x0F);
                        }
                    }
                }
                """));

        assertThat(result.success()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void aFixedGlobalPlatformApiVersionIsTheOneChecked_jcvm31_4_5_2() {
        Result result = Javac.compile(project, Map.of("p/Registry.java", """
                package p;

                import javacard.framework.AID;
                import org.globalplatform.GPSystem;

                public class Registry {
                    Object entry(AID aid) {
                        return GPSystem.getRegistryEntry(aid);
                    }
                }
                """), "-Ajavacard.globalPlatformApi=1.0");

        assertThat(result.errors()).singleElement().satisfies(e -> assertThat(e).startsWith("Registry.java:8:")
                .contains("introduced in version 1.1"));
    }

    @Test
    void anotherPackageOfTheBuildIsLeftToTheConversion_jcvm31_4_3_3() {
        Result result = Javac.compile(project, Map.of("p/Ledger.java", """
                package p;

                public class Ledger {
                    short twice(short s) {
                        return lib.Maths.twice(s);
                    }
                }
                """, "lib/Maths.java", """
                package lib;

                public class Maths {
                    public static short twice(short s) {
                        return (short) (s * 2);
                    }
                }
                """), "-Ajavacard.packageName=p");

        assertThat(result.success()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void aMethodInheritedFromAnInterfaceOfAnotherPackageCompiles_jcvm31_4_3_3() {
        Result result = Javac.compile(project, Map.of("lib/Wallet.java", """
                package lib;

                public interface Wallet extends javacard.framework.Shareable {
                    void debit(short amount);
                }
                """, "app/BonusWallet.java", """
                package app;

                public interface BonusWallet extends lib.Wallet {
                    void bonus();
                }
                """, "app/Client.java", """
                package app;

                import javacard.framework.APDU;
                import javacard.framework.Applet;
                import javacard.framework.JCSystem;

                public class Client extends Applet {
                    public static void install(byte[] bArray, short bOffset, byte bLength) {
                        new Client().register();
                    }

                    public void process(APDU apdu) {
                        BonusWallet w = (BonusWallet) JCSystem.getAppletShareableInterfaceObject(null, (byte) 0);
                        w.debit((short) 5);
                    }
                }
                """));

        assertThat(result.success()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
    }

    @Test
    void anExportFileTheBuildSuppliesIsUsedAsByTheConversion_jcvm31_4_3_3() throws IOException {
        Map<String, String> sources = Map.of("p/Registry.java", """
                package p;

                import javacard.framework.AID;
                import org.globalplatform.GPSystem;

                public class Registry {
                    Object entry(AID aid) {
                        return GPSystem.getRegistryEntry(aid);
                    }
                }
                """);
        Path exportFile = Files.write(Files.createDirectories(project.resolve("exp")).resolve("globalplatform.exp"),
                GlobalPlatformApi.exportFileBytes("1.0", JavaCardVersion.V3_0_5));

        assertThat(Javac.compile(project.resolve("a"), sources, "-Ajavacard.exportFiles=" + exportFile).errors())
                .singleElement().satisfies(e -> assertThat(e).startsWith("Registry.java:8:")
                        .contains("getRegistryEntry", "org.globalplatform 1.0"));
        assertThat(Javac.compile(project.resolve("b"), sources).success()).isTrue();
    }
}
