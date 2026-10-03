package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.input.PackageInfo;
import name.velikodniy.jcexpress.converter.token.ImportedTypes;
import name.velikodniy.jcexpress.converter.token.TokenAssigner;
import name.velikodniy.jcexpress.converter.token.TokenMap;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.constant.MethodTypeDesc;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Declares in the abstract classes of the package the interface methods they leave to their
 * subclasses.
 *
 * <p>Java lets an abstract class implement an interface without declaring its methods (JLS 8.1.1.1,
 * 9.4.1), and javac then writes no declaration. The Class component, however, maps every method of an
 * implemented interface to a virtual method token of the class (JCVM 3.1 §6.9.2.5), and abstract
 * methods are represented in the Method, Class and Descriptor components like any other method
 * (§6.9.2.3, §6.10, §6.14). Each such method is therefore added to the class file as a
 * {@code public abstract} method with the interface method's name and descriptor, after the declared
 * methods: the result is the class file the source would give with the declaration written out, so the
 * conversion continues as for that source.
 */
final class AbstractMethodDeclarations {

    private AbstractMethodDeclarations() {}

    /**
     * Adds the missing declarations to the class files of the package.
     *
     * @param pkg        the package, read from {@code classFiles}
     * @param classFiles class file bytes by internal class name, in the order of {@code pkg.classes()}
     * @param imported   imported class and interface information (export files)
     * @return {@code classFiles} itself if no abstract class leaves an interface method undeclared,
     *         otherwise a new map in the same order with the completed class files
     */
    static Map<String, byte[]> complete(PackageInfo pkg, Map<String, byte[]> classFiles, ImportedTypes imported) {
        Map<String, List<TokenMap.MethodEntry>> undeclared = TokenAssigner.undeclaredInterfaceMethods(pkg, imported);
        if (undeclared.isEmpty()) {
            return classFiles;
        }
        Map<String, byte[]> completed = new LinkedHashMap<>(classFiles);
        undeclared.forEach((cls, methods) -> completed.put(cls, declare(classFiles.get(cls), methods)));
        return completed;
    }

    private static byte[] declare(byte[] classFile, List<TokenMap.MethodEntry> methods) {
        ClassFile cf = ClassFile.of();
        return cf.transformClass(cf.parse(classFile), ClassTransform.endHandler(cb -> {
            for (TokenMap.MethodEntry m : methods) {
                cb.withMethod(m.name(), MethodTypeDesc.ofDescriptor(m.descriptor()),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT, mb -> { });
            }
        }));
    }
}
