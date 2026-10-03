package com.example.inheritstatic;

/**
 * javac uses the qualifying type (JLS §13.1): {@code getstatic Leaf.baseStatic} and
 * {@code invokestatic Leaf.helper} for members declared in {@link Base}.
 */
public class Leaf extends Middle {
    public short leafS;

    public Leaf() {
        super();
    }

    public short sum() {
        return (short) (baseS + leafS + pkgS + midB + baseArr[0]);
    }

    static short statics() {
        Leaf.baseStatic = (short) (Leaf.baseStatic + 1);
        Leaf.baseStaticArr = new byte[1];
        return Leaf.helper(Leaf.baseStatic);
    }
}
