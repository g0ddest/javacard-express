# Changelog

Notable changes of each release. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions
follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html), and before 1.0 a minor release can change the API.

## [0.4.0] - 2026-10-03

CAP files pass Oracle's off-card verifier, secure channels work on real cards, and one applet test class runs on
jCardSim, on a simulated GlobalPlatform card or on a card in a PC/SC reader. The toolkit was checked on a real card
(NXP JCOP 4, see [LIVE_CARD_TESTING.md](LIVE_CARD_TESTING.md)).

### Upgrading from 0.3.0

- **Rebuild every CAP file built with 0.3.0 or earlier.** Their Class component has a layout that Oracle's verifier
  rejects and with which a card can dispatch virtual methods wrongly; more conversion errors are listed under Fixed.
- **Recompile applets that use these members of `javacard-express-api`**, whose wrong values or signatures are
  compiled into the class files: `Cipher.ALG_AES_CBC_ISO9797_M1/M2`, `ALG_AES_CBC_PKCS5`, `ALG_AES_ECB_ISO9797_M1/M2`,
  `ALG_AES_ECB_PKCS5`, `KeyBuilder.TYPE_HMAC`, `TYPE_HMAC_TRANSIENT_RESET`, `TYPE_HMAC_TRANSIENT_DESELECT`,
  `APDU.PROTOCOL_MEDIA_CONTACTLESS_TYPE_A/B`, `PROTOCOL_MEDIA_USB`, `APDU.STATE_ERROR_*`, `APDUException.T1_IFD_ABORT`,
  `NO_T0_REISSUE`, `APDU.getInBlockSize()`, `APDU.getOutBlockSize()`, `Signature.getLength()`.
- **Maven plugin:** declare the `build` goal in an `<executions>` block, or inherit the new
  `javacard-express-applet-parent` and name the plugin (README, Quick Start); without either the goal does not run.
  The goal runs in `process-classes`, before the tests, and needs Maven 3.9.0 or later. Applet AIDs are the package
  AID followed by an index (`A00000006212` → `A0000000621201`); in 0.3.0 they had a foreign RID, which cards reject.
- **GlobalPlatform:** `GPSession.open()` needs explicit keys (`keys(SCPKeys.defaultKeys())` for development cards).
- **Tests on jCardSim:** `EmbeddedSession.reset()` is a card reset that keeps the applets and their data (it wiped the
  card), and the applet's `install` method gets the install data in the Java Card layout `[Li AID][Lc][La data]`, as
  on a card (it got the bare parameters). Every session loads the applet classes with its own class loader, as a
  card loads a package: their static fields start fresh in every session, and a test's own reference to an
  applet's static field (`MyApplet.counter`) sees the test's copy of the class, not the card's (in 0.3.0 all
  sessions ran the test's classes and shared their static fields); check the applet's state through its commands.
  `-Djcx.embedded.sharedStatics=true` restores the 0.3.0 behaviour for now.
- **PACE:** `PaceParameterId` IDs and ordinals follow ICAO 9303-11 Table 12. `password(PasswordRef.MRZ, bytes)` takes
  the password key K_π as given, like the other references (0.3.0 derived a key from the bytes); `mrzPassword(...)`
  derives it from the MRZ.

### Added

- **Declarative applet tests** (core): `@JavaCardTest` with the repeatable `@InstallApplet(value, aid, params,
  isolation)` installs applets before a class, a `@Nested` class or a test method and deletes them afterwards, also
  when a test fails. `Isolation.PER_TEST` (default) gives every test and every parameterized invocation a fresh
  instance, `PER_CLASS` one instance for the class. Tests, lifecycle methods and constructors get the card as a
  `SmartCardSession` parameter or `@SmartCard` field. New `SmartCardSession` methods `aid(Class)`, `aid(String)` and
  `deselect()`.
- **One test class for every backend**, chosen when the tests run with `jcx.backend`: `embedded` (jCardSim, default),
  `simulated-gp` (a simulated GlobalPlatform card: conversion, SCP03, LOAD, INSTALL and DELETE without a reader; fine
  on CI) and `livecard` (the card in a PC/SC reader; accepted only as a JVM system property). AIDs are the same on every
  backend: the project's prefix (`F0` + 4 bytes of SHA-256 of `groupId:artifactId`, or `jcx.aidPrefix`) plus suffixes
  derived from the package and class names. `@EnabledOnBackend` and `@DisabledOnBackend`; backends are a
  `ServiceLoader` SPI.
