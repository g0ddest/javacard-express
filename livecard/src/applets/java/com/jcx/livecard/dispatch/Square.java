package com.jcx.livecard.dispatch;

/** A square: area side * side, four sides. */
final class Square extends Shape {
    private final short side;

    Square(short side) {
        this.side = side;
    }

    short area() {
        return (short) (side * side);
    }

    short sides() {
        return 4;
    }
}
