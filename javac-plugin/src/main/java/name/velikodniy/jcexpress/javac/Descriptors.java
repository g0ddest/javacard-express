package name.velikodniy.jcexpress.javac;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Method descriptors of source methods as javac writes them into the class file (JVMS §4.3.3): the erasure of the
 * parameter and return types, classes by binary name. They name the methods in the converter's messages.
 */
final class Descriptors {

    private Descriptors() {
    }

    /**
     * Returns the descriptor of a method or constructor as declared in the source. For the constructor of an inner,
     * local or anonymous class javac adds parameters (the enclosing instance, captured variables) that the source
     * does not show.
     *
     * @param method   the method
     * @param types    the compilation's type utilities
     * @param elements the compilation's element utilities
     * @return e.g. {@code (Ljavacard/framework/APDU;S)V}
     */
    static String of(ExecutableElement method, Types types, Elements elements) {
        StringBuilder descriptor = new StringBuilder("(");
        for (VariableElement parameter : method.getParameters()) {
            append(descriptor, types.erasure(parameter.asType()), elements);
        }
        descriptor.append(')');
        append(descriptor, types.erasure(method.getReturnType()), elements);
        return descriptor.toString();
    }

    private static void append(StringBuilder descriptor, TypeMirror type, Elements elements) {
        switch (type.getKind()) {
            case BOOLEAN -> descriptor.append('Z');
            case BYTE -> descriptor.append('B');
            case CHAR -> descriptor.append('C');
            case SHORT -> descriptor.append('S');
            case INT -> descriptor.append('I');
            case LONG -> descriptor.append('J');
            case FLOAT -> descriptor.append('F');
            case DOUBLE -> descriptor.append('D');
            case VOID -> descriptor.append('V');
            case ARRAY -> {
                descriptor.append('[');
                append(descriptor, ((ArrayType) type).getComponentType(), elements);
            }
            case DECLARED -> descriptor.append('L').append(elements.getBinaryName(
                    (TypeElement) ((DeclaredType) type).asElement()).toString().replace('.', '/')).append(';');
            default -> descriptor.append("Ljava/lang/Object;");
        }
    }
}
