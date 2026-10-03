# Optional black-box checks with the Oracle Java Card SDK

The build never needs an Oracle SDK. When one is installed locally, more converter tests run: Oracle's tools
judge or reproduce our output as black boxes (see [PROVENANCE.md](../../PROVENANCE.md#use-of-oracle-tools)).
Nothing produced by them may be committed; everything below lives in the git-ignored `build/` directory.

## Install the kits

Download the Java Card development kits from Oracle and unpack them under `build/oracle-sdks/`:

| Directory | Used by |
|-----------|---------|
| `build/oracle-sdks/jc305u3_kit` | `OracleVerifycapTest` (off-card verifier on every converter test package), plugin ITs with `-Djcx.it.verifycap=report|strict`, reference CAPs for 3.0.5 and the multi-class packages |
| `jc212_kit`, `jc221_kit`, `jc222_kit`, `jc303_kit`, `jc304_kit`, `jc310r20210706_kit`, `jc320v24.0_kit` | reference CAPs of `TestApplet` for the other targets |
| `build/oracle-sdks/jc304_kit` | the livecard module: `TestAppletsVerifierTest` (every live-test applet and the open-source applets through the 3.0.4 off-card verifier) and the live-card runs, which check every CAP file with it before loading (taken automatically when `verifierSdk` is not set) ([LIVE_CARD_TESTING.md](../../LIVE_CARD_TESTING.md)) |
| any kit | `BuiltinExportsSdkComparisonTest`, `ExportFileReaderSdkTest` (built-in API data and export file reader against the kits' export files) |

## Generate the reference CAP files

```bash
JAVA11_HOME=/path/to/jdk-11 tools/oracle/generate-oracle-refs.sh
```

The script compiles the converter's test applets (`converter/src/test/java/com/example/...`), runs each kit's
converter on them and writes `build/oracle-refs/oracle-*.cap`. The old converters need Java 8: set
`JAVA8_HOME`, or let the script run Java 8 in Docker (`eclipse-temurin:8-jdk`) when no native Java 8 is
available. The 3.2.0 kit runs on Java 11 (`JAVA11_HOME`).

The comparison tests read the files from `build/oracle-refs/` (or the directory given with
`-Djcx.oracle.refs=<dir>`) and are skipped when a file is missing:

```bash
./mvnw test -pl converter -am -Dtest='Oracle*Test,PerVersionOracleComparisonTest' -Dsurefire.failIfNoSpecifiedTests=false
```

Always use `-am` with `-pl converter`: without it Maven may take an older `javacard-express-api` from the local
repository and compile the test applets against stale stubs.

## What the comparisons assert

- `OracleVerifycapTest`: every converter test package, converted in the default mode, passes Oracle's verifier
  with 0 errors and 0 warnings.
- `OracleByteIdentityTest`, `PerVersionOracleComparisonTest`, `OracleReferenceComparisonTest`: every CAP
  component of `TestApplet` is byte-identical to the Oracle converter's output for all eight targets, and so are
  the components of the multi-class, interface, exception and inheritance packages (3.0.5). For `CryptoApplet`
  the constant pool holds the same entries in another order, which JCVM 3.1 §6.8 allows; the Constant Pool,
  Method and Descriptor components are therefore compared by size and entries, all others byte for byte.
