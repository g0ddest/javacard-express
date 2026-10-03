package name.velikodniy.jcexpress.readme;

import javacard.framework.Applet;
import javacard.framework.ISO7816;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.APDULogEntry;
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.Hex;
import name.velikodniy.jcexpress.JavaCardExtension;
import name.velikodniy.jcexpress.LogicalChannel;
import name.velikodniy.jcexpress.LoggingSession;
import name.velikodniy.jcexpress.PinApplet;
import name.velikodniy.jcexpress.SW;
import name.velikodniy.jcexpress.SelectException;
import name.velikodniy.jcexpress.SmartCard;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.WellKnownAIDs;
import name.velikodniy.jcexpress.apdu.APDUBuilder;
import name.velikodniy.jcexpress.apdu.APDUCodec;
import name.velikodniy.jcexpress.apdu.APDUCommand;
import name.velikodniy.jcexpress.apdu.APDUSequence;
import name.velikodniy.jcexpress.fakes.ContractCardTerminal;
import name.velikodniy.jcexpress.memory.MemoryInfo;
import name.velikodniy.jcexpress.memory.MemoryProbeApplet;
import name.velikodniy.jcexpress.pcsc.PcscSession;
import name.velikodniy.jcexpress.pin.PinFormat;
import name.velikodniy.jcexpress.pin.PinSession;
import name.velikodniy.jcexpress.tlv.TLV;
import name.velikodniy.jcexpress.tlv.TLVBuilder;
import name.velikodniy.jcexpress.tlv.TLVList;
import name.velikodniy.jcexpress.tlv.TLVParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The code snippets of core/README.md, section by section, with the README's variable names. Snippets for a
 * card reader run on {@link ContractCardTerminal} (the javax.smartcardio contract fake) instead of
 * {@code PcscSession.open()}. If a README claim stops being true, a test here fails.
 */
@ExtendWith(JavaCardExtension.class)
class ReadmeSnippetsTest {

    static final APDUCommand GET_DATA = APDUCommand.of(0x80, 0xCA).p1p2(0x00, 0x66).le(256);

    @SmartCard
    SmartCardSession card;

    @Test
    void smartCardSession() {
        byte[] data = {0x01, 0x02};
        byte[] rawApduBytes = Hex.decode("80010000");
        AID aid = AID.fromHex("F000000002");
        byte[] installParams = {0x11, 0x22};

        // Install and send
        card.install(MyApplet.class);
        APDUResponse r = card.send(0x80, 0x01).requireSuccess();  // throws on non-9000

        // Fluent decorators
        LoggingSession logged = card.logged();
        PinSession pin = card.pin();
        LoggingSession verbose = card.logged(true);

        // All send() overloads
        card.send(0x80, 0x01);
        card.send(0x80, 0x01, 0x00, 0x00);
        card.send(0x80, 0x01, 0x00, 0x00, data);
        card.send(0x80, 0x01, 0x00, 0x00, data, 256);
        APDUResponse echoed = card.send(APDUCommand.of(0x80, 0x02).data(0x01, 0x02).le(256));
        APDUResponse hello = card.sendHex("80 01 00 00");

        // Raw transmit
        byte[] raw = card.transmit(rawApduBytes);

        // Lifecycle
        card.install(MyApplet.class, AID.fromHex("F000000001"));
        card.install(MyApplet.class, aid, installParams);
        APDUResponse fci = card.send(APDUCommand.select(aid));     // a SELECT whose response (the FCI) the test checks
        card.select(AID.fromHex("F000000001"));
        card.reset();

        assertThat(r).dataAsString().isEqualTo("Hello");
        assertThat(echoed).hasDataHex("0102");
        assertThat(hello).dataAsString().isEqualTo("Hello");
        assertThat(new APDUResponse(raw)).isSuccess();
        assertThat(logged.delegate()).isSameAs(card);
        assertThat(pin).isNotNull();
        assertThat(verbose.entries()).isEmpty();
        assertThat(fci).isSuccess();
        card.select(aid);
        assertThat(card.send(0x80, 0x02, 0x00, 0x00, data, 256)).dataEquals(0x01, 0x02);
    }

