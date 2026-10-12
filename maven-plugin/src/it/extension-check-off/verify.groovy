import name.velikodniy.jcexpress.plugin.testing.ItChecks

ItChecks.assertLogContains(basedir, ' set up com.example:check-off:', 'dependencies javacard-express-core (test)')
ItChecks.assertLogLacks(basedir, 'the javac check', 'Java Card: ')
assert new File(basedir, 'target/classes/com/example/bad/BadApplet.class').isFile()
return true
