# JavaCard Express :: Maven Plugin

Maven plugin for building JavaCard CAP files. No Oracle SDK required.

> Version 0.3.0 on Maven Central behaves differently in several places; see the [changelog](../CHANGELOG.md).

## Requirements

- **Maven must run on JDK 25 or newer.** The built-in converter reads class files with the JDK
  ClassFile API (`java.lang.classfile`, JEP 484), so the plugin needs a Java 25 runtime
  (`mvn -v` shows which JDK Maven uses). On an older JDK Maven stops with
  "Required Java version 25 is not met". Your applet code is still compiled for an old class-file
  version (`maven.compiler.release` 8, see below), which is what Java Card tools expect.
- **Maven 3.9.0 or newer** (the plugin uses only the stable Maven 3 plugin API).
- No Oracle Java Card SDK, no `JCDK_HOME`.

## Quick Start

A complete `pom.xml` for an applet project that builds the CAP file and tests the applet on the
jCardSim simulator (applet in `src/main/java`, tests in `src/test/java`):

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>hello-applet</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <jcexpress.version>0.4.0</jcexpress.version>
        <!-- Java Card supports a subset of Java: compile applets to Java 8 class files -->
        <maven.compiler.release>8</maven.compiler.release>
        <!-- Tests run on JDK 25 (the toolkit's class files are Java 25): compile them for Java 25 -->
        <maven.compiler.testRelease>25</maven.compiler.testRelease>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <!-- 1. Test runtime first: javacard-express-core brings jCardSim, which executes the
                javacard.* API in the tests -->
        <dependency>
            <groupId>name.velikodniy</groupId>
            <artifactId>javacard-express-core</artifactId>
            <version>${jcexpress.version}</version>
            <scope>test</scope>
        </dependency>
        <!-- 2. Java Card 3.0.5 API for javac (compile-only stubs: signatures and constants) -->
        <dependency>
            <groupId>name.velikodniy</groupId>
            <artifactId>javacard-express-api</artifactId>
            <version>${jcexpress.version}</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.14.2</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <version>3.27.7</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.4</version>
                <configuration>
                    <!-- The stubs and jCardSim define the same javacard.* classes: keep the
                         compile-only stubs off the test runtime -->
                    <classpathDependencyExcludes>
                        <classpathDependencyExclude>name.velikodniy:javacard-express-api</classpathDependencyExclude>
                    </classpathDependencyExcludes>
                </configuration>
            </plugin>
            <plugin>
                <groupId>name.velikodniy</groupId>
                <artifactId>javacard-express-maven-plugin</artifactId>
                <version>${jcexpress.version}</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>build</goal>
                        </goals>
                    </execution>
                </executions>
                <configuration>
                    <packageAid>A00000006212</packageAid>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

The applet, `src/main/java/com/example/hello/HelloWorldApplet.java`:

```java
package com.example.hello;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/** Minimal HelloWorld applet: CLA=80 INS=01 returns "Hello". */
public class HelloWorldApplet extends Applet {

    private static final byte[] HELLO = {'H', 'e', 'l', 'l', 'o'};

    public static void install(byte[] bArray, short bOffset, byte bLength) {
        new HelloWorldApplet().register();
    }

    @Override
    public void process(APDU apdu) {
        if (selectingApplet()) {
            return;
        }
        byte[] buf = apdu.getBuffer();
        if (buf[ISO7816.OFFSET_CLA] != (byte) 0x80) {
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
        }
        switch (buf[ISO7816.OFFSET_INS]) {
            case 0x01:
                Util.arrayCopyNonAtomic(HELLO, (short) 0, buf, (short) 0, (short) HELLO.length);
                apdu.setOutgoingAndSend((short) 0, (short) HELLO.length);
                return;
            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }
}
```

Its test, `src/test/java/com/example/hello/HelloWorldAppletTest.java`:

```java
package com.example.hello;

import name.velikodniy.jcexpress.InstallApplet;
import name.velikodniy.jcexpress.JavaCardTest;
import name.velikodniy.jcexpress.SmartCardSession;
import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;

@JavaCardTest
@InstallApplet(HelloWorldApplet.class)          // installed before each test, deleted after it
class HelloWorldAppletTest {

    @Test
    void returnsHello(SmartCardSession card) {
        assertThat(card.send(0x80, 0x01))
                .isSuccess()
                .dataAsString().isEqualTo("Hello");
    }
}
```

Run `mvn package`: after compilation the plugin discovers the applet and writes
`target/hello-applet-1.0-SNAPSHOT.cap`, then the test runs on jCardSim.

- The `<executions>` block is required: it binds the `build` goal to its default phase,
  `process-classes`, right after compilation. Without it Maven never runs the plugin and no CAP
  file is produced. Code outside the Java Card subset therefore fails the build before the tests
  run.
- `javacard-express-api` contains compile-only stubs of the Java Card API (signatures and
  constants, no implementation); jCardSim contains the executable classes of the same names. Declare
  `javacard-express-core` before `javacard-express-api` (IDE test runners follow the POM order)
  and keep the `classpathDependencyExcludes` entry (it makes Maven test runs independent of the
  order, which parent POMs or BOM imports can change). When the stubs win, jCardSim fails with
  `RuntimeException: Internal reflection error`.
- A build without tests only needs `javacard-express-api` and the plugin.
- `maven.compiler.release` 8 also applies to test sources unless `maven.compiler.testRelease` is set. The
  tests run on JDK 25 with the toolkit, whose class files are Java 25, so compile them for Java 25 and they
  can use the current Java API (`Path.of`, `var`, ...); only the applet code must stay at Java 8.
- The CAP file targets Java Card 3.0.5, the default `javaCardVersion`. A card links a CAP file only when it
  has every imported API package with the same major and an equal or higher minor version (JCVM 3.1 §4.5.2),
  so for a real card set `<javaCardVersion>` in the plugin's `<configuration>` to the card's Java Card
  version or an older one. A 3.0.5 CAP file imports `javacard.framework` 1.6: the Java Card 3.0.4 card of
  the project's live-card tests (an NXP JCOP 4) refused its first LOAD block with status word `6438`.

### The shortest POM: the applet parent

`javacard-express-applet-parent` sets all of the above (Java 8 applet code, Java 25 tests, the dependencies in
the right order, the stubs kept off the test runtime, the version of the plugin and its `<executions>`, and a check
that Maven runs on JDK 25 or newer) and adds the card test backends (`javacard-express-livecard`, test scope: the same tests
run on a simulated GlobalPlatform card with `-Djcx.backend=simulated-gp` or on a card in a reader with
`-Djcx.backend=livecard`), so a project that can inherit it needs only its coordinates, its package AID and the
plugin's name:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>name.velikodniy</groupId>
        <artifactId>javacard-express-applet-parent</artifactId>
        <version>0.4.0</version>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>hello-applet</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <javacard.packageAid>A00000006212</javacard.packageAid>
    </properties>

    <build>
        <plugins>
            <plugin>
                <groupId>name.velikodniy</groupId>
                <artifactId>javacard-express-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

The same `HelloWorldApplet` and `HelloWorldAppletTest` build and pass with `mvn package`. Every setting can be
overridden in the project's POM.

A project that already has a parent (a corporate one) keeps the explicit POM above and may import the BOM, which
manages the versions of all toolkit artifacts, so that the `<dependency>` entries need no `<version>`. Maven does
not import plugin versions from a BOM: the plugin keeps its `<version>`.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>name.velikodniy</groupId>
            <artifactId>javacard-express-bom</artifactId>
            <version>0.4.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

`A000000062` is used as an example RID throughout this page; use your own registered RID for
real cards (see [AIDs](#aids)).

## How It Works

The `build` goal runs in the `process-classes` phase (once bound with `<executions>`), after
compilation and before the tests, and:

1. Selects the Java package to convert (one package per CAP file)
2. Finds the applets: every non-abstract subclass of `javacard.framework.Applet`, or the
   classes listed in `<applets>`
3. Derives the applet AIDs from the package AID when they are not configured
4. Converts the `.class` files to a `.cap` file with the built-in clean-room converter
5. Writes the CAP file (and the export file) into `target/` and into the classes directory, with
   a build descriptor (see [Output Files and Artifacts](#output-files-and-artifacts))

## Configuration

```xml
<configuration>
    <!-- Package AID (hex). Auto-generated from package name if omitted -->
    <packageAid>A00000006212</packageAid>

    <!-- Java package to convert. Required only when classes are in several packages -->
    <packageName>com.example.myapplet</packageName>

    <!-- Package version <major>.<minor>, each 0..255 (default: 1.0) -->
    <packageVersion>1.0</packageVersion>

    <!-- Target JavaCard version (default: 3.0.5); any other value fails the build -->
    <!-- Valid: 2.1.2, 2.2.1, 2.2.2, 3.0.3, 3.0.4, 3.0.5, 3.1.0, 3.2.0 -->
    <javaCardVersion>3.0.5</javaCardVersion>

    <!-- Enable 32-bit integer support (default: false) -->
    <supportInt32>false</supportInt32>

    <!-- Export component + .exp file (default: automatic, see "Export Files") -->
    <generateExport>true</generateExport>

    <!-- Explicit applet list (optional, auto-discovered if omitted) -->
    <applets>
        <applet>
            <className>com.example.myapplet.MyApplet</className>
            <aid>A0000000621201</aid>
        </applet>
    </applets>

    <!-- Export files of imported packages (optional, see "Imported Packages") -->
    <importExportFiles>
        <importExportFile>path/to/external.exp</importExportFile>
    </importExportFiles>

    <!-- Directories/jars with <package>/javacard/<name>.exp files (optional) -->
    <exportPath>
        <path>path/to/export/files</path>
    </exportPath>

    <!-- Deprecated, will be removed: the Class component always follows JCVM 3.1 §6.9 -->
    <oracleCompatibility>false</oracleCompatibility>

    <!-- Output: <outputDirectory>/<finalName>[-<classifier>].cap and .exp -->
    <outputDirectory>${project.build.directory}</outputDirectory>
    <finalName>${project.build.finalName}</finalName>
    <classifier>wallet</classifier>

    <!-- Attach the .cap (type cap) and .exp (type exp) to the project (default: true) -->
    <attach>true</attach>

    <!-- Compiled classes to convert (default: ${project.build.outputDirectory}) -->
    <classesDirectory>${project.build.outputDirectory}</classesDirectory>

    <!-- Also write the CAP/EXP files and the build descriptor into the classes directory (default: true) -->
    <classesOutput>true</classesOutput>

    <!-- Fail when javacard-express-api or -core has another version than the plugin (default: true) -->
    <checkVersions>true</checkVersions>

    <!-- Skip plugin execution (default: false) -->
    <skip>false</skip>
</configuration>
```

All configuration parameters are also available as Maven properties:

| Property | Default |
|----------|---------|
| `javacard.packageAid` | auto-generated |
| `javacard.packageName` | the only package with classes |
| `javacard.packageVersion` | `1.0` |
| `javacard.version` | `3.0.5` |
| `javacard.supportInt32` | `false` |
| `javacard.generateExport` | automatic (see [Export Files](#export-files)) |
| `javacard.oracleCompatibility` | `false` (deprecated) |
| `javacard.outputDirectory` | `${project.build.directory}` |
| `javacard.finalName` | `${project.build.finalName}` |
| `javacard.classifier` | none |
| `javacard.attach` | `true` |
| `javacard.classesDirectory` | `${project.build.outputDirectory}` |
| `javacard.classesOutput` | `true` |
| `javacard.checkVersions` | `true` |
| `javacard.skip` | `false` |

## Output Files and Artifacts

The goal writes `target/<finalName>.cap` (by default `<artifactId>-<version>.cap`) and, for an
exported package, `target/<finalName>.exp`. Both are attached to the project as artifacts of type
`cap` and `exp`, so `mvn install` / `mvn deploy` publish them next to the jar and other modules
can depend on them (`<type>cap</type>`, `<type>exp</type>`). `<attach>false</attach>` only writes
the files. With `<classifier>` the files are named `<finalName>-<classifier>.cap/.exp` and
attached with that classifier, which lets one module build several CAP files with one execution
per package:

```xml
<executions>
    <execution>
        <id>wallet</id>
        <goals><goal>build</goal></goals>
        <configuration>
            <packageName>com.example.wallet</packageName>
            <packageAid>A00000006212</packageAid>
            <classifier>wallet</classifier>
        </configuration>
    </execution>
    <execution>
        <id>loyalty</id>
        <goals><goal>build</goal></goals>
        <configuration>
            <packageName>com.example.loyalty</packageName>
            <packageAid>A00000006213</packageAid>
            <classifier>loyalty</classifier>
        </configuration>
    </execution>
</executions>
```

The goal also writes into the classes directory (`target/classes`), so the files go into the jar
and are on the class path of the tests and of the modules that depend on this one:

| File | What it is for |
|------|----------------|
| `<package directory>/javacard/<name>.cap` | the CAP file, e.g. `com/example/hello/javacard/hello.cap` |
| `<package directory>/javacard/<name>.exp` | the export file of an exported package, where the converter of an importing package looks for it (JCVM 3.1 &sect;5.2): modules and projects that use the library need no dependency of type `exp`, and `mvn test` works in a multi-module build |
| `META-INF/javacard/<package>.properties` | the build descriptor: package and applet AIDs, `packageVersion`, `javaCardVersion`, `supportInt32`, `groupId:artifactId`. The card test backends of `javacard-express-core` read it, so tests on the simulated GlobalPlatform card or a real card convert the package as this build does |

`<classesOutput>false</classesOutput>` leaves the classes directory alone. When several executions
build the same package (two Java Card targets with classifiers), the first one writes these files.

The goal is thread-safe (`mvn -T`).

## Export Files

A package is *public* (other packages on the card can link against it) when its CAP file has an
Export component; the matching export file (`.exp`) is what the converter of a client package
needs (JCVM 3.1 &sect;6.13, &sect;5.6.1). What a package may export depends on its kind:

- a **library package** (no applets) exports its public classes and interfaces;
- an **applet package** exports only its public *shareable* interfaces (interfaces extending
  `javacard.framework.Shareable`), never the applet class itself.

By default (`generateExport` not set) the plugin exports exactly when there is something to
export: library packages always, applet packages only if they declare a public shareable
interface. Then the CAP file gets the Export component and `target/<artifactId>-<version>.exp`
is written. `<generateExport>false</generateExport>` suppresses both. `true` cannot make an
applet package without shareable interfaces export anything (the Export component would be
empty, which JCVM 3.1 &sect;6.13 forbids); the plugin warns and builds the CAP file without it.
The converter's Java API applies the same rule by default (`Converter.Builder.generateExport`, `true`
unless set to `false`).

## Packages

A CAP file holds exactly one Java package (JCVM 3.1 &sect;4.1.2). When all classes of the module
are in one package, the plugin converts that package. When there are classes in several packages
(a sub-package counts as a separate package), the build fails and lists them: set `<packageName>`
to the package to convert, and add one plugin execution per package if the module really builds
several CAP files. With `<packageName>` set, classes of the other packages are reported as not
converted. Classes in the default (unnamed) package cannot be converted.

## Imported Packages

The Java Card API packages are built into the converter. For any other package the converted
code uses (a library package of your own, the GlobalPlatform API, ...), the converter needs that
package's export file (JCVM 3.1 &sect;4.1.1, chapter 5). The plugin collects export files from:

1. `<importExportFiles>`: individual `.exp` files;
2. dependencies of type `exp` (the export files this plugin attaches; needed only for libraries
   built by an earlier version of the plugin, which did not put them into the jar);
3. `<exportPath>`: directories or jars searched for `<package directory>/javacard/<last name
   component>.exp` (e.g. `org/globalplatform/javacard/globalplatform.exp`), the layout of
   JCVM 3.1 &sect;5.1/&sect;5.2 and of the `api_export_files` directory of Java Card kits;
4. the dependencies themselves, jars and (in a multi-module build) classes directories, that
   contain the export file at that same location. A library built by this plugin has it there,
   so the dependency that gives javac the library's classes is all an applet module needs:

   ```xml
   <dependency>
       <groupId>com.example</groupId>
       <artifactId>counter-lib</artifactId>
       <version>1.0</version>
       <scope>provided</scope>
   </dependency>
   ```

Only the packages the code actually references are looked up in `<exportPath>` and in jars.

## Errors

Code that `javac` accepts but Java Card does not support fails the build with compiler-style
messages, each problem once, pointing at the source line:

```
[ERROR] /…/src/main/java/com/example/bad/BadApplet.java:[16] com.example.bad.BadApplet.process(): long type not supported in JavaCard
```

When the converter cannot resolve a class (for example `java.util.Arrays`, or a class of a
package whose export file is missing), the message says where the class is used and how to
provide the export file.

`java.util.Objects` in such a message usually comes from `javac`, not from your code: it
null-checks the outer instance of an inner (non-static nested) class with
`Objects.requireNonNull` at a qualified creation such as `outer.new Inner()` and, with
`--release 25`, in the constructor of every inner class. Use static nested classes and compile
with `maven.compiler.release` 8.

A package without an export file is reported once, with the places that use it and what to add (for a library
built by an earlier version of the plugin, its `exp` dependency; for a second package of the same module, how to
build it first). Code that needs the `int` type names `<supportInt32>`.

The goal also stops or warns before a build goes wrong without a word:

| Situation | What happens |
|-----------|--------------|
| `javacard-express-api` or `javacard-express-core` has another version than the plugin (a plugin declared without `<version>` is the newest release Maven finds) | the build fails and names the versions; `<checkVersions>false</checkVersions>` turns the check off |
| two executions of one module write the same CAP file or attach the same artifact | the build fails and asks for a `<classifier>` per execution |
| `mvn javacard-express:build` without compiled classes (after `mvn clean`) | the build fails and says to run `mvn package` (a bound execution in a module without sources only warns) |
| `-Djavacard.version=...` while the POM sets `<javaCardVersion>` | a warning: the POM wins over the command line |
| several discovered applets get their AIDs from their class-name order | a warning with the `<applets>` configuration that keeps these AIDs when another applet is added |
| a module with packaging `pom` (a parent) | skipped |

## Applets

Without `<applets>` the plugin registers every applet of the package: each non-abstract class
that extends `javacard.framework.Applet` directly or indirectly (for example through an abstract
base applet, also one from a dependency) and declares
`public static void install(byte[] bArray, short bOffset, byte bLength)` (JCVM 3.1 &sect;6.6).
Abstract base classes are skipped; a concrete subclass of `Applet` without such an `install`
method is skipped with a warning, because the card could not install it. A package without
applets is built as a library package.

Classes listed in `<applets>` are checked before conversion: a class that does not exist in the
converted package, is abstract, does not extend `Applet`, or lacks the `install` method fails the
build with an explanation (instead of a CAP file whose applet entry points at the wrong method).
Applets of the package that are missing from `<applets>` are reported.

## AIDs

An AID is a 5-byte RID (registered application provider identifier) followed by up to 11 bytes
of PIX, 5 to 16 bytes in total (JCVM 3.1 &sect;4.2.1). All applet AIDs of a CAP file must have
the RID of the package AID (&sect;4.2.2.2, &sect;6.6), so the plugin assigns them like this:

- `<packageAid>` configured: an applet without its own `<aid>` gets the package AID followed by
  its 1-based position (applets in class-name order, or in `<applets>` order), e.g.
  `A00000006212` &rarr; `A0000000621201`, `A0000000621202`.
- no `<packageAid>`: a development package AID `F0` + 7 bytes of SHA-1(package name) is used and a
  warning is printed. `F0`-prefixed AIDs are not registered (ISO/IEC 7816-5); configure your own
  RID for real cards.
- a configured applet `<aid>` must have the package RID; AIDs must be hex (`A0:00:00:00:62` and
  spaces are accepted), 5 to 16 bytes, unique within the CAP file, and different from the package
  AID (the CAP file and each applet are distinct AID-named entities, &sect;4.2.2).

## Supported JavaCard Versions

Choose the Java Card version of the target card or an older one: a CAP file for a newer version imports
API package versions the card does not have and does not load (JCVM 3.1 §4.5.2, see the
[Quick Start](#quick-start) notes).

| Version | CAP Format |
|---------|-----------|
| 2.1.2 | 2.1 |
| 2.2.1 | 2.1 |
| 2.2.2 | 2.1 |
| 3.0.3 | 2.1 |
| 3.0.4 | 2.1 |
| 3.0.5 | 2.1 |
| 3.1.0 | 2.3 |
| 3.2.0 | 2.3 |
