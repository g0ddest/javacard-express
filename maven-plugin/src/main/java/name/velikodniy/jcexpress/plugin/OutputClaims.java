package name.velikodniy.jcexpress.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps two executions of the goal in one module from writing the same file or attaching the
 * same artifact: the second one replaced the first CAP file without a word (for example two
 * Java Card targets, or two packages, without classifiers). The files and artifacts each
 * execution produces are recorded in the project's context for the rest of the build.
 *
 * <p>A goal named on the command line repeats a bound execution on purpose
 * ({@code mvn package javacard-express:build}) and is not checked.
 */
final class OutputClaims {

    private static final String CONTEXT_KEY = OutputClaims.class.getName();

    private OutputClaims() {
    }

    /**
     * Records the outputs of an execution.
     *
     * @param project    the module
     * @param invocation the execution
     * @param capFile    the CAP file it writes (the export file has the same base name)
     * @param attached   {@code true} if it attaches the files to the project
     * @param classifier their classifier, or {@code null}
     * @throws MojoExecutionException if another execution of this module writes the same file or
     *                                attaches the same artifact
     */
    static void claim(MavenProject project, Invocation invocation, Path capFile, boolean attached, String classifier)
            throws MojoExecutionException {
        if (invocation.commandLine()) {
            return;
        }
        Map<String, String> claims = claims(project);
        String id = invocation.executionId();
        String writer = claims.putIfAbsent("file " + capFile.toAbsolutePath().normalize(), id);
        if (writer != null && !writer.equals(id)) {
            throw conflict(writer, id, "write " + capFile + ", and the second would replace the first");
        }
        if (attached) {
            String what = classifier == null ? "without a classifier" : "with classifier " + classifier;
            String attacher = claims.putIfAbsent("artifact cap " + classifier, id);
            if (attacher != null && !attacher.equals(id)) {
                throw conflict(attacher, id, "attach a CAP file " + what + ", and Maven keeps only one of them");
            }
        }
    }

    /**
     * Decides which execution writes the files of a package into the classes directory ({@link ClassesOutput}):
     * the first execution of the module that builds the package, and a goal named on the command line. Another
     * execution for the same package (a second Java Card target with its own classifier) leaves them as the first
     * one wrote them.
     *
     * @param project     the module
     * @param invocation  the execution
     * @param packageName the converted package
     * @return the id of the execution that writes them, if it is another one
     */
    static Optional<String> classesWriter(MavenProject project, Invocation invocation, String packageName) {
        if (invocation.commandLine()) {
            return Optional.empty();
        }
        String id = invocation.executionId();
        String writer = claims(project).putIfAbsent("classes " + packageName, id);
        return writer == null || writer.equals(id) ? Optional.empty() : Optional.of(writer);
    }

    /** The executions of one module run one after the other; modules have their own project instances. */
    @SuppressWarnings("unchecked")
    private static Map<String, String> claims(MavenProject project) {
        Object claims = project.getContextValue(CONTEXT_KEY);
        if (claims == null) {
            claims = new ConcurrentHashMap<String, String>();
            project.setContextValue(CONTEXT_KEY, claims);
        }
        return (Map<String, String>) claims;
    }

    private static MojoExecutionException conflict(String first, String second, String what) {
        return new MojoExecutionException("Executions '" + first + "' and '" + second + "' of javacard-express:build"
                + " both " + what + ". Give each execution its own <classifier>: it names the files"
                + " (<finalName>-<classifier>.cap) and the attached artifacts.");
    }
}
