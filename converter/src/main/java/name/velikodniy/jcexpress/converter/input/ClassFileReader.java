package name.velikodniy.jcexpress.converter.input;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads JVM {@code .class} files and transforms them into the converter's
 * internal model objects ({@link ClassInfo}, {@link MethodInfo}, {@link FieldInfo}).
 *
 * <h2>Converter Pipeline: Stage 1 (Load)</h2>
 * <p>This class is the low-level workhorse of the Load stage. While
 * {@link PackageScanner} handles filesystem discovery, this class handles the
 * actual parsing of individual {@code .class} files. It uses the JDK ClassFile API
 * (JEP 484, Java 24+) to parse JVM class files and extract the structural
 * information needed by later converter stages:
 * <ul>
 *   <li>Class name, superclass, and implemented interfaces (in JVM internal name format)</li>
 *   <li>Access flags (used for interface/abstract detection and token assignment)</li>
 *   <li>Method declarations with bytecode, operand stack/locals limits, and exception handlers</li>
 *   <li>Field declarations with type descriptors and compile-time constant values</li>
 * </ul>
 *
 * <p>The output model uses JVM internal names (slash-separated, e.g.
 * {@code "javacard/framework/Applet"}) and JVM type descriptors
 * (e.g. {@code "(Ljavacard/framework/APDU;)V"}), which are later translated
 * to JCVM tokens during the Reference Resolution stage.
 *
 * <p>This class is stateless and all methods are static. It does not perform
 * any JavaCard-specific validation; that is the responsibility of the
 * {@link name.velikodniy.jcexpress.converter.check.SubsetChecker SubsetChecker}
 * in Stage 2.
 *
 * @see PackageScanner
 * @see ClassInfo
 * @see MethodInfo
 * @see FieldInfo
 */
public final class ClassFileReader {

    private ClassFileReader() {}

    /**
     * Reads a single {@code .class} file from raw bytes.
     *
     * <p>Parses the provided byte array as a JVM class file and returns
     * a {@link ClassInfo} record containing the class structure, methods,
     * and fields.
     *
     * @param classBytes the complete contents of a {@code .class} file
     * @return parsed class information
     */
    public static ClassInfo read(byte[] classBytes) {
        ClassModel cm = ClassFile.of().parse(classBytes);
        return fromClassModel(cm);
    }

    /**
     * Reads a single {@code .class} file from the filesystem.
     *
     * <p>Convenience method that reads all bytes from the given path and
     * delegates to {@link #read(byte[])}.
     *
     * @param path filesystem path to the {@code .class} file
     * @return parsed class information
     * @throws IOException if the file cannot be read
     */
    public static ClassInfo readFile(Path path) throws IOException {
        return read(Files.readAllBytes(path));
    }

    private static ClassInfo fromClassModel(ClassModel cm) {
        String thisClass = cm.thisClass().asInternalName();

        String superClass = cm.superclass()
                .map(ClassEntry::asInternalName)
                .orElse(null);

        List<String> interfaces = cm.interfaces().stream()
                .map(ClassEntry::asInternalName)
                .toList();

        List<name.velikodniy.jcexpress.converter.input.MethodInfo> methods =
                cm.methods().stream()
                        .map(ClassFileReader::fromMethodModel)
                        .toList();

        List<name.velikodniy.jcexpress.converter.input.FieldInfo> fields =
                cm.fields().stream()
                        .map(ClassFileReader::fromFieldModel)
                        .toList();

        return new ClassInfo(
                thisClass,
                superClass,
                interfaces,
                cm.flags().flagsMask(),
                methods,
                fields
        );
    }

    private static name.velikodniy.jcexpress.converter.input.MethodInfo fromMethodModel(MethodModel mm) {
        String name = mm.methodName().stringValue();
        String descriptor = mm.methodType().stringValue();
        int accessFlags = mm.flags().flagsMask();

        int maxStack = 0;
        int maxLocals = 0;
        byte[] bytecode = new byte[0];
        List<ExceptionHandlerInfo> handlers = List.of();

        var codeAttr = mm.code();
        if (codeAttr.isPresent()) {
            CodeModel code = codeAttr.orElseThrow();

            if (code instanceof CodeAttribute ca) {
                maxStack = ca.maxStack();
                maxLocals = ca.maxLocals();
                bytecode = ca.codeArray();

                // Extract exception handlers
                List<ExceptionHandlerInfo> handlerList = new ArrayList<>();
                for (var handler : code.exceptionHandlers()) {
                    String catchType = handler.catchType()
                            .map(ct -> ct.asInternalName())
                            .orElse(null);
                    handlerList.add(new ExceptionHandlerInfo(
                            ca.labelToBci(handler.tryStart()),
                            ca.labelToBci(handler.tryEnd()),
                            ca.labelToBci(handler.handler()),
                            catchType
                    ));
                }
                handlers = List.copyOf(handlerList);
            }
        }

        return new name.velikodniy.jcexpress.converter.input.MethodInfo(
                name, descriptor, accessFlags,
                maxStack, maxLocals, bytecode, handlers
        );
    }

    private static name.velikodniy.jcexpress.converter.input.FieldInfo fromFieldModel(FieldModel fm) {
        String name = fm.fieldName().stringValue();
        String descriptor = fm.fieldType().stringValue();
        int accessFlags = fm.flags().flagsMask();

        Object constantValue = null;
        var cvAttr = fm.findAttribute(java.lang.classfile.Attributes.constantValue());
        if (cvAttr.isPresent()) {
            constantValue = cvAttr.orElseThrow().constant().constantValue();
        }

        return new name.velikodniy.jcexpress.converter.input.FieldInfo(
                name, descriptor, accessFlags, constantValue
        );
    }
}
