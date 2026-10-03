package name.velikodniy.jcexpress.api.conformance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The stubs exist for compilation only: javac reads their signatures and constants, nothing else. Their bodies
 * must therefore never pretend to work. A placeholder result such as {@code Util.arrayCompare} returning 0
 * ("equal") would silently make code that runs against the stubs by mistake (for example a unit test whose class
 * path lists the stubs before a simulator) take the wrong branch. Every API member the stubs implement fails
 * instead, with the {@code RuntimeException("stub")} convention known from other compile-time API jars.
 */
class StubBodiesTest {

    /** Message of the exception thrown by every stub member. */
    private static final String STUB_MESSAGE = "stub";

    @Test
    void noStubMemberCanReturnNormally() throws IOException {
        Path classes = ApiLocations.stubClasses();
        List<String> returning = new ArrayList<>();
        try (Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                ClassModel model = ClassFile.of().parse(Files.readAllBytes(file));
                model.methods().stream()
                        .filter(StubBodiesTest::isApiMemberWithCode)
                        .filter(method -> method.code().map(StubBodiesTest::canReturn).orElse(false))
                        .forEach(method -> returning.add(model.thisClass().asInternalName() + "."
                                + method.methodName().stringValue() + method.methodType().stringValue()));
            }
        }
        assertThat(returning).as("stub members that return instead of throwing").isEmpty();
    }

    @Test
    void executingAStubFailsLoudly() throws ReflectiveOperationException {
        try (URLClassLoader stubsOnly = new URLClassLoader(new URL[] {stubClassesUrl()},
                ClassLoader.getPlatformClassLoader())) {
            Class<?> util = stubsOnly.loadClass("javacard.framework.Util");
            Method arrayCompare = util.getMethod("arrayCompare", byte[].class, short.class, byte[].class,
                    short.class, short.class);
            assertThat(util.getProtectionDomain().getCodeSource().getLocation()).isEqualTo(stubClassesUrl());
            assertThatThrownBy(() -> arrayCompare.invoke(null, new byte[] {1}, (short) 0, new byte[] {2},
                    (short) 0, (short) 1))
                    .isInstanceOf(InvocationTargetException.class)
                    .cause().isInstanceOf(RuntimeException.class).hasMessage(STUB_MESSAGE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Public and protected methods and constructors with a body. Private constructors (which only keep javac from
     * adding a public default constructor) and javac's synthetic bridge methods are not API.
     */
    private static boolean isApiMemberWithCode(MethodModel method) {
        int flags = method.flags().flagsMask();
        boolean api = (flags & (ClassFile.ACC_PUBLIC | ClassFile.ACC_PROTECTED)) != 0;
        boolean synthetic = (flags & (ClassFile.ACC_SYNTHETIC | ClassFile.ACC_BRIDGE)) != 0;
        return api && !synthetic && method.code().isPresent();
    }

    private static boolean canReturn(CodeModel code) {
        return code.elementStream().anyMatch(ReturnInstruction.class::isInstance);
    }

    private static URL stubClassesUrl() {
        try {
            return ApiLocations.stubClasses().toUri().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
    }
}
