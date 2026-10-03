# Live-card testing

The `livecard` module runs JavaCard Express against a **real card** in a PC/SC reader: the converter builds the
CAP files, the GlobalPlatform module loads and manages them over SCP03, and the applets' answers are compared
with known answers and with jCardSim. Insert a development card, run one command, read the results and the APDU
transcripts.

It serves two purposes:

- **Your applet tests on your card.** A `@JavaCardTest` class (see the [core README](core/README.md)) runs on
  the card when the tests run with `-Djcx.backend=livecard`: the declared applets are converted, loaded,
  installed and deleted through this module, behind the guard described below. See
  [Your tests on a card](#your-tests-on-a-card).
- **The project's own live suite**, which checks the toolkit itself on a card (the [test catalog](#test-catalog)
  and the [results](#verified-results)).

Everything the suite sends passes an **independent APDU guard** that can only let through what a test run
needs: it never sends commands that change keys, lock the card or touch applications that are not the tests'
own, and it never sends an authentication that could fail because of wrong keys, also not through a test applet
that forwards it to its Security Domain.

## Quick start

1. Insert a development card whose Issuer Security Domain uses the GlobalPlatform test keys `40..4F` (or
   configure your keys, see [Configuration](#configuration)).
2. Optionally fetch the two open-source applets the suite also runs (git submodules, see
   [Open-source applets](#open-source-applets-git-submodules); without them those two cases are skipped):

   ```bash
   git submodule update --init livecard/third_party/PivApplet livecard/third_party/SmartPGP
   ```
3. Run, from the project root:

   ```bash
   ./mvnw -Plivecard verify -pl livecard -am
   ```

   Every CAP file is checked by the off-card verifier of the Oracle development kit in `build/oracle-sdks` that
   matches `javaCardVersion` (`jc304_kit` for the default 3.0.4), found automatically when the `verifierSdk`
   setting is not given; without a kit, CAP files are not loaded unless you set `verifierSdk=none` (see
   [Nothing broken is loaded](#further-protections)). Any Maven option can be appended, e.g.
   `-Dtest=CryptoApiLiveTest` for one class.
4. Results are in the Maven output. Every test leaves an APDU transcript in
   `livecard/target/livecard-transcripts/<fully qualified test class>/<test>.txt` (`before-all.txt` and
   `after-all.txt` hold the class set-up and cleanup; a `@Nested` class has its own directory below its enclosing
   class's; every file starts anew in each run). The same files are attached to the tests' results (JUnit's
   `publishFile`; with Maven in `livecard/target/junit-jupiter/<class>/<test>/apdu-transcript.txt`, and IDEs that
   show attachments list them with the test). The loaded CAP files are in
   `livecard/target/livecard-transcripts/cap/`, the export files of packages converted with an Export component in
   `livecard/target/livecard-transcripts/exp/`.

Without `-Plivecard` the live tests do not run: the normal build (`./mvnw verify`) excludes them and keeps the
reader closed. In any project, a `@LiveCardTest` class runs only when the JVM system property
`jcx.livecard.enabled=true` is set for that run (`-Plivecard` sets it, and so does `-Djcx.backend=livecard`);
otherwise it is reported as skipped with the command that runs it.

## Safety design

### The APDU guard

`ApduGuard` sits in front of the wire (`GuardedPcscSession`). It decides about every command **before** it is
sent; a blocked command throws `GuardViolationException` and never reaches the card. The guard keeps its own
picture of the card per logical channel and is deliberately independent of the code under test: it has its own
AES-CMAC and SCP03 key derivation (`GuardCrypto`), not the `gp` module's.

A channel's context comes only from the **standard SELECT by AID**, the form the harness sends: a plain
inter-industry class (CLA `00`-`03` or `40`-`4F`: any logical channel, no secure messaging, no command chaining),
P1 `04`, P2 `00`, a 5-16 byte AID, answered `9000` or `61XX`.

| Where the command goes | Allowed |
|---|---|
| Anywhere | SELECT, MANAGE CHANNEL, GET RESPONSE in a plain inter-industry class, short length (an extended GET RESPONSE only to a test applet); in any other class these instructions reach the selected application and are checked like any other command. Never class `FF` (invalid, ISO/IEC 7816-4:2005 5.1.1; PC/SC readers take it as a command for the reader itself) |
| A test applet (standard SELECT of an AID under the prefix) | everything, because the command reaches the tests' own applet, except INITIALIZE UPDATE and EXTERNAL AUTHENTICATE in a proprietary class (INS `50`, `82`): they follow the rules of the ISD below, because an applet that uses GlobalPlatform's SecureChannel API forwards them to its Security Domain, whose key set counts failed authentications |
| The Issuer Security Domain (standard SELECT of the configured ISD AID) | GET DATA, GET STATUS (read-only) |
| | INITIALIZE UPDATE (SCP03, 8-byte host challenge) |
| | EXTERNAL AUTHENTICATE only as described below |
| | inside a write-access scope, never under C-ENC: INSTALL [for load] / [for install (and make selectable)] / [for make selectable] of AIDs under the prefix, with privileges `00`, no token, Security Domain = the ISD; LOAD after an accepted INSTALL [for load]; DELETE of one AID under the prefix; SET STATUS lock/unlock (`40 80` / `40 00`) of an AID under the prefix |
| | nothing else |
| Any other or unknown application | GET DATA only |

**Never sent**: PUT KEY, STORE DATA, SET STATUS of the card or of the ISD, INSTALL [for extradition] /
[for personalization] / [for registry update], DELETE with tokens or of foreign AIDs, privileges other than `00`,
authentication or card content management to anything but the explicitly selected ISD, extended-length commands
outside the test applets, class `FF`, and any instruction not in the table. SET STATUS `40` changes applications
and Supplementary Security Domains alike (GlobalPlatform Card Specification v2.3.1 11.10); the guard allows it
only under the prefix, where the harness creates no Security Domains and leftover removal refuses privileged
entries.

`LiveCard` opens write access only inside its management operations (`deploy`, `install`, `lock`, `unlock`,
`delete`, `manage`, cleanup). They run at security level `01` (C-MAC) so the guard can read every command; a
session opened with `LiveCard.gp()` (any level) is read-only for the card content. `ApduGuard.writeAccess(boolean)`
is public (reachable through `card.session().guard()`), so test code could open it too; every content rule above
still applies then.

When the guard is unsure it falls back to the rules for an unknown application: a failed, partial or
non-standard SELECT, a SELECT in another class answered with success, a MANAGE CHANNEL in another class answered
`9000` (every channel then), a newly opened logical channel, a card reset and a transport failure. A command it
blocks in that state names the command or event after which the selection became unknown and what to do. A test
applet's own command with INS `70`, or `A4` with P1 `04`, in a proprietary class causes this when the applet answers
it with success: give it another INS (the list of commands to avoid is in the README,
[On the GlobalPlatform backends](README.md#on-the-globalplatform-backends)). A verified handshake ends at the next
command on its channel other than its EXTERNAL AUTHENTICATE (or a plain GET RESPONSE), since the card may abandon
the initiation then.

### Why wrong keys never cost an authentication try

Development cards lock a key set (or the whole card) after a small number of failed EXTERNAL AUTHENTICATE
commands. INITIALIZE UPDATE only asks the card for its challenge and its *card cryptogram*, a MAC over both
challenges made with the card's keys. The guard recomputes the card cryptogram from the configured Key-MAC
(GlobalPlatform Amendment D 6.2.1/6.2.2.2). Only if it matches does the guard allow EXTERNAL AUTHENTICATE, and
only the exact command it computed itself: the same host cryptogram and the same C-MAC (6.2.2.3, 6.2.4), once
per INITIALIZE UPDATE, at a security level the card announced (`00`, `01`, `03`; R-MAC levels only if the card
supports them). With wrong keys the card cryptogram does not match, no EXTERNAL AUTHENTICATE is sent, and the
card has nothing to count. The `gp` module checks the card cryptogram as well; the guard is a second,
independent check. The same rules hold for a proprietary-class INITIALIZE UPDATE or EXTERNAL AUTHENTICATE sent to
a test applet (see the table), so a test of an applet that forwards them to its Security Domain cannot cost a try
either; an applet whose own commands use INS `50` or `82` in a proprietary class cannot be driven through the
guard, and the guard's message says so.

### Run-wide authentication cap

Every authentication problem is recorded in the run's `AuthenticationBudget`: INITIALIZE UPDATE rejected, card
cryptogram not matching, EXTERNAL AUTHENTICATE blocked by the guard or rejected by the card. At
`maxAuthFailures` (default **1**) the whole run is aborted: every further command is blocked, no new connection
is made, and the remaining test classes are skipped with the reason. One wrong-key run therefore sends exactly
one INITIALIZE UPDATE and no EXTERNAL AUTHENTICATE.

### CI/CD never runs live tests

CI/CD must never run tests against a live device. Four independent layers keep it that way:

1. **Default builds exclude the suite.** In `livecard/pom.xml` the default Surefire run has
   `excludedGroups=livecard` and sets `jcx.livecard.enabled=false`. Only the `livecard` profile of that module
   selects the tag and sets `enabled=true`, and the profile has no `<activation>`: it runs only when `-Plivecard`
   is typed. Nothing in the root POM, the other POMs, `.mvn/` or `.github/workflows/` activates it: the CI
   workflow runs `./mvnw clean verify -pl !container` (also with `-Prelease -Dgpg.skip -DskipTests` and with
   `sonar:sonar`) and `./mvnw clean test`, the release workflow `./mvnw clean verify -Prelease -Dgpg.skip` and
   `./mvnw clean deploy -Prelease -DskipTests`. The workflows check out the repository without its submodules;
   the tests that need them are skipped with the command that fetches them.
2. **Live mode is refused on CI.** When live-card mode is enabled and the environment looks like CI/CD, loading
   the settings fails with "Live-card tests are refused: this looks like a CI/CD environment (...)", so every
   live-card class fails before any reader is looked for, and `PcscConnector` refuses to open the reader. A
   variable counts when it is set to anything but empty, `false` or `0`. The variables of CI platforms refuse
   unconditionally: `GITHUB_ACTIONS`, `GITLAB_CI`, `TF_BUILD` (Azure Pipelines), `BUILDKITE`, `JENKINS_URL`,
   `TEAMCITY_VERSION`, `CIRCLECI`, `TRAVIS`, `APPVEYOR`, `DRONE`, `SEMAPHORE`, `CODEBUILD_BUILD_ID` (AWS CodeBuild),
   `BITBUCKET_BUILD_NUMBER`, `bamboo_buildKey` and `GO_PIPELINE_NAME` (GoCD). The generic `CI` alone, which a
   development machine may set for other tools, has one override: the system property
   `-Djcx.livecard.allowCi=true` on the command line; it cannot come from an environment variable, and in a
   settings file it is an error.
3. **Build check.** `NoLiveCardTestsInCiTest` (offline, part of every build) fails if a workflow in
   `.github/workflows/*.yml` or `*.yaml`, a composite action in `.github/actions/`, another common CI file
   (`.gitlab-ci.yml`, `Jenkinsfile`,
   `.circleci/config.yml`, `azure-pipelines.yml`, `.travis.yml`, `bitbucket-pipelines.yml`) or `.mvn/*.config`
   could start the suite: `-Plivecard`, `--activate-profiles ...livecard`, `-Dgroups=...livecard...`,
   the former `livecard-tests.sh`, a `livecard.properties` file, any `allowCi` override, `jcx.backend=livecard`,
   and the system properties `jcx.livecard.enabled` and `jcx.livecard` (the opt-in of an earlier core test) or the
   variables
   `JCX_LIVECARD_ENABLED` and `JCX_LIVECARD` with any value but `false` (a bare `-D` flag means `true`). Every POM
   element is checked the same way (`<argLine>`, `<environmentVariables>`, properties, ...): only the module's
   unactivated `livecard` profile may select the tag or enable live mode, nothing may set the CI override, and
   no Surefire `excludedEnvironmentVariables` may hide a CI variable from the test JVM. A Maven settings file in
   the repository (`*settings*.xml`) may not activate the `livecard` profile. The check reads the literal text
   of these files: values a CI job injects at run time (repository variables, a generated settings file) are
   invisible to it, and only the other layers apply to them.
4. **No reader without opt-in.** Even if a live-card class ran, it would be skipped unless the JVM system property
   `jcx.livecard.enabled` is `true`, and `PcscConnector` checks that property of its own JVM before it touches
   PC/SC (see below).

### Security level 00 is opt-in

The secure channel test authenticates only at the levels listed in `securityLevels`, by default `01` (C-MAC) and
`03` (C-MAC and C-DECRYPTION), both validated on the real card. Level `00` (no secure messaging after the
handshake) runs only when listed, e.g. `-Djcx.livecard.securityLevels=01,03,00`: a card may reject EXTERNAL
AUTHENTICATE with P1 `00`, and some cards count such a rejected EXTERNAL AUTHENTICATE as a failed authentication.
With the default `maxAuthFailures=1` a rejection also aborts the rest of the run, so list `00` last.

### Further protections

- **Opt-in twice.** Live tests run only with `enabled=true`; `PcscConnector`, the only class that touches PC/SC,
  checks it in the given settings *and* in the JVM's own system property, so settings built in code cannot open
  the reader on their own. Live-card mode is switched on for one run only, and only by the JVM system property
  `-Djcx.livecard.enabled=true` (on the command line, which Maven passes to the test JVM, or as a VM option of an
  IDE run; `-Plivecard` sets it, and `-Djcx.backend=livecard` counts as the same switch): the environment variable `JCX_LIVECARD_ENABLED` and
  `enabled` in a settings file are refused unless `false`, since every test run in that environment or project,
  also from an IDE, would then reach the card, and a JUnit configuration parameter `jcx.livecard.enabled` (from
  `junit-platform.properties`, for instance) does not switch the reader on. The normal build sets
  `jcx.livecard.enabled=false` and excludes the `livecard` tag; with live tests disabled not even the reader list
  is read. Code that sets the system property `jcx.livecard.enabled` itself would get past the check at run time;
  the build check below finds it in the project's main and test classes. `PcscAccessGuardTest` reads the compiled classes of every
  module of the build and checks that only `PcscSession` (core) and `PcscConnector` reach `TerminalFactory` or a
  `PcscSession.open` overload that looks up readers itself (the root README's snippet for real cards is compiled,
  never called), that only `LiveCard` and the extension create a `PcscConnector`, that no test class calls
  `LiveCard.connect(LiveCardConfig)`, that no class (main or test) sets `jcx.livecard.enabled` or
  `jcx.livecard.allowCi` as a system property, and that every test-kit launch of live-card classes names its
  (simulated) connector.
- **One card at a time.** With `reader` empty the connector takes the only reader that holds a card; with cards
  in several readers (or several readers matching `reader`) it refuses to guess and asks for a `reader` setting
  that names one reader.
- **Self-check.** Before the first command of a run the guard checks itself against RFC 4493 AES-CMAC vectors and
  two SCP03 handshakes from an independent reference implementation (`GuardSelfCheck`).
- **AID prefix.** Every AID the tests create starts with `aidPrefix` (default `F04A4358`), and everything under
  it is treated as disposable: leftovers there are deleted at the start of a run (see Cleanup), so use a prefix of
  your own. The prefix must be 4-13 bytes and may not overlap the ISD or the GlobalPlatform, Visa/OpenPlatform and
  Java Card API RIDs (`A000000151`, `A000000003`, `A000000062`, never, not even when named). It must be a
  proprietary, unregistered AID, whose first half byte is `F` (ISO/IEC 7816-5); a prefix under a registered RID
  (say `A000000308`, to test an applet under its real AID) needs that RID named in the setting `registeredRid`.
  The guard's own policy (`GuardPolicy`) and the settings record (`LiveCardConfig`, also when built in code)
  enforce these rules. `LiveCard.deploy` rejects foreign AIDs before converting.
- **Nothing broken is loaded.** Every deployment converts the classes again, in memory; a conversion error stops
  it before the first APDU, and a CAP file saved by an earlier run is deleted first and never loaded. JCVM 3.1
  §1.3 requires every CAP file to be verified before it is loaded, so every CAP file must pass the off-card
  verifier of the Oracle development kit in `verifierSdk` (one that matches `javaCardVersion`: the export files
  must match the card's API). Without `verifierSdk` a deployment is refused before anything is converted or sent;
  `verifierSdk=none` loads unverified CAP files at your own risk (the first deployment of a run then logs a
  warning). The project itself never depends on Oracle tools. When `verifierSdk` is not set anywhere, the kit
  `build/oracle-sdks/jc303_kit`, `jc304_kit` or a `jc305u*_kit` (for `javaCardVersion` 3.0.3, 3.0.4 (default) or
  3.0.5) under the project root is taken when it is there.
- **Cleanup.** The guard reports every accepted INSTALL; after each test class everything that class created is
  deleted (load files with their applications) and GET STATUS must confirm it, also when tests failed. Before the
  first card content change of a run, leftovers of earlier runs under the prefix are removed, and the transcript
  lists them first. Only what the harness can have created goes: an application under the prefix with
  privileges (a Security Domain, for instance) stops the run before anything is sent, a LOCKED application is
  unlocked first, and load files are deleted without related objects, so a load file from which another tool
  created an application outside the prefix stays (the card refuses, and the run stops with the AIDs). A run
  that is killed (Ctrl+C) leaves its content on the card until the next run's first content change; a pending
  INITIALIZE UPDATE without EXTERNAL AUTHENTICATE costs nothing.
- **Exclusive access.** One connection per test class with `Card.beginExclusive()`; no other PC/SC client can
  interleave commands, except during a card reset: `PcscSession.reset()` ends exclusive access before the
  resetting disconnect (macOS does not reset the card otherwise) and takes it again after reconnecting, so another
  client could send a command in between. The card is reset when the connection closes.
- **One thread, one class at a time.** javax.smartcardio admits only the thread that took exclusive access. Every
  `@LiveCardTest` class holds the JUnit resource lock `jcx.card` (READ_WRITE), so a parallel JUnit run executes
  the live classes one at a time and all methods of a class in the class's thread. A command from another thread
  (an executor, `assertTimeoutPreemptively`) is refused before the guard sees it, and a test that JUnit runs in
  another thread (`@Timeout` with `threadMode = SEPARATE_THREAD`, also as the default
  `junit.jupiter.execution.timeout.thread.mode.default`) fails with an explanation before it runs; use `@Timeout`
  in its default `SAME_THREAD` mode and `assertTimeout`. Live tests in several JVMs at once (Surefire `forkCount`
  above 1, parallel Maven modules) are not coordinated: run them in one JVM.
- **No secrets in output.** Transcripts, reports, `toString()` and exceptions never contain key values: a
  malformed `keys` setting is described by its shape only, card keys are refused as the system property
  `jcx.livecard.keys` (Maven passes `-D` properties to the test JVM and Surefire writes that JVM's system
  properties into its XML reports; use `JCX_LIVECARD_KEYS` or a settings file; `test` is accepted there), the
  module's Surefire reports of passing tests carry no properties, and the off-card verifier runs without the
  `JCX_LIVECARD_*` variables in its environment.

### Limits

- The guard verifies **SCP03 in S8 mode** only and fails closed otherwise: an SCP02 card's INITIALIZE UPDATE
  response is recognized, nothing more is sent and the run aborts; an S16 INITIALIZE UPDATE (16-byte host
  challenge) is not sent at all.
- Card content management at C-ENC (level `03`) is blocked because the guard cannot read encrypted data.
- Inside the test-applet context the guard trusts that the selected AID is the tests' applet (installed without
  privileges); it checks only GlobalPlatform authentication there (proprietary-class INS `50` and `82`), and it
  falls back to the strict rules after a SELECT or MANAGE CHANNEL in another class answered with success
  (proprietary-class INS `A4` with P1 `04`, INS `70`), which may be the applet's own command.
- With SunPCSC's defaults (`sun.security.smartcardio.t0GetResponse` and `t1GetResponse` true) `javax.smartcardio`
  itself answers `61XX` with GET RESPONSE and repeats a command after `6CXX`, below the guard and the transcript
  (every transcript notes the values). Such exchanges are not checked or recorded; the guard sees the whole
  response. For T=1 cards like the validated one, only long responses take this path.

## Requirements

- A PC/SC reader (macOS and Windows built-in PC/SC, Linux `pcscd`).
- A **development** card: GlobalPlatform 2.x with SCP03 in S8 mode, known ISD keys, card content management
  allowed (card life cycle OP_READY, INITIALIZED or SECURED), a few KB free. Validated on NXP JCOP 4 (Java Card
  3.0.4, SCP03 `i=00`, test keys, KVN `FF`). SCP02 cards and SCP03 S16 mode are not supported: the guard cannot
  verify their handshakes, so it fails closed (see [Limits](#limits)) and nothing that could cost an
  authentication try is sent.
- Java 25 (the Maven wrapper is in the repository).
- Optional: the two open-source applets of LC-OSS, git submodules
  (`git submodule update --init livecard/third_party/PivApplet livecard/third_party/SmartPGP`, see
  [Open-source applets](#open-source-applets-git-submodules)); without them those two cases are skipped.
- An Oracle Java Card development kit for off-card verification that matches `javaCardVersion` (not part of this
  project; e.g. `build/oracle-sdks/jc304_kit`, which is taken automatically when present). Without
  one, CAP files are loaded only with `verifierSdk=none` (unverified, at your own risk); tests that load nothing
  run either way.

## Configuration

The defaults fit a development card with GlobalPlatform test keys; then nothing needs to be configured.
Settings are read from, highest priority first:

1. system properties `jcx.livecard.<setting>` (`-Djcx.livecard.reader=ACR` on the Maven command line);
2. environment variables `JCX_LIVECARD_<SETTING>` (`JCX_LIVECARD_AID_PREFIX`);
3. `livecard.properties` in the working directory (the module, when run through Maven) and in each parent
   directory that belongs to the same build (it holds a `pom.xml`, `build.gradle(.kts)` or `settings.gradle(.kts)`),
   nearest first, so a module of a multi-module build finds the file in the project root; the search ends at the
   first directory outside the build;
4. `~/.jcx/livecard.properties`;
5. the defaults.

Settings files use the plain names (`reader=ACR`). An unknown name in a file is an error, so a typo cannot fall
back to a default. `livecard.properties` is git-ignored; never commit card keys.

| Setting | System property / environment variable | Default | Meaning |
|---|---|---|---|
| `enabled` | `jcx.livecard.enabled` (system property only) | `false` | live tests may run in this run: `-Djcx.livecard.enabled=true` (`-Plivecard` and `-Djcx.backend=livecard` set it); `JCX_LIVECARD_ENABLED` and `enabled` in a settings file are refused unless `false` |
| `reader` | `jcx.livecard.reader` / `JCX_LIVECARD_READER` | first reader with a card | substring of the reader name |
| `keys` | `jcx.livecard.keys` (only `test`) / `JCX_LIVECARD_KEYS` | `test` | `test` = GP test keys `404142...4F`; one hex key for ENC, MAC and DEK; or `enc,mac,dek` (AES-128/192/256); other keys only from the environment or a settings file |
| `kvn` | `jcx.livecard.kvn` / `JCX_LIVECARD_KVN` | `00` | Key Version Number (hex); `00` = the card's choice |
| `securityLevel` | `jcx.livecard.securityLevel` / `JCX_LIVECARD_SECURITY_LEVEL` | `01` | level of `LiveCard.gp()` sessions (hex: `00`, `01`, `03`, `11`, `13`, `33`); management always uses `01` |
| `securityLevels` | `jcx.livecard.securityLevels` / `JCX_LIVECARD_SECURITY_LEVELS` | `01,03` | levels the secure channel test authenticates at, in this order; `00` only when listed (see [Security level 00 is opt-in](#security-level-00-is-opt-in)) |
| `aidPrefix` | `jcx.livecard.aidPrefix` / `JCX_LIVECARD_AID_PREFIX` | `F04A4358`; for `@JavaCardTest` classes the run's prefix (the project's own for applets the Maven plugin built) | every AID the tests create starts with it (4-13 bytes, first half byte `F` unless `registeredRid` is set); everything under it is disposable |
| `registeredRid` | `jcx.livecard.registeredRid` / `JCX_LIVECARD_REGISTERED_RID` | none | a registered RID (5 bytes) that `aidPrefix` starts with, named on purpose; never `A000000151`, `A000000003`, `A000000062` |
| `javaCardVersion` | `jcx.livecard.javaCardVersion` / `JCX_LIVECARD_JAVA_CARD_VERSION` | `3.0.4`; for `@JavaCardTest` classes the build's `<javaCardVersion>` for applets the Maven plugin built | conversion target (`3.0.4` or `V3_0_4`) |
| `isd` | `jcx.livecard.isd` / `JCX_LIVECARD_ISD` | `A000000151000000` | Issuer Security Domain AID |
| `verifierSdk` | `jcx.livecard.verifierSdk` / `JCX_LIVECARD_VERIFIER_SDK` | the kit in `build/oracle-sdks` that matches `javaCardVersion`, if present | Oracle Java Card 3.0.x kit (`lib/`, `api_export_files/`) matching `javaCardVersion`; every CAP must pass its verifier before loading. No kit: deployments are refused; `none`: CAP files are loaded unverified (a warning is logged) |
| `transcriptDir` | `jcx.livecard.transcriptDir` / `JCX_LIVECARD_TRANSCRIPT_DIR` | `target/livecard-transcripts` | APDU transcripts and loaded CAP files (relative to the `livecard` module) |
| `maxAuthFailures` | `jcx.livecard.maxAuthFailures` / `JCX_LIVECARD_MAX_AUTH_FAILURES` | `1` | authentication failures that abort the run (1-3) |

`jcx.livecard.allowCi=true` is not a setting: it is accepted only as a system property on the command line and
only overrides the CI refusal (see [CI/CD never runs live tests](#cicd-never-runs-live-tests)).

Examples:

```bash
# Default development card, verification with build/oracle-sdks/jc304_kit if present
./mvnw -Plivecard verify -pl livecard -am

# Another reader, one test class
./mvnw -Plivecard verify -pl livecard -am -Djcx.livecard.reader="ACS ACR39U" -Dtest=SecureChannelLiveTest

# An explicit verifier kit
./mvnw -Plivecard verify -pl livecard -am -Djcx.livecard.verifierSdk=$PWD/build/oracle-sdks/jc304_kit

# Also try security level 00 (opt-in, last)
./mvnw -Plivecard verify -pl livecard -am -Djcx.livecard.securityLevels=01,03,00
```

```properties
# livecard.properties (project root) for a card with its own keys
keys=0123456789ABCDEF0123456789ABCDEF,FEDCBA9876543210FEDCBA9876543210,00112233445566778899AABBCCDDEEFF
kvn=30
javaCardVersion=3.0.5
verifierSdk=build/oracle-sdks/jc305u3_kit
```

`verifierSdk` relative paths are resolved against the working directory of the tests (the `livecard` module
when run through Maven); prefer absolute paths in settings files.

## Test catalog

Live tests (`livecard/src/test/java/name/velikodniy/jcexpress/livecard/live`, and LC-MODEL in `.../model`, tag `livecard`), in the order they
run (`@Order` on the classes, `junit-platform.properties`): read-only identification and `PcscSession` first,
then card content management, the converter features, the open-source applets and crypto, the secure channel
levels last. AIDs below use the default prefix `F04A4358`.

| ID | Test | What | How | Expected |
|---|---|---|---|---|
| LC-ID-1 | `CardIdentificationLiveTest.answersToResetWithAnIsoAtr` | ATR, protocol | connection data | ATR starts `3B`/`3F`, protocol `T=0`/`T=1`; reported |
| LC-ID-2 | `...issuerSecurityDomainAnswersWithItsFci` | ISD is selectable | SELECT ISD | `9000`, FCI `6F` with `84` = ISD AID |
| LC-ID-3 | `...cardProductionLifeCycleDataIs42Bytes` | CPLC | GET DATA `9F7F` (no SM) | `9000`, 42 bytes, parsed; reported |
| LC-ID-4 | `...cardRecognitionDataAnnounceScp03` | Card Recognition Data | GET DATA `0066` | `9000`, lists SCP03; reported |
| LC-ID-5 | `...keyInformationMatchesTheConfiguredKeys` | key set | GET DATA `00E0` | keys 1, 2, 3 of the version, AES of the configured length |
| LC-ID-6 | `...optionalDataObjectsAreAbsentOrWellFormed` (×3) | IIN, CIN, sequence counter | GET DATA `0042`, `0045`, `00C1` | `6A88`, or `9000` with the tag |
| LC-PCSC-1 | `PcscSessionLiveTest.sessionHoldsExclusiveAccessWithTheDefaultOptions` | `PcscSession` connection | options, protocol, ATR from `PcscSession.getATR()` (no command) | `Options.defaults()` (any protocol, exclusive), `T=0`/`T=1`, an ISO/IEC 7816-3 ATR (`3B`/`3F`...) equal to the one reported at connection |
| LC-PCSC-2 | `...getDataWithNe256SendsLe00` | Le encoding | SELECT ISD, GET DATA `9F7F` with Ne = 256 | sent as `80CA9F7F00`; `9000` with 45 bytes |
| LC-PCSC-3 | `...resetKeepsTheSessionUsable` | `reset()` is a real card reset | CPLC; open a logical channel and leave it open; reset; open a channel; SELECT ISD, CPLC | the same channel number again (the reset closed the channel), same ATR, same CPLC, `9000` |
| LC-PCSC-4 | `...logicalChannelReadsWhatTheBasicChannelReads` | `LogicalChannel.open` / `close` | MANAGE CHANNEL; SELECT ISD and GET DATA CPLC on the channel; GET DATA `0066` on the basic channel | channel 1, managed; same CPLC as the basic channel; basic channel `9000` after closing |
| LC-LIFE-1 | `AppletLifecycleLiveTest.loadFileAndSelectableInstanceAreRegistered` | deploy HelloApplet (`F04A43580101` / `F04A4358010101`) | converter → LOAD → INSTALL; GET STATUS | load file present; instance state `07`, privileges `00`, ELF = package |
| LC-LIFE-2 | `...answersExactlyLikeJCardSim` | 9 commands | card and jCardSim | each equals the validated answer (e.g. `8001000000` → `48656C6C6F2C20636172649000`) on both |
| LC-LIFE-3 | `...persistentCounterSurvivesACardReset` | persistence, deselection by reset | counter; reset; GET DATA CPLC before any SELECT; SELECT, counter | the ISD answers the GET DATA (`9000`, `9F7F...`; HelloApplet would answer `6D00`); counter + 1 |
| LC-LIFE-4 | `...deleteRemovesLoadFileAndInstance` | DELETE related | `LiveCard.delete` | gone from GET STATUS, SELECT `6A82` |
| LC-INST-1 | `InstallAndStatusLiveTest.bothInstancesAreSelectableWithoutPrivileges` | two instances of ParamsApplet | deploy with `C9`=`AABBCC`, INSTALL `F04A4358010202` with `112233445566` | both state `07`, privileges `00` |
| LC-INST-2 | `...loadFileListsItsModule` | GET STATUS `10` | load files with modules | package lists module `F04A4358010201` |
| LC-INST-3 | `...eachInstanceReceivedItsInstallParametersAndKnowsItsAid` | install parameters | INS `01`, `02` | `AABBCC9000` / `1122334455669000`; own AID + `9000` |
| LC-INST-4 | `...lockedInstanceCannotBeSelectedUntilUnlocked` | SET STATUS | lock, SELECT, unlock, SELECT | state `87`, SELECT `6A82`; state `07`, SELECT `9000` |
| LC-INST-5 | `...deletingOneInstanceKeepsTheOtherAndTheLoadFile` | DELETE one instance | `delete(aid, false)` | other instance and load file remain |
| LC-CONV-1 | `ConverterFeaturesLiveTest.virtualDispatch` | virtual dispatch (DispatchApplet, `F04A43580104`) | abstract `Shape` with package-private abstract and overridden methods, `Base`/`Middle`/`Leaf` chain with `super` calls, package-private `hidden()` overridden in the same package, interface `Counter` with two implementations | `002C`, `003F`, `0001000B006F`, `001000200020`, `0003FFFD` then `0006FFFA` |
| LC-CONV-2 | `...exceptions` | exception handling (ExceptionsApplet, `...0105`) | typed `catch` of a `CardRuntimeException` subclass next to `catch (ISOException)`, nested try/finally, catch-count-rethrow, uncaught custom exception | `1055`, `2A88`, `0000`, `0171`; rethrow answers `6985` and its counter persists (`0001`); uncaught `6F00` |
| LC-CONV-3 | `...arrays` | arrays (ArraysApplet, `...0106`) | `length` of byte/short/boolean/Object arrays, `buf[off++] = x`, `+=`/`++`/`x = a[i]++` on elements, a field as index (`a[f++]`, `a[++f]`, `a[f - 2] += f`, `f--`: `dup_x` forms), `Util` copy/fill/compare, checkcast of `Object[]` elements | exact bytes, e.g. `000306090C0F07`, `0A0B000C000200030003` |
| LC-CONV-4 | `...controlFlow` | branches and switches (FlowApplet, `...0107`) | if/else and a loop over more than 127 bytes of code (wide branches), dense `switch` (tableswitch) and sparse `switch` (lookupswitch) with hits and misses | the host's short arithmetic (`7C09`, `B58D`, `FE49`); `11 * (n + 1)` / `FFFF`; `0001`..`0006` / `0000` |
| LC-CONV-5 | `...statics` | static fields (StaticsApplet, `...0108`) | byte/short/boolean/array fields with initializers, static final byte/short/boolean arrays, an inlined constant, a counter; then changed | `12 1234 01 010203 A0A1 0102F00F7FFF 010001 0BEE 0000`, after the change `13 1244 00 010209 ... 0001`, persisting |
| LC-CONV-6 | `...committedTransactionAndTransientMemory` | transactions, transient memory (TransactionsApplet, `...0109`) | commit; write CLEAR_ON_DESELECT and CLEAR_ON_RESET arrays; select the ISD and the applet again; card reset | `00080301`; `5A5A`, after reselection `005A`, after the reset `0000`; persistent state kept |
| LC-CONV-7 | `...abortedTransactionRestoresState` | `JCSystem.abortTransaction` | value 5, journal 0; begin; 7, 9; abort | card `00050000` (rolled back); jCardSim `00070900` (its known deviation, asserted as such); aborted on the simulated card, which runs on jCardSim |
| LC-CONV-8 | `...apduInputAndOutput` | APDU I/O (ApduIoApplet, `...010A`) | 256 bytes with Le `00` (`sendBytesLong`), 40 bytes in five `sendBytes` chunks, Lc 255 through `setIncomingAndReceive`/`receiveBytes` | the byte patterns; count `00FF` and the sum of the received bytes |
| LC-CONV-9 | `...shareableInterfaceAcrossPackages` | cross-package shareable interface (`...010B` server with Export component, `...010C` client) | server deployed first; client converted with the server's export file on its export path and installed with `C9` = server AID; client calls `JCSystem.getAppletShareableInterfaceObject`; client deleted before the server | `0005`, `0008`, refused parameter `01`, server total `0008`; both packages gone |
| LC-CONV-10 | `...intSupport` | the optional int type (IntApplet, `...010D`, converted with int support) | int arithmetic, shifts, int array, persistent int field, conversions | Java's int results; a card that rejects the verified package with `6A80`/`6985` is reported as aborted, "card has no int support" (with `verifierSdk=none` the rejection fails: a broken CAP file cannot be ruled out) |
| LC-OSS-1 | `OpenSourceAppletsLiveTest.pivApplet` | PivApplet (package `F04A43580122`, applet `F04A4358012201`), built from its submodule | SELECT; VERIFY PIN `123456`; GENERAL AUTHENTICATE 9B: decrypt the card's witness with the default 3DES card management key `0102..08` ×3, send a challenge; GENERATE EC P-256 in 9A; GENERAL AUTHENTICATE 9A over a SHA-256 digest | application property template `61..`; `9000`; the card's encryption of the challenge checked on the host; public point `86 41 04..`; the ECDSA signature verifies on the host (`NONEwithECDSA` over the digest) |
| LC-OSS-2 | `...smartPgp` | SmartPGP (package `F04A43580123`, applet `F04A4358012301`), built from its submodule | SELECT; GET DATA `6E`; VERIFY PW3 `12345678`; GENERATE the RSA-2048 signature key (about 5 s on JCOP 4; the 270-byte answer is longer than a short Le allows and comes through `61XX` and GET RESPONSE, which the JDK's `javax.smartcardio` performs below the transcript; the test sends the command through `APDUSequence`, which does the same where the transport does not); VERIFY PW1 `123456`; PSO: COMPUTE DIGITAL SIGNATURE over a SHA-256 DigestInfo; GET DATA `C4` | algorithm attributes `C1 06 010800001103`; `9000`; modulus and exponent; a 256-byte signature that verifies on the host (`SHA256withRSA`); `9000` |
| LC-CRYPTO-1 | `CryptoApiLiveTest.knownAnswers` (×7) | AES-128 ECB/CBC (FIPS-197 C.1), SHA-256/SHA-1 of `abc` (FIPS 180-4), 2-key 3DES, HMAC-SHA-256 (RFC 4231 #2), AES-CBC ISO 9797-1 M2 | CryptoApplet: INS `1F` creates the algorithm's objects, then INS `10`-`19` computes | the published value on the card and on jCardSim (recomputed with the JDK by `CryptoKnownAnswersTest`); `6F03` from INS `1F` (CryptoException.NO_SUCH_ALGORITHM) = "not supported by this card" (aborted, not failed); any other error fails |
| LC-CRYPTO-2 | `...secureRandomGivesFreshBytes` | RandomData | two calls | 16 bytes, different, not zero |
| LC-CRYPTO-3 | `...ecdsaP256SignatureVerifiesOnTheHost` | ECDSA P-256 / SHA-256 | key pair and signature on the card | JCA verifies the signature with the card's public point |
| LC-CRYPTO-4 | `...rsa2048SignatureVerifiesOnTheHost` | RSA-2048 PKCS#1 / SHA-256 | key pair and signature on the card (about 20 s on JCOP 4; time limit 3 min) | 2048-bit modulus; JCA verifies the signature |
| LC-SC-1 | `SecureChannelLiveTest.authenticatesAndReadsUnderSecureMessaging` (one per level) | SCP03 at each level of `securityLevels`: `01`, `03` by default, `00` only when listed | `LiveCard.gp(level)`; GET DATA CPLC; GET STATUS `80`, `20` | level as requested, handshake verified by the guard, CPLC equal to the plain read, one ISD entry |
| LC-SC-2 | `...everySessionHasFreshChallengesAndCryptograms` | random card challenge | two sessions | different card challenges and cryptograms |
| LC-SC-3 | `...keyInformationListsTheAuthenticatedKeyVersion` | GET DATA under C-MAC | `00E0` | contains the session's KVN |
| LC-MODEL-1 | `DeclarativeModelLiveTest.freshInstancePerTest` | `@JavaCardTest`, `@InstallApplet` (default `PER_TEST`) | the scenario class `BackendScenarios.PerTest` (ModelApplet: counter, count of instances created since the package was loaded) runs with `jcx.backend=livecard` | each test sees a fresh instance (`0001 0002`, then `0001`); the static count shows one package load for the class (`0001`, `0002`) |
| LC-MODEL-2 | `...oneInstancePerClass` | `isolation = PER_CLASS` | two ordered tests and `@AfterAll` | the instance carries over (`0001`, `0002`, `0002`) |
| LC-MODEL-3 | `...instancesParametersMethodLevelAppletNestedClassAndDeselect` | several instances, install parameters, a method-level applet of the same package, a `@Nested` class, `deselect()` | instances `aid = "0101"` (`AABBCC`) and `"0102"` (`112233`, `PER_CLASS`), `@InstallApplet(OtherApplet.class)` on a method, a nested class with its own instance | each instance answers its parameters and its own AID; the method-level applet is selected first; CLEAR_ON_DESELECT is `00` after `deselect()`; the nested class sees the outer `PER_CLASS` instance |
| LC-MODEL-4 | `...parameterizedInvocations` | `@ParameterizedTest` | invocations with 1 and 3 increments | every invocation has a fresh instance (`0001`, `0003`) |
| LC-MODEL-5 | `...theIssuerSecurityDomainListsTheInstance` | a `LiveCard` parameter in a `@JavaCardTest` class | GET STATUS through the harness | the ISD lists the declared instance |
| LC-MODEL-6 | `...aPackageTheMavenPluginBuiltRunsUnderTheProjectsPrefix` | the build descriptor of the Maven plugin (`META-INF/javacard/<package>.properties`), the project's AID prefix | `BackendScenarios.Built`: BuiltApplet, whose package has a descriptor (package version 1.2, Java Card 3.0.4, project `com.example:built-applets`) | the AIDs start with the project's prefix (`F0` + 4 bytes of SHA-256 of `groupId:artifactId`); the package is converted with the descriptor's settings; the transcript lists the build's AIDs next to the run's |

On the validated JCOP 4 card HMAC is not available: `KeyBuilder.buildKey(TYPE_HMAC, ...)` and
`Signature.getInstance(ALG_HMAC_*)` throw CryptoException NO_SUCH_ALGORITHM, which the CryptoApplet answers as
`6F03` (earlier versions of the applet let it escape as `6F00`). Its known-answer case is therefore reported as
aborted with "not supported by this card"; any other CryptoException reason fails the test with the reason named.
Every other case passes there.

Offline checks in the normal build (no reader involved):

| Test | Proves |
|---|---|
| `GuardCryptoTest` | AES-CMAC (RFC 4493, NIST SP 800-38B AES-192/256) and SCP03 KDF against known answers and the reference handshake |
| `ApduGuardHandshakeTest` | EXTERNAL AUTHENTICATE only after the guard's own verification; wrong keys abort the run; the guard accepts the `gp` module's handshakes at `00`/`01`/`03` |
| `ApduGuardPolicyTest` | the allow-list, write access, prefix rules, test-applet context, logical channels, fallbacks; SELECT, MANAGE CHANNEL and GET RESPONSE pass only in a plain inter-industry class (CLA `80` with them is blocked at the ISD; rejected by a test applet, they change nothing) |
| `ApduGuardContextTest` | only the standard SELECT enters a context (chaining, secure messaging, RFU class, P2 other than `00`, extended length do not); a SELECT or MANAGE CHANNEL in another class answered with success makes the channels unknown; authentication and card content management only at the explicitly selected ISD; class `FF` never sent; GlobalPlatform authentication checked and counted inside a test applet, ISO authentication of the applet itself passes; a handshake ends at the next unrelated command |
| `GuardPolicyTest` | the guard's own prefix rule: reserved RIDs (also when named), the ISD, a proprietary prefix or the named registered RID |
| `RealCardTranscriptReplayTest` | every command of the validated real-card runs passes the guard, every real handshake verifies |
| `LiveCardConfigTest` | defaults, precedence of the sources, settings files of the module and of its parent directories in the build (nearest first, none above the build), validation (also of the record constructor), keys never printed (malformed keys by shape only) and never a system property |
| `LiveCardDeploymentTest` | deploy, tracking, cleanup, leftovers (listed, privileged ones refused, LOCKED ones unlocked, a foreign instance of a load file under the prefix kept), wrong keys, blocked dangerous commands, no deployment without a verifier decision, fresh conversion on every deployment and no stale CAP file after a failed one, an importing package deleted before the one it imports (cleanup and leftovers), a deleted instance installed again, a failure inside the simulated card reported as such (simulated card) |
| `SimulatedIsdTest` | the simulated ISD checks the C-MAC chain of every command in the session, decrypts C-DECRYPTION data and ends the session on a failure (Amendment D 6.2.3-6.2.6), so the offline suite catches secure messaging regressions of the `gp` module |
| `JCardSimCardTest` | the simulated card's jCardSim runtime installs a deleted AID again, as a new instance of the same or of another applet class (jCardSim alone refuses that AID) |
| `GuardedPcscSessionTest` | the bytes the guard checks are the bytes transmitted |
| `PcscConnectorTest` | the only reader with a card is chosen; cards in several readers need the `reader` setting |
| `CapVerifierTest` | the off-card verifier runs without the `JCX_LIVECARD_*` variables |
| `CryptoAppletAllocationTest` | only CryptoException.NO_SUCH_ALGORITHM (`6F03`) aborts a crypto case as not supported; other answers fail |
| `LiveCardExtensionTest` | cleanup after failing tests and failing `@BeforeAll`, nested classes, the card injected into the constructor of a `@TestInstance(PER_CLASS)` class, cleanup when such a constructor fails, abort skips the rest |
| `LiveCardTranscriptsTest` | transcripts in a directory per fully qualified class name, with a directory per `@Nested` class below it; every file starts anew in each run and continues within it; each test's transcript and the class's set-up and cleanup transcripts are published with their results |
| `LiveCardThreadingTest` | `@LiveCardTest` holds the resource lock `jcx.card`: in a parallel run two live classes run one at a time, each with all its methods in its own thread; `@Timeout(threadMode = SEPARATE_THREAD)` (also as the default) and `assertTimeoutPreemptively` are rejected with an explanation, and nothing reaches the card from the other thread |
| `LiveSuiteOnSimulatedCardTest` | the whole live suite passes against a simulated GlobalPlatform card (the validated card's ISD behaviour with SCP03 secure messaging checked, applets in jCardSim: one jCardSim card per load file, shared with the load files it imports, fresh static fields per load) reached through the project's `PcscSession` on a simulated reader, and leaves the card clean; only LC-CONV-7 is aborted there (jCardSim does not roll back), and LC-OSS-1/2 when their submodules are not initialized (checked separately: they abort with the init command, nothing fails); level `00` runs only when listed in `securityLevels`; an unverified int package that the card rejects fails LC-CONV-10 |
| `LiveTestsSkippedWithoutProfileTest` | without the profile every live test is skipped and no reader is looked for |
| `PcscAccessGuardTest` | in the compiled main and test classes of every module of the root POM, only `PcscSession` (core) and `PcscConnector` reach `TerminalFactory` or a `PcscSession.open` overload without a given reader; gp's README snippet that opens the first reader is compiled but nothing calls or references it; only `LiveCard` and the extension create a `PcscConnector`; no test class calls `LiveCard.connect(LiveCardConfig)`; no class (main or test) sets `jcx.livecard.enabled` / `jcx.livecard.allowCi` as a system property; every test-kit launch of live classes names its connector. Modules not built in the run are named in the output; core, gp and livecard must be there |
| `ContinuousIntegrationTest` | live mode is refused on CI (any `CI` value but empty/`false`/`0`, 15 platforms' variables); `-Djcx.livecard.allowCi=true` overrides only the generic `CI`; the override is not taken from the environment or files; the environment and settings files cannot switch live mode on |
| `LiveModeTest` | the reader is switched on only by the JVM system property `jcx.livecard.enabled=true`, not by a JUnit configuration parameter or the environment; the skip reason gives the command for any project (no repository paths) and names what did not switch it on |
| `NoLiveCardTestsInCiTest` | no CI workflow, composite action, CI file, `.mvn/*.config`, Maven settings file or POM element can start the live-card suite (profile, tag, script, settings file, CI override, `enabled` with any value but `false`) or hide CI variables from the test JVM; the module's default Surefire run excludes it and disables live mode |
| `TestAppletsVerifierTest` | every test applet's CAP file, prepared as `LiveCard.deploy` prepares it (int support, Export component, imported packages' export files), and those of PivApplet and SmartPGP (with their submodules) pass Oracle's 3.0.4 off-card verifier (skipped without `build/oracle-sdks/jc304_kit`); export files land in the export path layout |
| `AppletPackageTest` | int support and the Export component reach the converter (Header flags `ACC_INT`, `ACC_EXPORT`) |
| `JppTest` | the preprocessor for PivApplet's sources: `//#if`, `/*#if`, `!NAME`, `#else`/`#endif` forms, nesting, unbalanced directives, line numbers kept |
| `ThirdPartyAppletTest` | submodules registered with the upstream URLs; a missing submodule aborts with its `git submodule update --init ...` command, another commit or local changes abort too; with the submodules, Java 8 class files, PivApplet's line numbers kept, the pinned commits checked out |
| `CryptoKnownAnswersTest` | the crypto known answers are what the JDK computes |

## Open-source applets (git submodules)

LC-OSS runs two real-world applets on the card as test subjects, built from their upstream sources by this project's
toolchain:

| Applet | Upstream | Pinned commit | License | Submodule |
|---|---|---|---|---|
| PivApplet (NIST SP 800-73-4 PIV) | https://github.com/arekinath/PivApplet | `5cb14a9e8d16e92fbad73dcad86a219a9210554f` | MPL-2.0 | `livecard/third_party/PivApplet` |
| SmartPGP (OpenPGP card 3.4) | https://github.com/ANSSI-FR/SmartPGP | `da52ec4d6baa2b8f5bf8de35e7932572bb96b161` | GPL-2.0-or-later | `livecard/third_party/SmartPGP` |

Their code is not part of this repository and is never copied into it: the repository only records the
submodules (URL and commit). They remain under their own licenses and are used only as test subjects. Initialize
them in the project root (not recursively: their own submodules, an Oracle SDK mirror and a build plugin, are not
needed):

```bash
git submodule update --init livecard/third_party/PivApplet livecard/third_party/SmartPGP
```

The build does not need them and never touches the network: without a submodule, its case is aborted with the
command above (also in the offline run on the simulated card), so a checkout without submodules, as on CI, stays
green. With a submodule, the test

1. copies its sources to `livecard/target/third-party/<name>/src`; PivApplet's sources go through the conditional
   compilation of its build (`ThirdPartyApplet`/`Jpp`: `//#if [!]NAME` or `/*#if [!]NAME`, `#else`, `#endif`, with
   the defines `PIV_SUPPORT_RSA`, `PIV_SUPPORT_EC`, `PIV_SUPPORT_ECCP384`, `PIV_SUPPORT_AES`, `PIV_SUPPORT_3DES`,
   `YKPIV_ATTESTATION` and `APPLET_EXTLEN`; directive and dropped lines become empty lines, so line numbers stay
   the upstream ones);
2. compiles them with `javac --release 8` (`javax.tools`) against this project's API stubs into
   `livecard/target/third-party/<name>/classes`;
3. converts them with this project's converter for the configured Java Card version (3.0.4 by default, default
   mode), checks the CAP file with the off-card verifier (`verifierSdk`), deploys the package under the AID
   prefix, runs the flow and deletes the package right after the case, so that the next applet finds the card's
   memory free (the class cleanup removes anything left after a failure).

A case runs only when its submodule is at the pinned commit without local changes (`git status`, untracked
files included): only the pinned upstream code was validated, and it goes onto a card. Otherwise the case is
aborted with the command that restores the pinned commit.

[Yubico ykneo-oath](https://github.com/Yubico/ykneo-oath) is not included: on the validated JCOP 4 card with a
macOS reader its INSTALL never gets an answer and leaves the reader driver stuck after a successful LOAD, the same
with this project's toolchain and with Oracle's converter and GlobalPlatformPro.

## Adding a test

1. Create a class in `livecard/src/test/java/name/velikodniy/jcexpress/livecard/live`, name it `*LiveTest`,
   annotate it with `@LiveCardTest` and take a `LiveCard` parameter (test methods, `@BeforeAll`, constructor).
2. Talk to applets through `card.session()` (guarded, transcribed); open GlobalPlatform sessions with
   `card.gp()` / `card.gp(level)` for read-only commands.
3. Change card content only through `card.deploy(...)`, `card.install(...)`, `card.lock/unlock/delete(...)` or
   `card.manage(gp -> ...)`; everything created is tracked and deleted after the class.
4. Use AIDs under the prefix: `card.aid("01FF")`. New applet: put the source in `livecard/src/applets/java`
   (Java 8 language level, API stubs only; keep `process()` short and dispatch to small methods; avoid INS `6X`
   and `9X`, which T=0 cannot carry, and GlobalPlatform's `50`/`82`, so that transcripts stay unambiguous), add
   a `TestApplet` constant with its package and module suffixes (and its conversion: int support, Export
   component; `imports()` for packages it links to), deploy it with `card.deploy(TestApplet.X.pkg(card.config()))`.
   A package that imports another is deployed after it with `.withExportPath(deployment.exportPath())`; cleanup
   deletes in the reverse order.
5. Prefer exact expected values; `ApduCase` lists (command, expected response) run on the card with
   `ApduCase.run` and on jCardSim with `ApduCase.onSimulator`; a `Scenario` adds selections, deselection and card
   resets and runs unchanged on the card and on a `JCardSimCard`.
6. Check the new test offline first: add the class to `LiveSuiteOnSimulatedCardTest.SUITE` (update the expected
   test count); `TestAppletsVerifierTest` verifies every `TestApplet` with Oracle's verifier.

The guard already tracks logical channels (one context per channel) and secure messaging class bytes, so
logical-channel, `PcscSession` and secure-messaging tests can use `card.session()` directly.

## Your tests on a card

A `@JavaCardTest` class that runs on jCardSim runs on a card unchanged (see the
[core README](core/README.md#declarative-card-tests)). A project that inherits `javacard-express-applet-parent`
(the README Quick Start) has everything it needs; any other project adds this module for the tests (it brings core
and gp):

```xml
<dependency>
    <groupId>name.velikodniy</groupId>
    <artifactId>javacard-express-livecard</artifactId>
    <version>0.4.0</version>
    <scope>test</scope>
</dependency>
```

Tests compile for Java 25 (`maven.compiler.testRelease` 25, as in the Quick Start). Nothing else goes into the
POM: no profile, no tag exclusion, no `enabled=false`. The backend is chosen when the tests run:

```bash
mvn verify -Djcx.backend=simulated-gp               # a rehearsal without a reader, fine on CI
mvn verify -Djcx.backend=livecard -Djcx.livecard.verifierSdk=<kit> -Dtest=WalletAppletTest   # the card
```

On the card:

- **Load.** The first instance of a package converts the package with every applet of it (those of the build
  descriptor, or every concrete `Applet` subclass with an `install` method) and only the classes those applets
  need, read from every class path entry that holds the package (`target/classes`, test classes, jars), with the
  settings the Maven plugin recorded for it (package version, `supportInt32`, `javaCardVersion`; see Conversion);
  the off-card verifier checks the CAP file; then INSTALL [for load], LOAD and INSTALL [for install and make
  selectable]. Further instances, of any applet of the package, are installed from the loaded package. A package
  that imports another package outside the Java Card API (a library module of the project) is refused before
  conversion: these backends do not load the packages it imports yet. The transcript names the AIDs of the build
  next to the AIDs of the run, and labels the card management (`# install`, `# load` with the number of LOAD
  blocks, `# delete`, `# secure channel`; the class transcript file keeps every command).
- **Isolation.** A `PER_TEST` instance is deleted after its test (DELETE of the instance; the package stays loaded
  for the class, so static fields keep their values, as on the jCardSim backend); a `PER_CLASS` instance after the
  class.
- **Cleanup.** After the test class the package is deleted and GET STATUS must confirm that nothing of the run is
  left, also when tests failed. Before the first change of a run, leftovers of an aborted run under the prefix
  are removed.
- **AIDs.** Every AID starts with the run's prefix, followed by suffixes derived from the package and class
  names: the same AIDs as on jCardSim, so tests contain no literal AIDs. The prefix is the `aidPrefix` setting when
  you set it (everything under it is treated as disposable); otherwise the project's own prefix, `F0` + 4 bytes of
  SHA-256 of `groupId:artifactId`, for applets of a package the Maven plugin built, so the tests of two projects on
  one card never delete each other's applets; otherwise `F04A4358`.
- **Conversion.** The Java Card version is the `javaCardVersion` setting when you set it, otherwise the build's
  (`<javaCardVersion>` of the plugin; 3.0.4 for applets in test sources). Build for the card's version or an older
  one: a CAP file for 3.0.5 does not load on a 3.0.4 card, which answers LOAD with `6438`, and the failure says
  so. `-Djcx.supportInt32=true` or `false` overrides the build's int support.
- **Safety.** Every command passes the guard; keys, reader and verifier are the settings of
  [Configuration](#configuration) (custom keys in `livecard.properties` in the project root, never committed, or in
  `~/.jcx/livecard.properties`); test classes run one at a time, each with one connection, and the methods of a
  class run in its thread. `livecard` counts only as a JVM system property, and live mode is refused when the
  environment looks like CI.

Tests that need the card's content or its secure channel take a `LiveCard` parameter and run only there:
`@EnabledOnBackend({Mode.SIMULATED_GP, Mode.LIVECARD})`.

### Lower level: `@LiveCardTest`

The project's own live suite manages card content itself through `LiveCard`: a class annotated `@LiveCardTest`
takes a `LiveCard` parameter and deploys explicitly, e.g.
`card.deploy(AppletPackage.of(Path.of("target/classes"), "com.example.hello", card.aid("0101"))
.withApplet("HelloApplet", card.aid("010101")))` (see the javadoc of `LiveCard`, `AppletPackage` and
`LiveCardTest`). Such a class runs only in a run with `-Djcx.livecard.enabled=true` (or
`-Djcx.backend=livecard`) and is reported as skipped otherwise.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| all live tests skipped: "live-card tests are disabled" | run with the system property `-Djcx.livecard.enabled=true` (in this repository: `-Plivecard`; for `@JavaCardTest` classes `-Djcx.backend=livecard`) |
| "Live-card tests are refused: this looks like a CI/CD environment" | a CI variable (`CI`, `GITHUB_ACTIONS`, ...) is set; live tests do not run on CI/CD. On a development machine that sets only the generic `CI` anyway: `-Djcx.livecard.allowCi=true` (no override for a CI platform's own variables) |
| skipped: "no card present in any PC/SC reader" | reader not connected or card not inserted; with several readers set `reader` |
| skipped: "Cards in several readers ..." | set `reader` to a part of the name that only the reader with the development card has |
| "No off-card verifier: JCVM 3.1 §1.3 requires ..." | set `verifierSdk` to a kit that matches `javaCardVersion` (one in `build/oracle-sdks` under the project root is found automatically), or `verifierSdk=none` |
| "enabled=true in ... is refused" / "JCX_LIVECARD_ENABLED=true is refused" / "Card keys are not accepted as the system property" | live mode only per run, with the system property `-Djcx.livecard.enabled=true` (`-Plivecard`, or `-Djcx.backend=livecard`); custom keys only in `JCX_LIVECARD_KEYS` or a settings file |
| "AID prefix ... is not a proprietary AID" | use a prefix starting with `F`, or name its registered RID in `registeredRid` |
| "Not deleted as leftovers under ..." | applications with privileges under the prefix were not created by the harness; remove them by hand or use another prefix |
| skipped: "PC/SC is not available" | start the PC/SC service (`pcscd` on Linux) |
| "Cannot connect ... exclusive access" | another program holds the card (GlobalPlatformPro, a browser or OS smart card service); close it |
| "the card cryptogram does not match the configured keys" and the rest skipped as "run aborted" | wrong `keys` or `kvn`; no EXTERNAL AUTHENTICATE was sent, the card counted nothing; fix the settings and run again |
| "INITIALIZE UPDATE answered SW=6A88" | the key version `kvn` does not exist on the card; use `00` |
| "the guard verifies SCP03 handshakes only" | SCP02 card; not supported by the guard. In a test applet: the applet's own command uses INS `50` in a proprietary class, which the guard reads as INITIALIZE UPDATE (the message says so); give it another INS |
| "... became unknown after 80 70 ..." or "after 80 A4 04 ..." | the test applet's own command with INS `70`, or `A4` with P1 `04`, in a proprietary class answered with success, and the guard has to read it as MANAGE CHANNEL or SELECT; give it another INS |
| "Package ... uses classes of other packages outside the Java Card API" | the GlobalPlatform backends do not load packages that depend on other packages yet; run such tests on the embedded backend (`@EnabledOnBackend(Mode.EMBEDDED)`) |
| `GuardViolationException ... (not sent to the card)` | the test asked for something outside the allow-list; the transcript shows the blocked command and the reason |
| "Conversion of ... failed; nothing was loaded" / "fails the off-card verifier" | converter problem; nothing reached the card; the verifier output is in the message |
| "The card refused to load ..., converted for Java Card 3.0.5 ... (SW 6438)" | the card's Java Card version is older than the build's target: set `<javaCardVersion>` of the plugin to the card's version (or an older one), or the live-card setting `javaCardVersion` |
| "Cleanup incomplete" / "Leftovers ... could not be deleted" | the listed AIDs are still on the card; the next run retries before its first deployment |
| CryptoApiLiveTest: "SW 6F0x: CryptoException reason x" or "SW 6F00: an exception that is not a CryptoException" | creating the algorithm's objects failed for another reason than a missing algorithm (`6F03`, reported as not supported): memory, a wrong API constant or a converter defect |

## Known issues

The converter defects that kept constructs out of the test applets before (no wide branches, typed `catch` with
a wrong catch type, `array.length` not translated) are fixed on the integrated converter; LC-CONV-2 to LC-CONV-4
cover them on the card.

Converter defect found while writing the converter-feature applets, now fixed: a blank `static final` primitive
assigned in a static initializer (`static final short X; static { X = 1; }`) used to convert without an error
into a CAP file whose Descriptor lists the field, which Oracle's off-card verifier rejects (Oracle's converter
rejects such source as well). It is no compile-time constant (JCVM 3.1 §2.2.4.6), so the subset check now rejects
it at the assignment, naming the class, the field and the line. The StaticsApplet uses inline initializers.

`PcscSession.reset()` used to call `Card.disconnect(true)` while the session held exclusive access. macOS PC/SC
does not reset the card then (found by LC-CONV-6 on the validated card: the CLEAR_ON_RESET byte survived, the
applet stayed selected). The session now ends exclusive access before the resetting disconnect; LC-PCSC-3,
LC-LIFE-3 and LC-CONV-6 detect a reset that does not happen, and the simulated reader models the macOS
behaviour, so the offline run would have caught it.

jCardSim deviations that the converter-feature tests account for (the card is held to the Java Card
specification):

- `JCSystem.abortTransaction()` does not roll back: LC-CONV-7 asserts jCardSim's known answer and the specified
  one on the card, and is aborted on the simulated card.
- A case 4 command with Lc 255 and Le (261 bytes) makes jCardSim answer `6F00`; LC-CONV-8 sends its 255 data
  bytes as a case 3 command and reads the result with a second command.
- jCardSim's own runtime cannot install an AID again after deleting it from the same simulator. `JCardSimCard`
  uses a runtime that forgets the deleted applet's AID, so DELETE and INSTALL of the same AID give a new instance
  (of the same or another applet class), as on a card. The simulated card gives every load file a new jCardSim card
  (a load file that imports another joins that one's card). A failure inside the simulated card (an applet class
  that cannot be loaded, a jCardSim error) is reported as a failed transmission with its cause, not as a lost
  connection.
- The simulated card runs the applet classes, not the CAP files: converter defects show on the real card and in
  `TestAppletsVerifierTest`, not in `LiveSuiteOnSimulatedCardTest`.
- The simulated card runs test applets on the basic channel only: a SELECT of a test applet on channels 1 to 3 is
  answered `6881`, with a note in the transcript; the ISD can be selected on any channel.
- jCardSim registers an applet that calls `register()` without arguments under the instance AID it was installed
  with; a card registers it under the applet's own AID from the CAP file (Java Card API, `Applet.register()`), so
  several instances need `register(bArray, (short) (bOffset + 1), bArray[bOffset])`. The GlobalPlatform backends
  note an applet that calls `register()` and is installed under another AID.

## Verified results

The project's lead engineer ran the suite, and the standalone checks that came before it, on one real card. This
section records what was run and what came out. It covers one card model; see [Limits](#limits) for what the
guard supports and the end of this section for what has not been run on hardware.

### Environment

| | |
|---|---|
| Card | NXP JCOP 4 development card. ATR `3BDC18FF8191FE1FC38073C821136605036351000250`, protocol T=1. CPLC: IC fabricator `4790` (NXP), IC type `0503`, operating system `8211`, OS release date `6351`, OS release level `0302` (the CPLC fields that identify the individual card are not recorded) |
| Platform | Java Card 3.0.4 (imports of `javacard.framework` 1.5 accepted; a CAP file converted for 3.0.5, which imports `javacard.framework` 1.6, is refused at the first LOAD block with `6438`), int type supported, HMAC not available; GlobalPlatform 2.1.1 card management (Card Recognition Data, OID `1.2.840.114283.2.2.1.1`); card life cycle INITIALIZED; ISD `A000000151000000` |
| Secure channel | SCP03 `i=00` (S8 mode), one AES-128 key set, KVN `FF`, the GlobalPlatform test keys `40..4F` |
| Content before and after every run | the ISD and the load file `A0000001515350`, nothing else |
| Reader | Generic EMV Smartcard Reader (USB) |
| Host | macOS with its built-in PC/SC, Eclipse Temurin JDK 25, the project's Maven wrapper |
| Off-card verifier | Oracle Java Card 3.0.4 development kit, installed locally in `build/oracle-sdks/jc304_kit` (not part of the project): every CAP file was checked with it before loading |

### Latest full run

| | |
|---|---|
| Date | 2026-10-02, 09:40 to 09:48 EEST |
| Code | the release branch of 2026-10-02, with the declarative test model (`@JavaCardTest`, `@InstallApplet`, backends), the build descriptor of the Maven plugin and the projects' own AID prefixes: the state of the commit that added this section |
| Command | `git submodule update --init livecard/third_party/PivApplet livecard/third_party/SmartPGP`, then `./mvnw -Plivecard verify -pl livecard -am` (the verifier kit `build/oracle-sdks/jc304_kit` was found automatically) |
| Live tests | 53 run: 52 passed, 0 failed, 1 aborted (the HMAC-SHA-256 known answer: the card answers `6F03`, CryptoException NO_SUCH_ALGORITHM) |
| Same Maven run | the tests of `core` (610), `gp` (431), `javacard-api` (69) and `converter` (1352): 2462 run, 0 failures, 0 skipped (the Oracle reference CAP files, generated locally, were present in that checkout) |
| Authentication | 85 EXTERNAL AUTHENTICATE commands in the transcripts (44 of the `@LiveCardTest` classes, 41 of LC-MODEL), every one accepted (`9000`); no failed authentication |
| Guard | blocked 0 commands |
| Cleanup | 36 DELETE commands (16 in the `@LiveCardTest` classes, 20 in LC-MODEL), all `9000`; after every test class GET STATUS showed only the ISD and `A0000001515350` |
| Duration | about 7.7 minutes, the Maven build included |

| Test class | IDs | Result on the card | Observations |
|---|---|---|---|
| `CardIdentificationLiveTest` | LC-ID-1 to 6 | 8 passed | values as in [Environment](#environment) |
| `PcscSessionLiveTest` | LC-PCSC-1 to 4 | 4 passed | exclusive T=1 connection; Ne = 256 sent as Le `00`; `reset()` resets the card (a logical channel opened before it is closed by it); logical channel 1 reads the same CPLC as the basic channel |
| `AppletLifecycleLiveTest` | LC-LIFE-1 to 4 | 4 passed | the converted HelloApplet answers all 9 commands exactly like jCardSim; its persistent counter survives a card reset; DELETE removes load file and instance |
| `InstallAndStatusLiveTest` | LC-INST-1 to 5 | 5 passed | install parameters `AABBCC` and `112233445566` reach the two instances unchanged; lock gives state `87` and SELECT `6A82`, unlock restores `07` |
| `ConverterFeaturesLiveTest` | LC-CONV-1 to 10 | 10 passed | every exact answer as on jCardSim, except the aborted transaction: the card rolls it back (`00050000`), jCardSim does not; the card has the int type, so LC-CONV-10 ran |
| `OpenSourceAppletsLiveTest` | LC-OSS-1, 2 | 2 passed | PivApplet and SmartPGP built from their submodules, see below; 173 s for both, building them from source included |
| `CryptoApiLiveTest` | LC-CRYPTO-1 to 4 | 9 passed, 1 aborted | AES, SHA, 3DES and ISO 9797-1 M2 known answers equal the published values and jCardSim; HMAC-SHA-256 aborted: `6F03` (CryptoException NO_SUCH_ALGORITHM: not available on this card); ECDSA P-256 and RSA-2048 signatures verify on the host; RSA-2048 key generation took 9 s in this run (up to about 20 s in earlier runs) |
| `SecureChannelLiveTest` | LC-SC-1 to 3 | 4 passed | levels `01` and `03` (level `00` not requested); fresh card challenges per session |
| `DeclarativeModelLiveTest` | LC-MODEL-1 to 6 | 6 passed | the `@JavaCardTest` scenario classes ran on the card unchanged and observed exactly what they observe on jCardSim and on the simulated GlobalPlatform card: a fresh instance per test, one per class, several instances with their install parameters, a method-level applet of the same package, a nested class, parameterized invocations, deselection, the instance listed by the ISD; the package with a build descriptor was converted with its settings (Java Card 3.0.4, package version 1.2), passed the verifier and ran under the project's prefix `F0079E62FB` (build AIDs `A00000006299`/`A0000000629901` mapped to `F0079E62FBF63F`/`F0079E62FBF63F99E9` in the transcript). 236 commands, 20 installs, 20 deletes, cleanup verified after every class |

### The open-source applets on the card

Both are built from their pinned upstream sources by this project's toolchain (compiled against the API stubs,
converted by this converter for Java Card 3.0.4, checked by Oracle's verifier, loaded by the `gp` module) and
deployed under the test prefix.

- **PivApplet** ([arekinath/PivApplet](https://github.com/arekinath/PivApplet) `5cb14a9`, MPL-2.0, 13 classes,
  extended length): SELECT returns the application property template; VERIFY PIN `123456`; GENERAL AUTHENTICATE
  with the 3DES card management key (9B): the card's witness decrypted and its answer to a host challenge checked
  on the host; GENERATE an EC P-256 key in slot 9A; GENERAL AUTHENTICATE 9A signs a SHA-256 digest, and the
  signature verifies on the host with the public point the card returned.
- **SmartPGP** ([ANSSI-FR/SmartPGP](https://github.com/ANSSI-FR/SmartPGP) `da52ec4`, GPL-2.0-or-later, OpenPGP
  card 3.4, 10 classes): SELECT; GET DATA `6E` reports RSA-2048 for the signature key; VERIFY PW3 `12345678`;
  GENERATE the RSA-2048 signature key on the card (5.4 s; the 270-byte answer arrives through `61XX` and GET
  RESPONSE); VERIFY PW1 `123456`; PSO: COMPUTE DIGITAL SIGNATURE over a SHA-256
  DigestInfo; the 256-byte signature verifies on the host with `SHA256withRSA`; GET DATA `C4` shows the retry
  counters unchanged.

A third applet, [Yubico ykneo-oath](https://github.com/Yubico/ykneo-oath) (`543b4c3`), is not part of the suite.
On this card and reader its LOAD succeeds, but INSTALL gets no answer: after about 6 s the PC/SC transaction
fails (`SCARD_E_NOT_TRANSACTED`) and the macOS reader driver stays stuck until the reader is unplugged. The card
behaved **identically** with both toolchains: with this project's converter and `gp` module, and with a CAP file
made by Oracle's converter (2.2.2 kit) loaded by GlobalPlatformPro v25.10.20. It is therefore not a defect of
this project. The card itself was not affected; the leftover load file was deleted. The same applet passes the
RFC 4226 known answers on jCardSim and on Oracle's `cref` emulator.

### What the card runs found

- **`PcscSession.reset()` did not reset the card on macOS.** LC-CONV-6 failed in the first run with the
  converter features: a `CLEAR_ON_RESET` byte survived `reset()`. An experiment on the card with three ways to
  reset (a `CLEAR_ON_RESET` probe applet; first command after the reset without a SELECT) showed the cause:
  `Card.disconnect(true)` while the connection holds exclusive access does not reset the card (the applet stayed
  selected, the byte kept its value); ending exclusive access first, or a shared connection, does reset it (the
  first command reached the ISD, which answered `6D00`, and the byte was cleared). `PcscSession.reset()` now
  ends exclusive access before the resetting disconnect; LC-PCSC-3, LC-LIFE-3 and LC-CONV-6 detect a reset that
  does not happen, and the simulated reader models this behaviour.
- **HMAC is not available on this card**: `KeyBuilder.buildKey(TYPE_HMAC, ...)` and
  `Signature.getInstance(ALG_HMAC_SHA_256, ...)` throw (`6F00`). The same CAP file computes the RFC 4231 value
  on jCardSim. It is a property of this card configuration, reported as "not supported by this card".
- **jCardSim differs from the card** in `JCSystem.abortTransaction()` (no rollback) and for a case 4 command
  with Lc 255 and Le; see [Known issues](#known-issues). The card follows the Java Card specification.

### The Quick Start project on the card

The plugin README's Quick Start project (pom, `HelloWorldApplet`, its jCardSim test) was built with
`mvn package` against the locally installed main branch, with two settings for this card: `<packageAid>` under the
test prefix (`F04A435801F0`) and `<javaCardVersion>3.0.4</javaCardVersion>` (the plugin's default, 3.0.5,
imports API versions a Java Card 3.0.4 card does not have). The jCardSim test passed, the 2408-byte CAP file
passed Oracle's 3.0.4 verifier (0 warnings, 0 errors), and a one-off test with this harness loaded it with
`GPSession.loadAndInstall` inside `LiveCard.manage`: INSTALL `9000`, instance state `07`; `80 01` answered
`48656C6C6F` (`Hello`) `9000`, `80 02` `6D00`, `00 01` `6E00`; the cleanup deleted the package and GET STATUS
confirmed it.

### A project with the applet parent, its own test on the card

A new applet project outside this repository: the POM of the README Quick Start of that day (coordinates, the
parent `javacard-express-applet-parent`, `javacard.packageAid`; the parent bound the plugin itself then, the Quick
Start now names it) plus one property for this card,
`<javacard.version>3.0.4</javacard.version>`; the README's `HelloWorldApplet` and its unchanged `@JavaCardTest` class
(`@InstallApplet(HelloWorldApplet.class)`, one test sending `80 01`). Command, with Maven 3.9.12 on JDK 25:

```bash
mvn verify -Djcx.backend=livecard -Djcx.livecard.verifierSdk=<kits>/jc304_kit
```

The plugin converted the package in `process-classes` and wrote the build descriptor; the test then ran on the
card: the package was converted with the build's settings (Java Card 3.0.4, package version 1.0), passed Oracle's
3.0.4 verifier, and was loaded under the project's prefix `F0AE57AA79` (build AIDs `A00000006212` and
`A0000000621201` became `F0AE57AA795796` and `F0AE57AA795796AE6A`, as the transcript notes); `80 01` answered
`Hello` `9000`; the instance and the package were deleted (`9000`) and GET STATUS confirmed that nothing was left.
27 commands, 5 EXTERNAL AUTHENTICATE all `9000`, the guard blocked nothing; the whole `mvn verify` took 12 seconds.
The same project runs the same test on jCardSim with `mvn verify` and on the simulated GlobalPlatform card with
`-Djcx.backend=simulated-gp` (the applet parent's integration test does that in every build).

### History of the runs

Runs on 2026-10-01 and 2026-10-02 (times EEST), on the card above; every run left the card as it found it.

| Time | State of the code | Live tests | Result |
|---|---|---|---|
| 11:39-12:04 | standalone checks before the suite existed (not in the repository) | R1-R5 | SCP03 at levels `01` and `03` (EXTERNAL AUTHENTICATE `9000`, C-MAC and C-DECRYPTION commands accepted); a converted HelloApplet: Oracle's verifier 0 errors, 9/9 answers equal to jCardSim; install parameters, a second instance, application lock and unlock, DELETE; crypto 9 of 10 (HMAC not available); `PcscSession` exclusive access, Le `00`, logical channels |
| 16:48 | first version of the suite | 35 | 34 passed, 1 aborted (HMAC) |
| 18:26 | converter-feature tests added | 10 | 9 passed, 1 failed: LC-CONV-6 found the `reset()` defect above |
| 18:28 | reset experiment (standalone) | - | root cause shown, see above |
| 18:52 | `reset()` fixed, reset detection added | 45 | 44 passed, 1 aborted (HMAC) |
| 19:37 | open-source applets as git submodules | 2 | 2 passed |
| 22:47 | integrated tree (all fixes merged): the run above | 47 | 46 passed, 1 aborted (HMAC) |
| 22:55 | the plugin README Quick Start project, built with `mvn package` (see below) | 1 | passed |
| 23:33 | HelloApplet converted for Java Card 3.0.5 (`-Djcx.livecard.javaCardVersion=3.0.5`) | 1 | refused by the card at the first LOAD block with `6438`: the card is a 3.0.4 platform; the harness reported the failed deployment and its cleanup confirmed nothing was left |
| 2026-10-02 05:27 | all fixes, with the guard and harness hardening of 2026-10-02 | 47 | 46 passed, 1 aborted (HMAC) |
| 08:36 | the declarative test model; one Maven command, no script | 52 | 51 passed, 1 aborted (HMAC) |
| 08:45 | LC-MODEL again, now writing its transcripts | 5 | passed |
| 09:40 | the build descriptor of the Maven plugin, the projects' own AID prefixes, LC-MODEL-6: the run above | 53 | 52 passed, 1 aborted (HMAC) |
| 09:50 | an applet project of its own with the applet parent ran its JUnit test on the card (see below) | 1 | passed |

Over all transcribed runs the suite sent 355 EXTERNAL AUTHENTICATE commands and the card accepted every one;
the guard never had to block a command; no key, card life cycle or Security Domain was changed.

### Not run on hardware

SCP02 and the SCP03 S16 mode (the card offers SCP03 S8 only; the `gp` module's SCP02 and S16 code is tested
against recorded transcripts and spec-derived references), secure messaging and PACE/BAC of the `sm` and `pace`
modules (tested against the ICAO 9303-11 worked examples and a simulated chip), other card models and readers,
Linux and Windows PC/SC.
