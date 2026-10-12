import name.velikodniy.jcexpress.plugin.testing.ItChecks

// The extension left the test runtime out and warned why; it still added the stubs and converted the package
ItChecks.assertLogContains(basedir, ' set up com.example:testng-applet:',
        '[WARNING] JavaCard Express did not add javacard-express-core and javacard-express-livecard to' +
                ' com.example:testng-applet: the project tests with TestNG',
        'dependencies javacard-express-api (provided)')
assert new File(basedir, 'target/testng-applet-1.0-SNAPSHOT.cap').isFile()
// Surefire ran TestNG, both tests ran, and the one that fails on purpose failed the build
ItChecks.assertLogContains(basedir, 'org.apache.maven.surefire.testng.TestNGProvider', 'TESTNG-RAN-THIS-TEST',
        'Tests run: 2, Failures: 1')
ItChecks.assertLogLacks(basedir, 'JUnitPlatformProvider')
return true
