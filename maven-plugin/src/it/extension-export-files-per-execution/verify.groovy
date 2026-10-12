import name.velikodniy.jcexpress.plugin.testing.ItChecks

ItChecks.assertLogContains(basedir, ' set up com.example:two-targets:',
        'the javac check of the Java Card subset in the main compilation',
        "the export files of the build goal's executions differ, so the check links against the built-in API data")
ItChecks.assertLogLacks(basedir, 'Two export files were supplied', '-Ajavacard.exportFiles')
assert new File(basedir, 'target/two-targets-1.0-SNAPSHOT-jc304.cap').isFile()
assert new File(basedir, 'target/two-targets-1.0-SNAPSHOT-jc305.cap').isFile()
return true
