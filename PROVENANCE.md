# Provenance

This file states what each part of JavaCard Express is derived from, how Oracle's Java Card tools are used,
and which questions are still open. It replaces the provenance statements that used to be spread over
`converter/BINARY_COMPATIBILITY.md`, `converter/src/test/resources/PROVENANCE.md` and the design notes.

It records facts, not legal conclusions. The open items at the end need a review by the owner and, before a
1.0 release, by IP counsel.

## Sources by component

| Component | Written from | Interoperability data it contains | How it is checked |
|-----------|--------------|-----------------------------------|-------------------|
| `converter` (`.class` to `.cap`/`.exp`) | Java Card Virtual Machine Specification 3.0.5 and 3.1 (CAP format, export file format, token assignment, bytecodes). Code comments and test names cite the sections they implement, for example `JCVM 3.1 §6.9`. Class files are read with the JDK ClassFile API (JEP 484). No third-party libraries. | Built-in linking data of the standard Java Card API (`converter/src/main/resources/.../resolve/javacard-api-exports.txt`): package AIDs and versions, class, method and field tokens, access flags and supertypes. See [Built-in API linking data](#built-in-api-linking-data). | Spec-derived structural checks in every build; Oracle's off-card verifier and converter as black boxes when an SDK is installed locally (see [Use of Oracle tools](#use-of-oracle-tools)). |
| `javacard-api` (compile-only stubs) | The published Java Card 3.0.5 Classic API: names, signatures, modifiers and constant values, which applets need to compile. Method bodies throw `RuntimeException("stub")`; the documentation comments are this project's own wording. | Signatures and constant values (they are inlined by `javac` into applet class files, so they must be exact). The 2026 completion of the stubs generated skeletons from the public API surface of jCardSim (Apache License 2.0) and compared the result, as data, with the SDK's `api_classic.jar` through `javap -protected -constants`. | `ApiConformanceTest` compares the stubs with jCardSim's API classes in every build; `-Djcx.api.reference=<api_classic.jar>` compares them with an SDK jar. |
| `core`, `container`, `docker` | ISO/IEC 7816-4 (APDU encoding, logical channels, BER-TLV) and the Java Card API (install parameter layout). Runs applets on jCardSim (`com.klinec:jcardsim`, Apache License 2.0), which is a dependency, not copied code. | — | Unit tests against fakes of `javax.smartcardio` and against jCardSim. |
| `gp` | GlobalPlatform Card Specification v2.3.1 (Appendix E: SCP02) and Amendment D (SCP03), NIST SP 800-108 (KDF), NIST SP 800-38B / RFC 4493 (CMAC). | — | Known-answer transcripts whose sources are named in `gp/src/test/resources/.../scp-*.txt`: an independent reference implementation written from the specifications, public GlobalPlatformPro session logs of real cards, and the Samsung OpenSCP-Java AES transcripts. |
| `sm`, `pace` | ICAO Doc 9303 Part 11 (BAC, PACE with its standardized domain parameters, Secure Messaging), ISO/IEC 7816-4. | — | The worked examples of ICAO 9303-11 Appendices D and G reproduce byte for byte; generated vectors come from `sm/src/test/python/icao_ref.py`, a reference written from the specification. |
| `maven-plugin` | Maven plugin API; uses `converter`. | — | Unit tests and maven-invoker integration tests that build sample projects. |
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

## Trademarks

Java and Java Card are trademarks or registered trademarks of Oracle and/or its affiliates. JavaCard Express
is an independent project and is not affiliated with, sponsored by or endorsed by Oracle.

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
