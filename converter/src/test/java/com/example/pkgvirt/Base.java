package com.example.pkgvirt;

/**
 * Base class with package-private virtual methods: they receive private virtual method
 * tokens (JCVM 3.1 §4.3.7.6) and live in the package_virtual_method_table (§6.9.2.3).
 */
public class Base {
    short calc(short x) {
        return (short) (x + 1);
    }

    public short pub() {
        return 100;
    }

    void touch() {
    }
}
