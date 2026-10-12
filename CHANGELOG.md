# Changelog

Notable changes of each release. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); versions
follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html), and before 1.0 a minor release can change the API.

## [0.5.0] - 2026-10-12

Applets can use the GlobalPlatform Card API, javac reports code outside the Java Card subset at its line (in the
build, in IntelliJ IDEA's build and, with the new IDE plugin, in its editor), and one plugin in the POM sets an
applet project up. The GlobalPlatform API was checked on the real card of the live-card tests (LC-GP, see
[LIVE_CARD_TESTING.md](LIVE_CARD_TESTING.md)).

### Upgrading from 0.4.0

- **Optional: declare the plugin as a build extension.** `<extensions>true</extensions>` on
  `javacard-express-maven-plugin` lets it set the project up; a POM that configures everything itself keeps working
  as before, and the goal says in one line how to switch (`-Djavacard.setup=false` hides it).
- **The javac check comes with the build extension and with the applet parent.** Code outside the Java Card subset,
  which the conversion rejected in `process-classes`, now fails in `compile`, also in IntelliJ IDEA's build, with
  the converter's messages. The check needs javac of JDK 25 or newer (Maven's own javac is) and stays out of a
  compilation with a toolchain or a forked javac; `-Djavacard.check=false` turns it off.
- **Record patterns** on `ConverterResult`, `BuildDescriptor`, `AppletPackage`, `ClassReferences.Reference` and
  `APDULogEntry` need their new components, and two `ClassReferences.Reference` objects are equal only with the same
  `site`.
- **GlobalPlatform stand-ins:** the simulated cards implement `org.globalplatform` themselves; a GlobalPlatform API
  jar ahead of `javacard-express-core` on the test class path, or stand-in classes of that package among the test
  classes, are reported when an applet uses the package.

### Added

