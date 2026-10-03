package com.example.lib;

/** Library class with a package-private hook that other packages cannot override (§4.3.7.6). */
public class LibBase {
    protected short v;

    void check() {
        v = 1;
    }

    public short run() {
        check();
        return v;
    }
}
