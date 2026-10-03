package com.jcx.livecard.dispatch;

/** Abstract base: an abstract method, a package-private method subclasses override, a concrete caller. */
abstract class Shape {

    abstract short area();

    short sides() {
        return 0;
    }

    short describe() {
        return (short) (area() * 10 + sides());
    }
}
