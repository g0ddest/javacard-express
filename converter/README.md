# JavaCard Express :: Converter

Clean-room JavaCard CAP file converter. Compiles `.class` files to `.cap` files
without requiring the Oracle JavaCard SDK.

## Features

- **No Oracle SDK required** — standalone converter using only the JCVM 3.0.5 specification
- **Multi-version support** — targets JavaCard 2.1.2, 2.2.1, 2.2.2, 3.0.3, 3.0.4, 3.0.5, 3.1.0, and 3.2.0
- **Fluent API** — `Converter.builder()` with sensible defaults
- **Maven plugin** — zero-config `mvn package` workflow
- **Built-in exports** — javacard.framework, javacard.security, javacardx.crypto, java.lang
- **Binary compatible** — verified against Oracle SDK reference output

## Quick Start

### Library API

```java
import name.velikodniy.jcexpress.converter.*;

ConverterResult result = Converter.builder()
    .classesDirectory(Path.of("target/classes"))
    .packageName("com.example.myapplet")
    .packageAid("A00000006212")
    .packageVersion(1, 0)
    .applet("com.example.myapplet.MyApplet", "A0000000621201")
    .build()
    .convert();

Files.write(Path.of("MyApplet.cap"), result.capFile());
Files.write(Path.of("MyApplet.exp"), result.exportFile());
```

### Maven Plugin

```xml
<plugin>
    <groupId>name.velikodniy</groupId>
    <artifactId>javacard-express-maven-plugin</artifactId>
    <version>${jcexpress.version}</version>
    <configuration>
        <packageAid>A00000006212</packageAid>
    </configuration>
</plugin>
```

Run `mvn package` — the plugin auto-discovers applets and produces `.cap` and `.exp`
files in `target/`.

## API Reference

### Converter.Builder

| Method | Default | Description |
|--------|---------|-------------|
| `classesDirectory(Path)` | — | Directory with compiled `.class` files (required) |
| `packageName(String)` | — | Java package name in dot notation (required) |
| `packageAid(String)` | auto (SHA-1) | Package AID as hex string |
| `packageVersion(int, int)` | 1.0 | Package version (major, minor) |
| `applet(String, String)` | — | Register applet class name + AID |
| `javaCardVersion(JavaCardVersion)` | V3_0_5 | Target JavaCard specification version |
| `supportInt32(boolean)` | false | Enable 32-bit integer support (ACC_INT) |
| `generateExport(boolean)` | false | Generate Export component for library packages |
| `importExportFile(Path)` | — | External package `.exp` file for cross-package linking |

### JavaCardVersion

| Enum | CAP Format | Notes |
|------|-----------|-------|
| `V2_1_2` | 2.1 | Earliest supported version |
| `V2_2_1` | 2.1 | Classic cards |
| `V2_2_2` | 2.1 | Classic cards (SIM, banking) |
| `V3_0_3` | 2.1 | Java Card 3.0 Classic |
| `V3_0_4` | 2.2 | Unique CAP format version |
| `V3_0_5` | 2.1 | Default; verified against Oracle SDK 3.0.5u3 |
| `V3_1_0` | 2.3 | Extended API packages |
| `V3_2_0` | 2.3 | Latest supported version |

### ConverterResult

| Method | Description |
|--------|-------------|
| `capFile()` | CAP file bytes (ZIP format) |
| `exportFile()` | Export file bytes (binary format) |
| `warnings()` | List of non-fatal warnings |
| `capSize()` | Total CAP file size in bytes |

## Conversion Pipeline

The converter implements a 7-stage pipeline:

1. **Load** — Scan `.class` files using JDK ClassFile API (JEP 484)
2. **Subset Check** — Verify only JavaCard-allowed types and bytecodes are used
3. **Token Assignment** — Assign JCVM tokens to classes, methods, and fields
4. **Reference Resolution** — Map JVM symbolic references to JCVM numeric tokens
5. **Bytecode Translation** — Convert JVM bytecodes to JCVM instruction set
6. **CAP Generation** — Build all 11 CAP components and package as ZIP
7. **Export Generation** — Create `.exp` file for cross-package linking

## Binary Compatibility

See [BINARY_COMPATIBILITY.md](BINARY_COMPATIBILITY.md) for detailed comparison with
Oracle SDK output.

Summary: 4 of 10 components are byte-identical with Oracle, all 10 have matching sizes
(within 2 bytes for Descriptor). Differences are limited to constant pool entry ordering,
which is implementation-defined and does not affect card loading.

## Building

```bash
# Run converter tests
./mvnw test -pl converter

# Run Oracle reference comparison
./mvnw test -pl converter -Dtest=OracleReferenceComparisonTest

# Full build (all modules)
./mvnw verify -pl '!container'
```

## Project Structure

```
converter/src/main/java/name/velikodniy/jcexpress/converter/
├── Converter.java              # Entry point and builder API
├── ConverterResult.java        # Conversion output record
├── ConverterException.java     # Conversion failure with violations
├── JavaCardVersion.java        # Supported JC spec versions
├── input/                      # Stage 1: Class file parsing
├── check/                      # Stage 2: Subset validation
├── token/                      # Stage 3: Token assignment + export files
├── resolve/                    # Stage 4: Reference resolution
├── translate/                  # Stage 5: Bytecode translation
├── cap/                        # Stage 6: CAP component generation
└── exp/                        # Stage 7: Export file generation
```
