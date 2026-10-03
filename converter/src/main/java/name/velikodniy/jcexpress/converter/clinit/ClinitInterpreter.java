package name.velikodniy.jcexpress.converter.clinit;

import name.velikodniy.jcexpress.converter.check.Violation;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.LineNumber;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.reflect.AccessFlag;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Evaluates a class's {@code <clinit>} method at conversion time.
 *
 * <p>A Java Card VM has no mechanism to execute {@code <clinit>}; the Static Field component
 * carries its effect instead, as array initialization data and non-default primitive values
 * (JCVM 3.1 §6.10, §6.11). JCVM 3.1 §2.2.4.6 limits class initialization to what can be
 * represented that way:
 * <ul>
 *   <li>static fields of applet packages: primitive compile-time constants or arrays of
 *       primitive compile-time constants;</li>
 *   <li>static fields of library packages and of interfaces: primitive compile-time constants
 *       only;</li>
 *   <li>only static fields declared by the class itself;</li>
 *   <li>bytecodes: {@code iconst_<n>}, {@code bipush}, {@code sipush}, {@code ldc},
 *       {@code ldc_w}, {@code aconst_null}, {@code newarray} (boolean, byte, short, int),
 *       {@code dup}, {@code bastore}, {@code sastore}, {@code iastore}, {@code putstatic},
 *       {@code return}.</li>
 * </ul>
 * Anything else is reported as a {@link Violation} (with the source line when the class file
 * has line numbers), because silently dropping an initializer leaves the field at its default
 * value on the card.
 */
public final class ClinitInterpreter {

    /** Largest array length of the Java Card VM (array indices are short values). */
    private static final int MAX_ARRAY_LENGTH = 0x7FFF;
    /** array_init_info.count is a u2 byte count (JCVM 3.1 §6.11). */
    private static final int MAX_INIT_BYTES = 0xFFFF;

    private ClinitInterpreter() {}

    /**
     * Evaluates the {@code <clinit>} method of a class, if it has one.
     *
     * @param model          the parsed class file
     * @param libraryPackage {@code true} if the package defines no applets (arrays may then not
     *                       be initialized, JCVM 3.1 §2.2.4.6 and §6.11 {@code array_init_count})
     * @param violations     receives a description of every unsupported initializer
     * @return field name to initial value for the fields {@code <clinit>} assigns, in assignment
     *         order; empty if the class has no {@code <clinit>} or it is unsupported
     */
    public static Map<String, StaticValue> interpret(ClassModel model, boolean libraryPackage,
                                                     List<Violation> violations) {
        Optional<CodeModel> code = model.methods().stream()
                .filter(m -> m.methodName().equalsString("<clinit>"))
                .findFirst()
                .flatMap(MethodModel::code);
        if (code.isEmpty()) {
            return Map.of();
        }
        Evaluation evaluation = new Evaluation(model, libraryPackage);
        for (CodeElement element : code.get()) {
            if (!evaluation.step(element)) {
                violations.add(evaluation.violation());
                return Map.of();
            }
        }
        return evaluation.result();
    }

    /** Mutable array created by {@code newarray}; identity matters (it may be stored twice). */
    private static final class ArrayObject {
        final int type;
        final int[] elements;

        ArrayObject(int type, int length) {
            this.type = type;
            this.elements = new int[length];
        }
    }

    /** Marker for {@code aconst_null} on the operand stack. */
    private static final Object NULL = new Object();

    /** Symbolic execution of one {@code <clinit>} method. */
    private static final class Evaluation {
        private final ClassModel model;
        private final String self;
        private final boolean isInterface;
        private final boolean libraryPackage;
        private final Deque<Object> stack = new ArrayDeque<>();
        private final Map<String, Object> fields = new LinkedHashMap<>();
        private final Map<ArrayObject, String> stored = new IdentityHashMap<>();
        private int bci;
        private int line = -1;
        private String problem;
        private int problemBci;

        Evaluation(ClassModel model, boolean libraryPackage) {
            this.model = model;
            this.self = model.thisClass().asInternalName();
            this.isInterface = model.flags().has(AccessFlag.INTERFACE);
            this.libraryPackage = libraryPackage;
        }

        /** Executes one code element; returns {@code false} on an unsupported construct. */
        boolean step(CodeElement element) {
            if (element instanceof LineNumber ln) {
                line = ln.line();
                return true;
            }
            if (!(element instanceof Instruction ins)) {
                return true; // labels, local variable tables, stack maps
            }
            boolean ok = execute(ins);
            if (!ok) {
                problemBci = bci;
            }
            bci += ins.sizeInBytes();
            return ok;
        }

        private boolean execute(Instruction ins) {
            return switch (ins) {
                case ConstantInstruction c -> constant(c);
                case NewPrimitiveArrayInstruction n -> newArray(n);
                case ArrayStoreInstruction a -> arrayStore(a);
                case FieldInstruction f when f.opcode() == Opcode.PUTSTATIC -> putStatic(f);
                default -> switch (ins.opcode()) {
                    case DUP -> stack.isEmpty() ? fail("dup on an empty stack") : push(stack.peek());
                    case RETURN -> true;
                    default -> fail("unsupported bytecode " + ins.opcode().name().toLowerCase()
                            + " in a static initializer");
                };
            };
        }

        private boolean constant(ConstantInstruction c) {
            if (c.opcode() == Opcode.ACONST_NULL) {
                return push(NULL);
            }
            if (c.constantValue() instanceof Integer value) {
                return push(value);
            }
            return fail("unsupported constant " + c.constantValue() + " in a static initializer");
        }