- The card behaves the same on every backend: an `@InstallApplet` install selects nothing (`card.install(...)`
  selects the instance it installs); the first `PER_CLASS` applet is selected
  for `@BeforeAll`, and before each test the nearest applet is selected unless it still is, so a `PER_CLASS` instance
  keeps its `CLEAR_ON_DESELECT` memory (a verified PIN) between tests; `send` completes `61XX` (GET RESPONSE) and
  `6CXX` (the exact Le) like a PC/SC reader; `card.aid(Class)` refuses to choose between several instances;
  `card.history()` holds the current test's exchanges.
- A failed test, lifecycle method or declared install carries the APDU exchanges (a suppressed exception; a failed
  test also publishes the JUnit file entry `apdu-transcript.txt`). The transcript marks where the test body starts,
  says when the run installed no applet, and on the embedded backend names an exception that escaped the applet next to its
  `6F00`.
- **`javacard-express-livecard`** (new module): the `simulated-gp` and `livecard` backends, and the `@LiveCardTest`
  harness for scripted tests on a real card: deployment of the project's packages (loaded only after Oracle's off-card
  verifier, run as a black box, accepted them, or with `verifierSdk=none`), install, cleanup of everything a test
  class created, APDU transcripts. The backends convert every applet of a package and only the classes those
  applets need, from classes directories and jars (test-source applets next to their tests, packages split between
  main and test sources), and label the card management in transcripts (`# install`, `# load`, `# delete`).
  An independent APDU guard checks every command before it is sent: no key or card state changes, no foreign
  applications, EXTERNAL AUTHENTICATE only after the card cryptogram matched the configured keys, and a cap on
  authentication failures. Live mode is off unless a run sets `-Djcx.livecard.enabled=true` or
  `-Djcx.backend=livecard`, and refused on CI. See [LIVE_CARD_TESTING.md](LIVE_CARD_TESTING.md).
- **`javacard-express-bom`** manages the versions of all artifacts and of the Maven plugin.
  **`javacard-express-applet-parent`** is a parent POM for applet projects: Java 8 applet code, Java 25 tests, the test
  dependencies, the plugin's version and goal binding. A project needs its coordinates, `javacard.packageAid` and the
  plugin's name.
- **`PcscSession`** (core), the PC/SC backend: exclusive access, card reset that keeps the session usable, logical
  channels, `PcscException`.
- Core: `ClassByte`; `APDUSequence.leCorrection(boolean)` for commands already protected by a secure channel.
- **Test helpers** (core): `SW` names the status words and describes them (`SW.describe`, `SW.format`);
  `APDUResponse` reads numbers and ranges of the data (`u8`, `u16`, `s16`, `data(from, to)`) and checks a set of
  status words (`requireSw`); `APDUResponseAssert` has the AssertJ-style names `hasStatusWord`, `hasStatusWordIn`,
  `hasSw2`, `isNotSuccess`, `hasData`, `hasDataHex` and `hasNoData`, navigates to `data()`, `u8`, `u16` and `s16`,
  and reports status word and data mismatches with expected and actual value, which IDEs show as a diff.
- `APDUCommand` (core), an immutable command that tests keep in constants or take as `@CsvSource` arguments
  (`fromHex`), sent with `SmartCardSession.send(APDUCommand)`; `sendHex(String)` sends a command written as hex;
  `APDUCommand.select(AID)` is a SELECT whose response (the FCI) a test checks.
- `EmbeddedSession.installWithoutSelecting(...)` and `EmbeddedSession.deselect()`.
- `PinSession`: `cla(...)`, `padTo(length, padByte)` (PIV-style padding), `retries(ref)` (an `OptionalInt`) and
  `isVerified(ref)`.
- `-Djcx.log=true` prints the exchanges of the card of `@JavaCardTest` classes while the tests run, one line each on
  standard output (a handler on the logger `name.velikodniy.jcexpress` takes them instead); `@SmartCard(log = true)`
  also works on session parameters.
- Maven plugin: the CAP and export files are attached as artifacts (types `cap` and `exp`) and written, with a build
  descriptor `META-INF/javacard/<package>.properties`, into the classes directory, so a library carries its export
  file in its jar and the card test backends convert a package as the build did (`classesOutput`). New parameters
  `finalName`, `classifier`, `attach`, `exportPath`, `classesOutput` and `checkVersions`; export files are also found
  in dependencies of type `exp` and inside dependency jars.
- Converter: `Builder.exportPath(Path...)`, export files from directories and jars (`<package>/javacard/<name>.exp`),
  export files of format 2.0 to 2.3, built-in API data of all standard packages of Java Card 2.1.2 to 3.2.0.
