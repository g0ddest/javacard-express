# JavaCard Express :: Converter

Converts the `.class` files of one Java package into a Java Card CAP file and an export file, without the Oracle
Java Card SDK. Written from the Java Card Virtual Machine Specification (3.0.5 and 3.1); the code cites the
sections it implements. Most users run it through the [Maven plugin](../maven-plugin/README.md).

- **Targets** Java Card 2.1.2, 2.2.1, 2.2.2, 3.0.3, 3.0.4, 3.0.5 (CAP format 2.1) and 3.1.0, 3.2.0 (compact CAP
  format 2.3).
- **Linking** against the standard API packages of the selected target from built-in data, with per-version
  checks, and against other packages from their export files.
- **Fail-closed**: code outside the Java Card language subset, references the target API lacks, invalid AIDs,
  JCVM limits and features it does not implement (Java Card RMI) are reported as a `ConverterException` naming
  the class, method and source line where they apply. The converter does not write a CAP file it knows to be
  wrong.
- **Deterministic**: the same input gives the same bytes.
- Runs on **JDK 25 or newer** (it reads class files with the JDK ClassFile API, JEP 484); no other dependencies.

How the output is verified, and how it compares with Oracle's converter:
[BINARY_COMPATIBILITY.md](BINARY_COMPATIBILITY.md).

## Usage

```java
import java.nio.file.Files;
import java.nio.file.Path;
import name.velikodniy.jcexpress.converter.Converter;
import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.converter.ConverterResult;
import name.velikodniy.jcexpress.converter.JavaCardVersion;

ConverterResult result = Converter.builder()
    .classesDirectory(Path.of("target/classes"))
    .packageName("com.example.myapplet")
    .packageAid("A00000006212")
    .packageVersion(1, 0)
    .applet("com.example.myapplet.MyApplet", "A0000000621201")
    .javaCardVersion(JavaCardVersion.V3_0_4)
    .build()
    .convert();                                  // throws ConverterException

Files.write(Path.of("myapplet.cap"), result.capFile());
result.warnings().forEach(System.err::println);
```

A rejected conversion reports what the failing check found. Rule violations (language subset, static
initializers, tokens, RMI) come as a list of `Violation`s; AID, link and other errors only as the message:

```java
try {
    converter.convert();
} catch (ConverterException e) {
    if (e.violations().isEmpty()) {
        System.err.println(e.getMessage());          // AID, link, limit and other errors
    } else {
        e.violations().forEach(System.err::println); // class, method, bytecode index, source file and line, reason
    }
}
```

The checks run in stages (subset, links, tokens, ...), so fixing the reported problems can bring up those of a
later stage; the `int` rules of a method are checked once the method has no other subset violation.

### Builder options

