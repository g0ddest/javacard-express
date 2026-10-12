import name.velikodniy.jcexpress.plugin.testing.ItChecks

// The parent gives the module JUnit 4; the extension still added the toolkit's test runtime and warned about nothing
ItChecks.assertLogContains(basedir, ' set up com.example:app:', 'dependencies javacard-express-core (test),'
        + ' javacard-express-livecard (test), javacard-express-api (provided)')
ItChecks.assertLogLacks(basedir, 'did not add javacard-express-core')
// the README test (JUnit 5) compiled and ran on jCardSim
def report = new File(basedir, 'app/target/surefire-reports/TEST-com.example.hello.HelloWorldAppletTest.xml')
assert report.isFile() : 'the README test did not run'
def suite = (report.text =~ /<testsuite\b[^>]*>/)[0]
def attr = { String name -> (suite =~ /\b${name}="(\d+)"/)[0][1] }
assert attr('tests') == '1' && attr('failures') == '0' && attr('errors') == '0' : "README test: ${suite}"
assert new File(basedir, 'app/target/app-1.0-SNAPSHOT.cap').isFile()
return true