- GlobalPlatform: SCP03 S16 mode, SCP02 implementation options (default `'15'`), a check of the security level against
  protocol and options; new record components of `AppletInfo`, `CardInfo` and `KeyInfoEntry`.
- PACE: five more curves in `PaceParameterId`; `canPassword`, `pinPassword` and `pukPassword`. Basic Access Control
  (`BacSession`).
- Container: the simulator server is bundled in `javacard-express-container`, so container mode works in any project;
  `-Djcx.simulator.image` and `-Djcx.docker.dir` choose the image.
- [PROVENANCE.md](PROVENANCE.md) (how the code is written from public specifications) and `NOTICE`; the jars contain
  `LICENSE` and `NOTICE`.
- [TESTING.md](TESTING.md), a testing cookbook whose snippets the build compiles and runs (also a scenario across
  tests, the SELECT response, debugging a `6F00`, CI), and a "Java Card for Java developers" section in the README.

### Changed

- Converter: the Class component always uses the `class_info` layout of JCVM 3.1 §6.9. `<clinit>` is evaluated at
  conversion time into the Static Field image (array initializers included) and not emitted as a method.
  `Builder.generateExport` defaults to `true`: the CAP file gets the Export component when the package exports
  something (§6.2, §6.13). Output is deterministic.
- Converter: input outside the Java Card subset is reported as an error naming class, method and source line: types
  and constructs (enum, record, lambda, `assert`, `synchronized`, multi-dimensional arrays, `String` constants), `int`
  intermediate values that change a result, access rules (§2.2.1.1.6), applet and AID rules, packages that mix
  multiselectable and other applets (§2.2.5), Java Card RMI (§2.2.6), JCVM limits, and references that the target Java
  Card version lacks (the error names the version that introduced the element).
- Maven plugin: the goal runs in `process-classes` (it ran in `package`), so code outside the subset fails the build
  before the tests. `<javaCardVersion>` is the parameter's name (`javaCardVersionStr` stays an alias); `generateExport`
  is automatic (libraries export their public types, applet packages only public shareable interfaces). The goal fails
  when `javacard-express-api` or `javacard-express-core` has another version than the plugin, when two executions
  write the same file, and when there are no compiled classes. Errors are reported once each as
  `<file>:[<line>] <class>.<method>(): <reason>`.
- API stubs: the complete Java Card 3.0.5 Classic API (15 packages, 103 types; 0.3.0 had 45 classes in 3 packages) as
  Java 8 class files (they were Java 25), so any `javac` from 8 up compiles applets against them. Executing a stub
  member throws `RuntimeException("stub")`.
- Core: `@SmartCard` registers `JavaCardExtension` itself and may annotate parameters; sessions belong to the JUnit
  context (per test method, per class for static fields). The `le` argument of `send(...)` means the same on every
  backend: `SmartCardSession.NO_LE` (`-1`) sends no Le, 1 to 65536 is Ne (256 is sent as `'00'`). `EmbeddedSession`
  throws `IllegalStateException` when an AID is installed twice and `SelectException` when a SELECT fails, and fails
  fast when the API stubs shadow jCardSim's classes. An applet whose install method fails is reported as
  `InstallException` (an `IllegalStateException`) that names the applet, the instance AID, the install parameters
  and how a test passes them, with the applet's `ISOException` reason (jCardSim reported a bare `SystemException`).
  JUnit Jupiter API and AssertJ are compile-scope dependencies of `javacard-express-core` (they were `provided`).
- Core: `logged(true)` prints one line per exchange on standard output (it printed java.util.logging's two-line
  records on standard error). `JCXAssertions` extends AssertJ's `Assertions`, so one static import serves both. CLA, INS, P1 and P2 also
  take an applet's `byte` constants: -128 to -1 stand for the bytes `80` to `FF` (they were rejected). Status word
  descriptions in messages follow ISO/IEC 7816-4:2005 (`6A82` is "file or application not found").
- GlobalPlatform: `loadAndInstall` uses the applet AID from the CAP file as module and default instance AID (it used
  the package AID); case 2 and 4 commands send Le `'00'`; CPLC data must be 42 bytes; KDF3 key diversification matches
  GlobalPlatformPro; a protected command is sent once (after `6CXX` it is protected again with the corrected Le).
- Secure messaging: an unprotected response ends the SM context (an unprotected `9000` is an `SMException`), and so do
  a plain SELECT, install, reset and close.
- Container: sessions behave like embedded ones (card reset, Java Card install layout, `SelectException`,
  `InstallException`, channel commands refused); the server serves concurrent sessions with timeouts and reports
  errors with their original type.
