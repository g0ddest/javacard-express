# JavaCard Express :: Maven Plugin

Maven plugin for building JavaCard CAP files. No Oracle SDK required.

## Requirements

- **Maven must run on JDK 25 or newer.** The built-in converter reads class files with the JDK
  ClassFile API (`java.lang.classfile`, JEP 484), so the plugin needs a Java 25 runtime
  (`mvn -v` shows which JDK Maven uses). On an older JDK, JDK 8 included, the build extension stops the build with
  "JavaCard Express needs JDK 25 or newer", and the goal with "Required Java version 25 is not met"; with
  `-Djavacard.setup=false` other Maven commands (`validate`, `help:effective-pom`) still run on such a JDK. Your
  applet code is still compiled for an old class-file version (`maven.compiler.release` 8, see below), which is what
  Java Card tools expect.
- **The javac that compiles the applet code is JDK 25 or newer** when the [javac check](#compile-time-check) runs:
  the check runs inside javac and is Java 25 code. Maven's own javac is (see above); the build extension leaves the
  check out of a compilation that forks to another `javac` or uses a toolchain.
- **Maven 3.9.0 or newer.** The goal uses the stable Maven 3 plugin API (`AbstractMojo`, `MavenProject`,
  `MavenProjectHelper`, JSR-330); the build extension also uses maven-core's `AbstractMavenLifecycleParticipant`
  and the project model. The extension was run with Maven 3.9.0, 3.9.1, 3.9.2, 3.9.4 to 3.9.9, 3.9.11, 3.9.12 and
  3.10.0 on JDK 25; on JDK 8, 11, 21 and 24 it stops the build with the JDK it needs. Maven 4 has not been tested.
- No Oracle Java Card SDK, no `JCDK_HOME`.

## Quick Start

A complete `pom.xml` for an applet project that builds the CAP file and tests the applet on the
jCardSim simulator (applet in `src/main/java`, tests in `src/test/java`): the project's coordinates and the
plugin, declared as a build extension.

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>hello-applet</artifactId>
    <version>1.0-SNAPSHOT</version>

    <build>
        <plugins>
            <plugin>
                <groupId>name.velikodniy</groupId>
                <artifactId>javacard-express-maven-plugin</artifactId>
                <version>0.5.0</version>
                <!-- the plugin sets the project up: compiler, dependencies, the javac check -->
                <extensions>true</extensions>
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

`<extensions>true</extensions>` lets the plugin set the project up after Maven has read the POM and before it
plans the build. It adds what the POM does not configure itself; nothing the POM configures is changed or removed.
It only appends to lists the POM configures (the processor path, the compiler arguments, the Surefire excludes), and
the one setting it adds next to a configured one is the Java 25 of the tests (see below):

- the `build` goal, bound to `process-classes`: right after compilation, so code outside the Java Card subset fails
  the build before the tests run;
- the dependencies `javacard-express-core` (test: jCardSim, which executes the `javacard.*` API in the tests, the JUnit
  Jupiter API with its parameterized tests, and AssertJ; Surefire adds the JUnit engine), `javacard-express-livecard`
  (test: the card test backends, so that the same tests run on a simulated GlobalPlatform card with
  `-Djcx.backend=simulated-gp`) and `javacard-express-api` (provided: the compile-only stubs of the Java Card API),
  the test runtime before the stubs. Their version is the one the project's dependency management gives them (a BOM
  import), else the plugin's; the goal's version check then reports a mismatch with the plugin. A project that
  tests with TestNG and not with JUnit Jupiter, or has its own jCardSim (`jcardsim` under any group id:
  `com.klinec`, `com.licel`, `com.github.licel`), gets only the stubs, and a warning: the JUnit Platform that comes
  with `javacard-express-core` would replace TestNG as Surefire's runner, and a second jCardSim would compete with
  its own. JUnit 4, also from a parent POM, is no such stack: Surefire runs JUnit 4 tests on the JUnit Platform
  through the vintage engine, next to the toolkit's;
- `javacard-express-api` kept off the test class path of Surefire and Failsafe (`classpathDependencyExcludes`): the
  stubs and jCardSim define the same `javacard.*` classes, and only jCardSim's can run;
