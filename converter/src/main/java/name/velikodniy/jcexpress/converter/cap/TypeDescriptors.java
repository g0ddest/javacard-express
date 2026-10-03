package name.velikodniy.jcexpress.converter.cap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Encodes field types and method signatures as {@code type_descriptor} nibbles
 * (JCVM 3.1 §6.9.1, Table 6-5).
 *
 * <p>Values: 1 void, 2 boolean, 3 byte, 4 short, 5 int, 6 reference, 0xA-0xD arrays of boolean,
 * byte, short and int, 0xE array of reference. A reference (or array of reference) is followed
 * by the four nibbles of its {@code class_ref}. A method signature lists the parameter types
 * followed by the return type.
 */
final class TypeDescriptors {

    private TypeDescriptors() {}

    /**
     * Converts a JVM field or method descriptor into nibbles.
     *
     * @param descriptor       JVM type or method descriptor
     * @param classRefResolver internal class name to its {@code class_ref}
     * @return the nibbles
     */
    static int[] nibbles(String descriptor, Function<String, Integer> classRefResolver) {
        if (!descriptor.startsWith("(")) {
            return typeNibbles(descriptor, classRefResolver);
        }
        List<Integer> nibbles = new ArrayList<>();
        int i = 1;
        while (descriptor.charAt(i) != ')') {
            int end = typeEnd(descriptor, i);
            for (int n : typeNibbles(descriptor.substring(i, end), classRefResolver)) {
                nibbles.add(n);
            }
            i = end;
        }
        for (int n : typeNibbles(descriptor.substring(i + 1), classRefResolver)) {
            nibbles.add(n);
        }
        return nibbles.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Returns the index just after the field type starting at {@code start}. */
    private static int typeEnd(String descriptor, int start) {
        int i = start;
        while (descriptor.charAt(i) == '[') {
            i++;
        }
        return descriptor.charAt(i) == 'L' ? descriptor.indexOf(';', i) + 1 : i + 1;
    }

    /**
     * Nibbles of one field type: a single nibble for primitives and primitive arrays, five for
     * references and reference arrays.
     */
    static int[] typeNibbles(String descriptor, Function<String, Integer> classRefResolver) {
        char c = descriptor.charAt(0);
        if (c == 'L') {
            return withClassRef(6, descriptor.substring(1, descriptor.length() - 1), classRefResolver);
        }
        if (c == '[' && descriptor.charAt(1) == 'L') {
            return withClassRef(0xE, descriptor.substring(2, descriptor.length() - 1), classRefResolver);
        }
        return new int[] {nibble(descriptor)};
    }

    private static int[] withClassRef(int kind, String className, Function<String, Integer> classRefResolver) {
        int ref = classRefResolver.apply(className);
        return new int[] {kind, (ref >> 12) & 0xF, (ref >> 8) & 0xF, (ref >> 4) & 0xF, ref & 0xF};
    }

    /**
     * Single nibble of a type without its class_ref (references give 6, arrays of arrays and of
     * references give 0xE).
     */
    static int nibble(String descriptor) {
        return switch (descriptor.charAt(0)) {
            case 'V' -> 1;
            case 'Z' -> 2;
            case 'B' -> 3;
            case 'S', 'C' -> 4;
            case 'I' -> 5;
            case '[' -> arrayNibble(descriptor.charAt(1));
            default -> 6;
        };
    }

    private static int arrayNibble(char elementType) {
        return switch (elementType) {
            case 'Z' -> 0xA;
            case 'B' -> 0xB;
            case 'S', 'C' -> 0xC;
            case 'I' -> 0xD;
            default -> 0xE;
        };
    }

    /**
     * Encodes a field type for {@code field_descriptor_info.type} (§6.14.3): primitives as
     * {@code 0x8000 | value} (Table 6-19), references as an offset into the type table.
     */
    static int fieldType(String descriptor, TypeDescriptorTable table, Function<String, Integer> classRefResolver) {
        return switch (descriptor.charAt(0)) {
            case 'Z', 'B', 'S', 'C', 'I' -> 0x8000 | nibble(descriptor);
            default -> table.offsetOf(typeNibbles(descriptor, classRefResolver));
        };
    }
}
