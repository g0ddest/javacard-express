package name.velikodniy.jcexpress.converter.input;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Parsed representation of a single method declaration extracted from a JVM
 * {@code .class} file.
 *
 * <h2>Converter Pipeline: Stage 1 (Load)</h2>
 * <p>Each {@code MethodInfo} is produced by {@link ClassFileReader} when parsing
 * a class file. It captures everything needed by later converter stages:
 * <ul>
 *   <li>The method name and JVM type descriptor, used for symbolic resolution
 *       and token assignment.</li>
 *   <li>Access flags, used to distinguish static methods (which receive static
 *       method tokens) from virtual methods (which receive virtual method tokens),
 *       and to identify abstract/native methods that have no bytecode body.</li>
 *   <li>The raw JVM bytecode and operand stack/locals limits, consumed by the
 *       {@link name.velikodniy.jcexpress.converter.translate.BytecodeTranslator BytecodeTranslator}
 *       in Stage 5 to produce JCVM instructions.</li>
 *   <li>The exception handler table, translated into JCVM exception handler
 *       entries in the Method Component (JCVM 3.0.5 spec Section 6.10).</li>
 * </ul>
 *
 * <h2>JCVM Spec References</h2>
 * <ul>
 *   <li>Section 6.10 — Method Component: defines the binary layout of methods
 *       in a CAP file, including the {@code method_info} structure that this
 *       record's data populates.</li>
 *   <li>Section 6.10.2 — Exception Handler Table: maps to the
 *       {@code exceptionHandlers} field.</li>
 * </ul>
 *
 * @param name              method name (e.g. {@code "process"}, {@code "<init>"},
 *                          {@code "<clinit>"})
 * @param descriptor        JVM method descriptor
 *                          (e.g. {@code "(Ljavacard/framework/APDU;)V"})
 * @param accessFlags       JVM access flags bitmask ({@code ACC_PUBLIC},
 *                          {@code ACC_STATIC}, {@code ACC_ABSTRACT}, etc.)
 * @param maxStack          maximum operand stack depth required by this method
 * @param maxLocals         maximum number of local variable slots used
 * @param bytecode          raw JVM bytecode bytes from the {@code Code} attribute,
 *                          or an empty array for abstract/native methods
 * @param exceptionHandlers exception handler table entries from the {@code Code}
 *                          attribute
 * @see ClassFileReader
 * @see ExceptionHandlerInfo
 * @see ClassInfo
 */
public record MethodInfo(
        String name,
        String descriptor,
        int accessFlags,
        int maxStack,
        int maxLocals,
        byte[] bytecode,
        List<ExceptionHandlerInfo> exceptionHandlers
) {
    /**
     * Returns {@code true} if this method is abstract ({@code ACC_ABSTRACT} flag is set).
     *
     * <p>Abstract methods have no bytecode body and are represented in the
     * Method Component as abstract method entries without a code block.
     *
     * @return {@code true} if the {@code ACC_ABSTRACT} flag (0x0400) is set
     */
    public boolean isAbstract() {
        return (accessFlags & 0x0400) != 0;
    }

    /**
     * Returns {@code true} if this method is static ({@code ACC_STATIC} flag is set).
     *
     * <p>Static methods are assigned static method tokens (0-based per class)
     * during Stage 3, distinct from virtual method tokens.
     *
     * @return {@code true} if the {@code ACC_STATIC} flag (0x0008) is set
     */
    public boolean isStatic() {
        return (accessFlags & 0x0008) != 0;
    }

    /**
     * Returns {@code true} if this method is private ({@code ACC_PRIVATE} flag is set).
     *
     * <p>In JCVM, private instance methods are invoked via {@code invokespecial}
     * and assigned static method tokens, not virtual method tokens.
     * Java 25+ (JEP 181) compiles private instance calls as {@code invokevirtual},
     * so the converter must remap these.
     *
     * @return {@code true} if the {@code ACC_PRIVATE} flag (0x0002) is set
     */
    public boolean isPrivate() {
        return (accessFlags & 0x0002) != 0;
    }

    /**
     * Returns {@code true} if this method is native ({@code ACC_NATIVE} flag is set).
     *
     * <p>Native methods have no JVM bytecode body; the bytecode array will be empty.
     *
     * @return {@code true} if the {@code ACC_NATIVE} flag (0x0100) is set
     */
    public boolean isNative() {
        return (accessFlags & 0x0100) != 0;
    }

    /**
     * Returns {@code true} if this method is an instance constructor ({@code <init>}).
     *
     * <p>Constructors do not receive virtual or static method tokens during
     * Stage 3 (Token Assignment) and are handled separately in the Method Component.
     *
     * @return {@code true} if the method name is {@code "<init>"}
     */
    public boolean isConstructor() {
        return "<init>".equals(name);
    }

    /**
     * Returns {@code true} if this method is a static initializer ({@code <clinit>}).
     *
     * <p>Like constructors, static initializers do not receive method tokens.
     * They are placed in the Method Component but are not referenced from the
     * Descriptor Component's method entries.
     *
     * @return {@code true} if the method name is {@code "<clinit>"}
     */
    public boolean isStaticInitializer() {
        return "<clinit>".equals(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof MethodInfo(var n, var d, var af, var ms, var ml, var bc, var eh)) {
            return accessFlags == af
                    && maxStack == ms
                    && maxLocals == ml
                    && Objects.equals(name, n)
                    && Objects.equals(descriptor, d)
                    && Arrays.equals(bytecode, bc)
                    && Objects.equals(exceptionHandlers, eh);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Objects.hash(name, descriptor, accessFlags, maxStack, maxLocals, exceptionHandlers)
                + Arrays.hashCode(bytecode));
    }

    @Override
    public String toString() {
        return "MethodInfo[name=" + name
                + ", descriptor=" + descriptor
                + ", accessFlags=" + accessFlags
                + ", maxStack=" + maxStack
                + ", maxLocals=" + maxLocals
                + ", bytecode=" + HexFormat.of().formatHex(bytecode)
                + ", exceptionHandlers=" + exceptionHandlers + "]";
    }
}
