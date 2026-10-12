# JavaCard Express :: javac Plugin

Checks Java Card applet code while javac compiles it. Code outside the Java Card language subset (JCVM 3.1 §2.2) and
references to API the target platform does not have (§4.5.2) become compile errors at their source lines, with the
converter's messages:

```
src/main/java/com/example/bad/BadApplet.java:7: error: Java Card: long type not supported in JavaCard (JCVM 3.1 §2.2.1.3)
    private long counter;
                 ^
```

The Maven plugin, declared with `<extensions>true</extensions>` (or managed so by the applet parent), adds it to the
compilation of the applet code with the settings of its `build` goal; nothing else is needed (see the
[Maven plugin README](../maven-plugin/README.md#compile-time-check)). This page is about what the check does and how
to use it without the plugin's build extension.

## What is checked

- The converter's own rules, applied to the class files javac has just written (`PackageCheck` of the converter):
  the language subset (types, keywords, instructions, class kinds, the int rules, the access rules) and, when the
  subset holds, the references into the Java Card API of the target platform, into `org.globalplatform`, into the
  Java SE library and into the packages whose export files the build supplies (`javacard.exportFiles`,
  `javacard.exportPath`; a supplied export file replaces the built-in data of its package, as in the conversion).
- Left to the conversion: references into other packages, whose export files the check does not have (the
  conversion also finds export files among the dependencies and on the class path, the check does not); a method a
  class inherits while one of its supertypes is in such a package, even where a built-in supertype lacks it on the
  target (only the other package's export file can tell whether it declares the method, §4.3.3); static
  initialization (§2.2.4.6), Java Card RMI (§2.2.6), multiselectable applets (§2.2.5), the applet and AID rules,
  tokens and the limits of the CAP file format. The conversion still checks everything; the check only reports
  earlier what it would report.
- An export file or export path entry the build names that is not there when javac runs (another module writes it
  later, when it converts) or that holds a Maven expression nothing resolved: the check then checks the language
  subset only, leaves the linking to the conversion, which names what is missing, and says so in one note.
- Which package: the ones named by `javacard.packageName`; without it the only package of the class output, or, with
  classes in several packages, those that have an applet (a non-abstract subclass of `javacard.framework.Applet`).
  That last rule is the check's own: the conversion needs a package name then. Host code in other packages and the
  unnamed package are not checked.
- The whole package, also when a build compiles only some of its classes: the other class files of the package are
  read from the class output. Errors are reported at the sources of the classes compiled now, one per line and
  message; problems in classes not compiled now are named in one note, and they do not keep the references of the
  classes compiled now from being checked.

An error is reported at the line the class file names: inside the member it comes from, at a catch clause for the
class the clause names, on a continued line at the expression continued there, in a class javac adds itself (such
as `Outer$1` for a switch on an enum) at the line in the class it was added for. A source with errors gets no class
files, as javac writes none for a source with a compile error, so that the next incremental build compiles it again
and reports the errors again.

A compilation whose class output is a test output is not checked. Test outputs are recognised by the name of the
directory: Maven's `target/test-classes`, Gradle's `build/classes/<language>/test`, IntelliJ IDEA's
`out/test/<module>`. Add the check only to the compilation of the main sources, as the Maven plugin's build extension
does, and this does not matter.

## Where the errors show

Wherever javac's errors show: on the command line and in the Maven build (`[ERROR] File.java:[line,column] ...`),
which IDEs show with navigation to the line. javac runs the check when it compiles; while you type, the
[IntelliJ IDEA plugin](../intellij-plugin/README.md) runs it on the editor's text and marks the same errors. Eclipse
and VS Code compile with the Eclipse compiler, which does not run it.

IntelliJ IDEA's own build runs annotation processors through a wrapper; the check finds javac behind it (tested
with IntelliJ IDEA's javac runner, and in IntelliJ IDEA 2026.2: its build reports `java: Java Card: ...` at the
line). The check reads the class files when javac has finished: where a build has not written them to the class
output yet, the check says in a note that it did not run. With *Delegate IDE build/run actions to Maven* the IDE
builds with Maven, and the check runs as in any Maven build.

## When it does not run

The check never stops a compilation by failing itself:

- where it cannot run, it says so in a note (`Java Card: the check did not run: ...`): another compiler than
  javac, a wrapper of javac's environment it does not know, class files that are not in a class output directory
  (no `-d`, or a build that keeps them in memory);
- a file of the package in the class output that is no class file, or a class file of a later Java version, is a
  warning that names it (`Java Card: the check could not read p/Junk.class: ...`); the package is not checked;
- any other failure of the check is a warning that names the package; a processor path without the converter's
  jar is a warning too (`Java Card: the check did not run: NoClassDefFoundError: ...`), unless the check is off.

Errors are only what the conversion would report, an invalid option of the check, and export files the build
supplies that the conversion cannot use either (the class files of that package go, as for any error). javac's
`-Werror`, which Maven's `<failOnWarning>` passes, turns the check's warnings into failures as well.

Without line numbers (`-g:none`, `-g:source,vars`, Maven's `debuglevel` without `lines`) the class files name no
lines; the errors are then at their members, a field or a method.

Several compilations at the same time in one JVM, as Maven's parallel builds (`-T`) run them, each have the check in
a class loader of their own; it reads the converter's API tables without the JVM's shared cache of open jar files, so
that javac closing one compilation's class loader cannot break another's check.

## How javac runs it

It is an annotation processor that claims no annotation (`name.velikodniy.jcexpress.javac.JavaCardCheckProcessor`):
javac finds it on the annotation processor path, and it follows the compilation through javac's public
`com.sun.source.util` API until javac has written the class files. A `-Xplugin` plugin cannot do this: javac gives a
plugin no access to the class output. Annotation processing must not be off (`-proc:none`). Since JDK 23 javac runs
annotation processors only when an option asks for them: a processor path (`-processorpath`), processor names
(`-processor`) or `-proc:full`; the processor path is how this one is meant to be used.

A processor on the path changes two things in javac beyond the check: javac warns that files it compiles implicitly
(found through `-sourcepath`) were not subject to annotation processing (`-implicit:class` avoids it, `-Werror` would
turn it into a failure), and with a processor path javac no longer looks for processors on the class path, so a
build's own processors belong on the processor path too.

With Maven, the plugin's build extension adds it (see the
[Maven plugin README](../maven-plugin/README.md#compile-time-check)). Without the extension, the POM in
[Without the build extension](../maven-plugin/README.md#without-the-build-extension), which an integration test
builds as written, shows the block for `maven-compiler-plugin`: this jar on the annotation processor path of the
compilation of the main sources only (`default-compile`), and the settings of the `build` goal that are not the
defaults as processor options, for example `-Ajavacard.version=3.0.4`.

A project with its own `annotationProcessorPaths` lists this one there as well; a project that names its
processors (`annotationProcessors`) adds `name.velikodniy.jcexpress.javac.JavaCardCheckProcessor`.

On the command line: `javac --release 8 -processorpath javacard-express-javac-plugin.jar:javacard-express-converter.jar
-classpath javacard-express-api.jar -d classes ...`.

## Options

The options are javac's annotation processor options (`-Aname=value`). Those of the settings are named like the
Maven plugin's properties; `javacard.exportFiles` and `javacard.exportPath` are options of the check only (the
plugin's `<importExportFiles>` and `<exportPath>` have no property), which the build extension passes. An unset or
blank value, or a Maven expression that was not resolved (`${...}`), means the default; an invalid value is one
compile error that names the option.

| Option | Default | Meaning |
|---|---|---|
| `javacard.check` | `true` | `false` turns the check off |
| `javacard.version` | `3.0.5` | the target Java Card platform (`2.1.2` to `3.2.0`), for the API it links against |
| `javacard.supportInt32` | `false` | whether the target supports the int type (JCVM 3.1 §2.2.3.1) |
| `javacard.globalPlatformApi` | the lowest sufficient version | the version of `org.globalplatform` the conversion links against |
| `javacard.packageName` | the package the conversion would choose | the packages to check, separated by commas |
| `javacard.exportFiles` | none | export files the conversion is given (`importExportFiles`), absolute paths separated by the path separator (`:`, on Windows `;`) |
| `javacard.exportPath` | none | export path entries the conversion searches (directories or JAR files with `<package>/javacard/<name>.exp`), separated the same way |

A build with several executions of the Maven plugin's `build` goal gets one check for all of them from the build
extension, with the settings that accept the most; the
[Maven plugin README](../maven-plugin/README.md#compile-time-check) says how they are combined.

## Requirements

javac of JDK 25 or newer: the processor and the converter are Java 25 class files. A javac of an older JDK cannot
load the processor, and every compilation fails. The Maven plugin's build extension leaves the check out by itself
where the compilation runs another javac (a toolchain, a forked compiler executable); `javacard.check=false` is for
setups it cannot detect. The applet code may be compiled for any release; the toolkit compiles it for Java 8.
