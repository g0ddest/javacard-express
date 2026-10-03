package com.example.inheritstatic;

/** Intermediate class without static members. */
public class Middle extends Base {
    public byte midB;
    Object midObj;

    public Middle() {
        super();
        midObj = baseArr;
    }
}
