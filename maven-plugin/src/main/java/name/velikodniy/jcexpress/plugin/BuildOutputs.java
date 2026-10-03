package name.velikodniy.jcexpress.plugin;

import name.velikodniy.jcexpress.converter.ConverterResult;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * The files a build produces: {@code <finalName>[-<classifier>].cap} and, for an exported package,
 * the export file {@code <finalName>[-<classifier>].exp}. Both can be attached to the Maven project
 * (artifact types {@code cap} and {@code exp}) so that {@code install} and {@code deploy} publish
 * them and other modules can depend on them.
 */
final class BuildOutputs {

    /** Artifact type (and extension) of the CAP file. */
    static final String CAP_TYPE = "cap";
    /** Artifact type (and extension) of the export file. */
    static final String EXP_TYPE = "exp";

    private static final Pattern CLASSIFIER = Pattern.compile("[A-Za-z0-9_.-]+");

    private final Path capFile;
    private final Path exportFile;
    private final Path workDirectory;
    private final String classifier;

    private BuildOutputs(Path directory, String baseName, String classifier) {
        this.capFile = directory.resolve(baseName + "." + CAP_TYPE);
        this.exportFile = directory.resolve(baseName + "." + EXP_TYPE);
        this.workDirectory = directory.resolve("javacard-work").resolve(baseName);
        this.classifier = classifier;
    }

    /**
     * @param directory  output directory
     * @param finalName  base file name, e.g. {@code wallet-1.0}
     * @param classifier optional classifier ({@code null} or blank for none)
     * @return the outputs
     * @throws MojoExecutionException if the classifier contains characters Maven does not allow
     */
    static BuildOutputs of(Path directory, String finalName, String classifier) throws MojoExecutionException {
        String suffix = "";
        String normalized = null;
        if (classifier != null && !classifier.isBlank()) {
            normalized = classifier.trim();
            if (!CLASSIFIER.matcher(normalized).matches()) {
                throw new MojoExecutionException("Invalid <classifier> '" + classifier
                        + "': use letters, digits, '.', '_' and '-' only.");
            }
            suffix = "-" + normalized;
        }
        return new BuildOutputs(directory, finalName + suffix, normalized);
    }

    /** @return the CAP file this build writes */
    Path capFile() {
        return capFile;
    }

    /** @return the classifier of the files, or {@code null} for none */
    String classifier() {
        return classifier;
    }

    /** @return a directory for intermediate files of this build (e.g. export files taken from jars) */
    Path workDirectory() {
        return workDirectory;
    }

    /**
     * Writes the CAP file, and the export file if the package is exported; an export file left by a
     * previous build is deleted otherwise.
     *
     * @param result the conversion result
     * @param export whether the package is exported
     * @param log    receives the written files
     * @throws MojoExecutionException if a file cannot be written
     */
    void write(ConverterResult result, boolean export, Log log) throws MojoExecutionException {
        try {
            Files.createDirectories(capFile.getParent());
            Files.write(capFile, result.capFile());
            log.info("CAP file: " + capFile + " (" + result.capSize() + " bytes)");
            if (export) {
                Files.write(exportFile, result.exportFile());
                log.info("Export file: " + exportFile + " (" + result.exportFile().length + " bytes)");
            } else if (Files.deleteIfExists(exportFile)) {
                log.info("Deleted the export file of a previous build: " + exportFile);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write output files: " + e.getMessage(), e);
        }
    }

    /**
     * Attaches the written files to the project.
     *
     * @param project the project
     * @param helper  Maven's project helper
     * @param export  whether the export file was written
     */
    void attach(MavenProject project, MavenProjectHelper helper, boolean export) {
        helper.attachArtifact(project, CAP_TYPE, classifier, capFile.toFile());
        if (export) {
            helper.attachArtifact(project, EXP_TYPE, classifier, exportFile.toFile());
        }
    }
}
