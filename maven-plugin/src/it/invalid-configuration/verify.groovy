import name.velikodniy.jcexpress.plugin.testing.ItChecks

ItChecks.assertLogContains(basedir,
        // 1: unknown target platform, no silent fallback to 3.0.5
        "Unknown <javaCardVersion> '3.3'. Supported: 2.1.2, 2.2.1, 2.2.2, 3.0.3, 3.0.4, 3.0.5, 3.1.0, 3.2.0.",
        // 2: package version out of the u1 range (JCVM 3.1 4.5, 6.4)
        "Invalid <packageVersion> '1.300'",
        // 3: odd number of hex digits in the package AID
        "Invalid packageAid 'A0000000771'",
        // 4: misspelled applet class, with the applets that do exist
        "Applet class com.example.hello.HeloWorldApplet (configured in <applets>) was not found",
        "Applet classes of package com.example.hello: com.example.hello.HelloWorldApplet")
assert !new File(basedir, 'target/invalid-configuration-1.0.cap').exists()
return true