    /** README table "Command encoding and Le". */
    @Test
    void leTable() {
        card.install(MyApplet.class);
        LoggingSession logged = card.logged();
        byte[] data = {0x01};

        logged.send(0x80, 0x02, 0x00, 0x00, data, SmartCardSession.NO_LE);
        logged.send(0x80, 0x02, 0x00, 0x00, data, 256);
        logged.send(0x80, 0x01, 0x00, 0x00, null, 257);
        logged.send(0x80, 0x01, 0x00, 0x00, null, 0);

        assertThat(logged.entries()).extracting(APDULogEntry::commandHex).containsExactly(
                "80 02 00 00 01 01", "80 02 00 00 01 01 00", "80 01 00 00 00 01 01", "80 01 00 00 00");
    }

    /** README table "Backends", EmbeddedSession column. */
    @Test
    void embeddedBackendColumn() {
        card.install(MyApplet.class);

        assertThatThrownBy(() -> card.select(AID.fromHex("F0000000FF"))).isInstanceOf(SelectException.class);
        assertThatThrownBy(() -> LogicalChannel.open(card)).isInstanceOf(UnsupportedOperationException.class);
        card.install(MemoryProbeApplet.class);
        assertThatThrownBy(() -> MemoryInfo.from(card.send(0x80, 0x01)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void pcscSnippets() {
        ContractCardTerminal reader = new ContractCardTerminal("T=1",
                (channel, apdu) -> apdu[1] == (byte) 0xCA ? Hex.decode("6603730100 9000") : Hex.decode("9000"));
        byte[] hostChallenge = Hex.decode("0102030405060708");

        try (var card = PcscSession.open(reader)) {
            card.select(AID.fromHex("A000000151000000"));
            APDUResponse r = card.send(0x80, 0xCA, 0x00, 0x66, null, 256);  // GET DATA Card Data, Le '00'
            System.out.println("Card data: " + r.dataAsHex());
            assertThat(r.dataAsHex()).isEqualTo("6603730100");
        }
        try (var card = PcscSession.open(reader, PcscSession.Options.defaults().shared())) {
            System.out.println("ATR: " + Hex.encode(card.getATR()));
        }
        try (var card = PcscSession.open(reader)) {
            card.logged(true).send(0x80, 0x50, 0x00, 0x00, hostChallenge, 256);
        }

        assertThat(reader.wire).extracting(Hex::encodeSpaced).containsExactly(
                "00 A4 04 00 08 A0 00 00 01 51 00 00 00 00",
                "80 CA 00 66 00",
                "80 50 00 00 08 01 02 03 04 05 06 07 08 00");
    }

    @Test
    void composingDecorators() {
        card.install(PinApplet.class);

        // Logging + PIN
        LoggingSession logged = card.logged(true);
        PinSession pin = PinSession.on(logged);
        pin.verify(1, "1234");

        // Or directly from the session
        card.pin().verify(1, "1234");
        card.logged().send(0x80, 0x01);  // logged, one-off

        assertThat(logged.lastEntry().commandHex()).isEqualTo("00 20 00 01 04 31 32 33 34");
        assertThat(logged.lastEntry().response()).isSuccess();
    }

    @Test
    void assertions() {
        card.install(MyApplet.class);
        APDUResponse response = card.send(0x80, 0x01);
        APDUResponse unknownIns = card.send(0x80, 0x7E);

        assertThat(response).isSuccess();
        assertThat(response).hasStatusWord(SW.NO_ERROR);
        assertThat(unknownIns).hasStatusWord(ISO7816.SW_INS_NOT_SUPPORTED);
        assertThat(unknownIns).isNotSuccess().hasSw1(0x6D).hasSw2(0x00);
        assertThat(response).hasStatusWordIn(0x9000, 0x6310);
        assertThat(card.send(0x80, 0x7E)).statusWord(0x6D00);
        assertThat(new APDUResponse(new byte[0], 0x6110)).hasSw1(0x61);

        assertThat(response).hasDataLength(5);
        assertThat(response).dataEquals(0x48, 0x65, 0x6C, 0x6C, 0x6F);
        assertThat(response).hasDataHex("48 65 6C 6C 6F");
        assertThat(response).hasData(new byte[]{0x48, 0x65, 0x6C, 0x6C, 0x6F});
        assertThat(response).dataStartsWith(0x48, 0x65);
        assertThat(response).dataEndsWith(0x6C, 0x6F);
        assertThat(response).dataAsString().isEqualTo("Hello");
        assertThat(response).dataAsHex().isEqualTo("48656C6C6F");
        assertThat(response).data().hasSize(5);
        assertThat(response).u16(0).isEqualTo(0x4865);
        assertThat(unknownIns).hasNoData();

        APDUResponse fci = new APDUResponse(Hex.decode("6F098407A0000000031010"), 0x9000);
        assertThat(fci).tlvContains(0x6F);
        assertThat(fci).tlv()
                .containsTag(0x6F)
                .tag(0x6F).isConstructed()
                .tag(0x84).hasValue("A0000000031010");
    }

    /** README: the meanings of status words and the readers of APDUResponse. */
    @Test
    void statusWordNamesAndResponseReaders() {
        card.install(MyApplet.class);
        APDUResponse response = card.send(0x80, 0x01);

        assertThat(SW.SECURITY_STATUS_NOT_SATISFIED).isEqualTo(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED & 0xFFFF);
        assertThat(SW.describe(0x6A82)).isEqualTo("file or application not found");
        assertThat(SW.format(0x6A82)).isEqualTo("6A82 (file or application not found)");
        assertThat(response.u8(0)).isEqualTo(0x48);
        assertThat(response.u16(0)).isEqualTo(0x4865);
        assertThat(response.s16(0)).isEqualTo(0x4865);
        assertThat(response.data(1, 3)).containsExactly(0x65, 0x6C);
        assertThat(response.requireSw(SW.NO_ERROR, 0x6310)).isSameAs(response);
        assertThatThrownBy(() -> card.send(0x80, 0x7E).requireSw(SW.NO_ERROR, 0x6310))
                .isInstanceOf(AssertionError.class);
    }

    /** README: status word and data mismatches carry expected and actual values for an IDE. */
    @Test
    void mismatchesCarryExpectedAndActualValues() {
        APDUResponse notFound = new APDUResponse(new byte[0], 0x6A82);

        assertThatThrownBy(() -> assertThat(notFound).isSuccess())
                .isInstanceOf(org.opentest4j.AssertionFailedError.class)
                .satisfies(e -> {
                    org.opentest4j.AssertionFailedError failure = (org.opentest4j.AssertionFailedError) e;
                    assertThat(failure.getExpected().getValue()).isEqualTo("9000 (success)");
                    assertThat(failure.getActual().getValue()).isEqualTo("6A82 (file or application not found)");
                });
    }

    @Test
    void assertionFailureMessages() {
        APDUResponse notFound = new APDUResponse(new byte[0], 0x6A82);

        assertThatThrownBy(() -> assertThat(notFound).isSuccess())
                .hasMessage("Expected success (SW=9000) but was SW=6A82 (file or application not found)");
        assertThatThrownBy(() -> assertThat(notFound).hasSw1(0x61))
                .hasMessage("Expected SW1=61 but was SW1=6A (full SW=6A82, file or application not found)");
        assertThatThrownBy(() -> assertThat(new APDUResponse(Hex.decode("414243"), 0x9000)).dataStartsWith(0x48, 0x65))
                .hasMessage("Expected data to start with [48 65] but was [414243]");
        assertThatThrownBy(() -> assertThat(new APDUResponse(Hex.decode("8407A0000000031010"), 0x9000)).tlvContains(0x6F))
                .hasMessage("Expected TLV data to contain tag 6F but found tags: TLVList[TLV[84, 7 bytes]]");
    }

    @Test
    void apduBuilder() {
        card.install(MyApplet.class);

        APDUResponse r = APDUBuilder.command()
                .cla(0x80).ins(0x02).p1(0x00).p2(0x00)
                .data(Hex.decode("0102030405"))
                .le(256)
                .sendTo(card);

        APDUBuilder.select("A0000000031010").sendTo(card);
        APDUBuilder.getData(0x00, 0x66).sendTo(card);
        APDUBuilder.getResponse(256).sendTo(card);

        byte[] apdu = APDUBuilder.command()
                .cla(0x00).ins(0xA4).p1(0x04).p2(0x00)
                .data("A0000000031010")
                .build();
        byte[] onChannel2 = APDUBuilder.command()
                .cla(0x00).ins(0xA4).p1(0x04).p2(0x00)
                .channel(2)
                .build();

        assertThat(r).dataEquals(0x01, 0x02, 0x03, 0x04, 0x05);
        assertThat(Hex.encode(apdu)).isEqualTo("00A4040007A0000000031010");
        assertThat(onChannel2[0]).isEqualTo((byte) 0x02);
        assertThat(APDUBuilder.command().cla(0x00).channel(4).build()[0]).isEqualTo((byte) 0x40);
        assertThat(APDUBuilder.command().cla(0x80).channel(4).build()[0]).isEqualTo((byte) 0xC0);
    }

    @Test
    void apduCommandsAsValues() {
        card.install(MyApplet.class);

        APDUResponse cardData = card.send(GET_DATA);
        APDUResponse echoed = card.send(APDUCommand.of(0x80, 0x02).data(0x01, 0x02).le(256));
        APDUResponse parsed = card.send(APDUCommand.fromHex("80 02 00 00 02 0102 00"));
        APDUResponse sent = card.sendHex("80 02 00 00 02 0102 00");
        byte[] bytes = GET_DATA.toBytes();
        AID aid = AID.auto(MyApplet.class);
        APDUResponse fci = card.send(APDUCommand.select(aid));      // 00 A4 04 00 Lc AID 00: SELECT and its FCI

        assertThat(fci).isSuccess();
        assertThat(cardData).hasStatusWord(SW.INS_NOT_SUPPORTED);   // MyApplet answers INS 01 and 02 only
        assertThat(echoed).isEqualTo(parsed).isEqualTo(sent);
        assertThat(echoed).hasDataHex("0102");
        assertThat(Hex.encode(bytes)).isEqualTo("80CA006600");
        assertThat(card.send((byte) 0x80, (byte) 0x01)).isSuccess();
    }

    @Test
    void extendedApdu() {
        byte[] smallData = new byte[10];
        byte[] largeData = new byte[300];
        byte[] data = {0x01};
        int sw2 = 0x00;

        byte[] shortApdu = APDUBuilder.command()
                .cla(0x80).ins(0x01).p1(0x00).p2(0x00)
                .data(smallData).le(256).build();
        byte[] extApdu = APDUBuilder.command()
                .cla(0x80).ins(0x01).p1(0x00).p2(0x00)
                .data(largeData).le(4096).build();

        byte[] apdu = APDUCodec.encode(0x80, 0x01, 0x00, 0x00, data, 256);
        boolean ext = APDUCodec.isExtended(apdu);
        byte[] corrected = APDUCodec.correctLe(apdu, sw2);

        assertThat(shortApdu).hasSize(4 + 1 + 10 + 1);
        assertThat(extApdu).hasSize(4 + 3 + 300 + 2);
        assertThat(Hex.encode(extApdu)).startsWith("80010000" + "00012C").endsWith("1000");
        assertThat(ext).isFalse();
        assertThat(Hex.encode(corrected)).isEqualTo("80010000010100");
    }

    @Test
    void apduSequence() {
        card.install(MyApplet.class);
        byte[] rawApdu = Hex.decode("8001000000");
        byte[] data = {0x01};

        APDUResponse full = APDUSequence.on(card).transmit(rawApdu);
        APDUResponse r = APDUSequence.on(card)
                .maxChain(512)
                .transmit(rawApdu);
        APDUResponse r2 = APDUSequence.on(card)
                .send(0x80, 0xF2, 0x40, 0x00, data, 256);

        assertThat(full).dataAsString().isEqualTo("Hello");
        assertThat(r).isSuccess();
        assertThat(r2).statusWord(0x6D00);
    }

    @Test
    void tlvParser() {
        byte[] bytes = Hex.decode("6F098407A0000000031010");
        APDUResponse response = new APDUResponse(bytes, 0x9000);

        TLVList list = TLVParser.parse(bytes);
        TLVList fromHex = TLVParser.parse("6F 09 84 07 A0 00 00 00 03 10 10");
        TLVList fromResponse = response.tlv();

        TLV fci = list.find(0x6F).orElseThrow();
        TLV aid = fci.find(0x84).orElseThrow();
        String aidHex = aid.valueHex();
        TLV deep = list.findRecursive(0x84).orElseThrow();

        byte[] data = TLVBuilder.create()
                .add(0x84, "A0000000031010")
                .addConstructed(0xA5, b -> b
                        .add(0x88, new byte[]{0x01})
                )
                .build();

        assertThat(aidHex).isEqualTo("A0000000031010");
        assertThat(deep.valueHex()).isEqualTo(aidHex);
        assertThat(fromHex.size()).isEqualTo(fromResponse.size());
        assertThat(Hex.encode(data)).isEqualTo("8407A0000000031010" + "A503880101");
        assertThatThrownBy(() -> TLVParser.parse("DF 81 82 03 01 02 03")).hasMessageContaining("three bytes");
    }

    @Test
    void pathNavigationAndTlvAssertions() {
        TLVList list = TLVParser.parse("6F 0E 84 07 A0 00 00 00 03 10 10 A5 03 88 01 01");
        APDUResponse response = new APDUResponse(Hex.decode("6F098407A0000000031010"), 0x9000);

        byte[] val = list.at(0x6F, 0x84).orElseThrow().value();
        Optional<TLV> deep = list.at(0x6F, 0xA5, 0x88);

        assertThat(val).hasSize(7);
        assertThat(deep).map(TLV::valueHex).contains("01");
        assertThat(response).tlv()
                .containsTag(0x6F)
                .tag(0x6F).isConstructed()
                .tag(0x84).hasValue("A0000000031010").hasLength(7);
        TLVList three = TLVParser.parse("84 01 01 85 01 02 86 01 03");
        assertThat(three).hasSize(3).containsTag(0x84);
    }

    @Test
    void pinHelper() {
        card.install(PinApplet.class);
        PinSession pin = card.pin();

        pin.verify(1, "1234");
        pin.change(1, "1234", "5678");
        pin.changeWithoutOldPin(1, "5678");
        pin.unblock(1, "12345678", "1234");

        OptionalInt left = pin.retries(1);
        boolean verified = pin.isVerified(1);
        int retries = pin.retriesRemaining(1);
        boolean blocked = pin.isBlocked(1);
        PinSession piv = card.pin().cla(0x80).padTo(8, 0xFF);
        PinSession bcd = card.pin().format(PinFormat.BCD);
        PinSession iso = card.pin().format(PinFormat.ISO_9564_FORMAT_2);

        assertThat(left).hasValue(3);
        assertThat(verified).isFalse();
        assertThat(retries).isEqualTo(3);
        assertThat(blocked).isFalse();
        assertThat(piv).isNotSameAs(bcd);
        assertThat(bcd).isNotSameAs(iso);
    }

    @Test
    void logicalChannelsOnPcsc() {
        ContractCardTerminal reader = new ContractCardTerminal("T=1",
                ContractCardTerminal.cardAssignsChannels((channel, apdu) -> Hex.decode("9000")));

        try (PcscSession card = PcscSession.open(reader)) {
            try (LogicalChannel ch = LogicalChannel.open(card)) {
                ch.select(AID.fromHex("A0000000041010"));
                ch.send(0x80, 0x02);
            }
            LogicalChannel ch1 = LogicalChannel.basic(card, 1);
            assertThat(ch1.channelNumber()).isEqualTo(1);
        }

        assertThat(reader.wire).extracting(Hex::encodeSpaced).containsExactly(
                "00 70 00 00 01", "01 A4 04 00 07 A0 00 00 00 04 10 10 00", "81 02 00 00", "01 70 80 01");
    }

    @Test
    void apduLogging() {
        card.install(MyApplet.class, AID.fromHex("F000000001"));
        byte[] aidBytes = AID.fromHex("F000000001").toBytes();

        LoggingSession logged = card.logged();
        logged.send(0x00, 0xA4, 0x04, 0x00, aidBytes);
        logged.send(0x80, 0x01);

        List<APDULogEntry> all = logged.entries();
        List<APDULogEntry> selects = logged.entries(0xA4);
        APDULogEntry last = logged.lastEntry();

        assertThat(all).hasSize(2);
        assertThat(selects).hasSize(1);
        assertThat(last.ins()).isEqualTo(0x01);
        assertThat(logged.dump()).isEqualTo("""
                C: 00A4040005F000000001
                R: 9000
                C: 80010000
                R: 48656C6C6F9000
                """);
    }

    /** The memory snippet against a card that reports memory (jCardSim does not, see embeddedBackendColumn). */
    @Test
    void memoryProbing() {
        SmartCardSession card = new MemoryReportingCard();

        card.install(MemoryProbeApplet.class);
        MemoryInfo before = MemoryInfo.from(card.send(0x80, 0x01));

        card.install(MyApplet.class);
        card.select(MemoryProbeApplet.class);
        MemoryInfo after = MemoryInfo.from(card.send(0x80, 0x01));

        assertThat(after)
                .persistentConsumedAtMost(before, 4096)
                .transientDeselectConsumedAtMost(before, 256)
                .persistentAtLeast(16384);
    }

    @Test
    void aidUtilities() {
        AID.fromHex("A0000000031010");
        AID.of(0xA0, 0x00, 0x00, 0x00, 0x03);
        AID.auto(MyApplet.class);

        AID visa = AID.fromHex("A0000000031010");
        AID visaPrefix = AID.fromHex("A000000003");

        assertThat(visa.startsWith(visaPrefix)).isTrue();
        assertThatThrownBy(() -> AID.fromHex("A0000000")).isInstanceOf(IllegalArgumentException.class);
        card.install(MyApplet.class, WellKnownAIDs.MRTD);
        card.select(WellKnownAIDs.MRTD);
        assertThat(WellKnownAIDs.GP_ISD.toHex()).isEqualTo("A000000151000000");
    }

    /** A card whose probe reports 50 000 / 1000 / 800 bytes and, after MyApplet, 46 000 / 900 / 800 bytes. */
    private static final class MemoryReportingCard implements SmartCardSession {
        private int installed;

        @Override
        public void install(Class<? extends Applet> appletClass) {
            installed++;
        }

        @Override
        public void install(Class<? extends Applet> appletClass, AID aid) {
            installed++;
        }

        @Override
        public void install(Class<? extends Applet> appletClass, AID aid, byte[] installParams) {
            installed++;
        }

        @Override
        public void select(Class<? extends Applet> appletClass) {
            // the probe stays selected
        }

        @Override
        public void select(AID aid) {
            // the probe stays selected
        }

        @Override
        public void reset() {
            // nothing to reset
        }

        @Override
        public APDUResponse send(int cla, int ins, int p1, int p2, byte[] data, int le) {
            String figures = installed == 1 ? "0000C350" + "000003E8" + "00000320" : "0000B3B0" + "00000384" + "00000320";
            return new APDUResponse(Hex.decode(figures), 0x9000);
        }

        @Override
        public byte[] transmit(byte[] rawApdu) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            // nothing to release
        }
    }
}