        private boolean newArray(NewPrimitiveArrayInstruction n) {
            if (isInterface || libraryPackage) {
                return fail(isInterface
                        ? "static fields of an interface may only be initialized to primitive"
                                + " compile-time constants"
                        : "static fields of a library package may only be initialized to primitive"
                                + " compile-time constants, not arrays");
            }
            int type = switch (n.typeKind()) {
                case BOOLEAN -> StaticValue.PrimitiveArray.BOOLEAN;
                case BYTE -> StaticValue.PrimitiveArray.BYTE;
                case SHORT -> StaticValue.PrimitiveArray.SHORT;
                case INT -> StaticValue.PrimitiveArray.INT;
                default -> 0;
            };
            if (type == 0 || !(pop() instanceof Integer length) || length < 0) {
                return fail("unsupported array creation " + n.typeKind() + " in a static initializer");
            }
            int bytes = length * (type == StaticValue.PrimitiveArray.INT ? 4
                    : type == StaticValue.PrimitiveArray.SHORT ? 2 : 1);
            if (length > MAX_ARRAY_LENGTH || bytes > MAX_INIT_BYTES) {
                return fail("array of " + length + " elements is too large for array_init_info"
                        + " (at most 32767 elements and 65535 bytes, JCVM 3.1 §6.11)");
            }
            return push(new ArrayObject(type, length));
        }

        private boolean arrayStore(ArrayStoreInstruction a) {
            Object value = pop();
            Object index = pop();
            Object array = pop();
            if (!(array instanceof ArrayObject arr) || !(index instanceof Integer i) || !(value instanceof Integer v)
                    || i < 0 || i >= arr.elements.length || !storeMatches(a.opcode(), arr.type)) {
                return fail("unsupported array element store in a static initializer");
            }
            arr.elements[i] = v;
            return true;
        }

        private static boolean storeMatches(Opcode store, int arrayType) {
            return switch (store) {
                case BASTORE -> arrayType == StaticValue.PrimitiveArray.BYTE
                        || arrayType == StaticValue.PrimitiveArray.BOOLEAN;
                case SASTORE -> arrayType == StaticValue.PrimitiveArray.SHORT;
                case IASTORE -> arrayType == StaticValue.PrimitiveArray.INT;
                default -> false;
            };
        }

        private boolean putStatic(FieldInstruction f) {
            String name = f.name().stringValue();
            if (isInterface) {
                // javac stores only fields that are not constant variables; an interface's fields
                // are final, so any store is a non-constant initializer (and §6.14.2 gives an
                // interface no field descriptors that could describe its static field image slot)
                return fail("interface field " + f.owner().asInternalName() + "." + name + " is not a"
                        + " primitive compile-time constant; static fields of an interface may only be"
                        + " initialized to primitive compile-time constants");
            }
            if (!f.owner().asInternalName().equals(self) || declaredStaticField(name).isEmpty()) {
                return fail("a static initializer may only initialize static fields declared by its own"
                        + " class, not " + f.owner().asInternalName() + "." + name);
            }
            Object value = pop();
            boolean primitive = isPrimitive(f.typeSymbol().descriptorString());
            if (value == null || primitive != (value instanceof Integer)) {
                return fail("unsupported value for static field " + name);
            }
            if (value instanceof ArrayObject arr) {
                if (stored.containsKey(arr) && !stored.get(arr).equals(name)) {
                    return fail("the same array is assigned to static fields " + stored.get(arr) + " and " + name
                            + "; the static field image initializes every field with its own array");
                }
                stored.put(arr, name);
            }
            fields.put(name, value);
            return true;
        }

        private Optional<FieldModel> declaredStaticField(String name) {
            return model.fields().stream()
                    .filter(fm -> fm.fieldName().equalsString(name) && fm.flags().has(AccessFlag.STATIC))
                    .findFirst();
        }

        private static boolean isPrimitive(String descriptor) {
            return descriptor.length() == 1;
        }

        private boolean push(Object value) {
            stack.push(value);
            return true;
        }

        private Object pop() {
            return stack.isEmpty() ? null : stack.pop();
        }

        private boolean fail(String message) {
            problem = message;
            return false;
        }

        Violation violation() {
            String where = line >= 0 ? "<clinit> (line " + line + ")" : "<clinit>";
            return new Violation(self, where, problemBci, problem
                    + " (JCVM 3.1 §2.2.4.6: only primitive compile-time constants and, in applet packages,"
                    + " arrays of them can be represented in the Static Field component)");
        }

        Map<String, StaticValue> result() {
            Map<String, StaticValue> result = new LinkedHashMap<>();
            fields.forEach((name, value) -> result.put(name, toStaticValue(name, value)));
            return result;
        }

        private StaticValue toStaticValue(String field, Object value) {
            if (value instanceof ArrayObject arr) {
                return new StaticValue.PrimitiveArray(arr.type, narrow(arr.elements, arr.type));
            }
            if (value instanceof Integer v) {
                String desc = declaredStaticField(field).orElseThrow().fieldType().stringValue();
                return new StaticValue.Primitive(narrow(v, desc));
            }
            return new StaticValue.Null();
        }

        private static int[] narrow(int[] elements, int type) {
            int[] result = new int[elements.length];
            for (int i = 0; i < elements.length; i++) {
                result[i] = switch (type) {
                    case StaticValue.PrimitiveArray.BOOLEAN -> elements[i] & 1;
                    case StaticValue.PrimitiveArray.BYTE -> (byte) elements[i];
                    case StaticValue.PrimitiveArray.SHORT -> (short) elements[i];
                    default -> elements[i];
                };
            }
            return result;
        }

        private static int narrow(int value, String descriptor) {
            return switch (descriptor) {
                case "Z" -> value & 1;
                case "B" -> (byte) value;
                case "S", "C" -> (short) value;
                default -> value;
            };
        }
    }
}
