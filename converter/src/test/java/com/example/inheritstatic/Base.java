package com.example.inheritstatic;

/** Declares instance and static members that subclasses use through their own name. */
public class Base {
    public short baseS;
    protected byte[] baseArr;
    short pkgS;
    static short baseStatic;
    public static byte[] baseStaticArr;

    public Base() {
        baseArr = new byte[2];
    }

    static short helper(short v) {
        return (short) (v + 1);
    }
}
