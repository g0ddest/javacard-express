import name.velikodniy.jcexpress.plugin.testing.ItChecks

// 1: the extension added only what the project lacks, and said so
ItChecks.assertLogContains(basedir, ' set up com.example:own-settings:',
        'dependencies javacard-express-livecard (test), javacard-express-api (provided)', 'tests for Java 25')
ItChecks.assertLogLacks(basedir, 'applet code for Java 8', 'dependencies javacard-express-core')
def log = ItChecks.buildLog(basedir)
// the compiler got the project's processor path first, then the check, and the settings of the build goal
def processorPath = (log =~ /-processorpath (\S+)/)[0][1]
assert processorPath.indexOf('assertj-core') >= 0 && processorPath.indexOf('assertj-core') <
        processorPath.indexOf('javacard-express-javac-plugin') : "processor path: ${processorPath}"
assert log.contains('-Ajavacard.version=3.0.5') && log.contains('-Ajavacard.packageName=com.example.hello')
assert log.contains('Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed') &&
        log.contains('in com.example.hello.HelloWorldAppletTest') : 'the applet test did not pass'

// 2: the check of the host package found its long field
def tool = new File(basedir, 'src/main/java/com/example/host/Tool.java').canonicalPath
ItChecks.assertLogContains(basedir, "[ERROR] ${tool}:[4,10] Java Card: long type not supported in JavaCard")

// the project's own release 11 for the applet code (class file major version 55); goal 2 ran 'clean compile', so
// only the main classes are left to look at: the tests' Java 25 shows in the log line of goal 1 ('tests for Java 25')
// and in the test run above
def major = { String path -> new File(basedir, path).withDataInputStream { it.skipBytes(6); it.readUnsignedShort() } }
assert major('target/classes/com/example/hello/HelloWorldApplet.class') == 55
return true
