# JavaCard Express :: Core

[![Maven Central](https://img.shields.io/maven-central/v/name.velikodniy/javacard-express-core)](https://search.maven.org/artifact/name.velikodniy/javacard-express-core)
[![javadoc](https://javadoc.io/badge2/name.velikodniy/javacard-express-core/javadoc.svg)](https://javadoc.io/doc/name.velikodniy/javacard-express-core)

Core module — provides the `SmartCardSession` interface, the embedded jCardSim backend, a PC/SC backend for real cards, the JUnit 5 extension, APDU builder/codec/sequence and command values (`APDUCommand`), status word names (`SW`), BER-TLV parser with path navigation, fluent AssertJ assertions, PIN helpers, logical channels, APDU logging, memory probing and well-known AIDs.

Recipes for everyday applet tests: the [testing cookbook](../TESTING.md).

## Installation

```xml
<dependency>
    <groupId>name.velikodniy</groupId>
    <artifactId>javacard-express-core</artifactId>
    <version>0.4.0</version>
    <scope>test</scope>
</dependency>
```

This one dependency is enough to write tests: it brings jCardSim, the JUnit Jupiter API and AssertJ. Maven Surefire 3.x adds the matching JUnit Jupiter engine automatically; with other runners add `org.junit.jupiter:junit-jupiter-engine` (test scope).

## Table of Contents

- [Declarative Card Tests](#declarative-card-tests)
  - [Scenarios](#scenarios)
  - [Backends](#backends-of-javacardtest-classes)
  - [Lifetimes, Selection and AIDs](#lifetimes-selection-and-aids)
  - [Diagnostics and Parallel Runs](#diagnostics-and-parallel-runs)
- [SmartCardSession](#smartcardsession)
  - [Command encoding and Le](#command-encoding-and-le)
  - [Backends](#backends)
  - [PC/SC — Physical Card Reader](#pcsc--physical-card-reader)
  - [Composing Decorators](#composing-decorators)
- [Assertions](#assertions)
- [APDU Builder](#apdu-builder)
  - [APDU Commands as Values](#apdu-commands-as-values)
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
- [Low-level: Sessions without @JavaCardTest](#low-level-sessions-without-javacardtest)
  - [Complete Test Lifecycle](#complete-test-lifecycle)
- [See Also](#see-also)

## Declarative Card Tests

A test class marked `@JavaCardTest` declares the applets it needs with `@InstallApplet`; the extension installs
them before their scope and deletes them after it, also when a test fails. Tests receive the card as a
`SmartCardSession` parameter. The same class runs on jCardSim, on a simulated GlobalPlatform card and on a real
card; the backend is chosen when the tests run. The GlobalPlatform backends add a few rules (commands an applet's own
INS must avoid in a proprietary class, AIDs, one package at a time, the basic channel):
[On the GlobalPlatform backends](../README.md#on-the-globalplatform-backends).

```java
@JavaCardTest
@InstallApplet(CounterApplet.class)                  // a fresh instance for every test, deleted afterwards
class CounterAppletTest {

    @Test
    void startsAtZero(SmartCardSession card) {
        assertThat(card.send(0x80, 0x02, 0, 0, null, 4)).isSuccess().dataEquals(0, 0, 0, 0);
    }

    @Test
    void increments(SmartCardSession card) {
        card.send(0x80, 0x01);
        assertThat(card.send(0x80, 0x02, 0, 0, null, 4)).dataEquals(0, 0, 0, 1);
    }
}
```

### Scenarios

**One instance for the whole class**, deleted after it (state carries over, so order the tests). The first
`PER_CLASS` applet is selected after the class's installs, so `@BeforeAll` methods can set it up, and it stays
selected from test to test until a test selects something else, resets or deselects, so its `CLEAR_ON_DESELECT`
memory (a verified PIN) carries over too ([cookbook](../TESTING.md#a-scenario-across-tests)):

```java
@JavaCardTest
@InstallApplet(value = CounterApplet.class, isolation = Isolation.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CounterScenarioTest { ... }
```

**Several instances**, with install parameters (the GlobalPlatform C9 value the applet's `install` receives); the
same package is loaded once:

```java
@JavaCardTest
@InstallApplet(value = PurseApplet.class, aid = "0101", params = "AABBCC")
@InstallApplet(value = PurseApplet.class, aid = "0102", params = "112233")
class PurseTest {
    @Test
    void secondInstanceHasItsOwnParameters(SmartCardSession card) {
        card.select(card.aid("0102"));
        ...
    }
}
```

**An applet for one test only**, on the method; it is selected before the test:

```java
@Test
@InstallApplet(AuditApplet.class)
void auditSeesThePurse(SmartCardSession card) { ... }
```

**Nested classes** see the instances of their enclosing classes and may declare their own, deleted when the
nested class ends:

```java
@Nested
@InstallApplet(value = LoyaltyApplet.class, isolation = Isolation.PER_CLASS)
class WithLoyalty { ... }
```

**Parameterized and repeated tests**: every invocation counts as a test, so each gets a fresh `PER_TEST` instance.

**Computed install parameters** (a peer's AID, keys): install imperatively in `@BeforeAll` or `@BeforeEach`; the
instance is deleted with that scope, like a declared one. On the GlobalPlatform backends too: they load a package
with all its applets, so any applet of it can be installed later:

```java
@BeforeEach
void installClient(SmartCardSession card) {
    card.install(ClientApplet.class, card.aid("0201"), card.aid(LedgerApplet.class).toBytes());
}
```

**Backend-specific tests**: `@DisabledOnBackend(value = {Mode.EMBEDDED, Mode.SIMULATED_GP}, reason = "jCardSim does
not roll back JCSystem.abortTransaction()")` for a jCardSim deviation (both backends run the applets on jCardSim),
or `@EnabledOnBackend({Mode.SIMULATED_GP, Mode.LIVECARD})` for a test that takes a `LiveCard` parameter (card
content, secure channel; see the [live-card guide](../LIVE_CARD_TESTING.md)).

A `@SmartCard SmartCardSession` field of a `@JavaCardTest` class receives the same card as the parameters.

### Backends of @JavaCardTest classes

| `jcx.backend` | Runs on | Needs |
|---|---|---|
| `embedded` (default) | jCardSim in the test JVM; every test class run gets its own simulator, which loads the applet classes itself | this module |
| `container` | planned: the container protocol cannot delete applets yet, which per-test isolation needs; use `@SmartCard(mode = Mode.CONTAINER)` fields meanwhile | `javacard-express-container`, Docker |
| `simulated-gp` | a simulated GlobalPlatform card behind the live-card harness: conversion, APDU guard, SCP03, LOAD/INSTALL/DELETE, cleanup checked with GET STATUS; the applets then run from their class files on jCardSim (the converted bytecode runs only on a card); no reader, fine on CI | `javacard-express-livecard` (the applet parent has it) |
| `livecard` | the card in a PC/SC reader ([live-card guide](../LIVE_CARD_TESTING.md#your-tests-on-a-card)) | `javacard-express-livecard` (the applet parent has it), a development card, Oracle's off-card verifier (`jcx.livecard.verifierSdk`, or `none`), a build for the card's Java Card version |

`jcx.backend` comes from the system property, the JUnit configuration parameter or the environment variable
`JCX_BACKEND`; `livecard` only from a JVM system property (`mvn verify -Djcx.backend=livecard`, or a VM option
of an IDE run). A backend whose module is missing fails with the dependency to add.

### Lifetimes, Selection and AIDs

- A `PER_CLASS` instance exists from before the first `@BeforeAll` method until after the last `@AfterAll`
  method; a `PER_TEST` or method-level instance from before the first `@BeforeEach` until after the last
  `@AfterEach`. Instances are deleted in reverse order of installation.
- The package (load file) of an applet is loaded once per test class run on every backend: static fields start
  fresh for the class and keep their values across its tests, as on a card where the package stays loaded.
- An `@InstallApplet` install selects nothing, and installs and deletes deselect the selected applet, on every
  backend (as card content management through the Issuer Security Domain does); `card.install(...)` then selects
  the instance it installs, so after one in `@BeforeEach` the test starts with that instance selected. After the
  `PER_CLASS` instances of a class are installed, the first of them is selected, so `@BeforeAll` methods can talk
  to it. Before every test the first applet of the innermost scope (the method, then the nearest class) is
  selected, unless it still is the selected applet: a `PER_CLASS` instance keeps its CLEAR_ON_DESELECT memory
  between tests until another SELECT, `reset()`, `deselect()`, an install or a delete.
- `card.select(Class)` selects the class's instance, `card.deselect()` deselects (CLEAR_ON_DESELECT memory is
  cleared). With several instances of a class, `card.select(Class)` and `card.aid(Class)` refuse to choose and list
  the instances; name one by its suffix, `card.select(card.aid("0102"))`.
- AIDs start with the run's prefix: `jcx.aidPrefix` (hex) if set (on a real card the live-card setting
  `aidPrefix` wins); otherwise the project's own prefix, `F0` + 4 bytes of SHA-256 of `groupId:artifactId`, when
  the first declared applet comes from a package the Maven plugin built (its build descriptor
  `META-INF/javacard/<package>.properties` names the project); otherwise `F04A4358`. Package and applet AIDs are
  derived from the package and class names, the instance AID is the applet AID or prefix + `aid` suffix.
  `card.aid(CounterApplet.class)` and `card.aid("0102")` give them, so tests contain no literal AIDs and address
  the same AIDs on every backend. The history notes the AIDs the build gave a built applet next to the test AIDs.
- The GlobalPlatform backends convert a package the plugin built with the settings of its build descriptor
  (package version, `supportInt32`; `javaCardVersion` is the run's conversion target unless the live-card setting
  `javaCardVersion` or `jcx.livecard.javaCardVersion` names another), so a test on a card runs the package as it
  was built. Applets in test sources are converted with the backends' defaults. The backends convert every applet
  of the package (those of the build descriptor, or every concrete `Applet` subclass with an `install` method) and
  only the classes those applets need, from every class path entry that holds the package (classes directories and
  jars, main and test sources), so test classes next to a test applet are not converted. A package that imports
  another package of the project is refused before conversion; such tests run only on the default backend for now.
- Two declarations with the same instance AID are a configuration error that names the fix (give one an `aid`);
  identical repeated annotations count once.

### Diagnostics and Parallel Runs

Every session keeps a bounded history of its exchanges, each with the time its command was sent and how long it took
(`card.history().last()` is the newest); inside a test, `card.history()` of a `@JavaCardTest` class holds that test's
exchanges (from its SELECT on), in `@BeforeAll` and `@AfterAll` methods those of the class. A failed test carries the
last exchanges in its failure (a suppressed exception) and publishes `apdu-transcript.txt` with JUnit's file entries
(Maven Surefire writes it below `target/junit-jupiter/`; only failed tests write one); failures of `@BeforeAll`,
`@BeforeEach`, `@AfterEach` and `@AfterAll` methods and of declared installs carry the exchanges as well. In the
transcript, `# test body: <test>()` marks where the test method starts; on the GlobalPlatform backends notes label the
card management (`# install`, `# load`, `# delete`, `# secure channel`); when the run installed no applet, a note at the
top says so. On the embedded backend, an exception that escapes the applet's `process` or `select` is noted next to the
`6F00` it causes, with the applet's line ([cookbook](../TESTING.md#debugging-a-6f00)). The response lines of the
transcripts show the time of the exchange, and `-Djcx.log=true` prints the exchanges while the tests run (see
[APDU Logging](#apdu-logging)).

The methods of a `@JavaCardTest` class run one at a time in the class's thread (a JUnit resource lock per test
class); with `jcx.backend=livecard` all classes share one lock, because they share the card.

## SmartCardSession

The core interface for all card interactions. Provides send/transmit for APDUs, install/select for applet lifecycle, and fluent decorator methods:

```java
// Install and send
card.install(MyApplet.class);
APDUResponse r = card.send(0x80, 0x01).requireSuccess();  // throws on non-9000

// Fluent decorators — one call to enable features
LoggingSession logged = card.logged();                     // APDU logging
PinSession pin = card.pin();                               // PIN operations
LoggingSession verbose = card.logged(true);                // + every line printed (see APDU Logging)

// All send() overloads
card.send(0x80, 0x01);                                    // CLA + INS (no data, no Le)
card.send(0x80, 0x01, 0x00, 0x00);                        // + P1 + P2
card.send(0x80, 0x01, 0x00, 0x00, data);                  // + data
card.send(0x80, 0x01, 0x00, 0x00, data, 256);             // + Ne = 256 (Le '00')
card.send(APDUCommand.of(0x80, 0x02).data(0x01, 0x02).le(256));   // a command value (see APDU Commands as Values)
card.sendHex("80 01 00 00");                              // a command written as hex
card.send(APDUCommand.select(aid));                       // a SELECT whose response (the FCI) the test checks

// Raw transmit (no parsing, returns raw bytes)
byte[] raw = card.transmit(rawApduBytes);

// Lifecycle
card.install(MyApplet.class, AID.fromHex("F000000001"));   // explicit AID
card.install(MyApplet.class, aid, installParams);          // with applet install data
card.select(AID.fromHex("F000000001"));                    // select by AID (SelectException on failure)
card.reset();                                              // card reset: applets and persistent data survive
```

In `@JavaCardTest` classes `@InstallApplet` declares the applets; `install(...)` serves `@BeforeAll`/`@BeforeEach`
methods (installs that need computed parameters) and sessions you create yourself.

The card of a `@JavaCardTest` class completes `61XX` (with GET RESPONSE in the command's class) and `6CXX` (the
command again with the exact Le) in `send`, `send(APDUCommand)` and `sendHex`, as a PC/SC reader does, on every
backend. `transmit(...)` and the sessions you create yourself (`EmbeddedSession`, `ContainerSession`) return them
raw; [`APDUSequence`](#apdu-sequence) completes them.

`install(appletClass, aid, installParams)` passes `installParams` to the applet's `install(byte[] bArray, short bOffset, byte bLength)` in the Java Card layout `[Li][instance AID][Lc][control info][La][installParams]` (at most 127 bytes in total), so applets that register with `register(bArray, (short) (bOffset + 1), bArray[bOffset])` get the requested AID.

### Command encoding and Le

`send(cla, ins, p1, p2, data, le)` builds an ISO/IEC 7816-4 command APDU; `le` is Ne, the number of response bytes expected:

| `le` | Le field |
|------|----------|
| `SmartCardSession.NO_LE` (`-1`) | absent (Ne = 0); what the overloads without `le` send |
| `1` … `256` | short Le, `256` is coded as `'00'` |
| `257` … `65536` | extended Le (`65536` is `'0000'`) |
| `0` | `'00'` ("maximum"), kept for compatibility — prefer `256` |

Data longer than 255 bytes or `le` above 256 switch to extended length fields automatically. Every backend encodes with `APDUCodec`, so the same call produces the same bytes everywhere, and `LoggingSession` logs exactly those bytes.

### Backends

| | `EmbeddedSession` (jCardSim) | `PcscSession` (PC/SC reader) |
|---|---|---|
| `install(...)` | yes | no — load CAP files with the [GlobalPlatform module](../gp/README.md) |
| `select(aid)` failure | `SelectException` (AID + SW) | `SelectException` (AID + SW) |
| `reset()` | card reset: applets and persistent objects kept, `CLEAR_ON_RESET` cleared, no applet selected | warm reset and reconnect; the session stays usable |
| Logical channels | basic channel only; commands for other channels and MANAGE CHANNEL throw `UnsupportedOperationException` | channels opened with `LogicalChannel.open(card)` |
| Memory probing | not modelled by jCardSim (`MemoryInfo.from` throws) | cards implementing Java Card 3.0.4+ |
| After `close()` | `IllegalStateException` | `IllegalStateException` |

`ContainerSession` (Docker) is described in the [container module](../container/README.md).

### PC/SC — Physical Card Reader

Connect to a real card through any PC/SC reader. By default the session holds exclusive access to the card (no other PC/SC client can send commands in between), so use it from the thread that opened it, or open it with `PcscSession.Options.defaults().shared()`:

```java
// First available reader with a card present
try (var card = PcscSession.open()) {
    card.select(AID.fromHex("A000000151000000"));
    APDUResponse r = card.send(0x80, 0xCA, 0x00, 0x66, null, 256);  // GET DATA Card Data, Le '00'
    System.out.println("Card data: " + r.dataAsHex());
}

// Specific reader by name, shared access
try (var card = PcscSession.open("ACR122U", PcscSession.Options.defaults().shared())) {
    System.out.println("ATR: " + Hex.encode(card.getATR()));
}

// Works with all decorators
try (var card = PcscSession.open()) {
    card.logged(true).send(0x80, 0x50, 0x00, 0x00, hostChallenge, 256);
}
```

Applet installation on real cards requires the [GlobalPlatform module](../gp/README.md). To test applets on a real
card safely (an APDU allow-list in front of the reader, deployment, cleanup, `@LiveCardTest`), use the `livecard`
module: [LIVE_CARD_TESTING.md](../LIVE_CARD_TESTING.md). Tests of this project never open a reader in a normal build.

### Composing Decorators

Decorators wrap a session to add behavior. Chain them in any order:

```java
// Logging + PIN
LoggingSession logged = card.logged(true);
PinSession pin = PinSession.on(logged);
pin.verify(1, "1234");
// Log shows: [JCX] C: 002000010431323334
//            [JCX] R: 9000  (0.8 ms)

// Or directly from the session
card.pin().verify(1, "1234");
card.logged().send(0x80, 0x01);  // logged, one-off
```

Secure messaging and GlobalPlatform decorators live in their own modules. See [SM module](../sm/README.md) and [GP module](../gp/README.md).

## Assertions

AssertJ assertions for responses, with the meaning of the status words in their failure messages. `JCXAssertions`
extends AssertJ's `Assertions`, so its one static import also gives `assertThat(int)`, `assertThat(byte[])` and the
rest of AssertJ:

```java
import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;

// Status words: ints, SW constants, or the applet's own ISO7816 constants
assertThat(response).isSuccess();                                    // SW == 9000
assertThat(response).hasStatusWord(SW.NO_ERROR);                     // statusWord(...) is the same
assertThat(unknownIns).hasStatusWord(ISO7816.SW_INS_NOT_SUPPORTED);  // 6D00
assertThat(unknownIns).isNotSuccess().hasSw1(0x6D).hasSw2(0x00);
assertThat(response).hasStatusWordIn(0x9000, 0x6310);                // one of several

// Data assertions
assertThat(response).hasDataLength(5);
assertThat(response).dataEquals(0x48, 0x65, 0x6C, 0x6C, 0x6F);
assertThat(response).hasDataHex("48 65 6C 6C 6F");                   // hasData(byte[]) for arrays
assertThat(response).dataStartsWith(0x48, 0x65);                     // prefix match
assertThat(response).dataEndsWith(0x6C, 0x6F);                       // suffix match
assertThat(response).dataAsString().isEqualTo("Hello");
assertThat(response).dataAsHex().isEqualTo("48656C6C6F");
assertThat(response).data().hasSize(5);                              // AssertJ's byte[] assertions
assertThat(response).u16(0).isEqualTo(0x4865);                       // a big-endian number; u8 and s16 too
assertThat(unknownIns).hasNoData();

// TLV assertions (parses data as BER-TLV)
assertThat(response).tlvContains(0x6F);                        // top-level tag check
assertThat(response).tlv()                                     // navigate into TLV tree
    .containsTag(0x6F)
    .tag(0x6F).isConstructed()
        .tag(0x84).hasValue("A0000000031010");
```

`SW` names the status words as the Java Card API does (`SW.SECURITY_STATUS_NOT_SATISFIED` is
`ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED`); `SW.describe(0x6A82)` gives `file or application not found`,
`SW.format(0x6A82)` gives `6A82 (file or application not found)`. The same numbers outside assertions:
`response.u8(offset)`, `u16(offset)`, `s16(offset)` (all `int`), `response.data(from, to)`, and
`response.requireSw(SW.NO_ERROR, 0x6310)`, which fails like `requireSuccess()` on any other status word.

On failure, you get clear messages:

```
Expected success (SW=9000) but was SW=6A82 (file or application not found)
Expected SW1=61 but was SW1=6A (full SW=6A82, file or application not found)
Expected data to start with [48 65] but was [414243]
Expected TLV data to contain tag 6F but found tags: TLVList[TLV[84, 7 bytes]]
```

Status word and data mismatches are `AssertionFailedError`s with the expected and the actual value (for example
`9000 (success)` and `6A82 (file or application not found)`, or the data as hex), so an IDE shows the comparison.

## APDU Builder

Fluent builder for ISO 7816-4 commands. CLA/INS/P1/P2 are `0x00`-`0xFF` or an applet's `byte` constants such as
`(byte) 0x80` (other values are rejected), here as everywhere in the toolkit:

```java
// Fluent construction
APDUResponse r = APDUBuilder.command()
    .cla(0x80).ins(0x02).p1(0x00).p2(0x00)
    .data(Hex.decode("0102030405"))
    .le(256)
    .sendTo(card);

// Factory methods for common commands
APDUBuilder.select("A0000000031010").sendTo(card);
APDUBuilder.getData(0x00, 0x66).sendTo(card);          // Le '00'
APDUBuilder.getResponse(256).sendTo(card);

// Build raw bytes
byte[] apdu = APDUBuilder.command()
    .cla(0x00).ins(0xA4).p1(0x04).p2(0x00)
    .data("A0000000031010")    // hex string accepted
    .build();

// Logical channel encoding (ISO 7816-4 Tables 2/3, GP 11.1.4): channels 0-19
byte[] onChannel2 = APDUBuilder.command()
    .cla(0x00).ins(0xA4).p1(0x04).p2(0x00)
    .channel(2)                // CLA '02'; channel 4 gives '40', GP class '80' on channel 4 gives 'C0'
    .build();
```

### APDU Commands as Values

`APDUCommand` is an immutable command: a test class keeps its commands in constants, and `p1`, `p2`, `p1p2`,
`data`, `dataHex`, `le` and `noLe` return new commands.

```java
static final APDUCommand GET_DATA = APDUCommand.of(0x80, 0xCA).p1p2(0x00, 0x66).le(256);

APDUResponse cardData = card.send(GET_DATA);
card.send(APDUCommand.of(0x80, 0x02).data(0x01, 0x02).le(256));
card.send(APDUCommand.fromHex("80 02 00 00 02 0102 00"));   // every case of ISO/IEC 7816-4, short or extended
card.sendHex("80 02 00 00 02 0102 00");                     // the same in one call
byte[] bytes = GET_DATA.toBytes();                          // 80CA006600
APDUResponse fci = card.send(APDUCommand.select(aid));      // 00 A4 04 00 Lc AID 00: SELECT and its FCI
```

`APDUCommand.fromHex` is the only static factory of the class that takes a `String`, so JUnit converts
`@CsvSource` arguments to commands, as it does for `APDUResponse.fromHex`
([cookbook](../TESTING.md#data-driven-tests)).

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
byte[] apdu = APDUCodec.encode(0x80, 0x01, 0x00, 0x00, data, 256);
boolean ext = APDUCodec.isExtended(apdu);
byte[] corrected = APDUCodec.correctLe(apdu, sw2);     // after '6CXX': SW2 '00' means 256
```

## APDU Sequence

Automatic GET RESPONSE chaining (SW=61XX) and Le correction (SW=6CXX), ISO/IEC 7816-4 5.1.3:

```java
APDUResponse full = APDUSequence.on(card).transmit(rawApdu);

APDUResponse r = APDUSequence.on(card)
    .maxChain(512)                      // default 256 GET RESPONSEs (64 KB); more throws IllegalStateException
    .transmit(rawApdu);

APDUResponse r = APDUSequence.on(card)
    .send(0x80, 0xF2, 0x40, 0x00, data, 256);
```

GET RESPONSE is sent with the class byte of the original command, so the remaining bytes are fetched on the same logical channel and class (`getResponseCla(cla)` overrides the class, never the channel). Used internally by `GPSession`, and by the card of `@JavaCardTest` classes in `send`.

A command answered `6CXX` is re-sent with Le = `XX`. Never let that happen to bytes protected by a secure channel (a C-MAC the card has already verified fails the next time and aborts the channel, GPCS v2.3.1 E.4.4): `leCorrection(false)` returns `6CXX` instead, and the command must be protected again with the corrected Le, which `GPSession` does. With `PcscSession`, the JDK provider applies both rules itself by default, below the session (see the `PcscSession` javadoc and the gp README).

## TLV Parser

BER-TLV (ISO/IEC 7816-4 5.2.2): tags of one to three bytes, length fields of one to five bytes, constructed elements, recursive search and path navigation. Malformed data, tags longer than three bytes and nesting deeper than 64 levels throw `TLVException`:

```java
// Parse from bytes, hex string, or APDUResponse
TLVList list = TLVParser.parse(bytes);
TLVList list = TLVParser.parse("6F 09 84 07 A0 00 00 00 03 10 10");
TLVList list = response.tlv();

// Find by tag
TLV fci = list.find(0x6F).orElseThrow();
TLV aid = fci.find(0x84).orElseThrow();
String aidHex = aid.valueHex();

// Recursive search (finds at any depth)
TLV deep = list.findRecursive(0x84).orElseThrow();

// Build TLV data (tags are validated, the output always parses back)
byte[] data = TLVBuilder.create()
    .add(0x84, "A0000000031010")
    .addConstructed(0xA5, b -> b
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

OptionalInt left = pin.retries(1);       // SW 63CX -> X, 6983 -> 0, otherwise empty
boolean verified = pin.isVerified(1);    // an empty VERIFY answered 9000
int retries = pin.retriesRemaining(1);   // SW 63CX -> X, 6983 -> 0, otherwise -1
boolean blocked = pin.isBlocked(1);      // SW == 6983

// Applets with their own class and padding (PIV style: CLA 80, PIN padded with FF to 8 bytes)
PinSession piv = card.pin().cla(0x80).padTo(8, 0xFF);

// Encoding formats
PinSession bcd = card.pin().format(PinFormat.BCD);
PinSession iso = card.pin().format(PinFormat.ISO_9564_FORMAT_2);
```

Supported formats: `ASCII` (default), `BCD`, `ISO_9564_FORMAT_2`. `cla(...)` (default `00`) and `padTo(length, padByte)` apply to every command of the session.

### Complete PIN Lifecycle

A test of a `@JavaCardTest` class, with the [`PinApplet`](src/test/java/name/velikodniy/jcexpress/PinApplet.java) of
this module's tests:

```java
@Test
@InstallApplet(PinApplet.class)
void shouldHandleFullPinLifecycle(SmartCardSession card) {
    PinSession pin = card.pin();

    pin.verify(1, "1234");

    APDUResponse wrong = pin.verify(1, "0000");
    assertThat(wrong).hasStatusWord(0x63C2);      // 2 retries left

    assertThat(pin.retries(1)).hasValue(2);

    pin.verify(1, "0000");
    pin.verify(1, "0000");
    assertThat(pin.isBlocked(1)).isTrue();

    pin.unblock(1, "12345678", "5678");
    assertThat(pin.verify(1, "5678")).isSuccess();
}
```

## Logical Channels

ISO 7816-4 logical channels 0-19 (the channel is coded in CLA; each channel has its own selected applet and security status):

```java
// Managed channel — MANAGE CHANNEL OPEN (Le '01', the card assigns the number) / CLOSE
try (LogicalChannel ch = LogicalChannel.open(card)) {
    ch.select(AID.fromHex("A0000000041010"));
    ch.send(0x80, 0x02);                // CLA '81' on channel 1, 'C0' on channel 4
}  // close() sends MANAGE CHANNEL CLOSE

// Channel opened elsewhere — only re-codes CLA, sends no MANAGE CHANNEL
LogicalChannel ch1 = LogicalChannel.basic(card, 1);
```

Backend support: `PcscSession` maps channels opened with `LogicalChannel.open(card)` to `javax.smartcardio` channels (`javax.smartcardio` cannot open a given channel number or address a channel it did not open). `EmbeddedSession` has only the basic channel — jCardSim ignores the channel bits — so commands for other channels throw `UnsupportedOperationException` instead of silently reaching the applet on channel 0.

## APDU Logging

Capture and inspect APDU exchanges:

```java
LoggingSession logged = card.logged();     // or card.logged(true) to also print every line

logged.send(0x00, 0xA4, 0x04, 0x00, aidBytes);
logged.send(0x80, 0x01);

List<APDULogEntry> all = logged.entries();
List<APDULogEntry> selects = logged.entries(0xA4);
APDULogEntry last = logged.lastEntry();

System.out.println(logged.dump());
// C: 00A4040005F000000001
// R: 9000  (0.0 ms)
// C: 80010000
// R: 48656C6C6F9000  (0.0 ms)
```

Every entry carries when its command was sent (`timestampMs()`) and how long the exchange took (`duration()`); the dump, the transcripts and the printed lines show the time after each response, here jCardSim's, below a tenth of a millisecond. The history of a session keeps the times as well, so a test can check a time budget ([cookbook](../TESTING.md#timing-a-command)):

```java
card.send(0x80, 0x01);
APDULogEntry newest = card.history().last();              // the newest exchange of the session
assertThat(newest).isSuccess().tookAtMost(Duration.ofMillis(200));
```

On a card in a PC/SC reader the time includes the driver and the reader, and the GET RESPONSE or repeated command that the JDK sends by itself after `61XX` or `6CXX`; on jCardSim it is the simulator's time in the test JVM, which tells nothing about a card. A container session (`@SmartCard(mode = Mode.CONTAINER)`) keeps no history, so it has no times.

In `@JavaCardTest` classes the system property or JUnit configuration parameter `jcx.log=true` prints the exchanges of the card while the tests run, with a title line for every class and test, one line each with the prefix `[JCX]` (an example is in the [cookbook](../TESTING.md#watching-the-exchanges)). Sessions without `@JavaCardTest`: `@SmartCard(log = true)` on a field or parameter, or `jcx.log=true` for all of them; `card.logged(true)` prints the same way.

The lines go through java.util.logging, logger `name.velikodniy.jcexpress`, level INFO. Unless that logger has a handler of its own, the first printed line installs one that writes the bare lines to standard output (without java.util.logging's header lines); to route them elsewhere, give the logger a handler (`name.velikodniy.jcexpress.handlers=...` in `logging.properties`, or `Logger.getLogger("name.velikodniy.jcexpress").addHandler(...)`, for example SLF4J's `SLF4JBridgeHandler`). A level above INFO silences them.

## Memory Probing

`MemoryProbeApplet` reports the memory available on a card with `JCSystem.getAvailableMemory(short[], short, byte)` (Java Card 3.0.4 and later; the older `getAvailableMemory(byte)` is capped at 32767 bytes). Measure before and after installing or exercising an applet:

```java
card.install(MemoryProbeApplet.class);
MemoryInfo before = MemoryInfo.from(card.send(0x80, 0x01));

card.install(MyApplet.class);
card.select(MemoryProbeApplet.class);
MemoryInfo after = MemoryInfo.from(card.send(0x80, 0x01));

assertThat(after)
    .persistentConsumedAtMost(before, 4096)        // MyApplet used at most 4 KB of EEPROM
    .transientDeselectConsumedAtMost(before, 256)
    .persistentAtLeast(16384);                     // enough left for the next applet
```

jCardSim — the embedded and container backends — does not model memory: the probe reports nothing and `MemoryInfo.from` throws `UnsupportedOperationException` instead of returning made-up figures. Run memory tests on a card (install the probe with the GlobalPlatform module).

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

## Low-level: Sessions without @JavaCardTest

Before `@JavaCardTest`, tests received one simulator per `@SmartCard` field and installed their applets
themselves. This still works and suits tests that want one session under their own control (or the container
backend, which `@JavaCardTest` does not support yet): `@SmartCard` registers `JavaCardExtension`, the test installs
what it needs with `card.install(...)`, and the session's lifetime (below the example) decides what the next test
sees.

### Complete Test Lifecycle

```java
import name.velikodniy.jcexpress.JavaCardExtension;
import name.velikodniy.jcexpress.SmartCard;
import name.velikodniy.jcexpress.SmartCardSession;
import org.junit.jupiter.api.Test;
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

        card.reset();                  // applets stay installed, nothing is selected
        card.select(MyApplet.class);
        card.send(0x80, 0x01).requireSuccess();
    }
}
```

Session lifetime: an instance field gets a new card for every test method (`@Nested` classes included); with `@TestInstance(Lifecycle.PER_CLASS)` the tests of the class share one card, and a `static` field gives one card per test class (available in `@BeforeAll`). Fields declared in superclasses are injected too, and every session is closed when its JUnit context ends, also when tests run in parallel. Declare the field as `SmartCardSession`, `LoggingSession` or `EmbeddedSession`.

## See Also

- [GlobalPlatform module](../gp/README.md) — SCP02/SCP03 secure channels, card management
- [Secure Messaging module](../sm/README.md) — ISO 7816-4 SM (ePassport BAC, PACE)
- [PACE module](../pace/README.md) — Password Authenticated Connection Establishment
- [Container module](../container/README.md) — Docker-based sessions with Testcontainers
- [Project root](../README.md) — overview, modules, configuration
