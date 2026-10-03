package name.velikodniy.jcexpress.api.conformance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The stub jar is a compile-time dependency of applet projects, and Java Card toolchains are commonly pinned
 * to JDK 8, 11, 17 or 21. A javac can only read class files up to its own version (JVMS 4.1), so the stubs
 * must be class-file version 52 (Java SE 8) to be usable from every javac that can still build applets.
 */
class StubClassFileVersionTest {

    /** Class-file major version of Java SE 8 (JVMS 4.1, Table 4.1-A). */
    private static final int JAVA_8_MAJOR_VERSION = 52;

    @Test
    void everyStubClassIsReadableByJava8Compilers() throws IOException {
        Path classes = ApiLocations.stubClasses();
        List<String> tooNew;
        try (Stream<Path> files = Files.walk(classes)) {
            tooNew = files.filter(file -> file.toString().endsWith(".class"))
                    .filter(file -> majorVersion(file) > JAVA_8_MAJOR_VERSION)
                    .map(file -> classes.relativize(file) + " (major " + majorVersion(file) + ")")
                    .sorted()
                    .toList();
        }
        assertThat(tooNew).as("stub classes newer than class-file version 52").isEmpty();
    }

    private static int majorVersion(Path classFile) {
        try {
            ClassModel model = ClassFile.of().parse(Files.readAllBytes(classFile));
            return model.majorVersion();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + classFile, e);
        }
    }
}
