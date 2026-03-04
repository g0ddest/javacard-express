package com.example.forbidden;

/**
 * Test fixture: uses types forbidden in JavaCard (float, double, long).
 * Used by SubsetCheckerTest to verify that violations are detected.
 * <p>
 * Moved to separate package to avoid interfering with converter end-to-end tests
 * that scan the {@code com.example} package.
 */
@SuppressWarnings("all")
public class ForbiddenUsages {

    public float floatField = 1.0f;
    public double doubleField = 2.0;
    public long longField = 42L;

    public float getFloat() {
        return floatField;
    }

    public double getDouble() {
        return doubleField;
    }

    public long getLong() {
        return longField;
    }

    public int addLongs(long a, long b) {
        return (int) (a + b);
    }
}
