package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.AID;
import name.velikodniy.jcexpress.converter.Converter;
import name.velikodniy.jcexpress.converter.JavaCardVersion;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A Java Card package to convert and load: compiled classes, package name and AID, and its applets (Executable
 * Modules, GlobalPlatform Card Specification v2.3.1 11.5.2.3.2; Java Card applet AIDs, JCVM 3.0.5 6.5).
 *
 * <pre>{@code
 * AppletPackage hello = AppletPackage.of(classes, "com.example.hello", card.aid("0101"))
 *         .withApplet("HelloApplet", card.aid("010101"));
 * Deployment server = card.deploy(AppletPackage.of(classes, "com.example.server", card.aid("0102"))
 *         .withApplet("ServerApplet", card.aid("010201")).withExportComponent());
 * card.deploy(AppletPackage.of(classes, "com.example.client", card.aid("0103"))
 *         .withApplet("ClientApplet", card.aid("010301")).withExportPath(server.exportPath()));
 * }</pre>
 *
 * @param classesDirectory the directory with the compiled classes (package directories below it)
 * @param packageName      the Java package name, e.g. {@code com.example.hello}
 * @param aid              the package AID, uppercase hex
 * @param majorVersion     the package major version
 * @param minorVersion     the package minor version
 * @param modules          the applets of the package
 * @param intSupport       whether to convert with int support (JCVM 3.0.5 2.2.3.1; the card must support int)
 * @param exportComponent  whether the CAP file gets an Export component (JCVM 3.0.5 6.12), so that packages
 *                         loaded later can link to this one's shareable interfaces
 * @param exportPath       export path entries with the export files of imported packages (JCVM 3.0.5 4.1.1)
 */
public record AppletPackage(Path classesDirectory, String packageName, String aid, int majorVersion,
                            int minorVersion, List<Module> modules, boolean intSupport, boolean exportComponent,
                            List<Path> exportPath) {

    /**
     * An applet class of the package and its module AID.
     *
     * @param className the fully qualified class name
     * @param aid       the applet (module) AID, uppercase hex
     */
    public record Module(String className, String aid) {
    }

    /**
     * Validates and copies the values.
     *
     * @throws IllegalArgumentException if the version is outside 0-255
     */
    public AppletPackage {
        modules = List.copyOf(modules);
        exportPath = List.copyOf(exportPath);
        if (majorVersion < 0 || majorVersion > 0xFF || minorVersion < 0 || minorVersion > 0xFF) {
            throw new IllegalArgumentException("package version must be 0-255.0-255");
        }
    }

    /**
     * Creates a package description with version 1.0, no applets, no int support, no Export component and an
     * empty export path.
     *
     * @param classesDirectory the directory with the compiled classes
     * @param packageName      the Java package name
     * @param aid              the package AID
     * @return the package description
     */
    public static AppletPackage of(Path classesDirectory, String packageName, AID aid) {
        return new AppletPackage(classesDirectory, packageName, aid.toHex(), 1, 0, List.of(), false, false,
                List.of());
    }

    /**
     * Adds an applet.
     *
     * @param className the class name, fully qualified or relative to the package
     * @param aid       the applet (module) AID
     * @return a new description with the applet
     */
    public AppletPackage withApplet(String className, AID aid) {
        String qualified = className.contains(".") ? className : packageName + "." + className;
        List<Module> more = new ArrayList<>(modules);
        more.add(new Module(qualified, aid.toHex()));
        return new AppletPackage(classesDirectory, packageName, this.aid, majorVersion, minorVersion, more,
                intSupport, exportComponent, exportPath);
    }

    /**
     * Sets the package version.
     *
     * @param major the major version
     * @param minor the minor version
     * @return a new description with the version
     */
    public AppletPackage withVersion(int major, int minor) {
        return new AppletPackage(classesDirectory, packageName, aid, major, minor, modules, intSupport,
                exportComponent, exportPath);
    }

    /**
     * Converts with int support; a card without it rejects the CAP file when it is loaded.
     *
     * @return a new description with int support
     */
    public AppletPackage withIntSupport() {
        return new AppletPackage(classesDirectory, packageName, aid, majorVersion, minorVersion, modules, true,
                exportComponent, exportPath);
    }

    /**
     * Adds the Export component to the CAP file; {@link Deployment#exportPath()} then holds the export file.
     *
     * @return a new description with the Export component
     */
    public AppletPackage withExportComponent() {
        return new AppletPackage(classesDirectory, packageName, aid, majorVersion, minorVersion, modules,
                intSupport, true, exportPath);
    }

    /**
     * Adds export path entries, e.g. {@link Deployment#exportPath()} of a package this one imports.
     *
     * @param entries directories with export files in the layout {@code <package>/javacard/<name>.exp}
     * @return a new description with the entries appended
     */
    public AppletPackage withExportPath(Path... entries) {
        List<Path> more = new ArrayList<>(exportPath);
        more.addAll(List.of(entries));
        return new AppletPackage(classesDirectory, packageName, aid, majorVersion, minorVersion, modules,
                intSupport, exportComponent, more);
    }

    /**
     * Returns one instance per applet, with the module AID as instance AID and no install parameters.
     *
     * @return the default instances
     */
    public List<AppletInstance> defaultInstances() {
        return modules.stream().map(module -> AppletInstance.of(AID.fromHex(module.aid()))).toList();
    }

    /**
     * Returns a converter builder for this package.
     *
     * @param version the conversion target
     * @return the configured builder
     */
    Converter.Builder converter(JavaCardVersion version) {
        Converter.Builder builder = Converter.builder()
                .classesDirectory(classesDirectory)
                .packageName(packageName)
                .packageAid(aid)
                .packageVersion(majorVersion, minorVersion)
                .javaCardVersion(version)
                .supportInt32(intSupport)
                .generateExport(exportComponent)
                .exportPath(exportPath.toArray(Path[]::new));
        for (Module module : modules) {
            builder.applet(module.className(), module.aid());
        }
        return builder;
    }
}