- the applet code compiled for Java 8 (`maven.compiler.release` 8, with `-Xlint:-options` for the compilation of the
  applet code) when the POM sets no release, source or target version, the tests for Java 25
  (`maven.compiler.testRelease` 25) when it sets no test release, source or target version, and UTF-8 sources when it
  sets no encoding. A release, source or target the POM sets for the whole project (`maven.compiler.release` 11, a
  corporate parent's 17) stays for the applet code but not for the tests: they get Java 25 all the same, because the
  toolkit they run with is Java 25 (set `maven.compiler.testRelease` to choose). A parent that sets a release keeps
  the applet code at that release, so set `maven.compiler.release` to 8 in the project for a Java Card target;
- the [javac check](#compile-time-check) in the compilation of the applet code, with the settings of the `build` goal,
  where it cannot displace anything (see there).

The build log names what was added, and what was left out and why, in one line:
`JavaCard Express <version> set up com.example:hello-applet: ...`, also a javac check that is off (turned off,
skipped with the conversion, annotation processing off); a test runtime it left out is a warning as well.
`-Djavacard.setup=false` (or `<setup>false</setup>`) turns the setup off, `-Djavacard.check=false` (or
`<check>false</check>`) only the javac check. A dependency, a release or a processor path in the POM always wins: the setup adds the missing parts around
it. When the setup of a project fails, the build stops with the project's name and `-Djavacard.setup=false` as the
way out. The setup never adds test runtime settings (system properties, `argLine`, groups, profiles).

- An IDE sees the dependencies, the Java levels and the processor path that the extension adds only if its Maven
  import runs build extensions (Maven's lifecycle participants). IntelliJ IDEA 2026.2 does (after a project switches
  to the extension: reload Maven, and if the toolkit's classes stay unresolved, *File → Invalidate Caches*);
  NetBeans has not been verified, and for Eclipse (m2e) and VS Code it is not known. Where the import does not run
  them, use the POM of [Without the build extension](#without-the-build-extension). When the plugin is declared with
  `<extensions>true</extensions>` but its extension did not run, its goal warns, with the Maven and JDK versions.
- In a multi-module build, declare the plugin with `<extensions>true</extensions>` in the applet modules; a parent
  can manage its version in `<pluginManagement>`, as the applet parent does. Declared in a parent's
  `<build><plugins>`, it is inherited by every module: each jar module, host code included, would be set up and
  its classes checked against the Java Card subset.
- The CAP file targets Java Card 3.0.5, the default `javaCardVersion`. A card links a CAP file only when it
  has every imported API package with the same major and an equal or higher minor version (JCVM 3.1 §4.5.2),
  so for a real card set `<javaCardVersion>` in the plugin's `<configuration>` to the card's Java Card
  version or an older one. A 3.0.5 CAP file imports `javacard.framework` 1.6: the Java Card 3.0.4 card of
  the project's live-card tests (an NXP JCOP 4) refused its first LOAD block with status word `6438`.

### Without the build extension

The same project with everything the extension adds written out, for IDEs and tools that do not run Maven build
extensions (the plugin's goal then logs one line that points to `<extensions>true</extensions>`;
`-Djavacard.setup=false` hides it):

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example</groupId>
    <artifactId>hello-applet</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <jcexpress.version>0.5.0</jcexpress.version>
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
        <!-- 2. The card test backends: the same tests on a simulated GlobalPlatform card
                (-Djcx.backend=simulated-gp) -->
        <dependency>
            <groupId>name.velikodniy</groupId>
            <artifactId>javacard-express-livecard</artifactId>
            <version>${jcexpress.version}</version>
            <scope>test</scope>
        </dependency>
        <!-- 3. Java Card 3.0.5 API for javac (compile-only stubs: signatures and constants) -->
        <dependency>
            <groupId>name.velikodniy</groupId>
            <artifactId>javacard-express-api</artifactId>
            <version>${jcexpress.version}</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.15.0</version>
                <executions>
                    <execution>
                        <id>default-compile</id>
                        <configuration>
                            <compilerArgs>
                                <!-- javac 21+ warns that release 8 is obsolete; the old target is intentional -->
                                <arg>-Xlint:-options</arg>
                            </compilerArgs>
                            <!-- The javac check of the Java Card subset in the compilation of the applet code -->
                            <annotationProcessorPaths>
                                <path>
                                    <groupId>name.velikodniy</groupId>
                                    <artifactId>javacard-express-javac-plugin</artifactId>
                                    <version>${jcexpress.version}</version>
                                </path>
                            </annotationProcessorPaths>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
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
- The `maven-compiler-plugin` block configures the compilation of the applet code (`default-compile`) only: it
  silences javac's warning about release 8 and puts the [javac check](#compile-time-check) on the annotation
  processor path. Settings of the `build` goal other than the defaults go there as processor options, e.g.
  `<arg>-Ajavacard.version=3.0.4</arg>` next to `-Xlint:-options`.

### The shortest POM: the applet parent

`javacard-express-applet-parent` writes out all of the above (Java 8 applet code, Java 25 tests, the dependencies
in the right order with the card test backends of `javacard-express-livecard`, the stubs kept off the test runtime,
the version of the plugin and its `<executions>`), so that an IDE that does not run build extensions sees them too,
manages the plugin as a build extension (which adds the [javac check](#compile-time-check)), checks that Maven runs
on JDK 25 or newer and measures the coverage of the applet code with JaCoCo. A project that can inherit it needs
only its coordinates, its package AID and the plugin's name:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>name.velikodniy</groupId>
        <artifactId>javacard-express-applet-parent</artifactId>
        <version>0.5.0</version>
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

A project that already has a parent (a corporate one) uses the Quick Start POM, or the explicit POM, which may
import the BOM: it manages the versions of all toolkit artifacts, so that the `<dependency>` entries need no
`<version>`. Maven does not import plugin versions from a BOM: the plugin keeps its `<version>`.

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>name.velikodniy</groupId>
            <artifactId>javacard-express-bom</artifactId>
            <version>0.5.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

`A000000062` is used as an example RID throughout this page; use your own registered RID for
real cards (see [AIDs](#aids)).

## How It Works

The `build` goal runs in the `process-classes` phase (once bound, by the build extension or with
`<executions>`), after compilation and before the tests, and:

1. Selects the Java package to convert (one package per CAP file)
2. Finds the applets: every non-abstract subclass of `javacard.framework.Applet`, or the
   classes listed in `<applets>`
3. Derives the applet AIDs from the package AID when they are not configured
4. Converts the `.class` files to a `.cap` file with the built-in clean-room converter
5. Writes the CAP file (and the export file) into `target/` and into the classes directory, with
   a build descriptor (see [Output Files and Artifacts](#output-files-and-artifacts))

## Compile-Time Check

The build extension adds a check to javac's compilation of the applet code: code outside the Java Card language
subset (JCVM 3.1 §2.2) and references to API the target platform does not have (§4.5.2) become compile errors at
their source lines, with the converter's messages, before anything runs on a simulator that would accept them:

```
[ERROR] /…/src/main/java/com/example/bad/BadApplet.java:[7,18] Java Card: long type not supported in JavaCard (JCVM 3.1 §2.2.1.3)
```

- **What is checked**: the package the `build` goal converts (its `packageName`; without one the only package of
  the module, or, the check's own rule, with classes in several packages those that have an applet, where the goal
  would stop and ask for `packageName`), with the goal's `javaCardVersion`, `supportInt32` and `globalPlatformApi`.
  With several executions of the goal the check takes the settings that accept the most: the newest
  `javaCardVersion`, int support when one execution has it, the GlobalPlatform API the converter picks unless every
  execution fixes one, and the packages only when every execution names one. The rules are the converter's, applied
  to the class files javac has just written: the language subset and, when it holds, the references into the Java
  Card API of the target platform, into `org.globalplatform` and into the Java SE library (the
  [javac plugin README](../javac-plugin/README.md#what-is-checked) has the details). The export files the goal
  is given in `<importExportFiles>` and `<exportPath>` reach the check too (as the options `javacard.exportFiles`
  and `javacard.exportPath`), so an API export file of a card vendor counts for both, as long as every execution of
  the goal has the same ones: executions that give a package different files (one per target) leave the check with
  the built-in data, and the log line says so. An entry with an expression Maven resolves only when plugins run
  (`${session.executionRootDirectory}`) goes to the check as written, and the compiler plugin resolves it as the
  goal does; an entry that is not there when javac runs (another module writes it later) the check leaves to the
  conversion, with a note. Dependencies of type `exp` and export files the goal finds on the class path do not
  reach the check. The check reads the class files javac writes, not the goal's `classesDirectory`.
- **What stays with the conversion**: references into other packages (libraries with their own export files),
  static initialization (JCVM 3.1 §2.2.4.6), Java Card RMI (§2.2.6), multiselectable applets (§2.2.5), the applets
  and their `install` methods, the AIDs, the tokens and the limits of the CAP format. The conversion in
  `process-classes` still checks everything.
- **Where it shows**: wherever javac errors show, as long as javac runs with the check on its processor path: in the
  Maven build, and in the build output of an IDE that compiles with javac, with navigation to the line. With the
  Quick Start POM that needs an IDE whose Maven import runs build extensions, as IntelliJ IDEA 2026.2 does: its own
  build runs the check and shows `java: Java Card: ...` at the line (IDEA hands annotation processors a wrapper of
  javac's environment, which the check sees through). As you type, the
  [IntelliJ IDEA plugin](../intellij-plugin/README.md) marks the same errors in the editor. Eclipse and VS Code,
  which compile with the Eclipse compiler, do not run it; NetBeans has not been verified.
- A source with errors gets no class files, as with any compile error, so that the next build compiles it again.
- The tests are not checked: the check is added to the compilation of the main sources only, and it skips a
  compilation whose output is a test output (`target/test-classes`).
- `-Djavacard.check=false` (or `<check>false</check>`) turns it off, and so does `-Djavacard.skip=true` (or
  `<skip>true</skip>`), which skips the conversion the check stands for; the setup's log line says which.
- The goal's settings reach javac as processor options; one the project passes itself (in `<compilerArgs>` or
  `<compilerArgument>`) wins.

The check is the jar `javacard-express-javac-plugin` on javac's annotation processor path, in the compilation of
the main sources (`default-compile`); it claims no annotation. The extension adds it only where it cannot displace
anything:

- next to the project's own `annotationProcessorPaths` (and to `annotationProcessors` when that lists the
  project's processors by name);
- alone, in a project that runs no annotation processors.

It leaves the check out, and says why in its log line, when the project runs processors from its class path
(`<annotationProcessors>` without a processor path, `<proc>full</proc>` or `only`, or `-processor`,
`-processorpath`, `-proc:full` in `compilerArgs`): a processor path would hide the class path from javac, so the
project's processors would stop running. To get both, list them and `javacard-express-javac-plugin` (in the
plugin's version) in `annotationProcessorPaths` of `default-compile`. A project that names its processors with
`-processor` and has `annotationProcessorPaths` gets the check's jar on that path, where it changes nothing until it
is named: add `name.velikodniy.jcexpress.javac.JavaCardCheckProcessor` to the `-processor` list to run the check,
as the log line says. It also leaves the check out when the compilation forks to another `javac` (`<fork>` with
`<executable>`) or uses a toolchain, because the check needs javac of JDK 25 or newer; with such a javac, add it as
below. A project with `<proc>none</proc>` or `maven.compiler.proc=none` keeps annotation processing off and so has
no check.

Without the extension, the POM of [Without the build extension](#without-the-build-extension) has it: its
`maven-compiler-plugin` block puts the jar on the processor path of `default-compile`. The settings of the `build`
goal that are not the defaults go there as processor options: `-Ajavacard.version`, `-Ajavacard.supportInt32`,
`-Ajavacard.globalPlatformApi`, `-Ajavacard.packageName` (several packages separated by commas),
`-Ajavacard.exportFiles` and `-Ajavacard.exportPath` (absolute paths separated by the platform's path separator),
and `-Ajavacard.check`. See the [javac plugin README](../javac-plugin/README.md).

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

    <!-- Version of org.globalplatform the CAP file imports, 1.0 to 1.8 (default: the lowest version -->
    <!-- that has everything the code uses, see "The GlobalPlatform API") -->
    <globalPlatformApi>1.5</globalPlatformApi>

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

    <!-- Skip plugin execution, and with it the javac check (default: false) -->
    <skip>false</skip>

    <!-- With <extensions>true</extensions>: set the project up (default: true, see "Quick Start") -->
    <setup>true</setup>

    <!-- With <extensions>true</extensions>: add the javac check (default: true, see "Compile-Time Check") -->
    <check>true</check>
</configuration>
```

The parameters other than `<applets>`, `<importExportFiles>` and `<exportPath>` are also available as Maven
properties:

| Property | Default |
|----------|---------|
| `javacard.packageAid` | auto-generated |
| `javacard.packageName` | the only package with classes |
| `javacard.packageVersion` | `1.0` |
| `javacard.version` | `3.0.5` |
| `javacard.globalPlatformApi` | the lowest sufficient version |
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
| `javacard.setup` | `true` |
| `javacard.check` | `true` |

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
| `META-INF/javacard/<package>.properties` | the build descriptor: package and applet AIDs, `packageVersion`, `javaCardVersion`, `supportInt32`, `globalPlatformApi` when it is set, `groupId:artifactId`. The card test backends of `javacard-express-core` read it, so tests on the simulated GlobalPlatform card or a real card convert the package as this build does |

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

The Java Card API packages and the GlobalPlatform Card API package `org.globalplatform` are built
into the converter. For any other package the converted code uses (a library package of your own,
another GlobalPlatform package such as `org.globalplatform.upgrade`, a card vendor's API, ...), the
converter needs that package's export file (JCVM 3.1 &sect;4.1.1, chapter 5). The plugin collects
export files from:

1. `<importExportFiles>`: individual `.exp` files;
2. dependencies of type `exp` (the export files this plugin attaches; needed only for libraries
   built by an earlier version of the plugin, which did not put them into the jar);
3. `<exportPath>`: directories or jars searched for `<package directory>/javacard/<last name
   component>.exp` (e.g. `org/globalplatform/upgrade/javacard/upgrade.exp`), the layout of
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

### The GlobalPlatform API

An applet that uses `GPSystem`, a Security Domain's `SecureChannel` or another type of
`org.globalplatform` needs no export file: the converter has the linking data of the package
versions 1.0 to 1.8. These versions are the package's own, they do not follow the Java Card
version, and a card loads a CAP file only if it holds the imported package in the recorded version
or a later one (JCVM 3.1 &sect;4.5.2). The converter therefore records the lowest version that has
everything the code uses (1.0 for `GPSystem.getSecureChannel()` and the `SecureChannel` methods, 1.2
once a class implements `Personalization`, and so on), and the goal says which:

```text
[INFO] GlobalPlatform API: org.globalplatform 1.0 (the lowest version that has everything the package uses; cards with this version or a later one can load the CAP file)
```

`<globalPlatformApi>1.5</globalPlatformApi>` (or `-Djavacard.globalPlatformApi=1.5`) fixes the
version instead: that version is recorded even if the code needs less, and the build fails, naming
the place in the code and the version that introduced the element, when the code uses something a
later version added.

An export file of the package takes precedence over the built-in data, as for every package: one
named in `<importExportFiles>` or found in `<exportPath>` always (with `<globalPlatformApi>` it must
have that version), one inside a dependency jar (an API jar may hold it at
`org/globalplatform/javacard/globalplatform.exp`) only while `<globalPlatformApi>` is not set. The CAP
file then imports the version of that file, whatever the code uses, and the log line says so.

## Errors

Code that `javac` accepts but Java Card does not support fails the build with compiler-style
messages, each problem once, pointing at the source line. With the build extension (or the applet
parent) the [javac check](#compile-time-check) reports most of it already in `compile`; the goal
reports it in `process-classes`, and also what the check leaves to the conversion:

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
