import name.velikodniy.jcexpress.plugin.testing.CapFile
import name.velikodniy.jcexpress.plugin.testing.ItChecks

// README Quick Start: `mvn package` must run the plugin and produce the CAP file, and run the applet
// test on jCardSim (recommended class path: javacard-express-core before the API stubs, stubs
// excluded from the surefire runtime).
def report = new File(basedir, 'target/surefire-reports/TEST-com.example.hello.HelloWorldAppletTest.xml')
assert report.isFile() : 'the README test did not run'
def suite = (report.text =~ /<testsuite\b[^>]*>/)[0]
def attr = { String name -> (suite =~ /\b${name}="(\d+)"/)[0][1] }
assert attr('tests') == '1' && attr('failures') == '0' && attr('errors') == '0' && attr('skipped') == '0' :
        "README test on jCardSim: ${suite}"
ItChecks.assertLogLacks(basedir, 'Internal reflection error')

def capFile = new File(basedir, 'target/hello-applet-1.0-SNAPSHOT.cap')
assert capFile.isFile() : 'mvn package with the README pom produced no CAP file'
ItChecks.assertLogContains(basedir, 'javacard-express:' + projectVersion + ':build')

CapFile cap = ItChecks.cap(basedir, 'target/hello-applet-1.0-SNAPSHOT.cap')
assert cap.header().packageAid() == 'A00000006212'
assert cap.applets().size() == 1
// JCVM 3.1 4.2.2.2 / 6.6: the applet AID keeps the package RID (package AID + index)
assert cap.applets()[0].aid() == 'A0000000621201'
// JCVM 3.1 6.13 / 5.6.1: an applet package without shareable interfaces exports nothing
assert !cap.has('Export')
assert (cap.header().flags() & CapFile.ACC_EXPORT) == 0
assert !new File(basedir, 'target/hello-applet-1.0-SNAPSHOT.exp').exists()

// The goal runs in the process-classes phase: the CAP file is built before the tests run, and the
// classes directory (so the jar) holds it with the build descriptor the card test backends read
def log = new File(basedir, 'build.log').text
assert log.indexOf('javacard-express:' + projectVersion + ':build') < log.indexOf('T E S T S') :
        'the plugin did not run before the tests'
assert new File(basedir, 'target/classes/com/example/hello/javacard/hello.cap').bytes == capFile.bytes
def descriptor = new Properties()
new File(basedir, 'target/classes/META-INF/javacard/com.example.hello.properties').withInputStream { descriptor.load(it) }
assert descriptor.getProperty('applet.com.example.hello.HelloWorldApplet') == 'A0000000621201'
assert descriptor.getProperty('javaCardVersion') == '3.0.5'
assert descriptor.getProperty('project') == 'com.example:hello-applet'

ItChecks.verifycap(basedir, verifycapMode, oracleSdk, capFile)
return true