| Method | Default | Description |
|--------|---------|-------------|
| `classesDirectory(Path)` | required | Directory with the compiled classes |
| `packageName(String)` | required | Package to convert, `com.example` (or `com/example`); the unnamed package is rejected |
| `packageAid(String)` / `packageAid(byte[])` | generated | Package AID, 5 to 16 bytes. Without it an unregistered development AID `F0` + 7 bytes of SHA-1(package name) is used and a warning is returned |
| `packageVersion(int, int)` | 1.0 | Package version, each number 0 to 255 |
| `applet(String, String)` / `applet(String, byte[])` | none | Applet class and AID. The AID must start with the package RID, be unique and differ from the package AID (JCVM 3.1 §4.2.2, §6.6); the class must be a concrete `javacard.framework.Applet` with `static install(byte[], short, byte)` |
| `javaCardVersion(JavaCardVersion)` | `V3_0_5` | Target platform: CAP format, API versions in the Import component, available API. Use the card's version or an older one: a card links a CAP file only when it has the imported API versions (JCVM 3.1 §4.5.2) |
| `importExportFile(Path)` | none | Export file of an imported package; wins over the export path and the built-in data |
| `exportPath(Path...)` | none | Directories or JARs holding `<package>/javacard/<last>.exp`, for example a kit's `api_export_files` or a library built earlier |
| `supportInt32(boolean)` | false | Allows `int` (the card must support it); the header's ACC_INT flag is set only if the code uses `int` |
| `generateExport(boolean)` | true | Writes the Export component, which other packages on the card need to link against this one, when the package has something to export (JCVM 3.1 §6.2, §6.13): the public classes and interfaces of a library package, the public shareable interfaces of an applet package. `false` omits it; a library package converted so cannot be used by any other package |
| `oracleCompatibility(boolean)` | — | Deprecated, no effect (see [BINARY_COMPATIBILITY.md](BINARY_COMPATIBILITY.md#correction-there-is-no-oracle-dispatch-table-off-by-one-bug)) |

### ConverterResult

| Method | Description |
|--------|-------------|
| `capFile()` | CAP file (JAR) |
| `exportFile()` | Export file (`.exp`), always produced. A library package describes all its public classes and interfaces; a package with applets only its public shareable interfaces (JCVM 3.1 §5.5). Store it as `<package>/javacard/<last>.exp` so that an export path finds it |
| `warnings()` | Non-fatal findings, for example a generated package AID |
| `capSize()` | CAP file size in bytes |

### Target versions

| `JavaCardVersion` | CAP format | Export file format | `javacard.framework` |
|-------------------|------------|--------------------|----------------------|
| `V2_1_2` | 2.1 | 2.1 | 1.0 |
| `V2_2_1` | 2.1 | 2.1 | 1.2 |
| `V2_2_2` | 2.1 | 2.1 | 1.3 |
| `V3_0_3` | 2.1 | 2.1 | 1.4 |
| `V3_0_4` | 2.1 | 2.1 | 1.5 |
| `V3_0_5` | 2.1 | 2.1 | 1.6 |
| `V3_1_0` | 2.3 (compact) | 2.3 | 1.8 |
| `V3_2_0` | 2.3 (compact) | 2.3 | 1.9 |

## What the converter accepts

The Java Card language subset (JCVM 3.1 §2.2) is checked before translation:

- Types: `boolean`, `byte`, `short`, one-dimensional arrays, classes and interfaces; `int` only with
  `supportInt32(true)`. Without it, `int` intermediate values that can change a result (compared, divided,
  shifted right, used as an index, switch key, argument or return value) are rejected, for example
  `if (a + b > 100)` with `short` operands: cast to `short` or `byte`.
- Array indices and sizes must be `short` values: `buf[(short) (off + 1)]` is fine, `buf[off + 1]` with a
  `short off` is not.
- No `long`, `float`, `double`, `char`, `String` constants, multi-dimensional arrays, `synchronized`, enums,
  records, lambdas or `assert`; Java SE classes beyond the Java Card `java.lang` subset are link errors.
- Static initializers are evaluated at conversion time (§2.2.4.6): primitive constants and, in packages with
  applets, arrays of primitive constants; anything else is an error. Interface fields must be compile-time
  constants, and so must every `static final` field of a primitive type: a blank final assigned in a static
  initializer is an error (§2.2.4.6, §6.11, §6.14).
- Access rules (§2.2.1.1.6): a public class must not expose package-visible types in its public or protected
  API, and a public or protected method must not override a package-visible one.
- Private methods of nestmates (`javac --release 11` and later) are handled, and so are class files without a
  `StackMapTable` (ProGuard `-dontpreverify`, ASM `COMPUTE_MAXS`).
- `finally` blocks compiled into `jsr`/`ret` subroutines (class files of version 49 and older, for example from
  ECJ with `-target` 1.4 or lower or from javac 1.3) are supported (§2.3.2.2): the converter inlines the
  subroutines when it reads the class files, as later compilers do, so the CAP file contains no subroutines.
  Nested `try` statements in `finally` blocks are included: a nested `finally` block may leave for the code of
  the enclosing one (its `catch`, its loop). Messages about such a method give bytecode indexes of the inlined
  code and the original source lines. Rejected with the reason: subroutine shapes compilers do not write (a
  return to an outer subroutine, recursion, an overwritten return address), a method whose inlined code would
  exceed the 65535 bytes a class file method holds (JVMS 4.7.3), and `jsr`/`ret` in class files of version 51
  and later, which JVMS 4.9.1 forbids.
- `equals` called on a reference of an interface type becomes an `invokevirtual` of `java.lang.Object.equals`
  (JLS 9.2, §7.5.57) unless the interface declares the method.
- The applets of a package are all multiselectable (`javacard.framework.MultiSelectable`) or none is (§2.2.5).
- Java Card RMI is not supported: a package that defines a remote interface or class (`java.rmi.Remote`,
  §2.2.6) is rejected, because the converter does not write the remote information of CAP format 2.2.
- JCVM limits are errors: more than 255 public classes, 15 interfaces per class, 128 public or package virtual
  methods, 255 instance field cells, 255 exception handlers per package, methods over 32767 bytes, and others.

An abstract class may leave the methods of its interfaces to its subclasses: the converter declares them
`public abstract` in that class (the Class component maps every interface method to a virtual method of the
class, §6.9.2.5), which gives the same CAP and export files as writing the declarations out.

## Pipeline

1. **Load**: read the package's class files (`input/`) and inline `jsr`/`ret` subroutines (`SubroutineInliner`).
2. **Scan**: language subset (`check/`), imports from built-in data and export files, link check against the
   target API, AID and applet rules (`resolve/`, `AidRules`, `AppletRules`).
3. **Tokens**: class, method and field tokens per JCVM 3.1 §4.3.7 (`token/`).
4. **Static initialization**: `<clinit>` evaluated into the Static Field image (`clinit/`).
5. **Translation**: JVM bytecode to JCVM bytecode, with branch relaxation and the `_this` field forms
   (`translate/`).
6. **CAP assembly**: Header, Directory, Applet, Import, Constant Pool, Class, Method, Static Field, Reference
   Location, Export and Descriptor components (`cap/`).
7. **Export file** (`exp/`).

## Building and testing

```bash
./mvnw test -pl converter -am     # always with -am: the tests compile applets against the module's API stubs
```

Without `-am` Maven may take an older `javacard-express-api` from the local repository. Tests that need an
Oracle SDK or Oracle reference CAP files are skipped unless those are installed locally; see
[tools/oracle/README.md](../tools/oracle/README.md).
