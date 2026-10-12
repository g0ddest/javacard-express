package com.example.hello;

import org.testng.Assert;
import org.testng.annotations.Test;

public class HelloTest {

    @Test
    public void passes() {
        Assert.assertEquals("Hello".length(), 5);
    }

    @Test
    public void failsOnPurpose() {
        Assert.fail("TESTNG-RAN-THIS-TEST");
    }
}
