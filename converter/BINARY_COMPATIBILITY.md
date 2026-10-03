# Converter verification and compatibility

This page says how the CAP and export files written by the converter are checked, what was measured, and where
the output differs from Oracle's converter. Provenance and the use of Oracle tools are described in
[PROVENANCE.md](../PROVENANCE.md).

**Short version.** The converter writes the CAP format (2.1 for Java Card 2.1.2 to 3.0.5, compact 2.3 for 3.1
and 3.2) and export files (2.1, or 2.3 for 3.1/3.2) as the JCVM specification defines them. In the audit of
October 2026 every expected-valid package of a 151-entry corpus, including seven real open-source applets,
passed Oracle's off-card verifier, and all 47 packages run on Oracle's `cref` emulator behaved like the Oracle
converter's CAP files. Byte identity with Oracle's converter is not a goal: where the specification leaves an
order open the two converters may differ, and both results are valid.

## Correction: there is no "Oracle dispatch table off-by-one bug"

Earlier releases documented an "off-by-one bug" in Oracle's Class component and offered
`oracleCompatibility(true)` to reproduce it. That was a misreading of the specification. JCVM 3.1 §6.9 orders
`class_info` as `public_method_table_base`, `public_method_table_count`, `package_method_table_base`,
`package_method_table_count`, then `public_virtual_method_table[]` and `package_virtual_method_table[]`. Oracle's
converter follows that order. The old **default** output placed the package table base and count after the
public table, so Oracle's verifier rejected every default-mode CAP (`Invalid method offset 0`) and cards could
dispatch virtual methods wrongly.

Since the 2026 fixes the converter always writes the §6.9 layout. `Converter.Builder.oracleCompatibility(boolean)`
and the plugin parameter `oracleCompatibility` are deprecated and have no effect. **CAP files that the converter built
before these fixes in the default mode should be rebuilt;** the [changelog](../CHANGELOG.md) names those releases and
the other defects fixed at the same time.

## How the output is checked

