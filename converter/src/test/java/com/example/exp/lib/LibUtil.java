package com.example.exp.lib;

/**
 * Library fixture for export/import tests: public static and instance members, a
 * compile-time constant and package-private members that must not be exported.
 */
public class LibUtil {

    /** Compile-time constant: exported with token 0xFF and a ConstantValue (JCVM 3.1 §5.8). */
    public static final short MAGIC = 0x1234;

    /** Public static field: public static field token (JCVM 3.1 §4.3.7.3). */
    public static byte[] table;

    /** Public instance field: public instance field token (JCVM 3.1 §4.3.7.5). */
    public short counter;

    /** Protected instance field: exported as well. */
    protected byte flag;

    /** Package-private field: private token, never exported. */
    short hidden;

    /** Public constructor: public static method token (JCVM 3.1 §4.3.7.4). */
    public LibUtil() {
        counter = 0;
    }

    /**
     * Doubles a value.
     *
     * @param x value
     * @return {@code 2 * x}
     */
    public static short twice(short x) {
        return (short) (x + x);
    }

    /**
     * Increments the counter.
     *
     * @return the new counter value
     */
    public short next() {
        counter++;
        return counter;
    }

    /** Package-visible virtual method: private virtual token, never exported. */
    void touch() {
        hidden++;
    }
}
