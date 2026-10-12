# Provenance

This file states what each part of JavaCard Express is derived from, how Oracle's Java Card tools are used,
and which questions are still open. It replaces the provenance statements that used to be spread over
`converter/BINARY_COMPATIBILITY.md`, `converter/src/test/resources/PROVENANCE.md` and the design notes.

It records facts, not legal conclusions. The open items at the end need a review by the owner and, before a
1.0 release, by IP counsel.

## Sources by component

| Component | Written from | Interoperability data it contains | How it is checked |
|-----------|--------------|-----------------------------------|-------------------|
| `converter` (`.class` to `.cap`/`.exp`) | Java Card Virtual Machine Specification 3.0.5 and 3.1 (CAP format, export file format, token assignment, bytecodes). Code comments and test names cite the sections they implement, for example `JCVM 3.1 §6.9`. Class files are read with the JDK ClassFile API (JEP 484). No third-party libraries. | Built-in linking data of the standard Java Card API (`converter/src/main/resources/.../resolve/javacard-api-exports.txt`): package AIDs and versions, class, method and field tokens, access flags and supertypes. See [Built-in API linking data](#built-in-api-linking-data). The same kind of data for the GlobalPlatform Card API package `org.globalplatform` (`globalplatform-api-exports.txt`). See [GlobalPlatform Card API](#globalplatform-card-api). | Spec-derived structural checks in every build; Oracle's off-card verifier and converter as black boxes when an SDK is installed locally (see [Use of Oracle tools](#use-of-oracle-tools)). |
| `javacard-api` (compile-only stubs) | The published Java Card 3.0.5 Classic API and the published GlobalPlatform Card API (package `org.globalplatform`, the surface of its version 1.8): names, signatures, modifiers and constant values, which applets need to compile. Method bodies throw `RuntimeException("stub")`; the documentation comments are this project's own wording. | Signatures and constant values (they are inlined by `javac` into applet class files, so they must be exact). The 2026 completion of the stubs generated skeletons from the public API surface of jCardSim (Apache License 2.0) and compared the result, as data, with the SDK's `api_classic.jar` through `javap -protected -constants`. | `ApiConformanceTest` compares the stubs with jCardSim's API classes in every build; `-Djcx.api.reference=<api_classic.jar>` compares them with an SDK jar. For `org.globalplatform`: `GlobalPlatformKnownAnswerTest` pins the constants that GlobalPlatform Card Specification v2.3.1 codes, the stubs are compared with the simulator's implementation of the package and with the converter's linking data in every build, and `-Djcx.gpapi.reference=<jar>` compares them with GlobalPlatform's own API jar. |
| `core`, `container`, `docker` | ISO/IEC 7816-4 (APDU encoding, logical channels, BER-TLV) and the Java Card API (install parameter layout). Runs applets on jCardSim (`com.klinec:jcardsim`, Apache License 2.0), which is a dependency, not copied code. `core` also implements the GlobalPlatform Card API (`org.globalplatform`) for the simulated cards, see [GlobalPlatform Card API](#globalplatform-card-api). | The signatures and constant values of `org.globalplatform` (the same as in the stubs). | Unit tests against fakes of `javax.smartcardio` and against jCardSim; for the secure channel of `org.globalplatform`, public SCP03 transcripts replayed on the card side and sessions opened by the `gp` module. |
| `gp` | GlobalPlatform Card Specification v2.3.1 (Appendix E: SCP02) and Amendment D (SCP03), NIST SP 800-108 (KDF), NIST SP 800-38B / RFC 4493 (CMAC). | — | Known-answer transcripts whose sources are named in `gp/src/test/resources/.../scp-*.txt`: an independent reference implementation written from the specifications, public GlobalPlatformPro session logs of real cards, and the Samsung OpenSCP-Java AES transcripts. |
| `sm`, `pace` | ICAO Doc 9303 Part 11 (BAC, PACE with its standardized domain parameters, Secure Messaging), ISO/IEC 7816-4. | — | The worked examples of ICAO 9303-11 Appendices D and G reproduce byte for byte; generated vectors come from `sm/src/test/python/icao_ref.py`, a reference written from the specification. |
| `javac-plugin` | javac's public compiler API (`com.sun.source.util`, `javax.annotation.processing`, JSR 269); the rules are the converter's (`PackageCheck`). In IntelliJ IDEA's own build it reaches javac behind the IDE's wrapper through the public method `org.jetbrains.jps.javac.APIWrappers.unwrap` of IntelliJ IDEA's JPS javac runner (IntelliJ IDEA Community, Apache License 2.0), called by name through reflection; no IntelliJ code is copied or shipped. | — | Unit tests that compile sample applets with javac, one of them behind a proxy of the processing environment as a wrapper hands it over; a manual run through IntelliJ IDEA's javac runner and a build in IntelliJ IDEA 2026.2; maven-invoker integration tests of the Maven plugin. |
| `maven-plugin` | Maven plugin API and, for the build extension, Maven's lifecycle participant API; uses `converter` and `javac-plugin`. | — | Unit tests and maven-invoker integration tests that build sample projects. |
| `intellij-plugin` (IntelliJ IDEA plugin, a separate Gradle build) | The IntelliJ Platform's public plugin API (an external annotator, the Java and Maven plugins' project model) and javac's public API (`javax.tools`). It has no rules of its own: it runs the `javac-plugin` and `converter` jars of the project's own annotation processor path, which it does not ship. It is built with the IntelliJ Platform Gradle Plugin against IntelliJ IDEA and tested with the IntelliJ test framework, all fetched by its Gradle build from JetBrains' repositories and Maven Central, only to build and test; the plugin zip contains only this project's classes. | — | Tests in IntelliJ IDEA's headless test framework (the annotator in a light project) and with the toolkit's javac check; JetBrains' plugin verifier. |
| `livecard` | GlobalPlatform Card Specification v2.3.1 and Amendment D (the APDU guard's allow-list and its own SCP03 handshake check), NIST SP 800-38B / RFC 4493 (AES-CMAC) and NIST SP 800-108 (KDF), written independently of the `gp` module; ISO/IEC 7816-4 (classes, logical channels). Uses `core`, `gp` and `converter`. | — | Known answers (RFC 4493, SP 800-38B, reference SCP03 handshakes), replay of recorded real-card transcripts with the card serial zeroed, and the live suite on a simulated card in every build; on a real card only with `./mvnw -Plivecard verify -pl livecard -am` ([LIVE_CARD_TESTING.md](LIVE_CARD_TESTING.md)). |

## Built-in API linking data

To link an applet against `javacard.framework` and the other API packages, a converter needs the tokens of
the API's classes and members, the package AIDs and the package versions (JCVM 3.1 §4.3.6, §4.5.2, §5.3). The
specification does not list these values; they exist in the API export files that ship with each Java Card
development kit, and a CAP file only links on a card when they match exactly.

The converter therefore ships them as data. They were read from the API export files of the development kits
2.1.2, 2.2.1, 2.2.2, 3.0.3, 3.0.4, 3.0.5u3, 3.1.0 and 3.2.0 with this project's own export file reader (the
test tool `BuiltinApiDataGenerator`), and `BuiltinExportsSdkComparisonTest` checks them against those files
when the kits are installed locally. No constant values and no other content of the export files are
included. Users can supply their own export files (`Converter.Builder.importExportFile`,
`Converter.Builder.exportPath`); these take precedence over the built-in data.

These values are interoperability facts. Shipping them is nevertheless data taken from SDK files and is
listed under [Open items](#open-items).

## GlobalPlatform Card API

GlobalPlatform publishes the Java Card API of its Card Specification, the package `org.globalplatform`, separately
from the specification: the documentation of its types and methods, a jar of stub classes and the export file, for
the package versions 1.0 (GlobalPlatform 2.1 and 2.1.1) to 1.8. They are free downloads under the GlobalPlatform
License Agreement, which permits products that implement the specification and says that such products are not
derivative works of it; it does not permit redistributing or modifying the specification. Nothing of GlobalPlatform's
files is part of this repository or of its artifacts:

- The stubs in `javacard-api` are written from the published API: names, signatures, modifiers, constant values and
  deprecations. The documentation comments are this project's own wording.
- The linking data (`converter/src/main/resources/.../resolve/globalplatform-api-exports.txt`) holds the package AID,
  the package versions and, for every class and method, its token, access flags, supertypes and the first version
  that has it. The values were read from the export files of the versions 1.0 to 1.8 with this project's own export
  file reader (the test tool `GlobalPlatformApiDataGenerator`), and `GlobalPlatformApiExportFilesTest` checks them
  against those files when they are present locally (`build/globalplatform-api/`, ignored by git, or
  `-Djcx.gpapi.exports=<directory>`). No constant values and no other content of the export files are included.
  Users can supply GlobalPlatform's export file themselves; it takes precedence over the built-in data.
- The implementation of the package on the simulated cards (`core`: `org.globalplatform` and
  `name.velikodniy.jcexpress.embedded.gp`) is written from GlobalPlatform Card Specification v2.3.1, Amendment D
  v1.1.2 and the documentation of the API, and cites them. The code of other simulators was not used. Its API
  classes are checked against a list of the API's types, method descriptors and constant values
  (`core/src/test/resources/.../embedded/gp/org-globalplatform-1.8-surface.txt`), and the stubs against these
  classes (`GlobalPlatformStubsAndSimulatorTest` in `livecard`).

The files this was done with in October 2026 were copies in public repositories
(`github.com/OpenJavaCard/globalplatform-exports`, `mvn.javacard.pro/vendors`, `github.com/suut/gpapi-exports`), not
a download from GlobalPlatform under its click-through licence; the copies of the different repositories are
identical. SHA-256 of the export files (`org/globalplatform/javacard/globalplatform.exp`), for comparison with an
official download:

| Package version | Export file format | SHA-256 |
|-----------------|--------------------|---------|
| 1.0 | 2.1 | `7a1f4db9e11d66ed76f44120a03ae17a776d3b6ba4646bfc028cd5bbd5d02f33` |
| 1.1 | 2.1 | `f24925fbb3f17c4611dd3692564f3fa93f2e6e1020398512deae673c7e7c410c` |
| 1.2 | 2.1 | `210b9d0042005d2e121dad491ab8cd965694aea953e65de5589dabb4ae00166a` |
| 1.3 | 2.1 | `fa19100ca3f14c303b3cc6eee03f05ea89c90bdf046a69b54df8d12ce220eeb0` |
| 1.4 | 2.1 | `42899f4668575fe2ebffb917a71c435940aed24eab4309b037813ced51ba411b` |
| 1.5 | 2.1 | `b67f7a76ab37b163c2bf46f9b0318be9daa58a055d6f0cbb8ba166001db93bde` |
| 1.6 | 2.1 | `c1abb54934d803a4e2df6eee5e979ac364e9dc4d88817e9d4f70179793c9dd26` |
| 1.7 | 2.1 | `f6a8cd5910385c1a980cb4cb8669900a3735e582529f99ebea34e0791f8675ce` |
| 1.8 | 2.3 | `869b9d231c54e6810efdafdcc3a4a9b9a4e62525a8a9868077792acb507572eb` |

Using these files is listed under [Open items](#open-items).

## Use of Oracle tools

Oracle's Java Card development kits are licensed by Oracle. They are not part of this repository and are
never redistributed. Download them from Oracle; contributors install them under `build/oracle-sdks/`, which is
ignored by git.

They are used only as black boxes, on this project's own test applets and on the open-source applets of the
acceptance corpus:

- the off-card verifier (`verifycap`, `verifyexp`) judges CAP and export files written by our converter
  (`OracleVerifycapTest`, the plugin integration tests with `-Djcx.it.verifycap`, and in the `livecard` module
  `TestAppletsVerifierTest` and `LiveCard.deploy` with the setting `verifierSdk`, before a CAP file is loaded onto
  a card);
- the converter produces reference CAP files that are compared with ours component by component
  (`OracleByteIdentityTest`, `OracleReferenceComparisonTest`, `PerVersionOracleComparisonTest`);
- the `cref` 2.2.2 emulator, in a local Docker image, runs CAP files during acceptance testing;
- `javap -public` / `-protected -constants` on the kit's API jar lists public API facts.

Oracle code is never decompiled, disassembled or read. All tests that need a kit or Oracle output skip when it
is absent, so the build never depends on Oracle material.

Oracle tool output is not redistributable. The reference CAP files are generated by
`tools/oracle/generate-oracle-refs.sh` into `build/oracle-refs/` and are never committed. Thirteen such files
were committed by mistake between March and April 2026 (commits `99e7953`, `7d31a81` and `c184d51`, under
`converter/src/test/resources/reference/`). They have been removed from the tree but remain in the git history.

## Third-party material

- jCardSim (`com.klinec:jcardsim`, Apache License 2.0) is a dependency of `core`; `container` and the
  simulator image bundle it, with its notices (`META-INF/THIRD-PARTY-NOTICES.txt`).
- The acceptance corpus used during the audit includes open-source applets (IsoApplet, PivApplet, SmartPGP,
  status-keycard, ykneo-oath, ykneo-openpgp, GidsApplet). They are not part of this repository.
- The live-card suite uses two of them as test subjects through git submodules under `livecard/third_party`:
  PivApplet (https://github.com/arekinath/PivApplet, MPL-2.0) and SmartPGP (https://github.com/ANSSI-FR/SmartPGP,
  GPL-2.0-or-later). The repository records only the submodule URL and commit; their code is not copied into it,
  is not part of any published artifact and is compiled only into `livecard/target` by the tests. The build does
  not need them.
- Test vectors from public sources are embedded with a source comment next to them.
- `intellij-plugin/gradle/wrapper/gradle-wrapper.jar` is the Gradle wrapper (Gradle, Apache License 2.0), as Gradle
  generates it for a build; it is in the source repository only.

## Trademarks

Java and Java Card are trademarks or registered trademarks of Oracle and/or its affiliates. JavaCard Express
is an independent project and is not affiliated with, sponsored by or endorsed by Oracle.

GlobalPlatform is a registered trademark of GlobalPlatform, Inc. JavaCard Express is not affiliated with,
sponsored by or endorsed by GlobalPlatform; its implementation of GlobalPlatform specifications is not certified
or qualified by GlobalPlatform.

## Open items

These are decisions for the owner; nothing in this repository settles them.

1. **Specification licence.** The JCVM specification is distributed under an Oracle licence with its own
   terms for implementations of the specification. Whether publishing and distributing an independent
   implementation needs a separate agreement has not been reviewed by counsel.
2. **Built-in API linking data.** The tokens, AIDs and versions are interoperability facts, but they were
   taken from SDK export files. The alternative is to ship no API data and require users to point the
   converter at their own export files.
3. **API stub skeletons.** The 2026 completion of the stubs was bootstrapped from jCardSim's public API
   surface (Apache License 2.0). Only names, signatures and constants were used; whether an attribution in
   `NOTICE` is required is open (it is given there as a precaution).
4. **Early design notes.** Design notes of 2026 that are not part of this repository list open-source projects
   as background references for the converter (caprunner, which has no licence; martinpaljak/capfile, MIT;
   jCardSim, Apache License 2.0; an academic paper). Whether any of them was consulted beyond the specifications while the converter was
   first written is for the original author to state.
5. **Git history.** The Oracle-generated CAP files removed from the tree are still in the history of every
   branch. Removing them needs a history rewrite and a force push, and forks keep their copies.
6. **Trademarks.** The project and artifact names contain "JavaCard".
7. **GlobalPlatform Card API.** The stubs and the linking data of `org.globalplatform` were derived from copies of
   GlobalPlatform's files in public repositories, not from a download under GlobalPlatform's click-through licence.
   The owner should download the official bundles, compare the checksums listed above and regenerate the data from
   them. Shipping tokens taken from export files is the question of item 2 again, here under GlobalPlatform's
   licence.
