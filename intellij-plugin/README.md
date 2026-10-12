# JavaCard Express :: IntelliJ IDEA Plugin

Shows the errors of the toolkit's javac check in the editor while you type: code outside the Java Card language
subset and API the target platform does not have are marked at their source lines, with the message the build gives,
before you build.

```text
private long total;        ← Java Card: long type not supported in JavaCard (JCVM 3.1 §2.2.1.3)
```

The plugin has no rules of its own. The check is the toolkit's [javac plugin](../javac-plugin/README.md), which runs
the converter's rules on the class files javac writes; the build runs it in `mvn compile` and in the IDE's own build.
The IDE plugin runs that same check, in the version the project's build uses, on the text in the editor.

Status: tested headless (the IntelliJ platform's test framework runs the annotator in a light project, and
JetBrains' plugin verifier finds no problem against IntelliJ IDEA 2026.2); the marks in the editor of a running IDE
have not been checked yet. It is installed from disk; it is not on JetBrains Marketplace.

## How it works

For a Java file of the main code, the plugin looks at the module's main compilation as the IDE imported it from
Maven:

- the check runs only when the annotation processor path of that compilation has `javacard-express-javac-plugin`
  (the Maven plugin as a build extension, or the applet parent, puts it there); the jars on that path, the project's
  own, are the ones that run;
- the check's options are those the build passes (`-Ajavacard.version=...` and the others the build extension writes
  into the compiler plugin's configuration), read from the Maven project, else from the IDE's compiler settings;
  `javacard.check=false` turns the marks off as it turns the check off;
- the package is one the build checks: the configured `packageName`, else the module's only package, else a package
  with an applet.

Then, in the background after each edit, it compiles the file's package in memory with javac of the IDE's runtime
(`--release` of the module, `-g`, the module's class path with the API stubs), with the check on the processor path,
into a temporary directory the check reads, and marks the errors the check reports (`Java Card: ...`) in the
package's files. javac's own errors are not repeated (the IDE shows them), and a file with Java errors is not checked
(javac stops before the class files the check needs). The result is kept per package until a text or a setting
changes.

## Requirements

- IntelliJ IDEA 2026.2 or newer. The check (the converter) is Java 25 code, which the IDE's runtime (JetBrains
  Runtime 25) runs; on an older runtime the plugin stays silent and writes one line to the IDE log.
- A project whose main compilation runs the check: `javacard-express-maven-plugin` declared with
  `<extensions>true</extensions>`, or a project with the applet parent, imported into the IDE from Maven. With the
  explicit POM of the Maven plugin README, the `maven-compiler-plugin` block there puts the check on the path.

## Install

Build the plugin and install it from disk:

```bash
./gradlew buildPlugin       # build/distributions/javacard-express-intellij-plugin-<version>.zip
```

*Settings → Plugins → ⚙ → Install Plugin from Disk...* and choose the zip; the IDE asks for a restart.

## Build and test

```bash
./gradlew test              # the check with the toolkit's jars, and the annotator in a headless IDE
./gradlew buildPlugin       # the plugin zip
./gradlew verifyPlugin      # JetBrains' plugin verifier against the IDE in gradle.properties
```

The build downloads IntelliJ IDEA (`platformVersion` in `gradle.properties`) as the platform;
`-PlocalIde="/Applications/IntelliJ IDEA.app"` uses an installed IDE of that version instead. The plugin has the
toolkit's version, which the build reads from the toolkit's `.mvn/maven.config` (`-Drevision=...`), and its tests
run the toolkit's javac check and API stubs of that version from the local Maven repository or Maven Central:
install the toolkit first when it is a snapshot (`./mvnw install -DskipTests -pl '!container'` in the toolkit's
root).

The project is a standalone Gradle build, like the simulator server in `docker/` is a standalone Maven build: IDE
plugins are built with the IntelliJ Platform Gradle Plugin, and the toolkit's Maven build does not include it.

## Limits

- The marks come after a short pause, as for other external tools: the package is compiled in the background.
- Only the main code of a module whose compilation runs the check is marked; tests never are.
- The check's limits apply: references into other packages of the build, static initialization, applets and AIDs
  are left to the conversion (see the [javac plugin README](../javac-plugin/README.md)).
- Export files the build supplies reach the check through the build's `-A` options. An entry that is not there
  yet, or holds an expression nothing resolved, makes the check leave the linking to the conversion, as in the
  build.
- Eclipse and VS Code are not supported (they compile with the Eclipse compiler).