- **One plugin is the whole setup.** Declared with `<extensions>true</extensions>`, `javacard-express-maven-plugin`
  sets an applet project up from a POM with nothing but its coordinates and the plugin: it binds the `build` goal,
  adds `javacard-express-core`, `javacard-express-livecard` (test) and `javacard-express-api` (provided), keeps the
  stubs off the test class path of Surefire and Failsafe, compiles the applet code for Java 8 and the tests for Java
  25, sets UTF-8 sources, and adds the javac check below. It adds only what the POM does not configure itself and says
  in one line of the build log what it added and what it left out: a version the project's dependency management gives
  a toolkit artifact wins over the plugin's; a project that tests with TestNG and not with JUnit Jupiter, or has its
  own jCardSim (under any group id), keeps them and gets only the stubs, with a warning, while JUnit 4 tests, also
  from a parent, keep running next to the toolkit's; and the check stays out where it would hide the project's own
  annotation processors or where another JDK's javac compiles. It only appends to lists the POM configures and never
  adds test runtime settings. `-Djavacard.setup=false` (`<setup>`) turns it off; a setup that fails stops the build
  with the project's name and that switch. It runs on Maven 3.9.0 and newer (Maven 4 is not tested); on a JDK older
  than 25, JDK 8 included, it stops the build with that reason (`-Djavacard.setup=false` lets other commands run), and
  when it is declared but did not run, the goal warns. In a multi-module build it belongs in the applet modules, not
  in a parent's `<build><plugins>`, where every module would inherit it. Without the flag the plugin works as before
  and its goal points to the flag in one line. The README Quick Start now shows this POM; the explicit POM, which
  writes out everything the extension adds, stays for IDEs and tools that do not run Maven build extensions (an IDE
  sees what the extension adds only if its Maven import runs it: IntelliJ IDEA 2026.2 does, NetBeans is not verified).
  See [Quick Start](maven-plugin/README.md#quick-start).
- **Java Card errors at compile time.** The new `javacard-express-javac-plugin` (managed by the BOM) lets javac check
  the applet code with the converter's rules: code outside the Java Card language subset (`long`, `String`,
  `synchronized`, lambdas, enums, `int` without int support, ...) and API the target platform does not have (a
  later Java Card version, the Java SE library, a later version of `org.globalplatform` than a fixed one) are
  compile errors at their source lines, in `mvn compile` and in IDE builds that run javac with it, before a test on
  jCardSim could pass with them.
  It checks the package the plugin converts with the plugin's settings, including the export files given in
  `<importExportFiles>` and `<exportPath>` when every execution of the goal has the same ones, and never the
  tests; the build extension adds it to the compilation of the applet code, `-Djavacard.check=false` (`<check>`)
  and `-Djavacard.skip=true` turn it off, and without the extension it goes on the annotation processor path (the
  explicit POM of the plugin README has it). It runs in javac of JDK 25 or newer and never stops javac by failing
  itself: where it cannot run (another compiler, class files kept in memory) it says so in a note, and a class
  file it cannot read is a warning that names it (`-Werror` turns such warnings into failures). It is reliable in
  parallel builds (`-T`): the converter reads its built-in API tables without the JVM's shared cache of open jar
  files, so javac closing one compilation's class loader no longer breaks the check, or a conversion, of another
  module. Code compiled without line numbers (`-g:none`, Maven's `debuglevel` without `lines`) is checked too,
  with the errors at their members, and each catch clause is reported at its own line. An export file or export
  path entry that is not there yet (another module writes it when it converts) or holds an unresolved expression
  does not fail the compilation: the check then checks the language subset only and leaves the linking to the
  conversion, with a note. A method inherited through a supertype of another package of the build (a library's
  shareable interface) is left to the conversion, which has that package's export file. IntelliJ IDEA's own build
  runs the check too (IDEA hands annotation processors a wrapper of javac's environment, which the check sees
  through). See [Compile-Time Check](maven-plugin/README.md#compile-time-check).
- **Java Card errors in the IntelliJ IDEA editor.** A plugin (`intellij-plugin/`, built with Gradle) marks the errors
  of the javac check while you type, at their source lines and with the build's messages: it compiles the open
  file's package in memory with the project's own check (the jars on the module's annotation processor path) and
  the build's options, for main code whose compilation runs the check. IntelliJ IDEA 2026.2 or newer; installed
  from disk for now. See [intellij-plugin/README.md](intellij-plugin/README.md).
- **Values of the Java Card types in tests.** `APDUResponse.dataAsByte()`, `dataAsShort()`, `dataAsInt()` and
  `dataAsBoolean()` read the whole response data (exactly one, two or four bytes, big-endian as `Util.setShort` and
  `JCint.setInt` write them; `00` or `01` for a boolean), `byteAt`, `shortAt` and `intAt` read a value at an
  offset, signed like the applet's. `assertThat(response)` has the same names as assertions, with an integer
  assertion for every number, so `assertThat(card.send(GET_BALANCE)).isSuccess().dataAsShort().isEqualTo(26)` takes
  an int literal (AssertJ's short assertion would compare a `Short` with an `Integer`). `Bytes.ofShort(56)`,
  `ofByte`, `ofInt`, `ofBoolean` and `concat` write command data the same way:
  `card.send(0x80, 0x10, 0x00, 0x00, Bytes.ofShort(56))`. The cookbook uses them; `u8`, `u16` and `s16` stay.
- `PackageCheck` in the converter: the checks of the conversion's scan stage (the language subset, then the linking
  against the API data of the target: the built-in data, or export files the build supplies) on the class files of
  a package, every problem with its class, member and source line, without converting.
  `JavaCardVersion.parse(String)` reads a version as build configurations write it,
  `JavaCardVersion.defaultVersion()` is the default target. `ClassReferences.Reference`
  has the structured place of a reference (`site()`, with the line and whether it is in code),
  `LinkChecker.problems(...)` returns each problem of the link check, `LinkChecker.isJavaSePackage` is public, and
  `PackageCheck.checkSubset` runs the language subset alone.

- **The GlobalPlatform Card API (`org.globalplatform`)**, for applets that use `GPSystem` and the `SecureChannel`
  of their Security Domain. A project needs nothing extra for it:
  - `javacard-express-api` has the stubs of the package (15 types, the API of its version 1.8);
  - the converter links the package from built-in data for its versions 1.0 to 1.8, without an export file, and the
    CAP file imports the lowest version that has everything the package uses (1.0 for `GPSystem` and
    `SecureChannel`, the version of GlobalPlatform 2.1.1 cards). `Converter.Builder.globalPlatformApi("1.5")` and
    the plugin's `<globalPlatformApi>` (`javacard.globalPlatformApi`) fix a version; an export file supplied for
    the package still takes precedence. `ConverterResult.imports()` lists the imported packages with their
    versions, and the plugin logs the imported version of the GlobalPlatform API;
  - the simulated cards (the default backend and `simulated-gp`) implement the package: the secure channel of the
    Security Domain as SCP03 on the card side (C-MAC, C-DECRYPTION, and R-MAC and R-ENCRYPTION where the Security
    Domain's option "i" has them), the life cycle states of the applet and the card, the registry entry with
    privileges, `lockCard`, `terminateCard`, `setATRHistBytes`, `getCardInfo` and Global Services.
    `EmbeddedSession.globalPlatform()` and the test parameter `GlobalPlatformCard` show and set the card's side;
  - a test opens the secure channel through the applet with `GPSession`, with the keys of the run's Security
    Domain: the new test parameter `SecurityDomainKeys` (every backend) and `SCPKeys.of(SecurityDomainKeys)`;
  - `simulated-gp` and `livecard` accept a package that imports `org.globalplatform`, and the off-card verifier
    gets the export file written from the built-in data (`AppletPackage.withGlobalPlatformApi` fixes the version;
    a build's `<globalPlatformApi>` reaches them through the build descriptor).

  See [An applet that uses the GlobalPlatform API](TESTING.md#an-applet-that-uses-the-globalplatform-api). Not
  simulated: a Global PIN, STORE DATA forwarded to `Application` or `Personalization`, SCP02 on the card side, the
  other GlobalPlatform packages. The new live test LC-GP passed on a JCOP 4 card (Java Card 3.0.4, GlobalPlatform
  2.1.1, SCP03 `i=00`): the CAP file that imports version 1.0 loads, and the host opens the secure channel through
  the applet at levels `01` and `03`.

- The time of every exchange: `APDULogEntry.duration()`, measured from just before the command is sent until the
  response has arrived, by the embedded, PC/SC, `simulated-gp` and `livecard` sessions and by `LoggingSession`
  (`null` for an entry whose session did not measure it). Transcripts (of failed tests, `apdu-transcript.txt`,
  `APDUHistory.transcript()`, the transcript files of the live-card harness) and the lines of `-Djcx.log=true` and
  `logged(true)` show it after each response: `R: 00649000  (12.3 ms)`.
- `APDUHistory.last()`, the newest exchange, and `assertThat(APDULogEntry)` (`APDULogEntryAssert`) with
  `tookAtMost(Duration)`, `isSuccess()` and `hasStatusWord(int)`, for a time budget of a command; see
  [Timing a command](TESTING.md#timing-a-command).
- For sessions of your own: `APDUHistory.exchange(command, transmission)` transmits and records an exchange with its
  time, `record(command, response, sentAtMillis, duration)` records a measured one, and `TranscriptFormat` (now
  public) writes the transcript lines.

- The applet parent measures the coverage of the applet code with JaCoCo: on the default backend and on
  `simulated-gp` the applet runs in the test JVM, so after `mvn test` the report in `target/site/jacoco/` covers it;
  `-Djacoco.skip=true` turns it off. See [Coverage of the applet code](TESTING.md#coverage-of-the-applet-code).

### Changed

- New record components: `ConverterResult.imports`, `BuildDescriptor.globalPlatformApi`,
  `AppletPackage.globalPlatformApi`, `ClassReferences.Reference.site`. The constructors with the previous components
  stay, but record patterns on these types need the new component. `ClassReferences.Reference` has six components
  now, and its `equals` and `hashCode` include `site`: two references to the same target from different places
  (another class, member or line) are no longer equal.
- The applet parent manages the plugin as a build extension, so projects that inherit it get the javac check too:
  `javacard-express-javac-plugin` on the annotation processor path of the compilation of the applet code (unless the
  project runs annotation processors from its class path, compiles with another JDK's javac or turns annotation
  processing off). It needs javac of JDK 25 or newer; `-Djavacard.check=false` turns it off. The parent keeps
  writing out its other settings for IDE imports that do not run build extensions.
- `javacard-express-core` passes `junit-jupiter-params` on to its users (compile scope, like `junit-jupiter-api`), so
  `@ParameterizedTest` works with nothing but `javacard-express-core` for the tests.
- The explicit POM of the plugin README adds `javacard-express-livecard`, `-Xlint:-options` and the javac check, and
  no longer declares `junit-jupiter` and `assertj-core`, which come with `javacard-express-core`.
- The `load` line of a live-card or `simulated-gp` transcript lists the packages the CAP file imports with their
  versions, and the explanation of a load refused with `6438` names it.
- The container backend refuses an applet that uses `org.globalplatform` before it is installed, with the backends
  that run it (the simulator in the container has no such package).
- The simulated cards take `org.globalplatform` from `javacard-express-core`. A GlobalPlatform stub jar ahead of
  it on the test class path, or stand-in classes of that package among a project's test classes, are reported when
  an applet uses the package: remove them (or keep them with `-Djcx.embedded.classpathCheck=false`).
- `APDULogEntry` has a fourth component, `duration`. The constructor with three arguments stays (without a time), but
  record patterns on `APDULogEntry` need the new component.
- The transcript files of the live-card harness show the time with one decimal, like the other transcripts.
- The documentation and the code no longer name release versions (the changelog does); `@Deprecated` has no `since`
  attribute.
- NOTICE names the Java Card API classes inside the bundled jCardSim (the simulator server of
  `javacard-express-container` and its Docker image).
- Building the toolkit from source: `maven-plugin` depends on `javacard-express-livecard` for its integration tests,
  so the reactor builds it after `livecard`; its own test `NoCardAccessInBuildToolsTest` keeps `maven-plugin` and
  `javac-plugin` away from PC/SC, as `PcscAccessGuardTest` does for the modules built before `livecard`. The IDE
  plugin is a separate Gradle build in `intellij-plugin/`, outside the Maven reactor.

### Fixed

- `APDULogEntry.timestampMs()` is the time the command was sent; it was taken after the response had arrived.

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
- **`APDUResponse.requireSuccess()`** throws `UnexpectedStatusWordError`, an `AssertionError` that shows the exchange,
  instead of `IllegalStateException`; outside tests check `isSuccess()` or catch `AssertionError`.
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

[0.5.0]: https://github.com/g0ddest/javacard-express/compare/0.4.0...0.5.0
[0.4.0]: https://github.com/g0ddest/javacard-express/compare/0.3.0...0.4.0
[0.3.0]: https://github.com/g0ddest/javacard-express/compare/0.2.1...0.3.0
[0.2.1]: https://github.com/g0ddest/javacard-express/compare/0.2.0...0.2.1
[0.2.0]: https://github.com/g0ddest/javacard-express/compare/v0.1.1...0.2.0
[0.1.1]: https://github.com/g0ddest/javacard-express/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/g0ddest/javacard-express/releases/tag/v0.1.0
