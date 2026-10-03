package name.velikodniy.jcexpress.pace;

import name.velikodniy.jcexpress.APDULogEntry;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.LoggingSession;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.fakes.ContractCardTerminal;
import name.velikodniy.jcexpress.pcsc.PcscSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.smartcardio.CommandAPDU;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PACE over the PC/SC backend and the logging decorator of core (integration of the sm-pace and core fix branches).
 *
 * <p>The PACE terminal asks for Ne = 256 on every GENERAL AUTHENTICATE (ICAO Doc 9303-11 App. G.1 sends Le '00') and
 * sends MSE:Set AT as a case 3 command. Core's {@code le} contract (ISO/IEC 7816-4:2005 5.1) turns Ne = 256 into the
 * short Le field {@code '00'} on the wire. Here the bytes that reach the card are those {@link PcscSession} hands to
 * a {@code javax.smartcardio} fake ({@link ContractCardTerminal}); the fake decodes them with
 * {@link CommandAPDU} (an encoder-independent reading) and passes the command to the independent
 * {@link IcaoPaceChipSimulator}. No real reader is used.</p>
 */
class PaceOverCoreBackendsTest {

    private static final String DOC = "T22000129";
    private static final String DOB = "640812";
    private static final String DOE = "101031";
    private static final PaceAlgorithm ALGORITHM = PaceAlgorithm.ECDH_GM_AES_CBC_CMAC_128;
    private static final PaceParameterId CURVE = PaceParameterId.BRAINPOOL_P256R1;

    private IcaoPaceChipSimulator chip;
    private ContractCardTerminal reader;
    private PcscSession card;

    @BeforeEach
    void connect() {
        byte[] kPi = PaceMrz.kdf(PaceMrz.encodeMrzPassword(DOC, DOB, DOE), 3, ALGORITHM.keyLength());
        chip = IcaoPaceChipSimulator.random(CURVE, ALGORITHM, PasswordRef.MRZ, kPi);
        reader = new ContractCardTerminal("T=1", (channel, apdu) -> toChip(apdu));
        card = PcscSession.open(reader);
    }

    @AfterEach
    void disconnect() {
        card.close();
    }

    /** Decodes the wire bytes independently of core's encoder: Ne = 0 means "no Le field". */
    private byte[] toChip(byte[] apdu) {
        CommandAPDU command = new CommandAPDU(apdu);
        int le = command.getNe() == 0 ? SmartCardSession.NO_LE : command.getNe();
        APDUResponse response = chip.send(command.getCLA(), command.getINS(), command.getP1(), command.getP2(),
                command.getData(), le);
        byte[] data = response.data();
        byte[] bytes = new byte[data.length + 2];
        System.arraycopy(data, 0, bytes, 0, data.length);
        bytes[data.length] = (byte) response.sw1();
        bytes[data.length + 1] = (byte) response.sw2();
        return bytes;
    }

    private static PaceSession terminal() {
        return PaceSession.builder().algorithm(ALGORITHM).parameterId(CURVE).mrzPassword(DOC, DOB, DOE).build();
    }

    @Test
    void paceAuthenticatesOverThePcscBackend() {
        PaceResult result = terminal().perform(card);

        assertThat(chip.authenticated).isTrue();
        assertThat(result.encKey()).isEqualTo(chip.ksEnc);
        assertThat(result.macKey()).isEqualTo(chip.ksMac);
    }

    @Test
    void generalAuthenticateCarriesTheShortLe00AndMseSetAtHasNoLe() {
        terminal().perform(card);

        List<byte[]> wire = reader.wire;
        assertThat(wire).hasSize(5);
        byte[] mse = wire.getFirst();
        assertThat(mse.length).as("MSE:Set AT = header + Lc + data, no Le").isEqualTo(5 + (mse[4] & 0xFF));
        for (byte[] ga : wire.subList(1, 5)) {
            assertThat(ga[1]).isEqualTo((byte) 0x86);
            assertThat(ga.length).as("GENERAL AUTHENTICATE = header + Lc + data + Le").isEqualTo(6 + (ga[4] & 0xFF));
            assertThat(ga[ga.length - 1]).as("Le '00' (Ne = 256)").isZero();
        }
        assertThat(chip.commands.subList(1, 5)).extracting(IcaoPaceChipSimulator.Command::le).containsOnly(256);
    }

    @Test
    void loggingSessionLogsTheBytesThatReachTheCard() {
        LoggingSession logged = card.logged();

        terminal().perform(logged);

        assertThat(logged.entries()).extracting(APDULogEntry::command)
                .usingRecursiveFieldByFieldElementComparator()
                .containsExactlyElementsOf(reader.wire);
        assertThat(chip.authenticated).isTrue();
    }
}
