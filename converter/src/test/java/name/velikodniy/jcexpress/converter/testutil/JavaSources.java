package name.velikodniy.jcexpress.converter.testutil;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compiles small Java Card probe sources in a test, against the API stubs on the test
 * classpath, so that a regression test can keep its applet next to its assertions.
 */
public final class JavaSources {

    private JavaSources() {}

    /**
     * Compiles the given sources into {@code outDir}.
     *
     * @param outDir  output directory for the class files
     * @param sources source text keyed by binary class name (e.g. {@code "com.acme.App"})
     * @return {@code outDir}
     * @throws AssertionError if the sources do not compile
     */
    public static Path compile(Path outDir, Map<String, String> sources) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        List<JavaFileObject> units = new ArrayList<>();
        sources.forEach((name, text) -> units.add(new Source(name, text)));
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        List<String> options = List.of("-d", outDir.toString(), "-proc:none", "-g",
                "-classpath", System.getProperty("java.class.path"));
        boolean ok = javac.getTask(null, null, diagnostics, options, null, units).call();
        if (!ok) {
            throw new AssertionError("Probe sources do not compile: " + diagnostics.getDiagnostics());
        }
        return outDir;
    }

    private static final class Source extends SimpleJavaFileObject {
        private final String text;

        Source(String binaryName, String text) {
            super(URI.create("string:///" + binaryName.replace('.', '/') + Kind.SOURCE.extension),
                    Kind.SOURCE);
            this.text = text;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }
}