- Build: jCardSim's unused ASM dependencies are excluded; jars are reproducible; published POMs link to the GitHub
  repository. A release validates its tag and runs all tests before anything is published.

### Deprecated

- `Converter.Builder.oracleCompatibility(boolean)` and the plugin parameter `oracleCompatibility`: no effect. The
  "Oracle off-by-one bug" they reproduced was a misreading of JCVM §6.9 (see
  [converter/BINARY_COMPATIBILITY.md](converter/BINARY_COMPATIBILITY.md)).
- `@SmartCard(persistentMemory)`: jCardSim does not model memory size; values other than the default are rejected.
- `EmbeddedSession(boolean)`, `EmbeddedSession(boolean, int)` and `ContainerSession(String, int, AutoCloseable,
  boolean)`: their flags never had an effect; `logged()` logs APDUs.
- `GPSession.pseudoRandomChallenge(boolean)`, `CardInfo.parse(byte[], boolean)` and `SCP03.from` with that flag: the
  INITIALIZE UPDATE response tells whether the card uses pseudo-random challenges. `SCP03.rmacChaining()`: use
  `macChaining()`. `GP.SCP03_DERIVE_DEK`: `'05'` is RFU in Amendment D.
- `GPSession.terminateApp(...)` always throws: a Security Domain can only lock and unlock another application (GPCS
  v2.3.1 11.10.2.2); use `lockApp` or `deleteAid`.
- `PaceMrz.computeKSeed`: use `encodeMrzPassword` (PACE) or `bacKeySeed` (BAC).
- The old `generate` overloads of the converter's `ClassComponent`, `DescriptorComponent` and `ExportComponent`.
- The system property `jcx.embedded.sharedStatics=true` (every `EmbeddedSession` runs the test's applet classes and
  shares their static fields, as in 0.3.0): it will be removed in the next release.

### Removed

- API stubs: members that the Java Card API does not have (`APDU.getMaxCommitCapacity()`, `AID.hashCode()`,
  `Applet.equals` and `hashCode`, the public constructors of `Util`, `JCSystem`, `KeyBuilder` and `APDU`).
- The Oracle-generated reference CAP files are no longer in the repository; `tools/oracle/generate-oracle-refs.sh`
  writes them to the ignored `build/oracle-refs/`.

### Fixed

- Converter (rebuild CAP files of 0.3.0):
  - the Class component layout: Oracle's verifier rejected every CAP file of the default mode;
  - tokens (JCVM §4.3.7): package-visible classes, package-visible virtual methods (private tokens and a package
    method table), instance field token groups (`int` takes two), interface methods including superinterface ones,
    shareable interfaces first; a public or protected override of a package-visible method is rejected;
  - bytecode: `arraylength`, `dup_x`, catch types after constant pool reordering, long branches, `getfield` and
    `putfield` above constant pool index 255, `_this` forms in static methods, array types in `checkcast` and
    `instanceof`, super calls (`CONSTANT_SuperMethodref`), inherited static members, overloads linked by name and
    descriptor, `equals` on an interface-typed reference, `supportInt32(true)` code that verifies;
  - class files the converter rejected or crashed on: `jsr`/`ret` subroutines (class files of version 49 and older)
    are inlined, class files without a `StackMapTable` convert, private methods of nestmates (javac 11 and later),
    abstract classes that leave interface methods to their subclasses;
  - the Method, Descriptor, Import, Export and Directory components (the Import component recorded the export file
    format instead of the package version), and the export file writer (JCVM Chapter 5: format 2.1 up to 3.0.5, 2.3
    for 3.1 and 3.2).
- Maven plugin: `<javaCardVersion>` was ignored; applet discovery finds indirect `Applet` subclasses and skips abstract
  ones.
- API stubs: 18 wrong constant values; the return types of `APDU.getInBlockSize()`, `getOutBlockSize()` and
  `Signature.getLength()`.
- Core: `EmbeddedSession.reset()` wiped the card; `LogicalChannel` encodes channels 0 to 19 (ISO/IEC 7816-4);
  `APDUSequence` sends GET RESPONSE with the command's class byte, handles `6C00` as 256 and stops runaway chains;
  BER-TLV parsing follows ISO/IEC 7816-4 §5.2.2 and limits the nesting depth; `APDUCodec` and `AID` validate ranges;
  `LoggingSession` logs exactly the bytes sent. `APDUResponseAssert.statusWord` and `hasSw1` and
  `new APDUResponse(data, sw)` accept the `short` status words of the Java Card API (`ISO7816.SW_NO_ERROR` failed as
  `FFFF9000`) and reject values outside two bytes (one for SW1).
