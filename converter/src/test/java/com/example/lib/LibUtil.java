package com.example.lib;

/** Public library class with exported static members (§6.13). */
public class LibUtil {
    public static short counter = 5;
    public static byte[] shared;
    static short internal;

    public LibUtil() {
        internal++;
    }

    public static short twice(short v) {
        return (short) (v * 2);
    }

    static short hidden(short v) {
        return (short) (v + Hidden.offset);
    }
}
