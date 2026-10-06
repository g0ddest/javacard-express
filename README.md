# JavaCard Express

[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=g0ddest_javacard-express&metric=coverage)](https://sonarcloud.io/summary/new_code?id=g0ddest_javacard-express)

A Java toolkit for Java Card applet development: build CAP files with Maven without the Oracle SDK, test applets
with JUnit 5 and AssertJ on jCardSim (an open-source Java Card simulator that runs the applet classes in the test
JVM), on a simulated GlobalPlatform card or on a real card, and talk to cards with APDU, BER-TLV, GlobalPlatform
(SCP02/SCP03), ISO 7816-4 Secure Messaging, PACE and BAC helpers.

> **Status:** the APIs can still change between releases; the [changelog](CHANGELOG.md) lists every change and what
> to do when upgrading. Rebuild CAP files that earlier releases built: their converter wrote a Class component that
> Oracle's verifier rejects.

## Requirements

- **JDK 25 or newer** to run Maven with the plugin (the converter uses the JDK ClassFile API) and to run tests
  that use `core`, `gp`, `sm`, `pace` or `container` (their class files are Java 25).
- **Applet code** compiles for Java 8 (`maven.compiler.release` 8) against `javacard-express-api`, whose class
  files are Java 8, so any `javac` from 8 up can compile against it. **Test code** compiles for Java 25
  (`maven.compiler.testRelease` 25): it runs with the toolkit on JDK 25 and may use the current Java API.
- **Maven 3.9.0** or newer.
- **Docker** only for container mode.
- No Oracle Java Card SDK to build, or to test on jCardSim and on the simulated GlobalPlatform card. Tests on a real
  card check the CAP files with Oracle's off-card verifier first, unless you opt out (see [Testing](#testing)).

## Quick start

### Build and test an applet

`pom.xml` of an applet project: it inherits the applet parent, which sets everything else (Java 8 applet code,
Java 25 tests, the toolkit's dependencies, the version of the plugin and the binding of its `build` goal); the
project names the plugin:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>name.velikodniy</groupId>
        <artifactId>javacard-express-applet-parent</artifactId>
        <version>0.4.0</version>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>hello-applet</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <javacard.packageAid>A00000006212</javacard.packageAid>
    </properties>

    <build>
        <plugins>
            <plugin>
                <groupId>name.velikodniy</groupId>
                <artifactId>javacard-express-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

A project that already has a parent uses the explicit POM of the
[plugin README](maven-plugin/README.md#quick-start) and imports `javacard-express-bom` for the versions.

The applet, `src/main/java/com/example/hello/HelloWorldApplet.java`:

```java
package com.example.hello;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/** Minimal HelloWorld applet: CLA=80 INS=01 returns "Hello". */
public class HelloWorldApplet extends Applet {

    private static final byte[] HELLO = {'H', 'e', 'l', 'l', 'o'};

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new HelloWorldApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_CLA] != (byte) 0x80) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }
        switch (buf[ISO7816.OFFSET_INS]) {
            case 0x01:
                Util.arrayCopyNonAtomic(HELLO, (short) 0, buf, (short) 0, (short) HELLO.length);
                apdu.setOutgoingAndSend((short) 0, (short) HELLO.length);
                return;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
}
```

Its test, `src/test/java/com/example/hello/HelloWorldAppletTest.java`:

```java
package com.example.hello;

import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.SmartCardSession;
import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;

@JavaCardTest
@InstallApplet(HelloWorldApplet.class)          // installed before each test, deleted after it
class HelloWorldAppletTest {

    @Test
    void returnsHello(SmartCardSession card) {
        assertThat(card.send(0x80, 0x01))
                .isSuccess()
                .dataAsString().isEqualTo("Hello");
    }
}
```

`mvn package` converts the applet right after compilation (code outside the Java Card subset fails here, before
any test), writes `target/<artifactId>-<version>.cap` with the applet AID `A0000000621201` (package AID + index),
and runs the test on jCardSim. `A000000062` is an example RID; use your own registered RID for real cards. That
AID is the CAP file's: tests install their instances under an AID prefix of the run and address applets by class
(`card.select(HelloWorldApplet.class)`, `card.aid(...)`), not by a literal AID ([why](#testing)).

The test declares its applet instead of installing it: `@InstallApplet` installs a fresh instance before every
test and deletes it afterwards, also when the test fails. The same test class runs on jCardSim, on a simulated
GlobalPlatform card and on a real card; [Testing](#testing) says how to choose, and what changes on the
GlobalPlatform backends.

Next: the [testing cookbook](TESTING.md) shows the everyday tasks of applet tests, and
[Java Card for Java developers](#java-card-for-java-developers) explains APDUs, status words and AIDs.

### Use the toolkit without JUnit

```java
import name.velikodniy.jcexpress.APDUResponse;
import name.velikodniy.jcexpress.SmartCardSession;
import name.velikodniy.jcexpress.apdu.APDUBuilder;
import name.velikodniy.jcexpress.embedded.EmbeddedSession;

try (SmartCardSession card = new EmbeddedSession()) {
    card.install(HelloWorldApplet.class);

    APDUResponse hello = card.send(0x80, 0x01).requireSuccess();   // throws unless SW = 9000
    System.out.println(hello.dataAsString());                        // Hello

    byte[] apdu = APDUBuilder.command().cla(0x80).ins(0x01).build();  // raw command bytes
    APDUResponse again = new APDUResponse(card.transmit(apdu));
    System.out.println(again.dataAsHex());                           // 48656C6C6F
}
```

### Load the CAP file onto a real card

This loads the CAP file with GlobalPlatform card management directly, outside the test harness and its APDU guard
(to run your tests on a card behind the guard, see [Testing](#testing)). Build for the card's Java Card version
first: the plugin targets Java Card 3.0.5 by default, and a 3.0.4 card refuses such a CAP file at LOAD with `6438`
(`<javacard.version>3.0.4</javacard.version>` in the POM).

```java
import java.nio.file.Path;
import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.gp.CAPFile;
import name.velikodniy.jcexpress.gp.GPSession;
import name.velikodniy.jcexpress.pcsc.PcscSession;
import name.velikodniy.jcexpress.scp.SCPKeys;

try (PcscSession card = PcscSession.open()) {                         // first reader with a card
    GPSession gp = GPSession.on(card)
        .keys(SCPKeys.defaultKeys())    // the 40..4F test keys of development cards: use your card's keys
        .open();                        // SCP02 or SCP03, detected from the card
    gp.loadAndInstall(CAPFile.fromFile(Path.of("target/hello-applet-1.0-SNAPSHOT.cap")),
            null,                       // instance AID: null installs the applet under its AID in the CAP file
            0x00,                       // privileges
            null);                      // install parameters
    gp.close();

    card.select(AID.fromHex("A0000000621201"));
    System.out.println(card.send(0x80, 0x01).dataAsString());       // Hello
}
```

Cards count failed authentications and lock up after a few: `open()` checks the card cryptogram before it sends
EXTERNAL AUTHENTICATE and never retries, but only the right keys get you in. See the
[GlobalPlatform README](gp/README.md).

## Java Card for Java developers

A card and its applets talk in APDUs (ISO/IEC 7816-4), short byte strings that `card.send(...)` builds and parses:

| Term | Meaning |
|---|---|
| Command APDU | `CLA INS P1 P2 [Lc data] [Le]`: the class byte (`00` for ISO commands; `80` to `FE` is a proprietary class, which an applet uses for its own commands), the instruction, two parameter bytes, optional data, and Le, the number of response bytes expected |
| Response APDU | `[data] SW1 SW2`: optional data and the two-byte status word |
| Applet, package | an applet is a class that the card selects by its AID and calls with `process(APDU)`; a package (one Java package) holds applets or library code and is loaded once |
| `process(APDU)` | the applet's one entry point, like a controller with a single method: it dispatches on INS, reads the command data (`apdu.setIncomingAndReceive()`), answers with `apdu.setOutgoingAndSend(...)`, and fails with `ISOException.throwIt(sw)`, which becomes the status word |
| Language | a subset of Java: `byte`, `short`, `boolean`, arrays and objects; no `String`, `long`, `float`, `double`, threads or collections; `int` only with `supportInt32`. The build names every violation with its file and class, and the method and line (or the field) ([what the converter accepts](converter/README.md#what-the-converter-accepts)) |
| Memory | fields and the objects they hold are persistent: they survive a reset, like rows in a database. Create them once, in the constructor that `install` calls, not in `process()`: a card reclaims objects only on request, if at all. Scratch data goes into transient arrays (`JCSystem.makeTransientByteArray`), cleared on reset or deselect |
| AID | the name of a package or applet instance: a 5-byte RID (registered application provider) and up to 11 more bytes; AIDs starting with `F0` are unregistered, fine for tests |
| CAP file | the converted package that a card loads (`mvn package` writes it); an export file (`.exp`) describes a library package to the packages that import it |
| Install parameters | the bytes an applet's `install(byte[], short, byte)` receives: `[Li][instance AID][Lc][control information][La][parameters]` |
| GlobalPlatform | the card management standard: the Issuer Security Domain loads, installs and deletes applets over an authenticated secure channel (SCP02, SCP03) |

The status word is the HTTP status of a card. As an analogy (the codes do not map one to one):

| SW | Meaning | Like HTTP |
|---|---|---|
| `9000` | success | 200 |
| `6982` | security status not satisfied (a PIN not verified) | 401, 403 |
| `6A82` | file or application not found (SELECT of an unknown AID) | 404 |
| `6D00` | instruction not supported | 405, no such endpoint |
| `6A80` | incorrect data | 400 |
| `6F00` | no precise diagnosis (an uncaught exception in the applet) | 500 |
| `63CX` | verification failed, X tries left | none |

`SW` names them in tests (`SW.SECURITY_STATUS_NOT_SATISFIED`). When the applet answers `61XX` (more data) or `6CXX`
(wrong Le), the card of a `@JavaCardTest` class sends GET RESPONSE or the command again, as a PC/SC reader does, and
the test gets the whole answer.

Tests run on one of four backends: `embedded` runs the applet classes on jCardSim in the test JVM (the default);
`simulated-gp` converts the package, loads and installs it on a simulated GlobalPlatform card without a reader, and
runs the applet classes on jCardSim; `livecard` uses a development card in a PC/SC reader; `container` runs jCardSim
in Docker (for `@SmartCard` fields). The two GlobalPlatform backends add a few rules
([On the GlobalPlatform backends](#on-the-globalplatform-backends)).

## Testing

Recipes for the everyday tasks (commands as constants, status words, response data, the SELECT response,
data-driven tests, install parameters, several applets, a scenario across tests, reset and deselect, PIN, TLV,
watching the exchanges, timing a command, debugging a `6F00`, CI, your own test annotation) are in the
**[testing cookbook](TESTING.md)**; this section is the reference.

A test class marked `@JavaCardTest` declares the applets it needs; the card is a `SmartCardSession` parameter
(or a `@SmartCard` field) of tests, lifecycle methods and constructors:

| `@InstallApplet` attribute | Default | Meaning |
|---|---|---|
| `value` | (required) | the applet class |
| `isolation` | `PER_TEST` | `PER_TEST`: a fresh instance for every test (and every parameterized invocation), deleted after it; `PER_CLASS`: one instance for all tests of the class, deleted after the class |
| `aid` | the applet's own AID: the run's prefix and a suffix derived from the package and class names | instance AID suffix under the run's AID prefix; give it when the same applet is installed more than once |
| `params` | none | install parameters (hex) the applet's `install` method receives |

`@InstallApplet` is repeatable, works on a class, a `@Nested` class (deleted when the nested class ends) and a
test method (the instance exists for that test only). An `@InstallApplet` install selects nothing; after the
`PER_CLASS` instances of a class are installed the first of them is selected, for `@BeforeAll`; before every test
the first applet of the innermost scope is selected, unless it still is (so a `PER_CLASS` instance keeps a verified
PIN between tests). `card.install(...)` in `@BeforeAll` or `@BeforeEach` works too, is deleted with that scope and
selects the instance it installs (after one in `@BeforeEach`, the test starts with that instance selected). Tests use
`card.select(WalletApplet.class)` and `card.aid(...)` instead of literal AIDs (with several instances of a class,
`card.aid("<suffix>")`), `card.deselect()` deselects. A failed test, or a failed lifecycle method, carries the APDU
exchanges in its failure. Details and more scenarios: the [core README](core/README.md#declarative-card-tests).

The backend is chosen when the tests run, not in the code:

| `jcx.backend` | Runs on | Needs |
|---|---|---|
| `embedded` (default) | jCardSim in the test JVM | `javacard-express-core` |
| `container` | planned (the container protocol cannot delete applets yet); for now use `@SmartCard(mode = Mode.CONTAINER)` fields | `javacard-express-container`, Docker |
| `simulated-gp` | the real-card code path without a reader: conversion, APDU guard, SCP03, LOAD/INSTALL/DELETE, cleanup checked with GET STATUS, on a simulated GlobalPlatform card; the applets then run from their class files on jCardSim, so it checks conversion and card management, not the converted bytecode; fine on CI ([recipe](TESTING.md#on-ci)) | `javacard-express-livecard` (the applet parent has it) |
| `livecard` | the card in a PC/SC reader, through the guard and the safety rules of the [live-card guide](LIVE_CARD_TESTING.md) | `javacard-express-livecard` (the applet parent has it); a development card with SCP03 and known ISD keys ([requirements](LIVE_CARD_TESTING.md#requirements)); Oracle's off-card verifier for the CAP files (`jcx.livecard.verifierSdk=<Java Card kit>` matching the card, or `none` to load unverified CAP files at your own risk); a build for the card's Java Card version (`javacard.version`; a 3.0.4 card refuses a 3.0.5 CAP file with `6438`) |

```bash
mvn verify                                          # jCardSim
mvn verify -Djcx.backend=simulated-gp               # the real-card code path, offline
mvn verify -Djcx.backend=livecard -Djcx.livecard.verifierSdk=<kit> -Dtest=WalletAppletTest   # the card
```

`jcx.backend` is read from the system property, the JUnit configuration parameter or the environment variable
`JCX_BACKEND`; `livecard` is accepted only as a JVM system property, so no file can switch a build to the card,
and CI never selects it. `@EnabledOnBackend` / `@DisabledOnBackend` limit a test to backends (for example where
jCardSim differs from a card). With `livecard`, test classes run one at a time; their methods always run in the
class's thread.

Every backend uses the same AIDs. They start with the run's prefix: the project's own `F0` prefix (derived from
`groupId:artifactId`) when the applets come from a module the Maven plugin built, so tests of different projects
on one development card never touch each other's applets; `F04A4358` for applets in test sources. The GlobalPlatform
backends convert such a package with the settings of the build (`javaCardVersion`, `supportInt32`, package
version), which the plugin records in `META-INF/javacard/<package>.properties`: a test on a card runs the package
as it was built, and the transcript lists the build's AIDs next to the test AIDs.

### On the GlobalPlatform backends

`simulated-gp` and `livecard` convert the package, load it and manage it as on a card, behind the APDU guard of the
[live-card guide](LIVE_CARD_TESTING.md#the-apdu-guard). That brings rules a test on jCardSim does not see:

- **Commands to avoid** for the applet's own commands, in a proprietary class (CLA `80` to `FE`): INS `50` and `82`,
  which the guard reads as GlobalPlatform's INITIALIZE UPDATE and EXTERNAL AUTHENTICATE also inside the test's own
  applet (an applet that uses the GlobalPlatform API forwards them to its Security Domain, whose key set counts
  failed authentications), so they are blocked, and a blocked `82` also counts against the run's authentication
  budget, which allows one failure by default ([run-wide cap](LIVE_CARD_TESTING.md#run-wide-authentication-cap)):
  after it every further command is blocked, so the test fails even when it catches the exception, the rest of its
  class fails on `simulated-gp`, and the rest of the run is skipped on a card; INS `70`, and `A4` with P1 `04`, which
  the guard has to read as MANAGE CHANNEL and SELECT: answered with
  success, they leave the guard without knowing what is selected, and it blocks what follows. The guard's message
  names the command. Give the applet's own command another INS
  ([why](LIVE_CARD_TESTING.md#why-wrong-keys-never-cost-an-authentication-try)).
- **AIDs** of the test instances are the run's: `card.aid(...)`, `@InstallApplet(aid = "<suffix>")`; an install
  under a literal AID outside the run's prefix is refused (a SELECT of a literal AID is sent as it is). A prefix
  under a registered RID needs the setting `jcx.livecard.registeredRid`.
- **One package**: a package that imports another package of the project (a library module, a shareable interface
  in another package) is refused before conversion; such tests run only on the default backend for now
  (`@EnabledOnBackend(Mode.EMBEDDED)`).
- **Basic channel**: the simulated card runs test applets on the basic channel only; a SELECT of a test applet on
  a logical channel 1 to 3 (opened with `LogicalChannel.open(card)`) is answered `6881`.
- **Registration**: an applet that is installed under an AID other than its own, or more than once, registers
  with `register(bArray, (short) (bOffset + 1), bArray[bOffset])`; a card registers `register()` under the applet's
  own AID, and the backends note an applet that calls it.

The backends convert every applet of the package (those of the build descriptor, or every concrete `Applet`
subclass with an `install` method) and only the classes those applets need, from classes directories and jars
alike, so test classes next to a test applet are not converted, and `card.install(...)` works for any applet of a
loaded package. A failed test's transcript labels the card management: `# install`, `# load` with the number of LOAD
blocks, `# delete`, `# secure channel`.

The older style, one simulator per `@SmartCard` field, still works: `@SmartCard` marks a field of type
`SmartCardSession`, `LoggingSession` or `EmbeddedSession` (the annotation registers the extension):

| Attribute | Default | Description |
|-----------|---------|-------------|
| `mode` | `EMBEDDED` | `EMBEDDED` (jCardSim in the test JVM) or `CONTAINER` (Docker); `SIMULATED_GP` and `LIVECARD` are backends of `@JavaCardTest` classes (`jcx.backend`) and rejected here |
| `image` | `""` | Container mode: a pre-built simulator image; empty uses the server bundled with `javacard-express-container` |
| `log` | `false` | Wraps the session in a `LoggingSession` that also logs every exchange (fields and session parameters; in `@JavaCardTest` classes use `jcx.log`) |

The lifetime of the simulated card (per test method, per class for static fields and `PER_CLASS`) is described
in the [core README](core/README.md#complete-test-lifecycle).

Settings (system properties or JUnit configuration parameters):

| Property | Description |
|----------|-------------|
| `jcx.backend` | the backend of `@JavaCardTest` classes (above) |
| `jcx.aidPrefix` | AID prefix of everything `@JavaCardTest` classes install (hex, 4-10 bytes; default the project's prefix, see above, or `F04A4358`); on a real card the live-card setting `aidPrefix` wins |
| `jcx.supportInt32` | `true` or `false`: int support of the packages the GlobalPlatform backends convert (default as the build, else `false`) |
| `jcx.log` | `true` prints the exchanges of the card of `@JavaCardTest` classes while the tests run, one line each on standard output, and logs every `@SmartCard` field and session parameter that can hold a `LoggingSession` (java.util.logging, logger `name.velikodniy.jcexpress`, level INFO; a handler of your own on that logger receives the lines instead) |
| `jcx.embedded.classpathCheck` | `false` disables the check that jCardSim, not the API stubs, provides `javacard.*` at test runtime |
| `jcx.simulator.image`, `jcx.docker.dir`, `jcx.container.timeout` | Container mode, see the [container README](container/README.md#configuration) |

## What works, and how it is checked

| Part | What it does | How it is verified |
|------|--------------|--------------------|
| **Converter** | `.class` → `.cap` and `.exp`, written from the JCVM specification. Targets Java Card 2.1.2 to 3.2.0 (CAP 2.1, compact CAP 2.3). Fails with a clear error (class, method, source line) on code outside the Java Card language subset. | Spec-derived structural checks in every build. In the October 2026 audit, 108 of 108 valid corpus packages (including 7 open-source applets) passed Oracle's off-card verifier, 41 of 41 invalid ones were rejected, and 47 of 47 ran on Oracle's `cref` emulator. Details: [converter/BINARY_COMPATIBILITY.md](converter/BINARY_COMPATIBILITY.md). Converted test applets and two open-source applets run on a real NXP JCOP 4 card ([live-card report](LIVE_CARD_TESTING.md#verified-results)). |
| **Maven plugin** | `build` goal: discovers applets, derives AIDs, converts right after compilation (before the tests), attaches `.cap`/`.exp` and puts them with a build descriptor into the jar. Applet parent POM and BOM. | Unit tests and integration tests that build real sample projects, including the Quick Start above. |
| **API stubs** | Compile-only Java Card 3.0.5 Classic API: 15 packages, 103 types, class files for Java 8. | Compared with jCardSim's API in every build, and with an SDK API jar on request. |
| **Testing toolkit** (`core`) | JUnit 5 model: `@JavaCardTest` with `@InstallApplet` installs applets before a test or class and deletes them after it; the same test class runs on jCardSim, on a simulated GlobalPlatform card or on a real card. Fluent APDU API, AssertJ assertions, TLV, PIN, APDU history attached to failures, logical channels. | Unit tests. jCardSim does not model the applet firewall, memory limits or logical channels; see the [core README](core/README.md#backends). |
| **PC/SC backend** (`core`) | `PcscSession` for physical readers through `javax.smartcardio`. | Tests against `javax.smartcardio` fakes; the live-card suite on a real NXP JCOP 4 card ([report](LIVE_CARD_TESTING.md#verified-results)). |
| **GlobalPlatform** (`gp`) | SCP02 and SCP03 (S8/S16, AES-128/192/256), card content management, PUT KEY, key diversification, CAP parsing. | Known-answer transcripts from independent sources (spec-derived reference, public real-card logs, Samsung OpenSCP-Java); SCP03 (S8, levels 01 and 03) and card content management verified on a real NXP JCOP 4 card by the live-card suite ([report](LIVE_CARD_TESTING.md#verified-results)). |
| **Secure Messaging** (`sm`) | ICAO 9303-11 profile of ISO 7816-4 secure messaging, 3DES and AES, short and extended APDUs. | ICAO 9303-11 Appendix D worked examples, byte for byte. |
| **PACE, BAC** (`pace`) | PACE with ECDH Generic Mapping and AES (all standardized curves), BAC. Not implemented: DH, Integrated Mapping, Chip Authentication Mapping, EAC. | ICAO 9303-11 Appendices D and G.1, plus an independent chip simulator. |
| **Container** (`container`) | The same jCardSim in a Docker container (Testcontainers) for process isolation. | Tests on Docker; same session behaviour as the embedded backend. |
| **Live card** (`livecard`) | Tests on a real card in a PC/SC reader behind an independent APDU allow-list (wrong keys never cost an authentication try), deployment of converted applets, cleanup, JUnit 5 `@LiveCardTest`, and the project's live suite. | The live suite on a real NXP JCOP 4 card ([report](LIVE_CARD_TESTING.md#verified-results)); in every build, the guard's rules and the whole suite offline against a simulated card. |

The converter and the protocol modules are independent implementations written from public specifications;
Oracle's tools are used only as black-box checks and are not needed to build or use the project. See
[PROVENANCE.md](PROVENANCE.md).

## Verified on a real card

The converter, the GlobalPlatform module and the PC/SC backend are new implementations written from the
specifications; they contain no Oracle code and need no Oracle SDK. They have nevertheless been verified on a
real card for the main scenarios, and they work there: on an NXP JCOP 4 development card (Java Card 3.0.4,
GlobalPlatform 2.1.1 card management, SCP03) the live-card suite passes 52 of its 53 tests; the remaining one
needs HMAC, which that card does not offer. The suite covers:

- SCP03 authentication at security levels 01 and 03, and GET DATA / GET STATUS under C-MAC and C-DECRYPTION;
- CAP files written by this converter, each also accepted by Oracle's off-card verifier, loaded, installed with
  install parameters and as a second instance, and deleted; an installed application locked and unlocked;
- the converter's translation executing on the card: virtual dispatch, exceptions, arrays, wide branches and
  switches, static fields, transactions and transient memory, APDU I/O, a shareable interface across two
  packages, the int type, with answers equal to jCardSim's (and to the Java Card specification where jCardSim
  differs);
- AES, 3DES, SHA-1, SHA-256, ECDSA P-256 and RSA-2048 on the card, signatures verified on the host;
- `PcscSession`: exclusive access, Le encoding, `reset()`, logical channels;
- two open-source applets built from their upstream sources by this toolchain, PivApplet (PIV) and SmartPGP
  (OpenPGP card): keys generated on the card, signatures verified on the host;
- the declarative test model on the card: `@JavaCardTest` classes that run on jCardSim ran unchanged with
  `-Djcx.backend=livecard` (installs and deletes per test and per class, several instances, nested classes,
  parameterized tests) and observed exactly what they observe on the simulator; a package built by the Maven
  plugin was converted with its build settings under the project's own AID prefix.

A new applet project built on the applet parent of the Quick Start above, with
`<javacard.version>3.0.4</javacard.version>` for the card, ran its own JUnit test on that card with
`mvn verify -Djcx.backend=livecard -Djcx.livecard.verifierSdk=<kits>/jc304_kit`: built, verified, loaded, answered,
deleted.
What was run, how, and every result: **[live-card testing report](LIVE_CARD_TESTING.md#verified-results)**.

That is one card. Other cards, SCP02, the SCP03 S16 mode, secure messaging and PACE have been tested only
against recorded transcripts, published worked examples and simulators.

Tests against a real card run only on request, with a card in the reader: the project's own suite with
`./mvnw -Plivecard verify -pl livecard -am`, your `@JavaCardTest` classes with `-Djcx.backend=livecard`
([guide](LIVE_CARD_TESTING.md)). An independent APDU allow-list checks every command before it reaches the card.
Normal builds and CI never run them and never open a PC/SC reader; build checks enforce both
(`NoLiveCardTestsInCiTest`, `PcscAccessGuardTest`).

## Modules

| Module | Artifact | Purpose | Docs |
|--------|----------|---------|------|
| API stubs | `javacard-express-api` | Compile-only Java Card 3.0.5 Classic API | — |
| Converter | `javacard-express-converter` | `.class` → `.cap`/`.exp` library | [README](converter/README.md) |
| Maven plugin | `javacard-express-maven-plugin` | `build` goal for applet and library projects | [README](maven-plugin/README.md) |
| Core | `javacard-express-core` | JUnit 5 model (`@JavaCardTest`, `@InstallApplet`), sessions (jCardSim, PC/SC), APDU builder/codec, TLV, assertions, PIN, logging, channels | [README](core/README.md) |
| GlobalPlatform | `javacard-express-gp` | SCP02/SCP03, card content and key management, CAP parsing | [README](gp/README.md) |
| Secure Messaging | `javacard-express-sm` | ICAO 9303-11 secure messaging (3DES, AES) | [README](sm/README.md) |
| PACE | `javacard-express-pace` | PACE (ECDH Generic Mapping, AES) and BAC | [README](pace/README.md) |
| Container | `javacard-express-container` | jCardSim in Docker via Testcontainers | [README](container/README.md) |
| Live card | `javacard-express-livecard` | The `livecard` and `simulated-gp` backends of `@JavaCardTest`: PC/SC session behind an APDU allow-list, conversion, deployment and verified cleanup; the live suite | [Guide](LIVE_CARD_TESTING.md) |
| BOM | `javacard-express-bom` | Versions of all toolkit artifacts and of the plugin | [plugin README](maven-plugin/README.md#the-shortest-pom-the-applet-parent) |
| Applet parent | `javacard-express-applet-parent` | Parent POM of applet projects: compiler settings, dependencies, plugin binding | [plugin README](maven-plugin/README.md#the-shortest-pom-the-applet-parent) |

Dependencies between the modules:

```
converter ← maven-plugin            (javacard-api is compile input for applets, not a dependency)

core ← gp
core ← sm ← pace
core ← container
core + gp + converter ← livecard
```

## Building from source

```bash
./mvnw verify -pl '!container'                                  # all modules except the Docker tests
(cd docker && ../mvnw package) && ./mvnw test -pl container -am  # container tests (Docker)
./mvnw verify -Prelease -Dgpg.skip -DskipTests -pl '!container'  # javadoc and source jars, as in a release
./mvnw install -DskipTests                                      # this checkout into ~/.m2, for your projects
```

The version of a checkout is set in one place, `.mvn/maven.config` (`-Drevision=...`, a `-SNAPSHOT` between
releases); the release workflow passes the release version with `-Drevision`. The snippets on these pages show the
release that the checkout becomes, the same version without `-SNAPSHOT`. To use a checkout before its release,
install it and put its `-SNAPSHOT` version into your POM.

The git submodules under `livecard/third_party` (two open-source applets that the live-card suite uses as test
subjects) are optional: the build does not need them, and their tests are skipped without them
(`git submodule update --init` fetches them).

Optional checks with a locally installed Oracle SDK: [tools/oracle/README.md](tools/oracle/README.md).

## License

Apache License 2.0, see [LICENSE](LICENSE) and [NOTICE](NOTICE). Java and Java Card are trademarks or
registered trademarks of Oracle and/or its affiliates; this project is not affiliated with Oracle.
