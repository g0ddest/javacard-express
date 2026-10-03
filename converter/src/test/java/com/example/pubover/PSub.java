package com.example.pubover;

/**
 * Overrides package-visible methods with public access, which the Java Card language subset
 * forbids (JCVM 3.1 §2.2.1.1): the converter must reject the package.
 */
public class PSub extends PBase {
    @Override
    public void touch() {
    }

    @Override
    public short val() {
        return 2;
    }
}
