package name.velikodniy.jcexpress.converter.translate;

import name.velikodniy.jcexpress.converter.resolve.ReferenceResolver;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.StoreInstruction;
import java.util.List;

/**
 * Translates JVM bytecode into JCVM bytecode for the Method component of a CAP file.
 *
 * <p>This class implements <b>Stage 5: Bytecode Translation</b> of the converter pipeline. It
 * sits between reference resolution ({@link ReferenceResolver}) and CAP assembly
 * ({@link name.velikodniy.jcexpress.converter.cap.MethodComponent}). Each method is translated
 * into symbolic JCVM code ({@link JcvmCode}) that is then laid out and encoded; the symbolic form
 * is kept in the {@link TranslatedMethod} so that the method can be re-encoded once the constant
 * pool order is final.
 *
 * <h2>Translation rules (JCVM 3.1 Chapter 7)</h2>
 * <ul>
 *   <li><b>Fail-closed</b>: an instruction without an exact JCVM counterpart stops the
 *       conversion with a {@link TranslationException} naming the class, method and source
 *       line; nothing is dropped silently.</li>
 *   <li><b>Field access</b>: getfield/putfield/getstatic/putstatic become the typed
 *       {@code _a/_b/_s/_i} forms; instance field instructions use a 1-byte constant pool index,
 *       or the {@code _w} form when the final index exceeds 255 (§7.5.22).</li>
 *   <li><b>Branches</b>: the 1-byte offset form is used when the offset fits, otherwise the
 *       {@code _w} form (§7.5.25, §7.5.40).</li>
 *   <li><b>{@code _this} forms</b>: {@code aload_0; getfield} and {@code aload_0; push; putfield}
 *       become {@code getfield_<t>_this} and {@code push; putfield_<t>_this} (§7.5.21, §7.5.76)
 *       in instance methods that never overwrite local 0.</li>
 * </ul>
 *
 * <h2>Translation modes</h2>
 * <ul>
 *   <li><b>With resolver</b>: emits real constant pool indices and records their positions for
 *       the Reference Location component (production mode).</li>
 *   <li><b>Without resolver</b> ({@link #translate(MethodModel, ClassModel, JcvmConstantPool)}):
 *       every constant pool index is 0; used to test translation in isolation.</li>
 * </ul>
 *
 * <p>This class is stateless and thread-safe.
 *
 * @see JcvmOpcode
 * @see TranslatedMethod
 * @see ReferenceResolver
 */
public final class BytecodeTranslator {

    private BytecodeTranslator() {}

    /**
     * Translates a method with full reference resolution.
     *
     * @param method     the JVM method model
     * @param classModel the enclosing class model
     * @param resolver   reference resolver for CP entry creation
     * @return translated method with real CP indices
     * @throws TranslationException if the method cannot be translated exactly
     */
    public static TranslatedMethod translate(MethodModel method, ClassModel classModel,
                                             ReferenceResolver resolver) {
        return translate(method, classModel, resolver, false, true);
    }

    /**
     * Translates a method with full reference resolution and optional int support.
     *
     * @param method               the JVM method model
     * @param classModel           the enclosing class model
     * @param resolver             reference resolver for CP entry creation
     * @param supportInt32         whether the target supports the int type (JCVM 3.1 §2.2.3.1)
     * @param optimizePutfieldThis whether {@code putfield_<t>_this} (§7.5.76) may be used
     * @return translated method with real CP indices
     * @throws TranslationException if the method cannot be translated exactly
     */
    public static TranslatedMethod translate(MethodModel method, ClassModel classModel,
                                             ReferenceResolver resolver, boolean supportInt32,
                                             boolean optimizePutfieldThis) {
        var options = new TranslationOptions(supportInt32, optimizePutfieldThis);
        if (method.code().isEmpty()) {
            return withoutCode(method, supportInt32);
        }
        return new MethodTranslator(method, classModel, resolver, options).translate();
    }

    /**
     * Translates a method without reference resolution (placeholder mode): every constant pool
     * index is 0. Suitable for testing translation logic in isolation.
     *
     * @param method     the JVM method model
     * @param classModel the enclosing class model
     * @param cp         unused; kept for API compatibility
     * @return translated method with placeholder CP indices
     * @throws TranslationException if the method cannot be translated exactly
     */
    public static TranslatedMethod translate(MethodModel method, ClassModel classModel,
                                             JcvmConstantPool cp) {
        if (method.code().isEmpty()) {
            return withoutCode(method, false);
        }
        return new MethodTranslator(method, classModel, null,
                new TranslationOptions(false, true)).translate();
    }

    /**
     * Abstract method: empty bytecode, nargs from the descriptor in words (JCVM 3.1 §6.10.4; an
     * int parameter takes two words when the target supports int).
     */
    private static TranslatedMethod withoutCode(MethodModel method, boolean intWords) {
        int nargs = Descriptors.argumentWords(method.methodType().stringValue(), intWords);
        if ((method.flags().flagsMask() & ClassFile.ACC_STATIC) == 0) {
            nargs++;
        }
        return new TranslatedMethod(new byte[0], 0, 0, nargs, List.of(), false, List.of());
    }

    /**
     * Whether local variable 0 holds {@code this} for the whole method, which the
     * getfield_&lt;t&gt;_this / putfield_&lt;t&gt;_this instructions require (JCVM 3.1 §7.5.21,
     * §7.5.76: "The currently executing method must be an instance method. The local variable
     * at index 0 must contain a reference objectref to the currently executing method's this
     * parameter"). False for static methods and for code that overwrites local 0.
     */
    static boolean thisAvailable(MethodModel method, CodeModel code) {
        if ((method.flags().flagsMask() & ClassFile.ACC_STATIC) != 0) {
            return false;
        }
        for (CodeElement e : code) {
            if (e instanceof StoreInstruction st && st.slot() == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Determines the JCVM field type suffix from a field descriptor.
     *
     * @return 'a' for reference, 'b' for byte/boolean, 's' for short, 'i' for int
     * @throws IllegalStateException for types the JCVM does not support (char, long, float, double)
     */
    static char fieldTypeSuffix(String descriptor) {
        return "absi".charAt(MemberInstructions.fieldType(descriptor));
    }
}
