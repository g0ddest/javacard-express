# Testing cookbook

Recipes for applet tests with `@JavaCardTest`, one task each. They test a small sample applet,
[`WalletApplet`](core/src/test/java/name/velikodniy/jcexpress/readme/cookbook/WalletApplet.java). Every Java
snippet on this page is compiled and run by the build: the recipe classes are in
[`core/src/test/java/name/velikodniy/jcexpress/readme/cookbook`](core/src/test/java/name/velikodniy/jcexpress/readme/cookbook),
and `TestingCookbookTest` checks that each Java block here is a contiguous part of one of them, so a recipe works
when you paste it.

To follow along, create a project like the [Quick start](README.md#quick-start)'s (its POM with your own
`artifactId` and `javacard.packageAid`) and put `WalletApplet.java` into its own package, for example
`src/main/java/com/example/wallet` (change its `package` line): the plugin builds one CAP file, so one Java Card
package, per module, and `WalletApplet` cannot go next to the Quick start's `HelloWorldApplet`. Put the test
classes into the same package (`src/test/java/com/example/wallet`): they use the applet's package-private
constants. The commands of "Commands as constants" are static fields of `WalletAppletTest`; the other test classes
import them statically (`import static com.example.wallet.WalletAppletTest.GET_BALANCE;`). New to Java Card?
[Java Card for Java developers](README.md#java-card-for-java-developers) explains APDUs, status words and AIDs in
a few lines.

- [The sample applet](#the-sample-applet)
- [The first test](#the-first-test)
- [Commands as constants](#commands-as-constants)
- [Status words](#status-words)
- [Response data](#response-data)
- [The SELECT response](#the-select-response)
- [Data-driven tests](#data-driven-tests)
- [Install parameters](#install-parameters)
- [Several applets](#several-applets)
- [A scenario across tests](#a-scenario-across-tests)
- [State across reset and deselect](#state-across-reset-and-deselect)
- [PIN](#pin)
- [TLV](#tlv)
- [Watching the exchanges](#watching-the-exchanges)
- [Debugging a 6F00](#debugging-a-6f00)
- [One test class, several backends](#one-test-class-several-backends)
- [On CI](#on-ci)
- [Your own test annotation](#your-own-test-annotation)
- [What the simulators do not model](#what-the-simulators-do-not-model)

## The sample applet

`WalletApplet` keeps a balance and guards DEBIT with a PIN. Its own commands use the proprietary class `80`:

| Command | APDU | Answer |
|---|---|---|
| SELECT | `00 A4 04 00` AID | `6F` FCI with the instance AID in tag `84`, then `9000` |
| VERIFY | `80 20 00 01 08` PIN | `9000`; `63CX` with X tries left; `6983` when blocked. The PIN is `1234`, ASCII, padded with `FF` to 8 bytes. Without data: `9000` when verified, otherwise `63CX` |
| CREDIT | `80 30 00 00 02` amount | `9000` |
| DEBIT | `80 40 00 00 02` amount | `9000`; `6982` before the PIN is verified; `6985` when the balance is too low |
| GET BALANCE | `80 52 00 00 02` | the balance, two bytes, then `9000` |
| GET INFO | `80 54 00 00 00` | `E1 07 81 02` balance `82 01` PIN tries left, then `9000` |

## The first test

```java
import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.SmartCardSession;
import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;   // also AssertJ's assertThat

@JavaCardTest
@InstallApplet(WalletApplet.class)              // a fresh instance before every test, deleted after it
class WalletAppletTest {

    @Test
    void startsEmpty(SmartCardSession card) {
        assertThat(card.send(0x80, 0x52, 0x00, 0x00, null, 2))     // GET BALANCE, Le = 2
                .isSuccess()
                .hasDataHex("0000");
    }
}
```

The card arrives as a `SmartCardSession` parameter (of tests, lifecycle methods or the constructor), and the
applet is selected before the test. `JCXAssertions` extends AssertJ's `Assertions`, so its one static import
serves the card assertions and `assertThat(int)`, `assertThat(byte[])` or `assertThat(String)` alike. The
`send` arguments are CLA, INS, P1, P2, the data and Le; `SmartCardSession.NO_LE` sends no Le field.

## Commands as constants

```java
static final APDUCommand GET_BALANCE = APDUCommand.of(0x80, 0x52).le(2);
static final APDUCommand CREDIT = APDUCommand.of(0x80, 0x30);       // the amount is the command data
static final APDUCommand DEBIT = APDUCommand.of(0x80, 0x40);
static final APDUCommand GET_INFO = APDUCommand.of(0x80, 0x54).le(256);

@Test
void credits(SmartCardSession card) {
    assertThat(card.send(CREDIT.data(0x00, 0x64))).isSuccess();   // a new command; CREDIT stays as it is
    assertThat(card.send(GET_BALANCE)).isSuccess().u16(0).isEqualTo(100);
}
```

`APDUCommand` is an immutable value: `p1`, `p2`, `p1p2`, `data`, `dataHex`, `le` and `noLe` return a new
command. `le(256)` asks for up to 256 bytes (Le `00`). A command written as hex, from a trace or a specification,
is sent as it is written, and the applet's own `byte` constants are accepted as header bytes:

```java
assertThat(card.sendHex("80 30 00 00 02 0064")).isSuccess();   // CREDIT 100, as written in a trace
assertThat(card.send(WalletApplet.CLA_WALLET, WalletApplet.INS_GET_BALANCE, 0, 0, null, 2))
        .hasDataHex("0064");
```

`sendHex` and `APDUCommand.fromHex` read every command case of ISO/IEC 7816-4 (short and extended length).

## Status words

```java
assertThat(card.send(GET_BALANCE)).isSuccess();                                   // 9000
assertThat(card.send(DEBIT.data(0x00, 0x0A))).hasStatusWord(SW.SECURITY_STATUS_NOT_SATISFIED);
assertThat(card.send(0x80, 0x7F)).hasStatusWord(ISO7816.SW_INS_NOT_SUPPORTED);   // the applet's constants
assertThat(card.send(0x80, 0x7F)).isNotSuccess().hasSw1(0x6D);
card.send(CREDIT.data(0x00, 0x64)).requireSuccess();         // an AssertionError unless the SW is 9000
card.send(DEBIT.data(0x00, 0x0A)).requireSw(SW.NO_ERROR, SW.SECURITY_STATUS_NOT_SATISFIED);
```

`SW` names the status words of ISO/IEC 7816-4 as the Java Card API does (`SW.SECURITY_STATUS_NOT_SATISFIED` is
`ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED`); `SW.describe(0x6982)` gives the meaning. Methods that take a status word
also accept the applet's `short` constants. `hasStatusWordIn(...)` accepts one of several. A failure names both
status words:

```text
org.opentest4j.AssertionFailedError: Expected SW=9000 (success) but was SW=6982 (security status not satisfied)
```

It is an `AssertionFailedError` with expected and actual value, so an IDE shows the comparison. `requireSuccess()`
and `requireSw(...)` throw an `AssertionError` that shows the exchange:

```text
name.velikodniy.jcexpress.UnexpectedStatusWordError:
Expected SW 9000 (success) but was 6982 (security status not satisfied)
C: 8040000002000A
R: 6982
```

## Response data

```java
card.send(CREDIT.data(0x00, 0x64)).requireSuccess();
APDUResponse info = card.send(GET_INFO);                   // E1 07 81 02 <balance> 82 01 <tries>
int balance = info.u16(4);                                 // two bytes, big-endian, unsigned: 100
int tries = info.u8(8);                                    // one byte, unsigned: 3
byte[] balanceTlv = info.data(2, 6);                       // bytes 2 to 5: 81 02 00 64
assertThat(card.send(GET_BALANCE)).isSuccess().u16(0).isEqualTo(100);
assertThat(card.send(GET_BALANCE)).hasDataHex("0064");
assertThat(card.send(GET_BALANCE)).data().hasSize(2);
```

`s16(offset)` reads a signed short, the value `Util.getShort` gives the applet. The readers return `int` on
purpose: AssertJ's `assertThat((short) 100).isEqualTo(100)` fails, because it compares a `Short` with an
`Integer`. `hasData(byte[])`, `hasNoData()`, `dataAsString()` and `dataAsHex()` cover the rest; a read beyond the
data fails with the offset and the data.

An answer longer than one response arrives whole: when the applet answers `61XX` (more data), the card of a
`@JavaCardTest` class fetches the rest with GET RESPONSE, and it sends a command answered `6CXX` again with the
exact Le, as a PC/SC reader does, on every backend. `card.transmit(byte[])` returns the raw answer.

## The SELECT response

`card.select(...)` checks that the applet answered `9000` and returns nothing. To check what the applet answers to
SELECT (the FCI), send the SELECT yourself:

```java
import name.velikodniy.jcexpress.AID;

@Test
void answersTheSelectWithItsAid(SmartCardSession card) {
    AID wallet = card.aid(WalletApplet.class);
    APDUResponse fci = card.send(APDUCommand.select(wallet));        // 00 A4 04 00 Lc AID 00
    assertThat(fci).isSuccess().tlv().tag(0x6F).tag(0x84).hasValue(wallet.toHex());
}
```

`card.aid(WalletApplet.class)` is the AID the test installed the applet under (see [Several applets](#several-applets)).

## Data-driven tests

```java
@ParameterizedTest
@CsvSource({
        "8052000002,     0000 9000",        // GET BALANCE of a new wallet
        "8040000002000A, 6982",             // DEBIT before the PIN is verified
        "807F0000,       6D00"})            // an instruction the applet does not know
void answers(APDUCommand command, APDUResponse expected, SmartCardSession card) {
    assertThat(card.send(command)).isEqualTo(expected);
}
```

JUnit converts the columns with `APDUCommand.fromHex` and `APDUResponse.fromHex` (data, then SW1 SW2; spaces are
ignored), no converter needed. Every invocation gets a fresh applet instance, like every test.

## Install parameters

```java
@JavaCardTest
@InstallApplet(value = WalletApplet.class, params = "0064")   // the install method reads the starting balance
class FundedWalletTest {

    @Test
    void startsWithItsInstallParameter(SmartCardSession card) {
        assertThat(card.send(GET_BALANCE)).u16(0).isEqualTo(100);
    }
}
```

The applet's `install` method gets the install data as a card passes it, `[Li][instance AID][Lc][control
information][La][install parameters]`:

```java
public static void install(byte[] bArray, short bOffset, byte bLength) {
    byte li = bArray[bOffset];                                   // [Li][instance AID]
    byte lc = bArray[(short) (bOffset + 1 + li)];                // [Lc][control information]
    byte la = bArray[(short) (bOffset + 2 + li + lc)];           // [La][install parameters]
    new WalletApplet(bArray, (short) (bOffset + 3 + li + lc), la)
            .register(bArray, (short) (bOffset + 1), li);
}
```

When the install method throws (`WalletApplet` answers parameters other than none or two bytes with `6A80`), the
test fails with `InstallException`, which names the applet, the instance AID, the parameters and this layout.
Register with `register(bArray, (short) (bOffset + 1), li)` as here: a card registers an applet that calls
`register()` without arguments under its own AID from the CAP file, not under the AID the test installs it with
(see [What the simulators do not model](#what-the-simulators-do-not-model)).

## Several applets

```java
@JavaCardTest
@InstallApplet(value = WalletApplet.class, aid = "0101", params = "0064")
@InstallApplet(value = WalletApplet.class, aid = "0102", params = "00C8")
class TwoWalletsTest {

    @Test
    void everyInstanceHasItsOwnBalance(SmartCardSession card) {
        assertThat(card.send(GET_BALANCE)).u16(0).isEqualTo(100);   // the first declared instance is selected
        card.select(card.aid("0102"));
        assertThat(card.send(GET_BALANCE)).u16(0).isEqualTo(200);
    }
}
```

`aid` is a suffix: `card.aid("0102")` is the run's AID prefix followed by it, so tests contain no literal AIDs and
address the same instances on every backend. With one instance of a class, `card.aid(WalletApplet.class)` and
`card.select(WalletApplet.class)` name it by class; with several, they throw and list the instances, so name one by
its suffix as above. `@InstallApplet` also works on a test method and on a `@Nested` class; see
[Scenarios](core/README.md#scenarios).

## A scenario across tests

```java
@JavaCardTest
@InstallApplet(value = WalletApplet.class, isolation = Isolation.PER_CLASS)   // one instance for all tests
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PaymentScenarioTest {

    @BeforeAll
    static void fund(SmartCardSession card) {
        card.send(CREDIT.data(0x00, 0x64)).requireSuccess();      // the class's applet is selected already
    }

    @Test
    @Order(1)
    void verifiesThePin(SmartCardSession card) {
        card.pin().cla(0x80).padTo(8, 0xFF).verify(1, "1234").requireSuccess();
    }

    @Test
    @Order(2)
    void debitsWithThePinOfTheFirstTest(SmartCardSession card) {
        assertThat(card.send(DEBIT.data(0x00, 0x0A))).isSuccess();   // no SELECT in between: still verified
        assertThat(card.send(GET_BALANCE)).u16(0).isEqualTo(90);
    }
}
```

The selection rules, the same on every backend: an `@InstallApplet` install selects nothing, while
`card.install(...)` selects the instance it installs; after the `PER_CLASS` instances of a class are installed,
the first of them is selected, so `@BeforeAll` methods talk to it; before each test the nearest declared applet
(the method's, then the nearest class's) is selected, unless it still is. The SELECT before a test comes before
the `@BeforeEach` methods, so after a `card.install(...)` in `@BeforeEach` the test starts with that new instance
selected. A `PER_CLASS`
instance therefore keeps its `CLEAR_ON_DESELECT` memory, here the verified PIN, from one test to the next, until
another SELECT, `reset()`, `deselect()`, an install or a delete.

## State across reset and deselect

```java
@Test
void theBalanceSurvivesAResetThePinValidationDoesNot(SmartCardSession card) {
    PinSession pin = card.pin().cla(0x80).padTo(8, 0xFF);
    card.send(CREDIT.data(0x00, 0x64)).requireSuccess();
    pin.verify(1, "1234").requireSuccess();

    card.reset();                                  // like pulling the card out: fields stay, nothing is selected
    card.select(WalletApplet.class);
    assertThat(card.send(GET_BALANCE)).u16(0).isEqualTo(100);
    assertThat(pin.isVerified(1)).isFalse();       // the PIN's validation flag is cleared by a reset
}

@Test
void deselectingEndsThePinValidation(SmartCardSession card) {
    PinSession pin = card.pin().cla(0x80).padTo(8, 0xFF);
    pin.verify(1, "1234").requireSuccess();

    card.deselect();                               // the applet's deselect() runs, CLEAR_ON_DESELECT memory is cleared
    card.select(WalletApplet.class);
    assertThat(pin.isVerified(1)).isFalse();       // WalletApplet.deselect() resets the PIN
}
```

Fields of an applet are persistent: they survive a reset, as on a card. Transient arrays
(`JCSystem.makeTransientByteArray`) are cleared on reset (`CLEAR_ON_RESET`) or also on deselect
(`CLEAR_ON_DESELECT`). After `reset()` or `deselect()` no applet of the test is selected until the test selects
one.

## PIN

```java
PinSession pin = card.pin().cla(0x80).padTo(8, 0xFF);    // the applet's class, PIN padded with FF to 8 bytes
assertThat(pin.verify(1, "0000")).hasStatusWord(0x63C2);  // wrong PIN: 2 tries left
assertThat(pin.retries(1)).hasValue(2);
assertThat(pin.verify(1, "1234")).isSuccess();
assertThat(pin.isVerified(1)).isTrue();
assertThat(card.send(DEBIT.data(0x00, 0x00))).isSuccess();
```

`card.pin()` sends ISO/IEC 7816-4 VERIFY, CHANGE REFERENCE DATA and RESET RETRY COUNTER. By default it uses class
`00` and sends the PIN in ASCII without padding; `cla(...)`, `padTo(...)` and `format(...)` (`BCD`,
`ISO_9564_FORMAT_2`) change that. `retries(ref)` is the counter of a `63CX` answer (`0` when blocked), or empty when
the card reports none; `isVerified(ref)` asks with an empty VERIFY.

## TLV

```java
card.send(CREDIT.data(0x00, 0x64)).requireSuccess();
APDUResponse info = card.send(GET_INFO);
assertThat(info).tlv().tag(0xE1).isConstructed().tag(0x81).hasValue("0064");
TLV tries = info.tlv().at(0xE1, 0x82).orElseThrow();      // E1 > 82, or Optional.empty()
```

`tlv()` parses the data as BER-TLV (ISO/IEC 7816-4 5.2.2); more in the
[core README](core/README.md#tlv-parser).

## Watching the exchanges

```bash
mvn test -Djcx.log=true                      # or -Djcx.log=true as VM option of an IDE run
```

prints every exchange while the tests run, one line each on standard output, here the `credits()` test of this
page:

```text
[JCX] ## WalletAppletTest > credits()
[JCX] # install name.velikodniy.jcexpress.readme.cookbook.WalletApplet as F04A4358D1E5113B
[JCX] C: 00A4040008F04A4358D1E5113B00
[JCX] R: 6F0A8408F04A4358D1E5113B9000
[JCX] # test body: credits()
[JCX] C: 80300000020064
[JCX] R: 9000
[JCX] C: 8052000002
[JCX] R: 00649000
[JCX] # deselect F04A4358D1E5113B
[JCX] # delete F04A4358D1E5113B
```

`C:` is a command, `R:` the response (data, then the status word), `#` a note; `# test body` marks where the test
method starts, after the installs, the SELECT and the `@BeforeEach` methods. In your project the lines name your
applet class, and its AIDs start with the project's own prefix (`F0` and four bytes, see the
[README](README.md#testing)) instead of the `F04A4358` of test-source applets like this page's. The lines go through
java.util.logging, logger `name.velikodniy.jcexpress`, level INFO, to standard output; to send them elsewhere, give
that logger a handler of your own (`name.velikodniy.jcexpress.handlers=...` in `logging.properties`, or
`Logger.getLogger("name.velikodniy.jcexpress").addHandler(...)`, for example SLF4J's bridge), and a level above INFO
silences them.

Without the setting, a test that fails carries its exchanges in its failure (a suppressed exception), and so does
a failing `@BeforeAll`, `@BeforeEach`, `@AfterEach` or `@AfterAll` method. A failed test also publishes them as
`apdu-transcript.txt`, with Maven Surefire under `target/junit-jupiter/<test class with package>/<test
method>(<parameter types>)/`; only failed tests write the file, and a file from an earlier failing run stays until
`mvn clean`. When the run installed no applet at all, the transcript starts with a note that says so.
Inside a test, `card.history()` holds that test's exchanges, from its SELECT on:

```java
@Test
void historyHoldsTheExchangesOfThisTest(SmartCardSession card) {
    card.send(CREDIT.data(0x00, 0x64)).requireSuccess();
    assertThat(card.history().entries())                       // this test's exchanges: its SELECT, CREDIT
            .extracting(APDULogEntry::ins).containsExactly(0xA4, 0x30);
}
```

## Debugging a 6F00

A card answers `6F00` when the applet lets an exception escape (an `ISOException` is a status word, anything else
is `6F00`). On the default backend the transcript of the failed test names the exception and the applet's line,
here from an applet with a bug (stack frames left out):

```text
org.opentest4j.AssertionFailedError: Expected success (SW=9000) but was SW=6F00 (no precise diagnosis)
	Suppressed: name.velikodniy.jcexpress.CardTestLifecycle$CardTranscript: APDU exchanges of the test (most recent last):
# install name.velikodniy.jcexpress.fakes.ThrowingApplet as F04A4358388A6704
C: 00A4040008F04A4358388A670400
R: 9000
# test body: crashes()
C: 80020700
R: 6F00
# applet threw java.lang.NullPointerException: Cannot store to byte/boolean array because "this.cache" is null at name.velikodniy.jcexpress.fakes.ThrowingApplet.fillCache(ThrowingApplet.java:58)
```

The applet runs as class files in the test JVM, on the default backend and on `simulated-gp`, so a breakpoint in
`process()` (or an exception breakpoint) stops where it throws when you debug the test in the IDE. `simulated-gp`
reports only the status word, and a real card can tell nothing more: reproduce such a failure on the default
backend.

## One test class, several backends

```bash
mvn verify                                                  # jCardSim, in the test JVM
mvn verify -Djcx.backend=simulated-gp                       # converted and loaded on a simulated GP card
mvn verify -Djcx.backend=livecard -Djcx.livecard.verifierSdk=<Java Card kit> -Dtest=WalletAppletTest
```

```java
@Test
@EnabledOnBackend({Mode.SIMULATED_GP, Mode.LIVECARD})          // only where the package is converted and loaded
void runsWhereThePackageIsConvertedAndLoaded(SmartCardSession card) {
    assertThat(card.send(GET_BALANCE)).isSuccess();
}
```

The same class runs on every backend with the same AIDs. The GlobalPlatform backends (`simulated-gp`, `livecard`)
convert the package, load it and manage it like a card, which brings a few rules: the applet's own commands avoid
some INS values in a proprietary class, test applets run on the basic channel only, packages that import another
package of the project run only on the default backend in 0.4.0, and instance AIDs come from `card.aid(...)`; the
list is in the README, [On the GlobalPlatform backends](README.md#on-the-globalplatform-backends). A failed test's
transcript notes what the backend did for it (`# install`, `# load` with the number of LOAD blocks, `# delete`,
`# secure channel`).

`livecard` needs a development card, Oracle's off-card verifier for the CAP files (`jcx.livecard.verifierSdk`, a
Java Card kit that matches the card; `none` loads unverified CAP files at your own risk), and a build for the
card's Java Card version (`<javacard.version>3.0.4</javacard.version>` in the POM for a 3.0.4 card, which refuses
a 3.0.5 CAP file with `6438`); see the [live-card guide](LIVE_CARD_TESTING.md#your-tests-on-a-card).

## On CI

`simulated-gp` needs nothing installed: no reader, no Oracle kit (it loads unverified CAP files and says so once per
run). A GitHub Actions job:

```yaml
- uses: actions/checkout@v4
- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: '25'
    cache: maven
- run: mvn -B verify                                # jCardSim
- run: mvn -B verify -Djcx.backend=simulated-gp     # conversion and card management, no reader
```

The environment variable `JCX_BACKEND` selects a backend too. `livecard` is refused when the environment looks
like CI.

## Your own test annotation

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@JavaCardTest
@InstallApplet(WalletApplet.class)
@interface WalletTest {
}

@WalletTest
class CreditTest {

    @Test
    void credits(SmartCardSession card) {
        assertThat(card.send(CREDIT.data(0x00, 0x0A))).isSuccess();
        assertThat(card.send(GET_BALANCE)).u16(0).isEqualTo(10);
    }
}
```

JUnit supports meta-annotations: it finds the extension of `@JavaCardTest` through `@WalletTest`, and the extension
finds `@InstallApplet` the same way, like a composed annotation in Spring.

## What the simulators do not model

The default backend runs the applet classes on jCardSim in the test JVM, and so does `simulated-gp` after it
converted and loaded the package. jCardSim differs from a card in a few places, on both:

- no applet firewall between applets and no memory limits (`MemoryInfo.from` throws): an applet that allocates
  objects in `process()` passes here and fills a card, which reclaims unreachable objects only on
  `JCSystem.requestObjectDeletion()`, if it supports that;
- `JCSystem.abortTransaction()` does not roll back;
- a case 4 command with Lc 255 and Le is answered `6F00`;
- `register()` without arguments registers the instance under the AID the test asked for; a card registers it
  under the applet's own AID from the CAP file, so several instances, or an instance AID other than the applet's,
  need `register(bArray, (short) (bOffset + 1), bArray[bOffset])` (the GlobalPlatform backends note an applet that
  does not);
- they run the class files, not the CAP file: what the converter wrote runs only on a real card.

The simulated GlobalPlatform card adds a few of its own: it loads a CAP file for any Java Card version (a card
refuses a newer one with `6438`), always has the `int` type, and runs test applets on the basic channel only. The
default backend has only the basic channel as well (commands for other channels throw
`UnsupportedOperationException`), and there a SELECT of an AID that no applet has reaches the selected applet,
which answers it with its own status word (this page's `WalletApplet` with `6E00`), where `simulated-gp` and a card
answer `6A82` (file or application not found).

Mark tests that depend on these with
`@DisabledOnBackend(value = {Mode.EMBEDDED, Mode.SIMULATED_GP}, reason = "...")` (both run jCardSim), or
`@EnabledOnBackend(Mode.LIVECARD)`, and run them with `-Djcx.backend=livecard`. Details: [core
README](core/README.md#backends) and the live-card guide's [known issues](LIVE_CARD_TESTING.md#known-issues).
