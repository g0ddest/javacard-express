package name.velikodniy.jcexpress.container;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.container.applets.ParamApplet;
import name.velikodniy.jcexpress.container.applets.StandardRegisterApplet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Install parameters in container mode. Regression tests for the audit finding
 * "install-params-no-127-limit-and-byte-truncation": the container path passed 128..255 parameter bytes through and
 * cast the length to a byte, so 256 bytes reached the applet as {@code bLength = 0}.
 *
 * <p>Java Card Platform API (Classic Edition 3.0.5, 3.1), {@code javacard.framework.Applet.install(byte[] bArray,
 * short bOffset, byte bLength)}: the parameters are the length-value fields
 * {@code [Li][instance AID][Lc][control info][La][applet data]}, and "the maximum value of bLength is 127". The
 * session's {@code installParams} are the applet data.</p>
 */
class InstallParamsTest {

    private static final AID AID_PARAMS = AID.of(0xF0, 0x00, 0x00, 0x00, 0x50, 0x01);
    /** 127 - 3 length bytes (Li, Lc, La) - 6 AID bytes. */
    private static final int MAX_APPLET_DATA = 118;

    private static LocalSimulatorServer server;
    private ContainerSession session;

    @BeforeAll
    static void startServer() {
        server = LocalSimulatorServer.start();
    }

    @AfterAll
    static void stopServer() {
        server.close();
    }

    @BeforeEach
    void connect() throws IOException {
        session = server.newSession();
    }

    @AfterEach
    void disconnect() {
        session.close();
    }

    @Test
    void appletReceivesTheJavaCardInstallParameterLayout() {
        session.install(ParamApplet.class, AID_PARAMS, new byte[]{0x11, 0x22, 0x33});

        assertThat(receivedInstallParameters())
                .isEqualTo(bytes(0x06, 0xF0, 0x00, 0x00, 0x00, 0x50, 0x01, 0x00, 0x03, 0x11, 0x22, 0x33));
    }

    @Test
    void installWithoutParametersStillCarriesTheInstanceAid() {
        session.install(ParamApplet.class, AID_PARAMS);

        assertThat(receivedInstallParameters())
                .isEqualTo(bytes(0x06, 0xF0, 0x00, 0x00, 0x00, 0x50, 0x01, 0x00, 0x00));
    }

    @Test
    void largestApplicationDataThatFitsArrivesUnchangedWithBLength127() {
        byte[] data = new byte[MAX_APPLET_DATA];
        Arrays.fill(data, (byte) 0x5A);

        session.install(ParamApplet.class, AID_PARAMS, data);

        byte[] received = receivedInstallParameters();
        assertThat(received).hasSize(127);
        assertThat(received[8]).as("La").isEqualTo((byte) MAX_APPLET_DATA);
        assertThat(Arrays.copyOfRange(received, 9, received.length)).isEqualTo(data);
    }

    @ParameterizedTest
    @ValueSource(ints = {MAX_APPLET_DATA + 1, 124, 127, 128, 200, 255, 256, 300})
    void parametersBeyond127BytesAreRejectedAndNothingIsInstalled(int length) {
        assertThatThrownBy(() -> session.install(ParamApplet.class, AID_PARAMS, new byte[length]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Install parameters too long")
                .hasMessageContaining("127");

        session.install(ParamApplet.class, AID_PARAMS, new byte[]{0x01});
        assertThat(receivedInstallParameters()).hasSize(10);
    }

    @Test
    void appletRegisteringWithTheAidFromItsParametersIsInstalledUnderTheRequestedAid() {
        AID aid = AID.of(0xF0, 0x00, 0x00, 0x00, 0x50, 0x02);

        session.install(StandardRegisterApplet.class, aid);
        session.select(aid);

        APDUResponse response = session.send(0x80, 0x00);
        assertThat(response.sw()).isEqualTo(0x9000);
        assertThat(response.data()).isEqualTo(aid.toBytes());
    }

    @ParameterizedTest
    @ValueSource(ints = {128, 256})
    void serverRejectsMoreThan127BytesFromAnyClient(int length) throws IOException {
        Map<String, byte[]> classes = new LinkedHashMap<>();
        classes.put(ParamApplet.class.getName(), RawClient.classFile(ParamApplet.class));

        try (RawClient client = new RawClient(server.port())) {
            RawClient.Reply reply = client.exchange(Protocol.CMD_INSTALL, RawClient.installPayload(
                    AID_PARAMS.toBytes(), ParamApplet.class.getName(), classes, new byte[length]));

            assertThat(reply.status()).isEqualTo(1);
            assertThat(reply.text()).contains("Install parameters too long: " + length + " bytes (max 127");
        }
    }

    /** INS 00 of {@link ParamApplet}: {@code bLength} (2 bytes) followed by the bytes install() received. */
    private byte[] receivedInstallParameters() {
        APDUResponse response = session.send(0x80, 0x00, 0x00, 0x00, null, 256);
        assertThat(response.sw()).isEqualTo(0x9000);
        byte[] data = response.data();
        int length = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
        assertThat(data).hasSize(2 + length);
        return Arrays.copyOfRange(data, 2, data.length);
    }

    private static byte[] bytes(int... values) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int value : values) {
            out.write(value);
        }
        return out.toByteArray();
    }
}
