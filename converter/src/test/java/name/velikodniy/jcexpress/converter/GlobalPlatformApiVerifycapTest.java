package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.resolve.GlobalPlatformApi;
import name.velikodniy.jcexpress.converter.testutil.GlobalPlatformExports;
import name.velikodniy.jcexpress.converter.testutil.GlobalPlatformStandIns;
import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import name.velikodniy.jcexpress.converter.translate.OracleVerifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs Oracle's off-card verifier (black box) on CAP files that import {@code org.globalplatform} from the
 * converter's built-in data. The verifier needs the export file of every imported package as a file; it gets the
 * one {@link GlobalPlatformApi#exportFileBytes} writes for the imported version. Where GlobalPlatform's own
 * export file of that version is installed locally (see {@link GlobalPlatformExports}) and the kit can read its
 * format, the same CAP file must pass with that file too.
 *
 * <p>The test needs a Java Card 3.0.5 development kit at {@code build/oracle-sdks/jc305u3_kit} (not part of the
 * repository) and is skipped otherwise.
 */
@EnabledIf("name.velikodniy.jcexpress.converter.translate.OracleVerifier#available")
class GlobalPlatformApiVerifycapTest {

    private static final String PASSED = "Verification completed with 0 warnings and 0 errors";

    /** Uses only what version 1.0 has: static methods, interface methods, an implemented interface. */
    private static final String SECURE_CHANNEL_APPLET = """
            package com.acme.gpv;
            import javacard.framework.*;
            import org.globalplatform.*;
            public class Probe extends Applet implements Application {
                private SecureChannel channel;
                public static void install(byte[] b, short o, byte l) { new Probe().register(); }
                public void process(APDU apdu) {
                    if (selectingApplet()) return;
                    byte[] buf = apdu.getBuffer();
                    channel = GPSystem.getSecureChannel();
                    if (buf[ISO7816.OFFSET_INS] == (byte) 0x50 || buf[ISO7816.OFFSET_INS] == (byte) 0x82) {
                        apdu.setOutgoingAndSend(ISO7816.OFFSET_CDATA, channel.processSecurity(apdu));
                        return;
                    }
                    short length = channel.unwrap(buf, (short) 0, (short) (apdu.setIncomingAndReceive() + 5));
                    if ((channel.getSecurityLevel() & SecureChannel.AUTHENTICATED) == 0) {
                        ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
                    }
                    CVM pin = GPSystem.getCVM(GPSystem.CVM_GLOBAL_PIN);
                    if (pin != null && !pin.isVerified()) channel.resetSecurity();
                    if (GPSystem.getCardContentState() == GPSystem.APPLICATION_SELECTABLE) {
                        GPSystem.setCardContentState((byte) 0x0F);
                    }
                    processData(buf, (short) 0, length);
                }
                public void processData(byte[] baBuffer, short sOffset, short sLength) {
                    channel.decryptData(baBuffer, sOffset, sLength);
                }
            }
            """;

    /** Needs version 1.2: Personalization; also the registry entry and a global service of 1.1. */
    private static final String PERSONALIZATION_APPLET = """
            package com.acme.gpv;
            import javacard.framework.*;
            import org.globalplatform.*;
            public class Probe extends Applet implements Personalization {
                private GPRegistryEntry entry;
                public static void install(byte[] b, short o, byte l) { new Probe().register(); }
                public void process(APDU apdu) {
                    if (selectingApplet()) return;
                    entry = GPSystem.getRegistryEntry(null);
                    if (entry.getState() != GPSystem.APPLICATION_SELECTABLE) entry.setState((byte) 0x07);
                    GlobalService service = GPSystem.getService(entry.getAID(), (short) 0x8200);
                    if (service == null) ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
                    SecureChannelx channel = (SecureChannelx) GPSystem.getSecureChannel();
                    channel.setSecurityLevel(channel.getSecurityLevel());
                }
                public short processData(byte[] in, short inOffset, short inLength, byte[] out, short outOffset) {
                    return Util.arrayCopyNonAtomic(in, inOffset, out, outOffset, inLength);
                }
            }
            """;

    /** Needs version 1.8, the newest: an interface and methods of the latest versions. */
    private static final String LATEST_APPLET = """
            package com.acme.gpv;
            import javacard.framework.*;
            import org.globalplatform.*;
            public class Probe extends Applet {
                public static void install(byte[] b, short o, byte l) { new Probe().register(); }
                public void process(APDU apdu) {
                    if (selectingApplet()) return;
                    byte[] buf = apdu.getBuffer();
                    short length = GPSystem.getCardInfoLength((short) 0x80);
                    GPSystem.getCardInfo(buf, (short) 0, (short) 0x80);
                    Shareable service = GPSystem.getService(null, (short) 0x8400)
                            .getServiceInterface(GPSystem.getRegistryEntry(null), (short) 0x8400, buf, (short) 0, length);
                    ((AdministrationSession) service).requestAdministrationSession(buf, (short) 0, length);
                    SecureChannelx2 channel = (SecureChannelx2) GPSystem.getSecureChannel();
                    apdu.setOutgoingAndSend((short) 0, channel.processSecurity(buf, (short) 0, (short) 5, (short) 0));
                }
            }
            """;

    @TempDir
    Path classes;

    @TempDir
    Path exports;

    @ParameterizedTest(name = "version {1}")
    @CsvSource({"SECURE_CHANNEL, 1.0", "PERSONALIZATION, 1.2", "LATEST, 1.8"})
    void capFilePassesTheVerifierWithTheGeneratedExportFile(String applet, String expectedVersion) throws Exception {
        ConverterResult result = convert(applet);
        String version = result.imports().stream().filter(i -> i.packageName().equals("org.globalplatform"))
                .map(ConverterResult.Import::version).findFirst().orElseThrow();
        assertThat(version).isEqualTo(expectedVersion);
        Path generated = Files.write(exports.resolve("globalplatform.exp"),
                GlobalPlatformApi.exportFileBytes(version, JavaCardVersion.V3_0_5));

        String output = OracleVerifier.verify(result.capFile(), generated);

        assertThat(output).as("verifycap with the generated export file of %s:%n%s", version, output)
                .contains(PASSED);
    }

    @ParameterizedTest(name = "version {1}")
    @CsvSource({"SECURE_CHANNEL, 1.0", "PERSONALIZATION, 1.2"})
    void sameCapFilePassesWithTheExportFileGlobalPlatformShips(String applet, String version) throws Exception {
        // export files of format 2.1 only: the kit's verifier does not read the format 2.3 file of version 1.8
        assumeTrue(Files.isRegularFile(GlobalPlatformExports.file(version)),
                "GlobalPlatform's export file of version " + version + " is installed");
        ConverterResult result = convert(applet);

        String output = OracleVerifier.verify(result.capFile(), GlobalPlatformExports.file(version));

        assertThat(output).as("verifycap with GlobalPlatform's export file of %s:%n%s", version, output)
                .contains(PASSED);
    }

    @Test
    void verifierNeedsTheExportFileOfThePackage() throws Exception {
        // the control for the tests above: without the file the verifier cannot check the references
        String output = OracleVerifier.verify(convert("SECURE_CHANNEL").capFile());

        assertThat(output).as("verifycap without an export file of org.globalplatform:%n%s", output)
                .doesNotContain(PASSED);
    }

    private ConverterResult convert(String applet) throws ConverterException {
        Map<String, String> sources = new LinkedHashMap<>(GlobalPlatformStandIns.sources());
        sources.put("com.acme.gpv.Probe", switch (applet) {
            case "SECURE_CHANNEL" -> SECURE_CHANNEL_APPLET;
            case "PERSONALIZATION" -> PERSONALIZATION_APPLET;
            default -> LATEST_APPLET;
        });
        JavaSources.compile(classes, sources);
        return Converter.builder()
                .classesDirectory(classes)
                .packageName("com.acme.gpv")
                .packageAid("A000000FFE51")
                .applet("com.acme.gpv.Probe", "A000000FFE5101")
                .javaCardVersion(JavaCardVersion.V3_0_5)
                .build()
                .convert();
    }
}
