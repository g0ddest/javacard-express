# JavaCard Express :: Core

[![Maven Central](https://img.shields.io/maven-central/v/name.velikodniy/javacard-express-core)](https://search.maven.org/artifact/name.velikodniy/javacard-express-core)
[![javadoc](https://javadoc.io/badge2/name.velikodniy/javacard-express-core/javadoc.svg)](https://javadoc.io/doc/name.velikodniy/javacard-express-core)

Core module — provides the `SmartCardSession` interface, embedded jCardSim backend, APDU builder/codec/sequence, BER-TLV parser with path navigation, fluent AssertJ assertions, PIN helpers, logical channels, APDU logging, memory probing, well-known AIDs, and more.

## Installation

```xml
<dependency>
    <groupId>name.velikodniy</groupId>
    <artifactId>javacard-express-core</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
```

## Table of Contents

- [SmartCardSession](#smartcardsession)
  - [Composing Decorators](#composing-decorators)
- [Assertions](#assertions)
- [APDU Builder](#apdu-builder)
  - [Extended APDU](#extended-apdu)
- [APDU Sequence](#apdu-sequence)
- [TLV Parser](#tlv-parser)
  - [Path Navigation](#path-navigation)
  - [TLV Assertions](#tlv-assertions)
- [PIN Helper](#pin-helper)
- [Logical Channels](#logical-channels)
- [APDU Logging](#apdu-logging)
- [Memory Probing](#memory-probing)
- [AID Utilities](#aid-utilities)
  - [Well-Known AIDs](#well-known-aids)
- [See Also](#see-also)

## SmartCardSession

The core interface for all card interactions. Provides send/transmit for APDUs, install/select for applet lifecycle, and fluent decorator methods:

```java
// Install and send
card.install(MyApplet.class);
APDUResponse r = card.send(0x80, 0x01).requireSuccess();  // throws on non-9000

// Fluent decorators — one call to enable features
LoggingSession logged = card.logged();                     // APDU logging
PinSession pin = card.pin();                               // PIN operations
LoggingSession verbose = card.logged(true);                // + console output

// All send() overloads
card.send(0x80, 0x01);                                    // CLA + INS
card.send(0x80, 0x01, 0x00, 0x00);                        // + P1 + P2
card.send(0x80, 0x01, 0x00, 0x00, data);                  // + data
card.send(0x80, 0x01, 0x00, 0x00, data, 256);             // + Le

// Raw transmit (no parsing, returns raw bytes)
byte[] raw = card.transmit(rawApduBytes);

// Lifecycle
card.install(MyApplet.class, AID.fromHex("A000..."));     // explicit AID
card.install(MyApplet.class, aid, installParams);          // with parameters
card.select(AID.fromHex("A0000000031010"));                // select by AID
card.reset();                                              // reset card state
```

In embedded mode, `SmartCardSession` is backed by jCardSim running in-process. In container mode, it communicates over TCP with a Docker container. The API is identical in both modes.

### Composing Decorators

Decorators wrap a session to add behavior. Chain them in any order:

```java
// Logging + PIN
LoggingSession logged = card.logged(true);
PinSession pin = PinSession.on(logged);
pin.verify(1, "1234");
// Console shows: >> 00 20 00 01 04 31323334  << 9000

// Or directly from the session
card.pin().verify(1, "1234");
card.logged().send(0x80, 0x01);  // logged, one-off
```

Decorator order matters for wrapping layers — SM and GP decorators are in their own modules to avoid circular dependencies. See [SM module](../sm/README.md) and [GP module](../gp/README.md).

### Complete Test Lifecycle

```java
import name.velikodniy.jcexpress.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;

@ExtendWith(JavaCardExtension.class)
class MyAppletTest {

    @SmartCard
    SmartCardSession card;

    @Test
    void shouldProcessCommand() {
        card.install(MyApplet.class);

        // requireSuccess() throws if SW != 9000
        byte[] data = card.send(0x80, 0x01).requireSuccess().data();

        // Or assert fluently
        assertThat(card.send(0x80, 0x01))
            .isSuccess()
            .dataAsString().isEqualTo("Hello");
    }

    @Test
    void shouldSurviveReset() {
        card.install(MyApplet.class);
        card.send(0x80, 0x01).requireSuccess();

        card.reset();
        card.select(MyApplet.class);
        card.send(0x80, 0x01).requireSuccess();
    }
}
```

## Assertions

Rich AssertJ-based assertions with descriptions for 26 standard ISO 7816-4 status words:

```java
import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;

// Status word assertions
assertThat(response).isSuccess();                              // SW == 9000
assertThat(response).statusWord(0x6D00);                       // exact SW
assertThat(response).hasSw1(0x61);                             // SW1 only (e.g., 61XX)

// Data assertions
assertThat(response).hasDataLength(5);
assertThat(response).dataEquals(0x48, 0x65, 0x6C, 0x6C, 0x6F);
assertThat(response).dataStartsWith(0x48, 0x65);              // prefix match
assertThat(response).dataEndsWith(0x6C, 0x6F);                // suffix match
assertThat(response).dataAsString().isEqualTo("Hello");
assertThat(response).dataAsHex().isEqualTo("48656C6C6F");

// TLV assertions (parses data as BER-TLV)
assertThat(response).tlvContains(0x6F);                        // top-level tag check
assertThat(response).tlv()                                     // navigate into TLV tree
    .containsTag(0x6F)
    .tag(0x6F).isConstructed()
        .tag(0x84).hasValue("A0000000031010");
```

On failure, you get clear messages:

```
Expected success (SW=9000) but was SW=6A82 (file not found)
Expected SW1=61 but was SW1=6A (full SW=6A82, file not found)
Expected data to start with [48 65] but was [414243]
Expected TLV data to contain tag 6F but found tags: TLVList[TLV[84, 7 bytes]]
```

## APDU Builder

Fluent builder for ISO 7816-4 commands. CLA/INS/P1/P2 are validated (0x00-0xFF range):

```java
// Fluent construction
APDUResponse r = APDUBuilder.command()
    .cla(0x80).ins(0xB0).p1(0x00).p2(0x00)
    .data(Hex.decode("0102030405"))
    .le(256)
    .sendTo(card);

// Factory methods for common commands
APDUBuilder.select("A0000000031010").sendTo(card);
APDUBuilder.getData(0x00, 0x66).sendTo(card);
APDUBuilder.getResponse(256).sendTo(card);

// Build raw bytes
byte[] apdu = APDUBuilder.command()
    .cla(0x00).ins(0xA4).p1(0x04).p2(0x00)
    .data("A0000000031010")    // hex string accepted
    .build();

// Logical channel encoding
byte[] apdu = APDUBuilder.command()
    .cla(0x00).ins(0xA4).p1(0x04).p2(0x00)
    .channel(2)                // CLA bits [1:0] = channel number
    .build();
```

### Extended APDU

Short vs extended format is chosen automatically based on data size and Le:

```java
// Short APDU (data <= 255, Le <= 256)
byte[] shortApdu = APDUBuilder.command()
    .cla(0x80).ins(0x01).p1(0x00).p2(0x00)
    .data(smallData).le(256).build();
// -> CLA INS P1 P2 Lc(1) Data Le(1)

// Extended APDU — automatic when data > 255 or Le > 256
byte[] extApdu = APDUBuilder.command()
    .cla(0x80).ins(0x01).p1(0x00).p2(0x00)
    .data(largeData).le(4096).build();
// -> CLA INS P1 P2 0x00 Lc(2) Data Le(2)

// Low-level codec
byte[] apdu = APDUCodec.encode(0x80, 0x01, 0x00, 0x00, data, le);
boolean ext = APDUCodec.isExtended(apdu);
byte[] corrected = APDUCodec.correctLe(apdu, newLe);
```

## APDU Sequence

Automatic GET RESPONSE chaining (SW=61XX) and Le correction (SW=6CXX):

```java
APDUResponse full = APDUSequence.on(card).transmit(rawApdu);

APDUResponse r = APDUSequence.on(card)
    .maxChain(5)
    .transmit(rawApdu);

APDUResponse r = APDUSequence.on(card)
    .send(0x80, 0xF2, 0x40, 0x00, data);
```

Used internally by `GPSession` — you get seamless chaining automatically.

## TLV Parser

Full BER-TLV: multi-byte tags, multi-byte lengths, constructed elements, recursive search, and path navigation:

```java
// Parse from bytes, hex string, or APDUResponse
TLVList list = TLVParser.parse(bytes);
TLVList list = TLVParser.parse("6F 10 84 07 A0...");
TLVList list = response.tlv();

// Find by tag
TLV fci = list.find(0x6F).orElseThrow();
TLV aid = fci.find(0x84).orElseThrow();
String aidHex = aid.valueHex();

// Recursive search (finds at any depth)
TLV deep = list.findRecursive(0x84).orElseThrow();

// Build TLV data
byte[] data = TLVBuilder.create()
    .add(0x84, "A0000000031010")
    .addConstructed(0x6F, b -> b
        .add(0x88, new byte[]{0x01})
    )
    .build();
```

### Path Navigation

Navigate nested TLV structures with `at()`:

```java
// Before — manual nesting
TLV fci = list.find(0x6F).orElseThrow();
TLV aid = fci.find(0x84).orElseThrow();
byte[] val = aid.value();

// After — path-based access
byte[] val = list.at(0x6F, 0x84).orElseThrow().value();

// Deep paths work too
Optional<TLV> deep = list.at(0x6F, 0xA5, 0x88);  // FCI -> Proprietary -> SFI
```

### TLV Assertions

```java
assertThat(response).tlv()
    .containsTag(0x6F)
    .tag(0x6F).isConstructed()
        .tag(0x84).hasValue("A0000000031010").hasLength(7);

TLVList list = TLVParser.parse(bytes);
assertThat(list).hasSize(3).containsTag(0x84);
```

## PIN Helper

ISO 7816-4 PIN operations:

```java
PinSession pin = card.pin();             // or PinSession.on(card)

pin.verify(1, "1234");                   // VERIFY (INS=0x20)
pin.change(1, "1234", "5678");           // CHANGE REFERENCE DATA
pin.changeWithoutOldPin(1, "5678");      // P1=01, new PIN only
pin.unblock(1, "12345678", "1234");      // RESET RETRY COUNTER

int retries = pin.retriesRemaining(1);   // parses SW 63CX -> X
boolean blocked = pin.isBlocked(1);      // SW == 6983

// Encoding formats
PinSession bcd = card.pin().format(PinFormat.BCD);
PinSession iso = card.pin().format(PinFormat.ISO_9564_FORMAT_2);
```

Supported formats: `ASCII` (default), `BCD`, `ISO_9564_FORMAT_2`.

### Complete PIN Lifecycle

```java
@Test
void shouldHandleFullPinLifecycle() {
    card.install(PinApplet.class);
    PinSession pin = card.pin();

    pin.verify(1, "1234");

    APDUResponse wrong = pin.verify(1, "0000");
    assertThat(wrong).statusWord(0x63C2);         // 2 retries left

    assertThat(pin.retriesRemaining(1)).isEqualTo(2);

    pin.verify(1, "0000");
    pin.verify(1, "0000");
    assertThat(pin.isBlocked(1)).isTrue();

    pin.unblock(1, "12345678", "5678");
    assertThat(pin.verify(1, "5678")).isSuccess();
}
```

## Logical Channels

ISO 7816-4 channels 0-3 for multi-session interaction:

```java
// Basic channel — wraps session on a specific channel
LogicalChannel ch1 = LogicalChannel.basic(card, 1);
ch1.select(AID.fromHex("A0000000031010"));
APDUResponse r = ch1.send(0x00, 0x01);  // CLA -> 0x01

// Managed channel — MANAGE CHANNEL OPEN/CLOSE
try (LogicalChannel ch = LogicalChannel.open(card)) {
    ch.select(AID.fromHex("A0000000041010"));
    ch.send(0x00, 0x02);
}  // auto-close sends MANAGE CHANNEL CLOSE
```

## APDU Logging

Capture and inspect APDU exchanges:

```java
LoggingSession logged = card.logged();     // or card.logged(true) for console

logged.send(0x00, 0xA4, 0x04, 0x00, aidBytes);
logged.send(0x80, 0x01);

List<APDULogEntry> all = logged.entries();
List<APDULogEntry> selects = logged.entries(0xA4);
APDULogEntry last = logged.lastEntry();

System.out.println(logged.dump());
// [0] >> 00 A4 04 00 07 A0 00 00 00 03 10 10
//     << 90 00
// [1] >> 80 01 00 00
//     << 01 02 [9000] 2 bytes
```

Enable via annotation: `@SmartCard(log = true)` or globally: `-Djcx.log=true`.

## Memory Probing

Measure applet memory footprint:

```java
card.install(MemoryProbeApplet.class);
MemoryInfo mem = MemoryInfo.from(card.send(0x80, 0x01));

System.out.println("Persistent: " + mem.persistent() + " bytes");
System.out.println("Transient reset: " + mem.transientReset() + " bytes");

assertThat(mem).persistentAtLeast(1024);
assertThat(mem).persistentBelow(65536);
```

## AID Utilities

```java
AID.fromHex("A0000000031010");            // from hex string
AID.of(0xA0, 0x00, 0x00, 0x00, 0x03);    // from individual bytes
AID.auto(MyApplet.class);                 // deterministic SHA-1 from class name

// Prefix matching
AID visa = AID.fromHex("A0000000031010");
AID visaPrefix = AID.fromHex("A000000003");
visa.startsWith(visaPrefix);               // true
```

AIDs are validated per ISO 7816-4 (5-16 bytes).

### Well-Known AIDs

Constants for common smart card applications:

```java
WellKnownAIDs.MRTD          // A0000002471001  — ePassport (ICAO 9303)
WellKnownAIDs.VISA          // A0000000031010  — Visa credit/debit
WellKnownAIDs.MASTERCARD    // A0000000041010  — Mastercard credit/debit
WellKnownAIDs.AMEX          // A000000025...   — American Express
WellKnownAIDs.GP_ISD        // A000000151000000 — GlobalPlatform ISD

// Usage
card.select(WellKnownAIDs.MRTD);
```

## See Also

- [GlobalPlatform module](../gp/README.md) — SCP02/SCP03 secure channels, card management
- [Secure Messaging module](../sm/README.md) — ISO 7816-4 SM (ePassport BAC, PACE)
- [PACE module](../pace/README.md) — Password Authenticated Connection Establishment
- [Container module](../container/README.md) — Docker-based sessions with Testcontainers
- [Project root](../README.md) — overview, modules, configuration