- GlobalPlatform: SCP02 and SCP03 failed on real cards. They were rewritten against the specifications and known-answer
  transcripts: SCP02 card cryptogram, ICV encryption, C-ENC and R-MAC; SCP03 INITIALIZE UPDATE layout, cryptograms,
  encryption counter, R-MAC chaining, R-ENC ICV, AES-192 keys. Also SET STATUS, GET STATUS continuations (`6310`),
  INSTALL parameter lengths, LOAD block sizes, extended key information, PUT KEY of AES keys over SCP02 and 3DES keys
  over SCP03.
- Secure messaging: the AES IV (encrypted send sequence counter), verification of protected error responses, extended
  length, odd INS (DO'85'), the logical channel in the class byte.
- PACE: the Generic Mapping generator, Brainpool keys on JDK 25, validation of the chip's public keys, padding of MRZ
  document numbers, Le `'00'` in GENERAL AUTHENTICATE.
- Container: the published images up to 0.3.0 contained no server jar and did not start; the server crashed on
  ordinary input; the client ships the applet's whole class closure.

### Security

- GlobalPlatform: EXTERNAL AUTHENTICATE is not sent when the card cryptogram does not verify, never retried and never
  encrypted; a host challenge is used once; session keys are destroyed on `close()`.
- Secure messaging and PACE: MACs are compared in constant time, `toString()` hides keys, PACE sessions implement
  `Destroyable`.
- Container: the image runs as user 65534; `@SmartCard(mode = CONTAINER)` publishes the port on `127.0.0.1` only, drops
  all capabilities and gives each container a random access token; outside Docker the server listens on the loopback
  interface.

### Known limitations

- The GlobalPlatform backends (`simulated-gp`, `livecard`) cannot test a package that imports another package of the
  project (a library module, a shareable interface in another package): they refuse it before conversion. Such tests
  run on the embedded backend.
- The simulated GlobalPlatform card runs test applets on the basic channel only, loads CAP files of any Java Card
  version and always has the `int` type; like the embedded backend it runs the applet classes on jCardSim, not the
  converted CAP file.

## [0.3.0] - 2026-04-04

### Added

- API stubs: `APDU.getOffsetCdata()`.

### Fixed

- Converter: Descriptor component tokens of methods and fields that are not exported; export files in format 2.1.
- API stubs: `Util.arrayCompare()` returns `byte`.

## [0.2.1] - 2026-03-29

### Added

- Converter: built-in API data of more `javacard.security` classes (`Signature`, `MessageDigest`,
  `InitializedMessageDigest`, `SignatureMessageRecovery`, ...).

### Fixed

- Converter: private instance methods that javac 11 and later calls with `invokevirtual` (nestmates, JEP 181) are
  invoked with `invokespecial`; `invokeinterface` carries the interface's class reference and the method token (JCVM
  §7.5.49).

## [0.2.0] - 2026-03-04

### Added

- `javacard-express-converter`, a clean-room `.class` → `.cap`/`.exp` converter; `javacard-express-api`, compile-only
  Java Card API stubs; `javacard-express-maven-plugin`, a `build` goal around the converter.

## [0.1.1] - 2026-02-27

### Added

- `javacard-express-container` is published to Maven Central.

Maven Central also lists a version `.0.1.1`, published by mistake from a malformed tag; it cannot be deleted. Ignore
it.

## [0.1.0] - 2026-02-27

### Added

- `javacard-express-core`: `SmartCardSession`, the jCardSim backend `EmbeddedSession`, the JUnit 5 extension with
  `@SmartCard`, APDU builder and codec, BER-TLV, AssertJ assertions, PIN helper, APDU logging.
- `javacard-express-gp` (GlobalPlatform card content management, SCP02 and SCP03), `javacard-express-sm` (ISO/IEC
  7816-4 secure messaging), `javacard-express-pace` (PACE); container mode on Testcontainers.

[0.4.0]: https://github.com/g0ddest/javacard-express/compare/0.3.0...0.4.0
[0.3.0]: https://github.com/g0ddest/javacard-express/compare/0.2.1...0.3.0
[0.2.1]: https://github.com/g0ddest/javacard-express/compare/0.2.0...0.2.1
[0.2.0]: https://github.com/g0ddest/javacard-express/compare/v0.1.1...0.2.0
[0.1.1]: https://github.com/g0ddest/javacard-express/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/g0ddest/javacard-express/releases/tag/v0.1.0
