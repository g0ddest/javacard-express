package com.example.ifstat;

/**
 * Interface with a field that is not a primitive compile-time constant: javac initializes it in
 * {@code <clinit>}, which JCVM 3.1 §2.2.4.6 does not allow for interfaces (and an interface's
 * Descriptor entry has no fields, §6.14.2). The converter must reject the package.
 */
public interface Config {
    Object LOCK = null;
    short SIZE = 5;

    short ping(short v);
}
