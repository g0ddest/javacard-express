# JavaCard Express :: Maven Plugin

Maven plugin for building JavaCard CAP files. No Oracle SDK required.

## Quick Start

Add the plugin and API stubs to your project:

```xml
<dependencies>
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
            <groupId>name.velikodniy</groupId>
            <artifactId>javacard-express-maven-plugin</artifactId>
            <version>${jcexpress.version}</version>
            <configuration>
                <packageAid>A00000006212</packageAid>
            </configuration>
        </plugin>
    </plugins>
</build>
```

Run `mvn package` — the plugin auto-discovers applets and produces `.cap` and `.exp`
files in `target/`.

## How It Works

The plugin binds to the `package` phase and:

1. Scans compiled classes for subclasses of `javacard.framework.Applet`
2. Generates deterministic AIDs from class names (if not configured explicitly)
3. Converts `.class` files to a `.cap` file using the built-in clean-room converter

No Oracle JavaCard SDK installation or `JCDK_HOME` configuration needed.

## Configuration

```xml
<configuration>
    <!-- Package AID (hex). Auto-generated from package name if omitted -->
    <packageAid>A00000006212</packageAid>

    <!-- Java package name. Auto-detected from classes if omitted -->
    <packageName>com.example.myapplet</packageName>

    <!-- Package version (default: 1.0) -->
    <packageVersion>1.0</packageVersion>

    <!-- Target JavaCard version (default: 3.0.5) -->
    <!-- Valid: 2.1.2, 2.2.1, 2.2.2, 3.0.3, 3.0.4, 3.0.5, 3.1.0, 3.2.0 -->
    <javaCardVersion>3.0.5</javaCardVersion>

    <!-- Enable 32-bit integer support (default: false) -->
    <supportInt32>false</supportInt32>

    <!-- Generate .exp export file (default: true) -->
    <generateExport>true</generateExport>

    <!-- Explicit applet list (optional, auto-discovered if omitted) -->
    <applets>
        <applet>
            <className>com.example.myapplet.MyApplet</className>
            <aid>A0000000621201</aid>
        </applet>
    </applets>

    <!-- External .exp files for package resolution (optional) -->
    <importExportFiles>
        <importExportFile>path/to/external.exp</importExportFile>
    </importExportFiles>

    <!-- Oracle compatibility mode: byte-identical Class.cap output (default: false) -->
    <oracleCompatibility>false</oracleCompatibility>

    <!-- Skip plugin execution (default: false) -->
    <skip>false</skip>
</configuration>
```

All configuration parameters are also available as Maven properties:

| Property | Default |
|----------|---------|
| `javacard.packageAid` | auto-generated |
| `javacard.packageName` | auto-detected |
| `javacard.packageVersion` | `1.0` |
| `javacard.version` | `3.0.5` |
| `javacard.supportInt32` | `false` |
| `javacard.generateExport` | `true` |
| `javacard.oracleCompatibility` | `false` |
| `javacard.skip` | `false` |

## Auto-Discovery

When `<applets>` is not configured, the plugin scans compiled classes and discovers
applets automatically by checking the superclass chain for `javacard.framework.Applet`.
A deterministic AID is generated for each discovered applet using SHA-1 of the
fully qualified class name.

## Supported JavaCard Versions

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
