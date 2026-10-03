package com.example.miranda;

/**
 * Abstract class that implements {@link Command} without declaring {@code run}: javac emits no
 * method for it (a "Miranda" method), but the Class component must map the interface method to a
 * virtual method of this class (JCVM 3.1 §6.9.2.5), so the converter declares {@code run} public
 * abstract in this class.
 */
public abstract class Base implements Command {
    public short twice(short v) {
        return (short) (run(v) * 2);
    }
}
