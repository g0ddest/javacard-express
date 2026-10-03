package com.example.util;

/** A library class other Java Card packages can link against. */
public class Checksum {

    /** Prevents instantiation. */
    protected Checksum() {
    }

    /**
     * XOR checksum of a byte range.
     *
     * @param data   the bytes
     * @param offset start offset
     * @param length number of bytes
     * @return the checksum
     */
    public static byte xor(byte[] data, short offset, short length) {
        byte result = 0;
        for (short i = 0; i < length; i++) {
            result ^= data[(short) (offset + i)];
        }
        return result;
    }
}