| Level | Runs | What it checks |
|-------|------|----------------|
| Spec-derived checks | every build, no SDK | `CapInvariantsTest` parses each CAP with an independent reader written from JCVM 3.1 Chapter 6 and checks structural invariants (`class_info` layout and tokens, public and package method tables, interface tables, constant pool entries and what they point to, static field image segments, exception handler indexes, the Export component and the header flags) for every test package in CAP 2.1 and 2.3. Component tests cite the spec sections they assert (`ClassComponentTest`, `DescriptorSpecTest`, `TokenAssignerSpecTest`, `StaticInitializationTest`, `ExportComponentSpecTest`, ...). `GeneratedExportFilesTest` checks generated export files against a checker of JCVM Chapter 5 that is calibrated on all API export files of eight SDKs. |
| Oracle verifier | when `build/oracle-sdks/jc305u3_kit` exists | `OracleVerifycapTest`: all 23 converter test packages, converted in the default mode, verify with 0 errors and 0 warnings. The Maven plugin's integration tests run the verifier on every CAP they build (they report by default; `-Djcx.it.verifycap=strict` fails on an error). |
| Oracle converter output | when reference CAPs were generated | `OracleByteIdentityTest`, `PerVersionOracleComparisonTest`, `OracleReferenceComparisonTest` compare the components of the test applets with Oracle's output (see below). |
| Acceptance corpus | audit, external harness | 151 entries; see [Acceptance results](#acceptance-results-october-2026). |
| Real card | lead engineer only | see [Real cards](#real-cards). |

How to install the kits and generate the reference files: [tools/oracle/README.md](../tools/oracle/README.md).

## Byte identity with Oracle's converter (test applets)

With the reference files generated, these tests pass with every component byte-identical:

| Package | Targets | Components compared byte for byte |
|---------|---------|-----------------------------------|
| `TestApplet` | 2.1.2, 2.2.1, 2.2.2, 3.0.3, 3.0.4, 3.0.5, 3.1.0, 3.2.0 | all |
| `MultiClassApplet`, `InterfaceApplet`, `ExceptionApplet`, `InheritanceApplet` | 3.0.5 | all |
| `CryptoApplet` | 3.0.5 | all except Constant Pool, Method and Descriptor, which hold the same constant pool entries in another order (§6.8: "There are no ordering constraints on constant pool entries") and are compared by size and content |

Version-specific details that match Oracle's output:

- `getfield_<t>_this` is used for every target; `putfield_<t>_this` from 3.0.5 on.
- The Import component lists only the packages a CAP file references; from 2.2.2 on `java.lang` is imported
  even when nothing references it, as Oracle's converter does. Package versions come from the target's API (for example
  `javacard.framework` 1.3 for 2.2.2, 1.6 for 3.0.5, `javacard.security` 1.7 for 3.1 and 1.8 for 3.2).

## Acceptance results (October 2026)

The audit ran a corpus of 151 packages through the converter with an external harness (not part of this
repository, because it drives Oracle tools and third-party applets). The corpus holds the converter's test
packages, one or more cases for every audit finding, AID validation cases, a version matrix and seven open-source
applets: IsoApplet, PivApplet, SmartPGP, status-keycard, ykneo-oath, ykneo-openpgp and GidsApplet. Applets were
compiled with `javac --release 8` against the target SDK's API jar (variant A) and against this project's stubs
(variant B). Results at the integrated converter (the state documented here):

| Check | Result | Converter before the fixes |
|-------|--------|--------------------------|
| Expected-valid packages, CAP passes the target SDK's verifier (A) | 108/108 | 0/108 |
| Same, compiled against the project's API stubs (B) | 102/102 (the other 6 target 3.1/3.2 APIs, which the stubs do not cover) | 0/102 |
| CAP(A) identical to CAP(B) | 103/103 | |
| Invalid packages rejected with a clear `ConverterException` | 41/41 (none accepted silently, no crashes) | 6/41 |
| Deterministic output (two conversions give identical JAR bytes) | 109/109 | |
| `javac --release 11/17/21/25` variants (nestmates and similar) | 10/10 | 0/10 |
| Oracle `cref` 2.2.2: load, install, select, scripted APDUs | 47/47, same as the Oracle converter's CAPs (47/47) | 0/47 |

An independent review then ran a second corpus of 109 packages through the same harness: deep hierarchies,
statics of every type, sparse switches, nested `try`/`finally`, 255 locals, long methods, `int` semantics with
and without int support, libraries and clients, interfaces, multiselection and RMI, for targets 2.1.2 to 3.2.0.
All 89 expected-valid packages pass the verifier, all 14 invalid ones are rejected cleanly, and 21 of 26 `cref`
scripts pass; the other 5 fail in the same way with the Oracle converter's CAP files (limits of `cref` 2.2.2),
or both converters reject the package. Eleven of those packages processed by ProGuard 7.6.1 with
`-dontpreverify` (class files without `StackMapTable`) also verify and pass their `cref` scripts.

Class files whose `finally` blocks are `jsr`/`ret` subroutines were checked the same way, compiled with ECJ 4.4.2
`-source 1.3 -target 1.2` and `-source 1.4 -target 1.4`: two `try`/`finally` probe applets (nested `finally`,
`finally` in `finally`, `return`, `break` and `continue` in and through `finally`, exceptions passing several
`finally` levels, `try`/`catch` and `switch` in `finally`; 89 and 45 scripted APDUs) pass Oracle's verifier and
answer on `cref` exactly as their `javac` build does on jCardSim, for both targets. So does a third probe applet
with `try` statements nested in `finally` blocks (a `try`/`catch` around a nested `try`/`finally`, `continue`,
`break` and `return` from a nested `finally` block; 64 scripted APDUs), whose inner subroutines leave for the code
of the enclosing ones. ECJ builds of FIDO2Applet, OpenFIPS201 and ykneo-openpgp pass the verifier too. Oracle's
converter rejects the first two probe applets (`continue`/`break` in a `finally` block inside a loop: "recursive
subroutine call"). In a differential check of 3206 class files of random programs with nested
`try`/`catch`/`finally` blocks, loops and switches, compiled by ECJ for `-target` 1.1, 1.2 and 1.4 and run on the
JVM before and after inlining for 256 arguments each, every result and exception was the same. Of the class files
whose inlined code is new or changed with the nested-`finally` support, the 328 that fit a Java Card method convert
and pass Oracle's verifier; the others exceed the Method component's limits and are rejected with the reason.

### Differences from Oracle's converter (same class files)

On the 109 entries both converters accept, all components are byte-identical for 46 entries. Per component:
Header 109/109, Directory 89, Import 96, Applet 98/107, Class 86, Method 50, Static Field 108, Constant Pool 48,
Reference Location 86, Descriptor 46, Export 5/6.

A semantic comparison that normalizes the orders the specification leaves open explains every remaining
difference; no content difference was found:

| Cause | Entries | Specification |
|-------|---------|---------------|
| Constant pool order (and therefore Method operands and Descriptor type tables) | 50 | §6.8: no ordering constraints |
| Order of imported packages | 13 | §4.3.7.1: package token order not specified |
| Order of methods in the Method component (Oracle puts abstract methods last), which moves install offsets, Class method tables and Reference Location | 12 | §6.10: no order |
| Token order of classes, virtual methods, static members, instance fields, interface methods | 6 | §4.3.7.2 to §4.3.7.7: not specified |
| Oracle keeps duplicate constant pool entries; we merge them | 6 | §6.8 |
| Order of values inside a static field image segment | 1 | §6.11 fixes only the segments |
| Method bodies: we keep an `s2b` before byte stores that Oracle drops when the value is already a byte (a no-op, 1 byte each); Oracle sometimes uses a wide branch where a 1-byte offset fits | 16 (42 of 823 methods) | both encodings are valid |

Oracle orders virtual method tokens with final methods first and abstract methods last, which keeps subclass
dispatch tables shorter. We assign tokens in declaration order. Both are valid; changing the order would
change the export-file tokens of existing libraries.

Where it costs nothing, the converter follows Oracle's observed choices for open orders (constructors
translated depth-first, instance field references grouped by class, type descriptors registered constant pool
first) so that test applets stay byte-comparable.

## Real cards

The lead engineer ran converted applets on an NXP JCOP4 card (Java Card 3.0.4, GlobalPlatform 2.1.1 card
management, SCP03) during the audit, with converter snapshots from the fix branches: a HelloApplet (static array
initializer, persistent counter, transient array) answered identically to jCardSim, install parameters and
multiple instances worked, and the crypto test applet produced the known-answer results for AES, SHA, 3DES,
ECDSA P-256 and RSA-2048 (HMAC is not supported by that card). The live-card suite later ran the converter
features on that card; see [LIVE_CARD_TESTING.md](../LIVE_CARD_TESTING.md#verified-results). Agents and CI never
access real cards.

## Limitations

- Output formats: CAP 2.1 and compact CAP 2.3. The extended CAP format, static resources and the Debug
  component are not generated.
- Java Card RMI is not supported: a package that defines a remote interface or class (§2.2.6) is rejected,
  because its CAP file would need the remote information of CAP format 2.2 (§6.9.2.1 ACC_REMOTE, §6.9.2.6).
- API: built-in linking data covers the standard packages of 2.1.2 to 3.2.0 with per-version checks. The
  compile-only stubs (`javacard-express-api`) cover the 3.0.5 Classic API; compile 3.1/3.2-only APIs against
  another API jar.
- Compile-time constants introduced after the target version cannot be detected (javac inlines them).
- Optimizations Oracle performs that we do not: dropping redundant `s2b`, Oracle's virtual token order.
