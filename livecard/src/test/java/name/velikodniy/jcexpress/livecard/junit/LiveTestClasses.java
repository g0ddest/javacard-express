package name.velikodniy.jcexpress.livecard.junit;

import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.constant.ClassDesc;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Finds the compiled live-card test classes (annotated {@link LiveCardTest}) of this module by reading the class
 * files, without loading them.
 */
final class LiveTestClasses {

    private static final ClassDesc LIVE_CARD_TEST = ClassDesc.of(LiveCardTest.class.getName());

    private LiveTestClasses() {
    }

    /**
     * Returns the directory with this module's compiled test classes.
     *
     * @return {@code target/test-classes}
     */
    static Path testClassesRoot() {
        try {
            return Path.of(LiveTestClasses.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Returns the names of all top-level classes annotated {@link LiveCardTest}.
     *
     * @return binary class names, sorted
     * @throws IOException if the class files cannot be read
     */
    static List<String> annotated() throws IOException {
        try (Stream<Path> files = Files.walk(testClassesRoot())) {
            return files.filter(path -> path.toString().endsWith(".class"))
                    .map(LiveTestClasses::parse)
                    .filter(LiveTestClasses::isLiveCardTest)
                    .map(model -> model.thisClass().asSymbol().packageName() + "."
                            + model.thisClass().asSymbol().displayName())
                    .sorted()
                    .toList();
        }
    }

    private static ClassModel parse(Path file) {
        try {
            return ClassFile.of().parse(file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isLiveCardTest(ClassModel model) {
        return model.findAttribute(Attributes.runtimeVisibleAnnotations())
                .map(attribute -> attribute.annotations().stream()
                        .anyMatch(annotation -> annotation.classSymbol().equals(LIVE_CARD_TEST)))
                .orElse(false);
    }
}
