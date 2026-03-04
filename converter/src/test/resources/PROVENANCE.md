# Provenance of Oracle-Generated Reference Files

## Purpose

This document records the origin, generation method, and legal rationale for all
Oracle-generated reference files used in the converter's test suite. These files
are used solely for interoperability testing — verifying that our clean-room
converter produces output compatible with the JavaCard platform specification.

## Reference Files Inventory

### CAP Files (Converter Output)

#### Reference Files for All 8 Versions

Oracle reference CAP files are generated for all 8 supported JavaCard specification
versions using the corresponding Oracle SDK converter from the
[martinpaljak/oracle_javacard_sdks](https://github.com/martinpaljak/oracle_javacard_sdks)
repository. All use the same `TestApplet` source applet (`com.example.TestApplet`).

| File | SDK Version | Size | Status |
|------|-------------|------|--------|
| `oracle-TestApplet-jc212.cap` | JC SDK 2.1.2 | 1,890 B | Present |
| `oracle-TestApplet-jc221.cap` | JC SDK 2.2.1 | 2,309 B | Present |
| `oracle-TestApplet-jc222.cap` | JC SDK 2.2.2 | 2,332 B | Present |
| `oracle-TestApplet-jc303.cap` | JC SDK 3.0.3 | 4,408 B | Present |
| `oracle-TestApplet-jc304.cap` | JC SDK 3.0.4 | 4,408 B | Present |
| `oracle-TestApplet-jc305.cap` | JC SDK 3.0.5u3 | 4,406 B | Present |
| `oracle-TestApplet-jc310.cap` | JC SDK 3.1.0 | 4,854 B | Present |
| `oracle-TestApplet-jc320.cap` | JC SDK 3.2.0 | 4,844 B | Present |

**Generation command (JC 3.0.5):**
```
converter -classdir target/test-classes \
          -out CAP \
          -exportpath api_export_files \
          -applet 0xA0:0x00:0x00:0x00:0x62:0x01:0x01:0x01:0x01 com.example.TestApplet \
          com.example 0xA0:0x00:0x00:0x00:0x62:0x01:0x01:0x01 1.0
```

**Source applet:** `TestApplet.java` is our own test code — a minimal JavaCard
applet that exercises field access, method calls, APDU processing, and static
fields. It is NOT derived from Oracle examples.

#### Complex Applet References (JC 3.0.5u3)

Additional reference CAP files are generated for complex test applets that
exercise multi-class packages, interface dispatch, exception handling, and
multi-level inheritance. All are generated using Oracle JC SDK 3.0.5u3.

| File | Applet Package | Classes | Size | Features Tested |
|------|---------------|---------|------|-----------------|
| `oracle-MultiClassApplet.cap` | `com.example.multiclass` | 2 (Helper, MultiClassApplet) | 5,521 B | Internal class refs, cross-class method calls |
| `oracle-InterfaceApplet.cap` | `com.example.iface` | 1 (InterfaceApplet) | 4,523 B | Shareable interface, getShareableInterfaceObject |
| `oracle-ExceptionApplet.cap` | `com.example.exception` | 1 (ExceptionApplet) | 4,479 B | try/catch, exception handler tables |
| `oracle-InheritanceApplet.cap` | `com.example.inherit` | 3 (Base, Middle, Inheritance) | 5,901 B | 3-level inheritance, virtual dispatch tables |

**Source applets:** All source applets (`MultiClassApplet`, `InterfaceApplet`,
`ExceptionApplet`, `InheritanceApplet`, and their helper classes) are entirely
our own test code, NOT derived from Oracle examples or documentation.

## Legal Rationale

### Interoperability Testing (Primary Justification)

The use of Oracle-generated output files for comparison testing is permissible
under the principle of interoperability, which is recognized in copyright law
across multiple jurisdictions:

1. **EU Software Directive (2009/24/EC), Article 6**: Permits decompilation and
   analysis for the purpose of achieving interoperability with independently
   created software. Our converter must produce files compatible with the
   JavaCard platform; comparison with reference output is essential to verify
   this interoperability.

2. **US Fair Use (17 U.S.C. § 107)**: Using small functional output files for
   testing purposes is transformative use. We are not distributing Oracle's
   converter — we are verifying that our independent implementation produces
   compatible results.

3. **Oracle v. Google (US Supreme Court, 2021)**: Established that reimplementing
   an API interface for interoperability purposes is fair use. Our use of Oracle's
   output files as a test oracle is analogous — verifying API-level compatibility.

### Nature of the Files

- **CAP files** are machine-generated binary output produced by compiling our
  own source code. They are compilation artifacts — the copyright belongs to the
  source code author, not the compiler author (same principle as `.class` files
  from `javac`). The CAP file format is defined by the publicly available JCVM
  specification.

- **Our source applets** (TestApplet, MultiClassApplet, etc.) are entirely our
  own code, not derived from Oracle examples or documentation.

### What We Are NOT Doing

- We do NOT distribute Oracle's converter tool or SDK
- We do NOT include Oracle source code in our production code
- We do NOT reverse-engineer Oracle's converter implementation
- We do NOT use Oracle's proprietary algorithms or data structures
- Our converter is a clean-room implementation based solely on the publicly
  available JCVM and JavaCard API specifications

### Minimal Use

Reference CAP files are used exclusively in test scope (`src/test/resources/`).
They are not included in production artifacts (JAR, CAP) distributed to users.
All reference files are compilation artifacts of our own source code, generated
by Oracle's converter tool.

## Clean-Room Implementation Verification

Our converter production source code (`converter/src/main/java/`) contains:

- **No Oracle SDK imports** — all imports are from standard Java, ASM, or our own packages
- **No Oracle-derived algorithms** — all encoding logic is derived from reading
  the publicly available JCVM specification (Oracle document number E57420-17)
- **No copy-pasted code** — implementation was written from specification text,
  not by examining Oracle's converter source code (which is not publicly available)

The JCVM specification is a public document published by Oracle for the purpose
of enabling third-party implementations. Our implementation references specific
specification sections (e.g., "JCVM §6.8.2", "JCVM §7.5.12") to document the
basis for each encoding decision.

## SDK License Considerations

Oracle JavaCard SDK 3.0.5u3 is distributed under the Oracle Technology Network
License Agreement for Oracle Java Card Development Kit. Key provisions:

- Section 1(a) permits use "solely for the purpose of developing, testing, [...]
  applications intended to run on the Java Card platform"
- Our use (generating reference output for interoperability testing of a
  JavaCard-compatible tool) falls within this permitted purpose
- We do not redistribute the SDK itself, only small output files generated
  by the SDK when processing our own source code
