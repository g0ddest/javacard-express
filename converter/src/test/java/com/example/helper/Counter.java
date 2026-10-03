package com.example.helper;

/** Package-private helper class: it must not consume a public class token (§4.3.7.2). */
final class Counter {
    short value;

    static short next(short v) {
        return (short) (v + 1);
    }
}
