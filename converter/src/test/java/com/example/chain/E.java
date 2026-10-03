package com.example.chain;

/**
 * Internal super call through a class (D) that does not redefine m(): javac emits
 * {@code invokespecial D.m()}, which must become a CONSTANT_SuperMethodref (§6.8.2, §7.5.55).
 */
public class E extends D {
    @Override
    public short m() {
        return (short) (super.m() + 1);
    }
}
