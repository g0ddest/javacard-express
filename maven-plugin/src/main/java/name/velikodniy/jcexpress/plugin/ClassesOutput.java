package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.converter.ConverterResult;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The files the goal writes into the classes directory (parameter {@code classesOutput}), from where they go into
 * the jar and onto the class path of the tests and of the modules that depend on the project:
 * <ul>
 *   <li>the CAP file and the export file at {@code <package directory>/javacard/<last name component>.cap} and
 *       {@code .exp}, the layout of JCVM 3.1 &sect;5.2 in which the converter of an importing package looks for
 *       an export file ({@link ExportFileLookup}), so a library needs no dependency of type {@code exp};</li>
 *   <li>the build descriptor {@code META-INF/javacard/<package>.properties}: the package, its AIDs and the
 *       conversion settings, which the card test backends of {@code javacard-express-core} use to convert the
 *       package for a card the way this build did.</li>
 * </ul>
 */
final class ClassesOutput {

    /** Directory of the build descriptors in the classes directory and the jar. */
    static final String DESCRIPTOR_DIRECTORY = "META-INF/javacard/";

    private final Path classesDirectory;
    private final Descriptor descriptor;

    /**
     * @param classesDirectory the classes directory of the module
     * @param descriptor       what the build produced and how
     */
    ClassesOutput(Path classesDirectory, Descriptor descriptor) {
        this.classesDirectory = classesDirectory;
        this.descriptor = descriptor;
    }

    /**
     * How a package was built.
     *
     * @param packageName     the package (dot notation)
     * @param packageAid      the package AID
     * @param packageVersion  the package version {@code <major>.<minor>}
     * @param javaCardVersion the target platform, e.g. {@code 3.0.5}
     * @param supportInt32    whether the package was converted with int support
     * @param export          whether the package is exported (CAP Export component and export file)
     * @param applets         the applets with their AIDs
     * @param project         {@code groupId:artifactId} of the module
     * @param pluginVersion   the version of this plugin, or {@code null} if unknown
     */
    record Descriptor(String packageName, Aid packageAid, String packageVersion, String javaCardVersion,
                      boolean supportInt32, boolean export, List<AppletAids.Assigned> applets, String project,
                      String pluginVersion) {

        /** @return the descriptor as a properties file (ASCII, fixed order, no time stamp) */
        String text() {
            StringBuilder text = new StringBuilder("# How javacard-express-maven-plugin built package ")
                    .append(escape(packageName)).append('\n');
            line(text, "package", packageName);
            line(text, "packageAid", packageAid.toString());
            line(text, "packageVersion", packageVersion);
            line(text, "javaCardVersion", javaCardVersion);
            line(text, "supportInt32", Boolean.toString(supportInt32));
            line(text, "export", Boolean.toString(export));
            line(text, "cap", resource(packageName, "cap"));
            for (AppletAids.Assigned applet : applets) {
                line(text, "applet." + applet.className(), applet.aid().toString());
            }
            line(text, "project", project);
            if (pluginVersion != null) {
                line(text, "pluginVersion", pluginVersion);
            }
            return text.toString();
        }

        private static void line(StringBuilder text, String key, String value) {
            text.append(escape(key)).append('=').append(escape(value)).append('\n');
        }

        /** The escapes of a properties file: backslash, and every character outside printable ASCII. */
        private static String escape(String text) {
            StringBuilder escaped = new StringBuilder(text.length());
            for (char c : text.toCharArray()) {
                if (c == '\\') {
                    escaped.append("\\\\");
                } else if (c < 0x20 || c > 0x7E) {
                    escaped.append(String.format("\\u%04X", (int) c));
                } else {
                    escaped.append(c);
                }
            }
            return escaped.toString();
        }
    }

    /**
     * @param packageName a package (dot notation)
     * @param extension   {@code cap} or {@code exp}
     * @return where the file of the package is in a classes directory or jar (JCVM 3.1 &sect;5.2)
     */
    static String resource(String packageName, String extension) {
        String last = packageName.substring(packageName.lastIndexOf('.') + 1);
        return packageName.replace('.', '/') + "/javacard/" + last + "." + extension;
    }

    /**
     * @param packageName a package (dot notation)
     * @return where its build descriptor is in a classes directory or jar
     */
    static String descriptorResource(String packageName) {
        return DESCRIPTOR_DIRECTORY + packageName + ".properties";
    }

    /**
     * Writes the CAP file, the export file (or deletes one of a previous build of a package that is no longer
     * exported) and the build descriptor.
     *
     * @param result the conversion result
     * @param log    receives the written files
     * @throws MojoExecutionException if a file cannot be written
     */
    void write(ConverterResult result, Log log) throws MojoExecutionException {
        String pkg = descriptor.packageName();
        Path cap = classesDirectory.resolve(resource(pkg, BuildOutputs.CAP_TYPE));
        Path exp = classesDirectory.resolve(resource(pkg, BuildOutputs.EXP_TYPE));
        Path properties = classesDirectory.resolve(descriptorResource(pkg));
        try {
            Files.createDirectories(cap.getParent());
            Files.write(cap, result.capFile());
            if (descriptor.export()) {
                Files.write(exp, result.exportFile());
            } else {
                Files.deleteIfExists(exp);
            }
            Files.createDirectories(properties.getParent());
            Files.writeString(properties, descriptor.text(), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write into the classes directory " + classesDirectory + ": "
                    + e.getMessage(), e);
        }
        log.info("Classes directory: " + resource(pkg, BuildOutputs.CAP_TYPE)
                + (descriptor.export() ? ", " + resource(pkg, BuildOutputs.EXP_TYPE) : "") + ", "
                + descriptorResource(pkg));
    }
}
