package name.velikodniy.jcexpress.converter.clinit;

import java.util.Arrays;

/**
 * Initial value that a class's {@code <clinit>} method assigns to one of its static fields.
 *
 * <p>A Java Card VM never executes {@code <clinit>}: its effect is represented in the Static
 * Field component as array initialization data and non-default primitive values (JCVM 3.1
 * §6.11), so the converter evaluates the method at conversion time
 * ({@link ClinitInterpreter}).
 */
public sealed interface StaticValue permits StaticValue.Primitive, StaticValue.PrimitiveArray, StaticValue.Null {

    /**
     * A {@code boolean}, {@code byte}, {@code short} or {@code int} value.
     *
     * @param value the value (already narrowed by the compiler to the field type)
     */
    record Primitive(int value) implements StaticValue {}

    /**
     * An array of a primitive type created and filled by {@code <clinit>} (Static Field component
     * segment 1, {@code array_init_info}, JCVM 3.1 §6.11).
     *
     * @param type     array type of JCVM 3.1 Table 6-15: 2 boolean, 3 byte, 4 short, 5 int
     * @param elements element values
     */
    record PrimitiveArray(int type, int[] elements) implements StaticValue {
        /** Array type boolean (Table 6-15). */
        public static final int BOOLEAN = 2;
        /** Array type byte (Table 6-15). */
        public static final int BYTE = 3;
        /** Array type short (Table 6-15). */
        public static final int SHORT = 4;
        /** Array type int (Table 6-15). */
        public static final int INT = 5;

        /**
         * Creates the value; the element array is copied.
         *
         * @param type     array type (Table 6-15)
         * @param elements element values
         */
        public PrimitiveArray {
            elements = elements.clone();
        }

        /**
         * Returns a copy of the element values.
         *
         * @return element values
         */
        @Override
        public int[] elements() {
            return elements.clone();
        }

        /**
         * Returns the size of one element in the static field image (JCVM 3.1 Table 6-14).
         *
         * @return 1, 2 or 4
         */
        public int elementSize() {
            return switch (type) {
                case SHORT -> 2;
                case INT -> 4;
                default -> 1;
            };
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof PrimitiveArray(int t, int[] e) && t == type && Arrays.equals(e, elements);
        }

        @Override
        public int hashCode() {
            return 31 * type + Arrays.hashCode(elements);
        }

        @Override
        public String toString() {
            return "PrimitiveArray[type=" + type + ", elements=" + Arrays.toString(elements) + "]";
        }
    }

    /** An explicit {@code null} reference (the default value of a reference field). */
    record Null() implements StaticValue {}
}
