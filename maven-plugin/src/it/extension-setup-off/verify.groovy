import name.velikodniy.jcexpress.plugin.testing.ItChecks

ItChecks.assertLogLacks(basedir, ' set up com.example:setup-off', 'Java Card: ')
assert new File(basedir, 'target/classes/com/example/bad/BadApplet.class').isFile()
return true
