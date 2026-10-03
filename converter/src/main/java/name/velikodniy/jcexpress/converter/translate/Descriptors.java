package name.velikodniy.jcexpress.converter.translate;

import java.util.ArrayList;
import java.util.List;

/** JVM method descriptor helpers (JVMS §4.3.3). */
final class Descriptors {

    private Descriptors() {}

    /**
     * Returns the field descriptors of the parameters of a method descriptor.
     *
     * @param methodDescriptor e.g. {@code ([BSB)V}
     * @return e.g. {@code [[B, S, B]}
     */
    static List<String> parameters(String methodDescriptor) {
        List<String> params = new ArrayList<>();
        int i = 1;
        while (methodDescriptor.charAt(i) != ')') {
            int start = i;
            while (methodDescriptor.charAt(i) == '[') {
                i++;
            }
            i = methodDescriptor.charAt(i) == 'L' ? methodDescriptor.indexOf(';', i) + 1 : i + 1;
            params.add(methodDescriptor.substring(start, i));
        }
        return params;
    }

    /** Returns the return type descriptor of a method descriptor. */
    static String returnType(String methodDescriptor) {
        return methodDescriptor.substring(methodDescriptor.indexOf(')') + 1);
    }

    /**
     * Number of JCVM words taken by the parameters: one per parameter, two for an int parameter
     * when the target supports int (JCVM 3.1 §6.10.4 "Parameters of type int are represented in
     * two words").
     *
     * @param methodDescriptor method descriptor
     * @param intWords         whether int parameters take two words
     * @return parameter words, without {@code this}
     */
    static int argumentWords(String methodDescriptor, boolean intWords) {
        int words = 0;
        for (String p : parameters(methodDescriptor)) {
            words += intWords && p.equals("I") ? 2 : 1;
        }
        return words;
    }

    /** Number of JVM local variable slots taken by the parameters (long and double take two). */
    static int argumentSlots(String methodDescriptor) {
        int slots = 0;
        for (String p : parameters(methodDescriptor)) {
            slots += p.equals("J") || p.equals("D") ? 2 : 1;
        }
        return slots;
    }
}
