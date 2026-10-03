package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.check.Violation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Packages that cannot be represented in a CAP file are rejected with a {@link ConverterException}
 * naming the class, instead of being converted into a CAP file that is silently wrong.
 */
class ConversionRejectionTest {

    @Test
    void publicOverrideOfPackageVisibleMethodIsRejected_2_2_1_1() {
        assertThatThrownBy(() -> TestFixtures.applet("PubOverApplet").convert())
                .isInstanceOf(ConverterException.class)
                .satisfies(e -> assertThat(((ConverterException) e).violations())
                        .extracting(Violation::className, Violation::context)
                        .containsExactlyInAnyOrder(
                                tuple("com/example/pubover/PSub", "touch()V"),
                                tuple("com/example/pubover/PSub", "val()S")));
    }

    @Test
    void classNeedingMoreThan255InstanceFieldCellsIsRejected_4_3_7_5(@TempDir Path dir) throws IOException {
        writeClass(dir, "p/Big", false, List.of(), cb -> {
            for (int i = 0; i < 130; i++) {
                cb.withField("f" + i, ConstantDescs.CD_int, ClassFile.ACC_PRIVATE);
            }
        });

        // int fields need int support (JCVM 3.1 §2.2.3.1): the class is rejected for its size only
        assertThatThrownBy(() -> library(dir, true).convert())
                .isInstanceOf(ConverterException.class)
                .satisfies(e -> assertThat(((ConverterException) e).violations())
                        .singleElement()
                        .satisfies(v -> {
                            assertThat(v.className()).isEqualTo("p/Big");
                            assertThat(v.message()).contains("260");
                        }));
    }

    @Test
    void classImplementingMoreThan15InterfacesIsRejected_6_9_2_1(@TempDir Path dir) throws IOException {
        List<ClassDesc> interfaces = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            writeClass(dir, "p/I" + i, true, List.of(), cb -> { });
            interfaces.add(ClassDesc.ofInternalName("p/I" + i));
        }
        writeClass(dir, "p/C", false, interfaces, cb -> { });

        assertThatThrownBy(() -> library(dir).convert())
                .isInstanceOf(ConverterException.class)
                .hasMessageContaining("p/C")
                .hasMessageContaining("at most 15");
    }

    private static Converter library(Path dir) {
        return library(dir, false);
    }

    private static Converter library(Path dir, boolean intSupport) {
        return Converter.builder().classesDirectory(dir).packageName("p").packageAid("A000000062F00101")
                .supportInt32(intSupport).build();
    }

    private static void writeClass(Path dir, String name, boolean isInterface, List<ClassDesc> interfaces,
                                   Consumer<ClassBuilder> body)
            throws IOException {
        byte[] bytes = ClassFile.of().build(ClassDesc.ofInternalName(name), cb -> {
            cb.withFlags(isInterface
                    ? new AccessFlag[] {AccessFlag.PUBLIC, AccessFlag.INTERFACE, AccessFlag.ABSTRACT}
                    : new AccessFlag[] {AccessFlag.PUBLIC});
            cb.withInterfaceSymbols(interfaces);
            body.accept(cb);
        });
        Path file = dir.resolve(name + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }
}
