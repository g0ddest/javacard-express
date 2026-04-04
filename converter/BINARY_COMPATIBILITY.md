# Binary Compatibility Status

**Converter**: javacard-express-converter

**Reference**: Oracle JavaCard SDK 3.0.5u3

## Legal Context

This converter is a **clean-room implementation** built entirely from the publicly available
JCVM specification (§6 "CAP File Format"). No Oracle source code was examined or copied.

Clean-room reimplementation of published specifications is well-established as legally
protected under US case law:
- **Google v. Oracle** (Supreme Court, 2021): reimplementing APIs for interoperability is fair use
- **Sega v. Accolade** (9th Cir., 1992): intermediate use for interoperability is fair use
- **Lotus v. Borland** (1st Cir., 1995): functional specifications are uncopyrightable methods of operation

**Output comparison testing** (comparing our converter's output against Oracle's without
examining Oracle's code) is black-box functional testing, analogous to verifying a clone
BIOS boots the same way as the original.

### Oracle Reference Files

Oracle-generated reference CAP/EXP files are used for comparison tests and are produced
locally by `build/generate-oracle-refs.sh`. These files require an Oracle JavaCard SDK
installation, which is available under the Oracle Technology Network (OTN) Developer License.

**Important**: OTN-licensed materials (SDK tools) are
**not redistributable**. Reference files should be generated locally and not committed to
the repository. Tests that require reference files use `Assumptions.assumeTrue()` and are
skipped when references are not present.

### API Stubs

The `javacard-api` module provides clean-room API stubs (`javacard.framework`,
`javacard.security`, `javacardx.crypto`) implemented from the JCVM specification. These
stubs are used instead of Oracle's `api_classic.jar` to avoid any dependency on OTN-licensed
materials. Token assignments match the specification (not Oracle's proprietary exports).

## TestApplet (single-class baseline, JC 3.0.5)

| Component | Tag | Size | Byte-Identical | Notes |
|-----------|-----|------|----------------|-------|
| Header | 1 | 21 = 21 | YES | |
| Directory | 2 | 34 = 34 | YES | |
| Applet | 3 | 16 = 16 | YES | |
| Import | 4 | 24 = 24 | YES | |
| ConstantPool | 5 | 61 = 61 | YES | |
| Class | 6 | 15 = 15 | SIZE MATCH | Oracle dispatch table off-by-one (2 bytes) |
| Method | 7 | 140 = 140 | YES | |
| StaticField | 8 | 13 = 13 | YES | |
| RefLocation | 9 | 27 = 27 | YES | |
| Descriptor | 11 | 117 = 117 | YES | |

**Result: 9/10 byte-identical, 1 size-match (Oracle dispatch table off-by-one bug only).**

## Complex Applets (multi-class, inheritance, interfaces, exceptions)

| Applet | BYTE-IDENTICAL | SIZE MATCH | Known Differences |
|--------|---------------|------------|-------------------|
| InheritanceApplet | 9 | 1 | Class (dispatch table) |
| InterfaceApplet | 9 | 1 | Class (dispatch table) |
| ExceptionApplet | 9 | 1 | Class (dispatch table) |
| MultiClassApplet | 9 | 1 | Class (dispatch table) |

## Known Oracle Converter Bug: Class.cap Dispatch Table Off-By-One

### Description

The Oracle converter has an off-by-one error in the `public_virtual_method_table` serialization
within the Class component (JCVM spec §6.9, Table 6-16). For every class entry, Oracle writes
a phantom `0x0000` as the first dispatch table entry and shifts all real method offset entries
right by 2 bytes. The last real entry overflows into the adjacent `package_method_table_base`
and `package_method_table_count` fields.

### Evidence

The bug is 100% consistent across all 7 classes in 4 test applets (verified on JC 3.0.5u3):

```
ExceptionApplet (1 class, pub_count=1):
  OURS:   [0x0022, 0x0000]    ← process @34, then padding
  ORACLE: [0x0000, 0x0022]    ← phantom 0, process @34 displaced

InheritanceApplet (3 classes, each with dispatch table):
  BaseApplet (pub_count=2):
    OURS:   [0x0010, 0x000B] [pkg_base=0x00]
    ORACLE: [0x0000, 0x0010] [0x000B displaced into pkg_base]

  MiddleApplet (pub_count=2):
    OURS:   [0x001C, 0x0024] [pkg_base=0x00]
    ORACLE: [0x0000, 0x001C] [0x0024 displaced]

  InheritanceApplet (pub_count=3):
    OURS:   [0x003F, 0x001C, 0x0084] [0x0000]
    ORACLE: [0x0000, 0x003F, 0x001C] [0x0084 displaced]
```

### Spec Reference

JCVM 3.0.5 specification, §6.9 Table 6-16 defines the `class_info` structure:
```
u1 public_method_table_base    // first virtual method token in the table
u1 public_method_table_count   // number of entries
u2[public_method_table_count] public_virtual_method_table  // method offsets
u1 package_method_table_base
u1 package_method_table_count
```

The `public_virtual_method_table` array should contain exactly `public_method_table_count`
entries, with `table[i]` being the method offset for token `(base + i)`. Oracle writes
`count+1` values starting with `0x0000`, displacing the last entry into the subsequent
`package_method_table_base`/`package_method_table_count` fields.

### Impact

This bug has **no runtime impact** on correctly implemented JCVMs because:
1. The dispatch table size is still `pub_count`, so the extra entry is ignored
2. The overflow into `pkg_base`/`pkg_count` only matters if package-visible virtual methods
   exist (which is rare in practice — Java Card applets typically don't use package-private methods)
3. All deployed Oracle-produced CAP files contain this bug, so JCVMs must be tolerant of it

### Our Implementation

By default, our converter produces the **spec-correct** format. The test suite verifies
that all non-dispatch-table bytes are identical to Oracle, and that the dispatch table
contains the same set of method offset values (just in different positions due to the
off-by-one).

### Oracle Compatibility Mode

Enabling `oracleCompatibility(true)` replicates the Oracle dispatch table bug, producing
**fully byte-identical** Class.cap output. This is useful for environments that validate
CAP files against Oracle reference output.

```java
Converter converter = Converter.builder()
    .classesDirectory(classesDir)
    .packageName("com.example")
    .oracleCompatibility(true)  // replicate Oracle dispatch table bug
    .build();
```

Maven plugin:
```xml
<configuration>
    <oracleCompatibility>true</oracleCompatibility>
</configuration>
```

Or via property: `-Djavacard.oracleCompatibility=true`

**Results with Oracle compatibility mode enabled:**

| Applet | BYTE-IDENTICAL | SIZE MATCH | Notes |
|--------|---------------|------------|-------|
| TestApplet (all 8 JC versions) | 10 | 0 | Fully identical |
| InheritanceApplet | 10 | 0 | Fully identical |
| InterfaceApplet | 10 | 0 | Fully identical |
| ExceptionApplet | 10 | 0 | Fully identical |
| MultiClassApplet | 10 | 0 | Fully identical |

## Per-Version Comparison Status (TestApplet)

The converter supports 8 JavaCard specification versions. Binary compatibility is
verified by comparing our output against Oracle SDK reference CAP files for each version.

| JC Version | CAP Format | Reference File | Byte-Identical | Size-Match | Size-Diff |
|------------|-----------|----------------|----------------|------------|-----------|
| 2.1.2 | 2.1 | `oracle-TestApplet-jc212.cap` | 9 | 1 | 0 |
| 2.2.1 | 2.1 | `oracle-TestApplet-jc221.cap` | 9 | 1 | 0 |
| 2.2.2 | 2.1 | `oracle-TestApplet-jc222.cap` | 9 | 1 | 0 |
| 3.0.3 | 2.1 | `oracle-TestApplet-jc303.cap` | 9 | 1 | 0 |
| 3.0.4 | 2.1 | `oracle-TestApplet-jc304.cap` | 9 | 1 | 0 |
| 3.0.5 | 2.1 | `oracle-TestApplet-jc305.cap` | 9 | 1 | 0 |
| 3.1.0 | 2.3 | `oracle-TestApplet-jc310.cap` | 9 | 1 | 0 |
| 3.2.0 | 2.3 | `oracle-TestApplet-jc320.cap` | 9 | 1 | 0 |

**All 8 versions: 9/10 byte-identical, 1 size-match, 0 size-diff.**
Class.cap is size-match due to the Oracle dispatch table off-by-one bug only.

### PUTFIELD_x_THIS Version-Specific Optimization

Oracle converters prior to JC 3.0.5 apply `GETFIELD_x_THIS` optimization
(folds `ALOAD_0 + GETFIELD_x` → `GETFIELD_x_THIS`) but do **not** apply the
analogous `PUTFIELD_x_THIS` optimization. Starting with JC 3.0.5, Oracle
applies both optimizations.

Our converter matches this behavior per version:
- **JC 2.1.2–3.0.4**: Only `GETFIELD_x_THIS` optimization (no `PUTFIELD_x_THIS`)
- **JC 3.0.5+**: Both `GETFIELD_x_THIS` and `PUTFIELD_x_THIS` optimizations

### Import.cap Version-Specific Behavior

- **JC 2.1.2, 2.2.1**: Import.cap contains 1 package (javacard.framework only,
  14 bytes) — matching Oracle's reference output.
- **JC 2.2.2+**: Import.cap contains 2 packages (javacard.framework + java.lang,
  24 bytes) — matching Oracle's reference output.

## Oracle JCVM Runtime Validation

Our generated CAP files have been validated on Oracle's reference JCVM implementation
using two independent Oracle tools.

### Oracle `verifycap` — Structural Verification

The `verifycap` tool from Oracle JavaCard SDK 3.0.5u3 performs static analysis of CAP
file structure, verifying internal consistency, reference integrity, and spec compliance.

| Applet | Errors | Warnings | Result |
|--------|--------|----------|--------|
| TestApplet | 0 | 0 | PASS |
| MultiClassApplet | 0 | 0 | PASS |
| InterfaceApplet | 0 | 0 | PASS |
| ExceptionApplet | 0 | 0 | PASS |
| InheritanceApplet | 0 | 0 | PASS |
| VisibilityApplet | 0 | 0 | PASS |
| CryptoApplet | 1 | 0 | FAIL (CP ordering — type mismatch in virtual method ref) |

### Oracle `cref` — Runtime Execution on JCVM Emulator

The `cref` (C-language Java Card Reference Implementation) from Oracle JavaCard SDK 2.2.2
is Oracle's official card emulator. CAP files are loaded via the proprietary installer
protocol (component-by-component download over TLP-224), then instantiated and selected.

| Applet | Load (10 components) | CREATE | SELECT | APDU Test | Result |
|--------|---------------------|--------|--------|-----------|--------|
| TestApplet | All `9000` | `9000` | `9000` | PUT `DEADBEEF` → `9000` | PASS |
| MultiClassApplet | All `9000` | `9000` | `9000` | — | PASS |
| InterfaceApplet | All `9000` | `9000` | `9000` | — | PASS |
| ExceptionApplet | All `9000` | `9000` | `9000` | — | PASS |
| InheritanceApplet | All `9000` | `9000` | `9000` | — | PASS |

**Setup**: cref runs in a Docker container (`i386/debian:bullseye-slim` + 32-bit ELF binary).
Oracle `scriptgen` converts our CAP to APDU install scripts, `apdutool` sends them to cref
via TCP on port 9025.

### Validation Summary

Three independent levels of validation confirm our CAP files are correct:

1. **Static verification** (`verifycap`) — structural integrity of all 10 components (6/7 applets pass; CryptoApplet fails due to CP ordering)
2. **JCVM loading** (`cref` installer) — component download, linking, and class resolution
3. **Runtime execution** (`cref` APDU) — bytecode execution, object creation, method dispatch

## Oracle Compatibility Mode: Implementation Details

Achieving byte-identical output for multi-class packages (e.g., MultiClassApplet) required
replicating three Oracle-specific behaviors beyond the dispatch table bug:

### Depth-First Constructor Chaining

Oracle processes constructors depth-first: when translating class A's `<init>` and
encountering `invokespecial B.<init>`, Oracle immediately translates B's constructor
before continuing with A's remaining bytecodes. This affects CP entry creation order.

Our implementation uses a callback mechanism on `ReferenceResolver`: when an internal
static method ref for `<init>` is created, the callback triggers immediate translation
of the target class's constructor.

### Instance Field Reference Ordering by Class Token

Oracle orders instance field CP entries by the declaring class's token value, not by
CP creation order. Our `reorderInstanceFieldsFirst()` accepts a `fieldClassTokens` map
and sorts internal field refs by class token (external refs preserve original order).

### Descriptor Type Table Registration Order

Oracle registers type descriptors in a specific order: CP entry types first, then method
parameter/return types, then field types. Our `buildTypeTable()` follows this same
registration order to produce identical type_offset values.

## Test Strategy

The test suite enforces:

1. **Default mode** (`OracleReferenceComparisonTest`): 9/10 components byte-identical for
   all 5 applets across all 8 JC versions. Class.cap is size-match only due to Oracle's
   dispatch table off-by-one bug.
2. **Oracle compatibility mode** (`OracleCompatModeTest`): 10/10 components byte-identical
   for all 5 applets. Also verifies all 8 JC versions for TestApplet.
3. **Per-version comparison** (`PerVersionOracleComparisonTest`): 9/10 byte-identical for
   TestApplet across all 8 JC versions (Class.cap size-match).

Any difference beyond the documented Class.cap dispatch table bug will cause test failure.

## Verification

### Running Tests

```bash
# All converter tests (reference comparison tests are skipped if refs are absent)
./mvnw test -pl converter

# Primary comparison (JC 3.0.5) — requires Oracle reference files
./mvnw test -pl converter -Dtest=OracleReferenceComparisonTest

# Oracle compatibility mode (strict byte-identical)
./mvnw test -pl converter -Dtest=OracleCompatModeTest

# All 8 versions
./mvnw test -pl converter -Dtest=PerVersionOracleComparisonTest
```

### Generating Oracle Reference Files

Reference CAP files must be generated locally using the Oracle JavaCard SDK (OTN license).
They are not distributed with the project.

```bash
# 1. Clone Oracle SDKs (maintained by community, use at your own discretion)
git clone https://github.com/martinpaljak/oracle_javacard_sdks.git build/oracle-sdks

# 2. Generate reference CAP files for all test applets and JC versions
./build/generate-oracle-refs.sh

# Generated files appear in converter/src/test/resources/reference/
```

### Oracle cref Runtime Validation (requires Docker)

```bash
# Build cref Docker image (one-time setup, uses JC 2.2.2 SDK cref binary)
docker build --platform linux/386 -t jcref:2.2.2 /path/to/cref-docker/

# Verify CAP files with Oracle verifycap (no Docker required)
java -cp "$JC305_SDK/lib/*" com.sun.javacard.offcardverifier.Verifier \
  "$JC305_SDK/api_export_files/java/lang/javacard/lang.exp" \
  "$JC305_SDK/api_export_files/javacard/framework/javacard/framework.exp" \
  /path/to/your.cap
```
