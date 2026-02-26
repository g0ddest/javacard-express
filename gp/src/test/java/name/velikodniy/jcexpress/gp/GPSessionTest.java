package name.velikodniy.jcexpress.gp;

import javacard.framework.Applet;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.crypto.CryptoUtil;
import name.velikodniy.jcexpress.scp.GP;
import name.velikodniy.jcexpress.scp.SCPKeys;
import name.velikodniy.jcexpress.tlv.TLVBuilder;
import name.velikodniy.jcexpress.tlv.Tags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link GPSession}, {@link CardInfo}, and GP command helpers.
 */
class GPSessionTest {

    private static final byte[] HOST_CHALLENGE = Hex.decode("AABBCCDDEEFF0011");
    private static final String DIVERSIFICATION = "00010203040506070809";
    private static final String SEQ_COUNTER = "0027";
    private static final String CARD_CHALLENGE_SCP02 = "112233445566";

    private SCPKeys keys;

    @BeforeEach
    void setUp() {
        keys = SCPKeys.defaultKeys();
    }

    // ── GP Stub Session ──

    /**
     * Stub session that simulates a GP Card Manager.
     * Validates INITIALIZE UPDATE and EXTERNAL AUTHENTICATE commands,
     * returns correct synthetic responses.
     */
    static class GPStubSession implements SmartCardSession {

        private final SCPKeys keys;
        private final byte[] hostChallenge;
        private final int scpVersion;
        private final List<byte[]> transmittedApdus = new ArrayList<>();
        private boolean authenticated = false;

        /** Queued responses for post-authentication commands. */
        private final List<byte[]> postAuthResponses = new ArrayList<>();
        private int postAuthIndex = 0;

        GPStubSession(SCPKeys keys, byte[] hostChallenge, int scpVersion) {
            this.keys = keys;
            this.hostChallenge = hostChallenge;
            this.scpVersion = scpVersion;
        }

        /** Adds a response that will be returned for post-authentication transmit calls. */
        GPStubSession queueResponse(byte[] data, int sw) {
            byte[] raw = new byte[data.length + 2];
            System.arraycopy(data, 0, raw, 0, data.length);
            raw[data.length] = (byte) ((sw >> 8) & 0xFF);
            raw[data.length + 1] = (byte) (sw & 0xFF);
            postAuthResponses.add(raw);
            return this;
        }

        GPStubSession queueResponse(int sw) {
            return queueResponse(new byte[0], sw);
        }

        List<byte[]> transmittedApdus() {
            return transmittedApdus;
        }

        @Override
        public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
            // INITIALIZE UPDATE
            if (cla == 0x80 && ins == 0x50) {
                return buildInitUpdateResponse(data);
            }
            return new APDUResponse(new byte[0], 0x6D00);
        }

        @Override
        public byte[] transmit(byte[] rawApdu) {
            transmittedApdus.add(rawApdu.clone());

            // EXTERNAL AUTHENTICATE (wrapped, so CLA = 0x84 with secure bit)
            if (!authenticated && rawApdu.length > 1 && (rawApdu[1] & 0xFF) == 0x82) {
                authenticated = true;
                return new byte[]{(byte) 0x90, 0x00};
            }

            // Post-auth commands
            if (authenticated && postAuthIndex < postAuthResponses.size()) {
                return postAuthResponses.get(postAuthIndex++);
            }

            return new byte[]{(byte) 0x90, 0x00};
        }

        private APDUResponse buildInitUpdateResponse(byte[] hostChallengeFromCommand) {
            byte[] div = Hex.decode(DIVERSIFICATION);

            if (scpVersion == 2) {
                byte[] seqCounter = Hex.decode(SEQ_COUNTER);
                byte[] cardChallenge = Hex.decode(CARD_CHALLENGE_SCP02);

                // Derive session ENC key
                byte[] sessionEncKey = CryptoUtil.deriveSCP02SessionKey(
                        keys.enc(), seqCounter, GP.SCP02_DERIVE_ENC);

                // Card cryptogram: MAC(sessionENC, seqCounter || cardChallenge || hostChallenge)
                byte[] cryptoInput = new byte[16];
                System.arraycopy(seqCounter, 0, cryptoInput, 0, 2);
                System.arraycopy(cardChallenge, 0, cryptoInput, 2, 6);
                System.arraycopy(hostChallengeFromCommand, 0, cryptoInput, 8, 8);
                byte[] padded = CryptoUtil.pad80(cryptoInput, 8);
                byte[] cardCryptogram = CryptoUtil.des3Mac(sessionEncKey, padded, new byte[8]);

                // Build response: div(10) + keyInfo(2) + seqCounter(2) + cardChallenge(6) + cryptogram(8)
                byte[] response = new byte[28];
                System.arraycopy(div, 0, response, 0, 10);
                response[10] = 0x01; // key version
                response[11] = 0x02; // SCP02
                System.arraycopy(seqCounter, 0, response, 12, 2);
                System.arraycopy(cardChallenge, 0, response, 14, 6);
                System.arraycopy(cardCryptogram, 0, response, 20, 8);
                return new APDUResponse(response, 0x9000);

            } else {
                // SCP03
                byte[] cardChallenge = Hex.decode("1122334455667788");

                byte[] context = new byte[16];
                System.arraycopy(hostChallengeFromCommand, 0, context, 0, 8);
                System.arraycopy(cardChallenge, 0, context, 8, 8);

                byte[] cardCryptoFull = CryptoUtil.deriveSCP03SessionKey(
                        keys.mac(), context, GP.SCP03_DERIVE_CARD_CRYPTO, 64);
                byte[] cardCryptogram = Arrays.copyOf(cardCryptoFull, 8);

                byte[] response = new byte[28];
                System.arraycopy(div, 0, response, 0, 10);
                response[10] = 0x01; // key version
                response[11] = 0x03; // SCP03
                System.arraycopy(cardChallenge, 0, response, 12, 8);
                System.arraycopy(cardCryptogram, 0, response, 20, 8);
                return new APDUResponse(response, 0x9000);
            }
        }

