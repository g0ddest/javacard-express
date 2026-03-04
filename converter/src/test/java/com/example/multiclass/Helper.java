package com.example.multiclass;

/**
 * Helper class for multi-class test — exercises cross-class field access
 * and method calls within the same package.
 */
public class Helper {

    private short counter;

    public Helper() {
        counter = 0;
    }

    public short increment() {
        counter++;
        return counter;
    }

    public short getCounter() {
        return counter;
    }

    public void reset() {
        counter = 0;
    }
}
