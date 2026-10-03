import java.util.zip.ZipFile

// The parent-based Quick Start: the child POM has only its coordinates, the parent and the package AID, and
// `mvn package` still runs the applet's JUnit test on jCardSim and builds the CAP file.
def report = new File(basedir, 'target/surefire-reports/TEST-com.example.hello.HelloWorldAppletTest.xml')
assert report.isFile() : 'the applet test did not run'
def suite = (report.text =~ /<testsuite\b[^>]*>/)[0]
def attr = { String name -> (suite =~ /\b${name}="(\d+)"/)[0][1] }
assert attr('tests') == '1' && attr('failures') == '0' && attr('errors') == '0' && attr('skipped') == '0' :
        "applet test on jCardSim: ${suite}"

def cap = new File(basedir, 'target/hello-applet-1.0-SNAPSHOT.cap')
assert cap.isFile() : 'mvn package with the parent-based POM produced no CAP file'
new ZipFile(cap).withCloseable { zip ->
    def names = zip.entries().collect { it.name }
    assert names.any { it.endsWith('/javacard/Header.cap') } : "not a CAP file: ${names}"
    def header = zip.getInputStream(zip.getEntry(names.find { it.endsWith('/javacard/Header.cap') })).bytes
    // Header component (JCVM 3.1 6.3): tag, size, magic, versions, flags, package_info with the package AID
    def hex = header.collect { String.format('%02X', it & 0xFF) }.join()
    assert hex.contains('06A00000006212') : "package AID A00000006212 not in the Header component: ${hex}"
}
def log = new File(basedir, 'build.log').text
assert log.contains('javacard-express-maven-plugin') || log.contains('javacard-express:') : 'the plugin did not run'
assert !log.contains('Internal reflection error')

// The second build ran the same test class on the simulated GlobalPlatform card: the package was converted with
// the settings of the build descriptor, under the project's AID prefix, loaded, installed, deleted, and the
// cleanup was checked with GET STATUS
def descriptor = new Properties()
new File(basedir, 'target/classes/META-INF/javacard/com.example.hello.properties').withInputStream { descriptor.load(it) }
assert descriptor.getProperty('project') == 'com.example:hello-applet'
def transcript = new File(basedir, 'target/livecard-transcripts/com.example.hello.HelloWorldAppletTest/card.txt')
assert transcript.isFile() : 'the test did not run on the simulated GlobalPlatform card'
def card = transcript.text
assert card.contains('package com.example.hello built by com.example:hello-applet: converted for Java Card 3.0.5 as the build')
assert card.contains('AIDs of the build -> this run: package A00000006212 -> F0')
assert card.contains('cleanup verified with GET STATUS')
return true