        @Override public APDUResponse send(int cla, int ins) { return send(cla, ins, 0, 0, null); }
        @Override public APDUResponse send(int cla, int ins, int p1, int p2) { return send(cla, ins, p1, p2, null); }
        @Override public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) { return send(cla, ins, p1, p2, data); }
        @Override public void install(Class<? extends Applet> c) { }
        @Override public void install(Class<? extends Applet> c, AID aid) { }
        @Override public void install(Class<? extends Applet> c, AID aid, byte[] p) { }
        @Override public void select(Class<? extends Applet> c) { }
        @Override public void select(AID aid) { }
        @Override public void reset() { }
        @Override public void close() { }
    }

    /** Stub that returns error on INITIALIZE UPDATE. */
    static class FailingInitStub extends GPStubSession {
        private final int failSw;
        FailingInitStub(int failSw) {
            super(SCPKeys.defaultKeys(), HOST_CHALLENGE, 2);
            this.failSw = failSw;
        }
        @Override
        public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
            if (ins == 0x50) return new APDUResponse(new byte[0], failSw);
            return super.send(cla, ins, p1, p2, data);
        }
    }

    /** Stub that returns error on EXTERNAL AUTHENTICATE. */
    static class FailingExtAuthStub extends GPStubSession {
        FailingExtAuthStub(SCPKeys keys, byte[] hostChallenge) {
            super(keys, hostChallenge, 2);
        }
        @Override
        public byte[] transmit(byte[] rawApdu) {
            transmittedApdus().add(rawApdu.clone());
            if (rawApdu.length > 1 && (rawApdu[1] & 0xFF) == 0x82) {
                return new byte[]{0x69, (byte) 0x82}; // security status not satisfied
            }
            return new byte[]{(byte) 0x90, 0x00};
        }
    }

    // ── Authentication tests ──

    @Nested
    class Authentication {

        @Test
        void shouldOpenWithSCP02() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);

            GPSession gp = GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .open();

            assertThat(gp.isOpen()).isTrue();
            assertThat(gp.cardInfo()).isNotNull();
            assertThat(gp.cardInfo().scpVersion()).isEqualTo(2);
            assertThat(gp.secureChannel()).isNotNull();
        }

        @Test
        void shouldOpenWithSCP03() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 3);

            GPSession gp = GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .open();

            assertThat(gp.isOpen()).isTrue();
            assertThat(gp.cardInfo().scpVersion()).isEqualTo(3);
        }

        @Test
        void shouldAutoDetectSCPVersion() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);

            GPSession gp = GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .open();

            // Auto-detected from response byte[11] = 0x02
            assertThat(gp.cardInfo().scpVersion()).isEqualTo(2);
        }

        @Test
        void shouldUseDefaultKeysIfNotSet() {
            GPStubSession stub = new GPStubSession(SCPKeys.defaultKeys(), HOST_CHALLENGE, 2);

            GPSession gp = GPSession.on(stub)
                    .hostChallenge(HOST_CHALLENGE)
                    .open(); // no .keys() call

            assertThat(gp.isOpen()).isTrue();
        }

        @Test
        void shouldRejectFailedInitUpdate() {
            FailingInitStub stub = new FailingInitStub(0x6A88);

            assertThatThrownBy(() -> GPSession.on(stub)
                    .hostChallenge(HOST_CHALLENGE)
                    .open())
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("INITIALIZE UPDATE");
        }

        @Test
        void shouldRejectFailedExtAuth() {
            FailingExtAuthStub stub = new FailingExtAuthStub(keys, HOST_CHALLENGE);

            assertThatThrownBy(() -> GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .open())
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("EXTERNAL AUTHENTICATE");
        }

        @Test
        void shouldRejectDoubleOpen() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            GPSession gp = GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .open();

            assertThatThrownBy(gp::open)
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("already open");
        }

        @Test
        void shouldForceScpVersion() {
            // Stub reports SCP02, but we force SCP03 — should fail
            // (because the card crypto won't match SCP03 derivation)
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);

            assertThatThrownBy(() -> GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .scpVersion(3)
                    .open())
                    .isInstanceOf(Exception.class); // SCPException or GPException
        }
    }

    // ── Command dispatch tests ──

    @Nested
    class CommandDispatch {

        @Test
        void sendShouldWrapAndTransmit() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(Hex.decode("0102"), 0x9000);

            GPSession gp = GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .open();

            APDUResponse r = gp.send(0x80, 0xF2, 0x40, 0x00);

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.data()).isEqualTo(Hex.decode("0102"));
        }

        @Test
        void sendShouldSetSecureMessagingBit() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000);

            GPSession gp = GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .open();

            gp.send(0x80, 0xF2, 0x40, 0x00);

            // The last transmitted APDU should have CLA with secure messaging bit
            List<byte[]> apdus = stub.transmittedApdus();
            byte[] lastApdu = apdus.get(apdus.size() - 1);
            assertThat(lastApdu[0] & 0x04).isEqualTo(0x04); // bit 2 set
        }

        @Test
        void shouldThrowIfNotOpen() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            GPSession gp = GPSession.on(stub).keys(keys);

            assertThatThrownBy(() -> gp.send(0x80, 0xF2, 0x40, 0x00))
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("not open");
        }

        @Test
        void closeShouldPreventFurtherCommands() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            GPSession gp = GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .open();

            gp.close();
            assertThat(gp.isOpen()).isFalse();

            assertThatThrownBy(() -> gp.send(0x80, 0xF2, 0x40, 0x00))
                    .isInstanceOf(GPException.class);
        }
    }

    // ── GP Command Helpers ──

    @Nested
    class GpCommands {

        private GPSession openSession(GPStubSession stub) {
            return GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .open();
        }

        @Test
        void getStatusShouldParseTLVResponse() {
            // Synthetic GET STATUS response in TLV format:
            // E3 10 4F 07 A0000000031010 9F70 01 07 C5 01 00
            // Length: 4F(9) + 9F70(4) + C5(3) = 16 = 0x10
            byte[] tlvResponse = Hex.decode(
                    "E310" + "4F07A0000000031010" + "9F700107" + "C50100");

            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(tlvResponse, 0x9000);

            GPSession gp = openSession(stub);
            List<AppletInfo> apps = gp.getStatus(0x40);

            assertThat(apps).hasSize(1);
            assertThat(apps.get(0).aidHex()).isEqualTo("A0000000031010");
            assertThat(apps.get(0).lifeCycleState()).isEqualTo(0x07);
            assertThat(apps.get(0).isSelectable()).isTrue();
            assertThat(apps.get(0).privileges()).isEqualTo(0x00);
        }

        @Test
        void getStatusShouldParseMultipleEntries() {
            // Two E3 entries, each length = 0x10
            byte[] tlvResponse = Hex.decode(
                    "E310" + "4F07A0000000031010" + "9F700107" + "C50100"
                            + "E310" + "4F07A0000000041010" + "9F700103" + "C50100");

            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(tlvResponse, 0x9000);

            GPSession gp = openSession(stub);
            List<AppletInfo> apps = gp.getStatus();

            assertThat(apps).hasSize(2);
            assertThat(apps.get(0).aidHex()).isEqualTo("A0000000031010");
            assertThat(apps.get(1).aidHex()).isEqualTo("A0000000041010");
            assertThat(apps.get(1).isSelectable()).isFalse(); // 0x03 = INSTALLED, not SELECTABLE
        }

        @Test
        void deleteAidShouldSendCorrectCommand() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000);

            GPSession gp = openSession(stub);
            APDUResponse r = gp.deleteAid("A0000000031010");

            assertThat(r.isSuccess()).isTrue();
            // Verify the transmitted APDU contains the DELETE command
            List<byte[]> apdus = stub.transmittedApdus();
            byte[] lastApdu = apdus.get(apdus.size() - 1);
            assertThat(lastApdu[1] & 0xFF).isEqualTo(0xE4); // INS=DELETE
        }

        @Test
        void installForLoadShouldSendCorrectCommand() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000);

            GPSession gp = openSession(stub);
            APDUResponse r = gp.installForLoad("A000000003", null);

            assertThat(r.isSuccess()).isTrue();
            List<byte[]> apdus = stub.transmittedApdus();
            byte[] lastApdu = apdus.get(apdus.size() - 1);
            assertThat(lastApdu[1] & 0xFF).isEqualTo(0xE6); // INS=INSTALL
            assertThat(lastApdu[2] & 0xFF).isEqualTo(0x02); // P1=for load
        }

        @Test
        void installForInstallShouldSendCorrectCommand() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000);

            GPSession gp = openSession(stub);
            APDUResponse r = gp.installForInstall(
                    "A000000003", "A00000000301", "A00000000301",
                    0x00, null);

            assertThat(r.isSuccess()).isTrue();
            List<byte[]> apdus = stub.transmittedApdus();
            byte[] lastApdu = apdus.get(apdus.size() - 1);
            assertThat(lastApdu[1] & 0xFF).isEqualTo(0xE6); // INS=INSTALL
            assertThat(lastApdu[2] & 0xFF).isEqualTo(0x0C); // P1=for install + make selectable
        }

        @Test
        void storeDataShouldSendSingleBlock() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000);

            GPSession gp = openSession(stub);
            gp.storeData(new byte[100]);

            // Should be one STORE DATA command (100 bytes < 247)
            List<byte[]> apdus = stub.transmittedApdus();
            byte[] lastApdu = apdus.get(apdus.size() - 1);
            assertThat(lastApdu[1] & 0xFF).isEqualTo(0xE2); // INS=STORE DATA
            assertThat(lastApdu[2] & 0xFF).isEqualTo(0x80); // P1=last block
        }

        @Test
        void storeDataShouldChainLargePayload() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000); // first block
            stub.queueResponse(0x9000); // second (last) block

            GPSession gp = openSession(stub);
            gp.storeData(new byte[400]); // 400 > 247, needs 2 blocks

            // Count STORE DATA commands (INS=E2)
            long storeDataCount = stub.transmittedApdus().stream()
                    .filter(a -> a.length > 1 && (a[1] & 0xFF) == 0xE2)
                    .count();
            assertThat(storeDataCount).isEqualTo(2);
        }
    }

    // ── LOAD command tests ──

    @Nested
    class LoadCommand {

        private GPSession openSession(GPStubSession stub) {
            return GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .open();
        }

        private CAPFile buildTestCap() throws IOException {
            byte[] header = new byte[20];
            header[10] = 0; // minor
            header[11] = 1; // major
            header[12] = 7; // AID length
            System.arraycopy(Hex.decode("A0000000031010"), 0, header, 13, 7);

            byte[] classComp = new byte[50];
            Arrays.fill(classComp, (byte) 0xCC);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                zos.putNextEntry(new ZipEntry("com/test/javacard/Header.cap"));
                zos.write(header);
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("com/test/javacard/Class.cap"));
                zos.write(classComp);
                zos.closeEntry();
            }
            return CAPFile.from(baos.toByteArray());
        }

        @Test
        void shouldLoadSingleBlock() throws IOException {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000); // LOAD response

            GPSession gp = openSession(stub);
            CAPFile cap = buildTestCap();
            APDUResponse r = gp.load(cap);

            assertThat(r.isSuccess()).isTrue();

            // Should have exactly 1 LOAD command (small data fits in one block)
            long loadCount = stub.transmittedApdus().stream()
                    .filter(a -> a.length > 1 && (a[1] & 0xFF) == 0xE8)
                    .count();
            assertThat(loadCount).isEqualTo(1);

            // P1 should be 0x80 (last block) since it's the only block
            byte[] loadApdu = stub.transmittedApdus().stream()
                    .filter(a -> a.length > 1 && (a[1] & 0xFF) == 0xE8)
                    .findFirst().orElseThrow();
            assertThat(loadApdu[2] & 0xFF).isEqualTo(0x80); // P1=last
            assertThat(loadApdu[3] & 0xFF).isEqualTo(0x00); // P2=block 0
        }

        @Test
        void shouldLoadMultipleBlocks() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000); // block 0
            stub.queueResponse(0x9000); // block 1
            stub.queueResponse(0x9000); // block 2

            GPSession gp = openSession(stub);

            // Create load data larger than 247*2 = 494 bytes → needs 3 blocks
            byte[] largeData = new byte[600];
            Arrays.fill(largeData, (byte) 0xAA);
            gp.load(largeData);

            List<byte[]> loadApdus = stub.transmittedApdus().stream()
                    .filter(a -> a.length > 1 && (a[1] & 0xFF) == 0xE8)
                    .toList();

            assertThat(loadApdus).hasSize(3);

            // First blocks: P1=0x00 (more blocks), last: P1=0x80
            assertThat(loadApdus.get(0)[2] & 0xFF).isEqualTo(0x00);
            assertThat(loadApdus.get(1)[2] & 0xFF).isEqualTo(0x00);
            assertThat(loadApdus.get(2)[2] & 0xFF).isEqualTo(0x80);

            // P2 = block number
            assertThat(loadApdus.get(0)[3] & 0xFF).isEqualTo(0x00);
            assertThat(loadApdus.get(1)[3] & 0xFF).isEqualTo(0x01);
            assertThat(loadApdus.get(2)[3] & 0xFF).isEqualTo(0x02);
        }

        @Test
        void shouldFailOnLoadError() throws IOException {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x6985); // conditions not satisfied

            GPSession gp = openSession(stub);
            CAPFile cap = buildTestCap();

            assertThatThrownBy(() -> gp.load(cap))
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("LOAD failed");
        }

        @Test
        void shouldLoadAndInstall() throws IOException {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000); // INSTALL [for load]
            stub.queueResponse(0x9000); // LOAD
            stub.queueResponse(0x9000); // INSTALL [for install]

            GPSession gp = openSession(stub);
            CAPFile cap = buildTestCap();
            APDUResponse r = gp.loadAndInstall(cap, "A0000000031010", 0x00, null);

            assertThat(r.isSuccess()).isTrue();

            // Verify command sequence: E6 (install for load) → E8 (load) → E6 (install for install)
            List<Integer> instructions = stub.transmittedApdus().stream()
                    .filter(a -> a.length > 1)
                    .map(a -> a[1] & 0xFF)
                    .filter(ins -> ins == 0xE6 || ins == 0xE8)
                    .toList();

            assertThat(instructions).containsExactly(0xE6, 0xE8, 0xE6);
        }

        @Test
        void loadAndInstallShouldUsePackageAidAsDefault() throws IOException {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000); // INSTALL [for load]
            stub.queueResponse(0x9000); // LOAD
            stub.queueResponse(0x9000); // INSTALL [for install]

            GPSession gp = openSession(stub);
            CAPFile cap = buildTestCap();

            // instanceAidHex = null → should use package AID
            APDUResponse r = gp.loadAndInstall(cap, null, 0x00, null);
            assertThat(r.isSuccess()).isTrue();
        }
    }

    // ── PUT KEY tests ──

    @Nested
    class KeyManagement {

        private GPSession openSession(GPStubSession stub) {
            return GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .open();
        }

        @Test
        void shouldPutKeysSCP02() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000); // PUT KEY response

            GPSession gp = openSession(stub);
            SCPKeys newKeys = SCPKeys.fromMasterKey(Hex.decode("00112233445566778899AABBCCDDEEFF"));
            APDUResponse r = gp.putKeys(newKeys, 0x01);

            assertThat(r.isSuccess()).isTrue();

            // Verify PUT KEY APDU was sent
            byte[] lastApdu = stub.transmittedApdus().get(stub.transmittedApdus().size() - 1);
            assertThat(lastApdu[1] & 0xFF).isEqualTo(0xD8); // INS = PUT KEY
            assertThat(lastApdu[3] & 0xFF).isEqualTo(0x81); // P2 = 0x81 (index 1 | multiple)
        }

        @Test
        void shouldPutKeysSCP03() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 3);
            stub.queueResponse(0x9000); // PUT KEY response

            GPSession gp = openSession(stub);
            SCPKeys newKeys = SCPKeys.fromMasterKey(Hex.decode("00112233445566778899AABBCCDDEEFF"));
            APDUResponse r = gp.putKeys(newKeys, 0x01);

            assertThat(r.isSuccess()).isTrue();

            byte[] lastApdu = stub.transmittedApdus().get(stub.transmittedApdus().size() - 1);
            assertThat(lastApdu[1] & 0xFF).isEqualTo(0xD8); // INS = PUT KEY
        }

        @Test
        void putKeysShouldContainKeyData() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000);

            GPSession gp = openSession(stub);
            byte[] newKeyBytes = Hex.decode("00112233445566778899AABBCCDDEEFF");
            SCPKeys newKeys = SCPKeys.fromMasterKey(newKeyBytes);
            gp.putKeys(newKeys, 0x20);

            // Unwrap: the wrapped APDU data contains the PUT KEY payload
            // After CLA(1) INS(1) P1(1) P2(1) Lc(1) comes the data, then 8-byte MAC
            byte[] lastApdu = stub.transmittedApdus().get(stub.transmittedApdus().size() - 1);
            int lc = lastApdu[4] & 0xFF;
            // Data starts at offset 5, ends before MAC (last 8 bytes)
            int dataLen = lc - 8; // subtract MAC
            assertThat(dataLen).isGreaterThan(0);

            // First byte of data (after secure channel header) should be new key version
            // Note: data may be encrypted if C-ENC is on, but with C-MAC only it's plaintext
            // The data payload starts at offset 5
            byte[] payload = Arrays.copyOfRange(lastApdu, 5, 5 + dataLen);
            // First byte = new key version
            assertThat(payload[0] & 0xFF).isEqualTo(0x20);
            // Each DES3 key block: type(0x80) + len(0x10) + encrypted(16) + kcvLen(0x03) + kcv(3) = 22 bytes
            // Total: 1 (version) + 3 * 22 = 67 bytes
            assertThat(payload).hasSize(67);
        }

        @Test
        void putKeyShouldSendSingleKey() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000);

            GPSession gp = openSession(stub);
            byte[] newKey = Hex.decode("00112233445566778899AABBCCDDEEFF");
            APDUResponse r = gp.putKey(1, newKey, 0x01, 0x01);

            assertThat(r.isSuccess()).isTrue();

            byte[] lastApdu = stub.transmittedApdus().get(stub.transmittedApdus().size() - 1);
            assertThat(lastApdu[1] & 0xFF).isEqualTo(0xD8); // INS = PUT KEY
            assertThat(lastApdu[3] & 0xFF).isEqualTo(0x01); // P2 = key index (no 0x80)
        }

        @Test
        void shouldFailOnPutKeyError() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x6982); // security status not satisfied

            GPSession gp = openSession(stub);
            SCPKeys newKeys = SCPKeys.fromMasterKey(Hex.decode("00112233445566778899AABBCCDDEEFF"));

            assertThatThrownBy(() -> gp.putKeys(newKeys, 0x01))
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("PUT KEY failed");
        }

        @Test
        void shouldUseExistingKeyVersion() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000);

            GPSession gp = openSession(stub);
            SCPKeys newKeys = SCPKeys.fromMasterKey(Hex.decode("00112233445566778899AABBCCDDEEFF"));
            gp.putKeys(newKeys, 0x30, 0x20);

            // P1 should be the existing key version (0x20)
            byte[] lastApdu = stub.transmittedApdus().get(stub.transmittedApdus().size() - 1);
            assertThat(lastApdu[2] & 0xFF).isEqualTo(0x20); // P1 = existing version
        }
    }

    // ── Diversification tests ──

    @Nested
    class DiversificationTests {

        @Test
        void shouldOpenWithDiversification() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);

            // With diversification, the keys used for session creation are derived.
            // Our stub computes cryptograms from the original keys, so we need
            // a diversifier that returns the same keys (identity) for this test.
            GPSession gp = GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .diversification((masterKeys, divData) -> masterKeys) // identity
                    .open();

            assertThat(gp.isOpen()).isTrue();
        }

        @Test
        void diversificationShouldReceiveDivData() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);

            final byte[][] capturedDivData = {null};
            GPSession gp = GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .diversification((masterKeys, divData) -> {
                        capturedDivData[0] = divData;
                        return masterKeys; // identity, to not break auth
                    })
                    .open();

            // Div data should be first 10 bytes of INIT UPDATE response
            assertThat(capturedDivData[0]).isNotNull();
            assertThat(capturedDivData[0]).hasSize(10);
            assertThat(Hex.encode(capturedDivData[0])).isEqualTo(DIVERSIFICATION.toUpperCase());
        }
    }

    // ── R-MAC tests ──

    /**
     * Stub session that appends R-MAC to post-auth responses.
     * Computes R-MAC the same way as SCP02 does internally.
     */
    static class RmacStubSession extends GPStubSession {

        private final byte[] sessionRmacKey;
        private byte[] rmacChaining = new byte[8];

        RmacStubSession(SCPKeys keys, byte[] hostChallenge) {
            super(keys, hostChallenge, 2);
            // Derive R-MAC session key (same derivation as SCP02.from())
            byte[] seqCounter = Hex.decode(SEQ_COUNTER);
            this.sessionRmacKey = CryptoUtil.deriveSCP02SessionKey(
                    keys.mac(), seqCounter, GP.SCP02_DERIVE_R_MAC);
        }

        /** Queues a response with a valid R-MAC appended to the data. */
        RmacStubSession queueResponseWithRmac(byte[] plainData, int sw) {
            byte[] padded = CryptoUtil.pad80(plainData, 8);
            byte[] rmac = CryptoUtil.retailMac(sessionRmacKey, padded, rmacChaining);

            byte[] dataWithMac = new byte[plainData.length + 8];
            System.arraycopy(plainData, 0, dataWithMac, 0, plainData.length);
            System.arraycopy(rmac, 0, dataWithMac, plainData.length, 8);

            rmacChaining = rmac.clone();

            // Use parent's queueResponse with the combined data
            byte[] raw = new byte[dataWithMac.length + 2];
            System.arraycopy(dataWithMac, 0, raw, 0, dataWithMac.length);
            raw[dataWithMac.length] = (byte) ((sw >> 8) & 0xFF);
            raw[dataWithMac.length + 1] = (byte) (sw & 0xFF);
            postAuthResponses().add(raw);
            return this;
        }

        /** Queues a response with a bad (all-zero) R-MAC. */
        RmacStubSession queueResponseWithBadRmac(byte[] plainData, int sw) {
            byte[] dataWithMac = new byte[plainData.length + 8]; // last 8 bytes = zeros
            System.arraycopy(plainData, 0, dataWithMac, 0, plainData.length);

            byte[] raw = new byte[dataWithMac.length + 2];
            System.arraycopy(dataWithMac, 0, raw, 0, dataWithMac.length);
            raw[dataWithMac.length] = (byte) ((sw >> 8) & 0xFF);
            raw[dataWithMac.length + 1] = (byte) (sw & 0xFF);
            postAuthResponses().add(raw);
            return this;
        }

        /** Expose postAuthResponses for direct manipulation. */
        List<byte[]> postAuthResponses() {
            // Access via reflection-free approach: use the parent field directly
            // We need to access the parent's postAuthResponses list
            return postAuthResponsesList;
        }

        // Shadow parent's list to make it accessible
        private final List<byte[]> postAuthResponsesList = new ArrayList<>();

        @Override
        public byte[] transmit(byte[] rawApdu) {
            transmittedApdus().add(rawApdu.clone());

            // EXTERNAL AUTHENTICATE
            if (!isAuthenticated() && rawApdu.length > 1 && (rawApdu[1] & 0xFF) == 0x82) {
                setAuthenticated(true);
                return new byte[]{(byte) 0x90, 0x00};
            }

            // Post-auth
            if (isAuthenticated() && postAuthIdx < postAuthResponsesList.size()) {
                return postAuthResponsesList.get(postAuthIdx++);
            }

            return new byte[]{(byte) 0x90, 0x00};
        }

        private boolean authState = false;
        private int postAuthIdx = 0;

        private boolean isAuthenticated() { return authState; }
        private void setAuthenticated(boolean v) { authState = v; }
    }

    @Nested
    class ResponseMacTests {

        @Test
        void shouldUnwrapResponseWithRmac() {
            RmacStubSession stub = new RmacStubSession(keys, HOST_CHALLENGE);
            byte[] plainData = Hex.decode("0102030405");
            stub.queueResponseWithRmac(plainData, 0x9000);

            GPSession gp = GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .securityLevel(GP.SECURITY_C_MAC_C_ENC_R_MAC)
                    .open();

            APDUResponse r = gp.send(0x80, 0xF2, 0x40, 0x00);

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.data()).isEqualTo(plainData);
        }

        @Test
        void shouldPassThroughWithoutRmac() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(Hex.decode("0102030405"), 0x9000);

            GPSession gp = GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .securityLevel(GP.SECURITY_C_MAC)
                    .open();

            APDUResponse r = gp.send(0x80, 0xF2, 0x40, 0x00);

            // Without R-MAC, data should be returned as-is
            assertThat(r.isSuccess()).isTrue();
            assertThat(r.data()).isEqualTo(Hex.decode("0102030405"));
        }

        @Test
        void shouldRejectBadRmacInSession() {
            RmacStubSession stub = new RmacStubSession(keys, HOST_CHALLENGE);
            stub.queueResponseWithBadRmac(Hex.decode("0102030405"), 0x9000);

            GPSession gp = GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .securityLevel(GP.SECURITY_C_MAC_C_ENC_R_MAC)
                    .open();

            assertThatThrownBy(() -> gp.send(0x80, 0xF2, 0x40, 0x00))
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("Response MAC verification failed");
        }
    }

    // ── R-ENC tests ──

    /**
     * Stub session that encrypts and R-MACs post-auth responses (SCP03).
     */
    static class RencStubSession extends GPStubSession {

        private static final String CARD_CHALLENGE_SCP03 = "1122334455667788";

        private final byte[] sessionEncKey;
        private final byte[] sessionRmacKey;
        private byte[] rmacChaining = new byte[16];

        RencStubSession(SCPKeys keys, byte[] hostChallenge) {
            super(keys, hostChallenge, 3);

            // Derive SCP03 session keys same as SCP03.from() does
            byte[] cardChallenge = Hex.decode(CARD_CHALLENGE_SCP03);
            byte[] context = new byte[16];
            System.arraycopy(hostChallenge, 0, context, 0, 8);
            System.arraycopy(cardChallenge, 0, context, 8, 8);

            this.sessionEncKey = CryptoUtil.deriveSCP03SessionKey(
                    keys.enc(), context, GP.SCP03_DERIVE_ENC, 128);
            this.sessionRmacKey = CryptoUtil.deriveSCP03SessionKey(
                    keys.mac(), context, GP.SCP03_DERIVE_R_MAC, 128);
        }

        /** Queues a response with encrypted data + R-MAC. */
        RencStubSession queueResponseWithRenc(byte[] plainData, int sw) {
            // Encrypt: pad80 + AES-CBC(sessionEncKey, zero IV)
            byte[] padded = CryptoUtil.pad80(plainData, 16);
            byte[] encrypted = CryptoUtil.aesCbcEncrypt(sessionEncKey, padded);

            // R-MAC over encrypted data + SW
            byte[] macInput = new byte[rmacChaining.length + encrypted.length + 2];
            System.arraycopy(rmacChaining, 0, macInput, 0, rmacChaining.length);
            System.arraycopy(encrypted, 0, macInput, rmacChaining.length, encrypted.length);
            macInput[macInput.length - 2] = (byte) ((sw >> 8) & 0xFF);
            macInput[macInput.length - 1] = (byte) (sw & 0xFF);

            byte[] fullMac = CryptoUtil.aesCmac(sessionRmacKey, macInput);
            byte[] rmac = Arrays.copyOf(fullMac, 8);
            rmacChaining = fullMac.clone();

            // Wire: [encrypted] [R-MAC(8)] [SW1] [SW2]
            byte[] responseData = new byte[encrypted.length + 8];
            System.arraycopy(encrypted, 0, responseData, 0, encrypted.length);
            System.arraycopy(rmac, 0, responseData, encrypted.length, 8);

            byte[] raw = new byte[responseData.length + 2];
            System.arraycopy(responseData, 0, raw, 0, responseData.length);
            raw[responseData.length] = (byte) ((sw >> 8) & 0xFF);
            raw[responseData.length + 1] = (byte) (sw & 0xFF);
            rencResponses.add(raw);
            return this;
        }

        private final List<byte[]> rencResponses = new ArrayList<>();
        private boolean authState = false;
        private int postAuthIdx = 0;

        @Override
        public byte[] transmit(byte[] rawApdu) {
            transmittedApdus().add(rawApdu.clone());

            if (!authState && rawApdu.length > 1 && (rawApdu[1] & 0xFF) == 0x82) {
                authState = true;
                return new byte[]{(byte) 0x90, 0x00};
            }

            if (authState && postAuthIdx < rencResponses.size()) {
                return rencResponses.get(postAuthIdx++);
            }

            return new byte[]{(byte) 0x90, 0x00};
        }
    }

    @Nested
    class ResponseEncryptionTests {

        @Test
        void shouldDecryptResponseWithRenc() {
            RencStubSession stub = new RencStubSession(keys, HOST_CHALLENGE);
            byte[] plainData = Hex.decode("48656C6C6F"); // "Hello"
            stub.queueResponseWithRenc(plainData, 0x9000);

            GPSession gp = GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .securityLevel(GP.SECURITY_C_MAC_C_ENC_R_MAC_R_ENC)
                    .scpVersion(3)
                    .open();

            APDUResponse r = gp.send(0x80, 0xF2, 0x40, 0x00);

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.data()).isEqualTo(plainData);
        }

        @Test
        void shouldWorkWithFullProtection() {
            RencStubSession stub = new RencStubSession(keys, HOST_CHALLENGE);
            byte[] plainData = Hex.decode("0102030405060708090A0B0C0D0E0F");
            stub.queueResponseWithRenc(plainData, 0x9000);

            GPSession gp = GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .securityLevel(GP.SECURITY_C_MAC_C_ENC_R_MAC_R_ENC)
                    .scpVersion(3)
                    .open();

            APDUResponse r = gp.send(0x80, 0xCA, 0x00, 0xCF);

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.data()).isEqualTo(plainData);
        }
    }

    // ── SCP03 Pseudo-Random Challenge tests ──

    /**
     * Stub session that simulates SCP03 with pseudo-random challenge mode (i=60).
     * Builds a 29-byte INIT UPDATE response with seqCounter(3) + cardChallenge(6).
     */
    static class PseudoRandomStubSession extends GPStubSession {

        private static final String SEQ_COUNTER_PR = "001234";
        private static final String CARD_CHALLENGE_PR = "AABB11223344";

        private final SCPKeys stubKeys;

        PseudoRandomStubSession(SCPKeys keys, byte[] hostChallenge) {
            super(keys, hostChallenge, 3);
            this.stubKeys = keys;
        }

        @Override
        public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data) {
            if (cla == 0x80 && ins == 0x50) {
                return buildPseudoRandomInitUpdateResponse(data);
            }
            return new APDUResponse(new byte[0], 0x6D00);
        }

        private APDUResponse buildPseudoRandomInitUpdateResponse(byte[] hostCh) {
            byte[] div = Hex.decode(DIVERSIFICATION);
            byte[] seqCounter = Hex.decode(SEQ_COUNTER_PR);
            byte[] cardChallenge = Hex.decode(CARD_CHALLENGE_PR);

            // Context = hostChallenge(8) || seqCounter(3) || cardChallenge(6) = 17 bytes
            byte[] context = new byte[17];
            System.arraycopy(hostCh, 0, context, 0, 8);
            System.arraycopy(seqCounter, 0, context, 8, 3);
            System.arraycopy(cardChallenge, 0, context, 11, 6);

            byte[] cardCryptoFull = CryptoUtil.deriveSCP03SessionKey(
                    stubKeys.mac(), context, GP.SCP03_DERIVE_CARD_CRYPTO, 64);
            byte[] cardCryptogram = Arrays.copyOf(cardCryptoFull, 8);

            // Build 29-byte response: div(10) + keyInfo(2) + seqCounter(3) + cardChallenge(6) + cryptogram(8)
            byte[] response = new byte[29];
            System.arraycopy(div, 0, response, 0, 10);
            response[10] = 0x01; // key version
            response[11] = 0x03; // SCP03
            System.arraycopy(seqCounter, 0, response, 12, 3);
            System.arraycopy(cardChallenge, 0, response, 15, 6);
            System.arraycopy(cardCryptogram, 0, response, 21, 8);
            return new APDUResponse(response, 0x9000);
        }
    }

    @Nested
    class PseudoRandomChallengeTests {

        @Test
        void shouldOpenWithPseudoRandomChallenge() {
            PseudoRandomStubSession stub = new PseudoRandomStubSession(keys, HOST_CHALLENGE);

            GPSession gp = GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .scpVersion(3)
                    .pseudoRandomChallenge(true)
                    .open();

            assertThat(gp.isOpen()).isTrue();
            assertThat(gp.cardInfo().scpVersion()).isEqualTo(3);
            assertThat(gp.cardInfo().sequenceCounter()).hasSize(3);
            assertThat(gp.cardInfo().cardChallenge()).hasSize(6);
        }

        @Test
        void shouldSendWrappedCommandsInPseudoRandomMode() {
            PseudoRandomStubSession stub = new PseudoRandomStubSession(keys, HOST_CHALLENGE);
            stub.queueResponse(Hex.decode("0102"), 0x9000);

            GPSession gp = GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .scpVersion(3)
                    .pseudoRandomChallenge(true)
                    .open();

            APDUResponse r = gp.send(0x80, 0xF2, 0x40, 0x00);

            assertThat(r.isSuccess()).isTrue();
            assertThat(r.data()).isEqualTo(Hex.decode("0102"));

            // Verify wrapped APDU has secure messaging bit
            List<byte[]> apdus = stub.transmittedApdus();
            byte[] lastApdu = apdus.get(apdus.size() - 1);
            assertThat(lastApdu[0] & 0x04).isEqualTo(0x04);
        }
    }

    // ── GET DATA tests ──

    @Nested
    class GetDataTests {

        private GPSession openSession(GPStubSession stub) {
            return GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .open();
        }

        @Test
        void getDataShouldSendCorrectApdu() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(Hex.decode("0102"), 0x9000);

            GPSession gp = openSession(stub);
            gp.getData(0x00, 0x42);

            List<byte[]> apdus = stub.transmittedApdus();
            byte[] lastApdu = apdus.get(apdus.size() - 1);
            assertThat(lastApdu[1] & 0xFF).isEqualTo(0xCA); // INS = GET DATA
        }

        @Test
        void getIINShouldReturnBytes() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            byte[] iin = Hex.decode("0102030405060708");
            stub.queueResponse(iin, 0x9000);

            GPSession gp = openSession(stub);
            byte[] result = gp.getIIN();

            assertThat(result).isEqualTo(iin);
        }

        @Test
        void getCINShouldReturnBytes() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            byte[] cin = Hex.decode("AABBCCDD");
            stub.queueResponse(cin, 0x9000);

            GPSession gp = openSession(stub);
            byte[] result = gp.getCIN();

            assertThat(result).isEqualTo(cin);
        }

        @Test
        void getCardDataShouldParseTLV() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);

            // Build Card Data response: 66 [ 73 [ 06 OID ] ]
            byte[] response = TLVBuilder.create()
                    .addConstructed(Tags.GP_CARD_DATA, card -> card
                            .addConstructed(Tags.GP_CARD_RECOGNITION_DATA, rec -> rec
                                    .add(Tags.GP_OID, "2A864886FC6B01")))
                    .build();
            stub.queueResponse(response, 0x9000);

            GPSession gp = openSession(stub);
            CardData data = gp.getCardData();

            assertThat(data.recognitionData().isEmpty()).isFalse();
            assertThat(data.oidStrings()).contains("1.2.840.114283.1");
        }

        @Test
        void getKeyInformationShouldParseEntries() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);

            // Build Key Info Template: E0 [ C0(key1) C0(key2) ]
            byte[] response = TLVBuilder.create()
                    .addConstructed(Tags.GP_KEY_INFO_TEMPLATE, e0 -> e0
                            .add(Tags.GP_KEY_INFO_DATA, "01308010")      // key 1: DES3
                            .add(Tags.GP_KEY_INFO_DATA, "02208810"))     // key 2: AES
                    .build();
            stub.queueResponse(response, 0x9000);

            GPSession gp = openSession(stub);
            List<KeyInfoEntry> keys = gp.getKeyInformation();

            assertThat(keys).hasSize(2);
            assertThat(keys.get(0).keyId()).isEqualTo(1);
            assertThat(keys.get(0).components().get(0).isDes3()).isTrue();
            assertThat(keys.get(1).keyId()).isEqualTo(2);
            assertThat(keys.get(1).components().get(0).isAes()).isTrue();
        }

        @Test
        void getSequenceCounterShouldParseValue() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(Hex.decode("001234"), 0x9000); // counter = 0x1234

            GPSession gp = openSession(stub);
            int counter = gp.getSequenceCounter();

            assertThat(counter).isEqualTo(0x1234);
        }

        @Test
        void getDataShouldThrowOnFailure() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x6A88); // referenced data not found

            GPSession gp = openSession(stub);

            assertThatThrownBy(() -> gp.getIIN())
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("GET DATA");
        }

        @Test
        void getCPLCShouldParseResponse() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            // 38-byte CPLC data
            byte[] cplcData = Hex.decode(
                    "4790" + "6354" + "4700" + "3210" + "3F00"
                  + "5123" + "DEADBEEF" + "1234" + "0066"
                  + "5200" + "0033" + "5210" + "0011" + "5220"
                  + "AABB" + "0022" + "5230" + "CCDD");
            stub.queueResponse(cplcData, 0x9000);

            GPSession gp = openSession(stub);
            CPLCData cplc = gp.getCPLC();

            assertThat(cplc.icFabricator()).isEqualTo(0x4790);
            assertThat(cplc.serialNumberHex()).isEqualTo("DEADBEEF");
        }

        @Test
        void getCPLCShouldThrowOnFailure() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x6A88);

            GPSession gp = openSession(stub);

            assertThatThrownBy(() -> gp.getCPLC())
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("CPLC");
        }
    }

    // ── SET STATUS tests ──

    @Nested
    class SetStatus {

        private GPSession openSession(GPStubSession stub) {
            return GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .open();
        }

        @Test
        void setStatusShouldSendCorrectCommand() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000);

            GPSession gp = openSession(stub);
            gp.setStatus(Lifecycle.SCOPE_APPS, "A0000000031010", Lifecycle.APP_LOCKED);

            byte[] lastApdu = stub.transmittedApdus().get(stub.transmittedApdus().size() - 1);
            assertThat(lastApdu[1] & 0xFF).isEqualTo(0xF0); // INS = SET STATUS
            assertThat(lastApdu[2] & 0xFF).isEqualTo(0x40); // P1 = SCOPE_APPS
            assertThat(lastApdu[3] & 0xFF).isEqualTo(0x80); // P2 = APP_LOCKED
        }

        @Test
        void setStatusShouldIncludeAidInData() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000);

            GPSession gp = openSession(stub);
            gp.setStatus(Lifecycle.SCOPE_APPS, "A0000000031010", Lifecycle.APP_SELECTABLE);

            // Wrapped APDU: CLA INS P1 P2 Lc [data] [MAC(8)]
            byte[] lastApdu = stub.transmittedApdus().get(stub.transmittedApdus().size() - 1);
            int lc = lastApdu[4] & 0xFF;
            int dataLen = lc - 8; // subtract MAC
            assertThat(dataLen).isGreaterThan(0);

            // Data = 4F len AID
            byte[] payload = Arrays.copyOfRange(lastApdu, 5, 5 + dataLen);
            assertThat(payload[0] & 0xFF).isEqualTo(0x4F); // tag
            assertThat(payload[1] & 0xFF).isEqualTo(7);     // AID length
        }

        @Test
        void lockAppShouldUseScopeApps() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000);

            GPSession gp = openSession(stub);
            gp.lockApp("A0000000031010");

            byte[] lastApdu = stub.transmittedApdus().get(stub.transmittedApdus().size() - 1);
            assertThat(lastApdu[1] & 0xFF).isEqualTo(0xF0); // INS
            assertThat(lastApdu[2] & 0xFF).isEqualTo(0x40); // P1 = SCOPE_APPS
            assertThat(lastApdu[3] & 0xFF).isEqualTo(0x80); // P2 = APP_LOCKED
        }

        @Test
        void unlockAppShouldRevertToSelectable() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000);

            GPSession gp = openSession(stub);
            gp.unlockApp("A0000000031010");

            byte[] lastApdu = stub.transmittedApdus().get(stub.transmittedApdus().size() - 1);
            assertThat(lastApdu[3] & 0xFF).isEqualTo(0x07); // P2 = APP_SELECTABLE
        }

        @Test
        void terminateAppShouldSetTerminated() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000);

            GPSession gp = openSession(stub);
            gp.terminateApp("A0000000031010");

            byte[] lastApdu = stub.transmittedApdus().get(stub.transmittedApdus().size() - 1);
            assertThat(lastApdu[3] & 0xFF).isEqualTo(0xFF); // P2 = APP_TERMINATED
        }

        @Test
        void lockCardShouldUseIsdScope() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000);

            GPSession gp = openSession(stub);
            gp.lockCard();

            byte[] lastApdu = stub.transmittedApdus().get(stub.transmittedApdus().size() - 1);
            assertThat(lastApdu[1] & 0xFF).isEqualTo(0xF0); // INS = SET STATUS
            assertThat(lastApdu[2] & 0xFF).isEqualTo(0x80); // P1 = SCOPE_ISD
            assertThat(lastApdu[3] & 0xFF).isEqualTo(0x7F); // P2 = CARD_LOCKED
            // ISD commands have no data → Lc = 8 (MAC only)
            assertThat(lastApdu[4] & 0xFF).isEqualTo(8);
        }

        @Test
        void shouldFailOnSetStatusError() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x6985); // conditions not satisfied

            GPSession gp = openSession(stub);

            assertThatThrownBy(() -> gp.lockApp("A0000000031010"))
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("SET STATUS failed");
        }
    }

    // ── Domain Management tests ──

    @Nested
    class DomainManagement {

        private GPSession openSession(GPStubSession stub) {
            return GPSession.on(stub)
                    .keys(keys)
                    .hostChallenge(HOST_CHALLENGE)
                    .open();
        }

        @Test
        void getDomainsShouldFilterByPrivilege() {
            // Two entries: one SD (privileges=0x80), one regular app (privileges=0x00)
            byte[] tlvResponse = Hex.decode(
                    "E310" + "4F07A0000000031010" + "9F700107" + "C50180"  // SD
                  + "E310" + "4F07A0000000041010" + "9F700107" + "C50100");// app

            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(tlvResponse, 0x9000);

            GPSession gp = openSession(stub);
            List<AppletInfo> domains = gp.getDomains();

            assertThat(domains).hasSize(1);
            assertThat(domains.get(0).aidHex()).isEqualTo("A0000000031010");
            assertThat(domains.get(0).isSecurityDomain()).isTrue();
        }

        @Test
        void getLoadFilesShouldUseScope20() {
            byte[] tlvResponse = Hex.decode(
                    "E310" + "4F07A0000000031010" + "9F700107" + "C50100");

            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(tlvResponse, 0x9000);

            GPSession gp = openSession(stub);
            List<AppletInfo> loadFiles = gp.getLoadFiles();

            assertThat(loadFiles).hasSize(1);

            // Verify the GET STATUS was sent with P1=0x20 (Executable Load Files scope)
            byte[] getStatusApdu = stub.transmittedApdus().stream()
                    .filter(a -> a.length > 1 && (a[1] & 0xFF) == 0xF2)
                    .findFirst().orElseThrow();
            assertThat(getStatusApdu[2] & 0xFF).isEqualTo(0x20); // P1=scope for load files
        }

        @Test
        void extraditeShouldSendInstallP1_10() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000);

            GPSession gp = openSession(stub);
            APDUResponse r = gp.extradite("A0000000031010", "A0000000041010");

            assertThat(r.isSuccess()).isTrue();
            byte[] lastApdu = stub.transmittedApdus().get(stub.transmittedApdus().size() - 1);
            assertThat(lastApdu[1] & 0xFF).isEqualTo(0xE6); // INS=INSTALL
            assertThat(lastApdu[2] & 0xFF).isEqualTo(0x10); // P1=for extradition
        }

        @Test
        void personalizeShouldSendInstallP1_20() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000);

            GPSession gp = openSession(stub);
            APDUResponse r = gp.personalize("A0000000031010");

            assertThat(r.isSuccess()).isTrue();
            byte[] lastApdu = stub.transmittedApdus().get(stub.transmittedApdus().size() - 1);
            assertThat(lastApdu[1] & 0xFF).isEqualTo(0xE6); // INS=INSTALL
            assertThat(lastApdu[2] & 0xFF).isEqualTo(0x20); // P1=for personalization
        }

        @Test
        void registryUpdateShouldSendInstallP1_40() {
            GPStubSession stub = new GPStubSession(keys, HOST_CHALLENGE, 2);
            stub.queueResponse(0x9000);

            GPSession gp = openSession(stub);
            APDUResponse r = gp.registryUpdate("A0000000031010", 0xA0);

            assertThat(r.isSuccess()).isTrue();
            byte[] lastApdu = stub.transmittedApdus().get(stub.transmittedApdus().size() - 1);
            assertThat(lastApdu[1] & 0xFF).isEqualTo(0xE6); // INS=INSTALL
            assertThat(lastApdu[2] & 0xFF).isEqualTo(0x40); // P1=for registry update
        }
    }

    // ── AppletInfo tests ──

    @Nested
    class AppletInfoTests {

        @Test
        void isSelectableShouldReturnTrueForState07() {
            AppletInfo info = new AppletInfo(Hex.decode("A000000003"), 0x07, 0x00);
            assertThat(info.isSelectable()).isTrue();
        }

        @Test
        void isSelectableShouldReturnFalseForState03() {
            AppletInfo info = new AppletInfo(Hex.decode("A000000003"), 0x03, 0x00);
            assertThat(info.isSelectable()).isFalse();
        }

        @Test
        void isLockedShouldDetectBit7() {
            AppletInfo info = new AppletInfo(Hex.decode("A000000003"), 0x83, 0x00);
            assertThat(info.isLocked()).isTrue();
            // 0x83 & 0x07 = 0x03, NOT 0x07 → not selectable
            assertThat(info.isSelectable()).isFalse();
        }

        @Test
        void toStringShouldContainAid() {
            AppletInfo info = new AppletInfo(Hex.decode("A0000000031010"), 0x07, 0x00);
            assertThat(info.toString()).contains("A0000000031010");
        }

        @Test
        void isPersonalizedShouldReturnTrueForState0F() {
            AppletInfo info = new AppletInfo(Hex.decode("A000000003"), 0x0F, 0x00);
            assertThat(info.isPersonalized()).isTrue();
            // Not personalized for 0x07
            AppletInfo selectable = new AppletInfo(Hex.decode("A000000003"), 0x07, 0x00);
            assertThat(selectable.isPersonalized()).isFalse();
        }

        @Test
        void isTerminatedShouldReturnTrueForStateFF() {
            AppletInfo info = new AppletInfo(Hex.decode("A000000003"), 0xFF, 0x00);
            assertThat(info.isTerminated()).isTrue();
            // Not terminated for 0x07
            AppletInfo selectable = new AppletInfo(Hex.decode("A000000003"), 0x07, 0x00);
            assertThat(selectable.isTerminated()).isFalse();
        }

        @Test
        void lifeCycleDescriptionShouldDelegate() {
            AppletInfo info = new AppletInfo(Hex.decode("A000000003"), 0x07, 0x00);
            assertThat(info.lifeCycleDescription()).contains("SELECTABLE");
        }

        @Test
        void isSecurityDomainShouldDetect() {
            AppletInfo sd = new AppletInfo(Hex.decode("A000000003"), 0x07, 0x80);
            assertThat(sd.isSecurityDomain()).isTrue();

            AppletInfo app = new AppletInfo(Hex.decode("A000000003"), 0x07, 0x00);
            assertThat(app.isSecurityDomain()).isFalse();
        }

        @Test
        void hasDelegatedManagementShouldDetect() {
            AppletInfo sd = new AppletInfo(Hex.decode("A000000003"), 0x07, 0xA0);
            assertThat(sd.hasDelegatedManagement()).isTrue();

            AppletInfo app = new AppletInfo(Hex.decode("A000000003"), 0x07, 0x80);
            assertThat(app.hasDelegatedManagement()).isFalse();
        }

        @Test
        void privilegeDescriptionShouldFormat() {
            AppletInfo sd = new AppletInfo(Hex.decode("A000000003"), 0x07, 0xA0);
            assertThat(sd.privilegeDescription()).isEqualTo("SECURITY_DOMAIN | DELEGATED_MANAGEMENT");

            AppletInfo none = new AppletInfo(Hex.decode("A000000003"), 0x07, 0x00);
            assertThat(none.privilegeDescription()).isEqualTo("none");
        }
    }

    // ── CardInfo tests ──

    @Nested
    class CardInfoParsing {

        @Test
        void shouldParseSCP02Response() {
            byte[] response = Hex.decode(
                    DIVERSIFICATION + "0102" + SEQ_COUNTER + CARD_CHALLENGE_SCP02
                            + "AABBCCDDEEFF0011"); // fake cryptogram

            CardInfo info = CardInfo.parse(response);

            assertThat(info.keyVersion()).isEqualTo(1);
            assertThat(info.scpIdentifier()).isEqualTo(2);
            assertThat(info.scpVersion()).isEqualTo(2);
            assertThat(info.sequenceCounter()).isEqualTo(Hex.decode(SEQ_COUNTER));
            assertThat(info.cardChallenge()).isEqualTo(Hex.decode(CARD_CHALLENGE_SCP02));
            assertThat(info.diversificationHex()).isEqualTo(DIVERSIFICATION.toUpperCase());
        }

        @Test
        void shouldParseSCP03Response() {
            byte[] response = Hex.decode(
                    DIVERSIFICATION + "0103"
                            + "1122334455667788"   // card challenge (8 bytes for SCP03)
                            + "AABBCCDDEEFF0011"); // fake cryptogram

            CardInfo info = CardInfo.parse(response);

            assertThat(info.scpVersion()).isEqualTo(3);
            assertThat(info.sequenceCounter()).isEmpty();
            assertThat(info.cardChallenge()).hasSize(8);
        }

        @Test
        void shouldRejectShortResponse() {
            assertThatThrownBy(() -> CardInfo.parse(new byte[20]))
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("too short");
        }

        @Test
        void toStringShouldContainKeyInfo() {
            byte[] response = Hex.decode(
                    DIVERSIFICATION + "2002" + SEQ_COUNTER + CARD_CHALLENGE_SCP02
                            + "AABBCCDDEEFF0011");

            CardInfo info = CardInfo.parse(response);

            assertThat(info.toString()).contains("keyVersion=32");
            assertThat(info.toString()).contains("scp=2");
        }

        @Test
        void shouldParseSCP03PseudoRandomResponse() {
            // 29-byte response: div(10) + keyInfo(2) + seqCounter(3) + cardChallenge(6) + cryptogram(8)
            byte[] response = Hex.decode(
                    DIVERSIFICATION + "0103"
                            + "001234"               // sequence counter (3 bytes)
                            + "AABB11223344"          // card challenge (6 bytes)
                            + "AABBCCDDEEFF0011");    // fake cryptogram (8 bytes)

            CardInfo info = CardInfo.parse(response, true);

            assertThat(info.scpVersion()).isEqualTo(3);
            assertThat(info.sequenceCounter()).isEqualTo(Hex.decode("001234"));
            assertThat(info.cardChallenge()).isEqualTo(Hex.decode("AABB11223344"));
            assertThat(info.cardCryptogram()).isEqualTo(Hex.decode("AABBCCDDEEFF0011"));
        }

        @Test
        void pseudoRandomShouldRejectShortResponse() {
            // 28 bytes — too short for pseudo-random (needs 29)
            byte[] response = Hex.decode(
                    DIVERSIFICATION + "0103"
                            + "1122334455667788"
                            + "AABBCCDDEEFF0011");

            assertThatThrownBy(() -> CardInfo.parse(response, true))
                    .isInstanceOf(GPException.class)
                    .hasMessageContaining("pseudo-random");
        }
    }
}
