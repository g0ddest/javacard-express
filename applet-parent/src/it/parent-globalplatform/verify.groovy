import java.util.zip.ZipFile

// `mvn package`: the applet uses GPSystem and SecureChannel of org.globalplatform; the POM has only its coordinates,
// the parent and the package AID. The applet's JUnit test ran on jCardSim, where the simulated card implements the
// package: it opened a secure channel through the applet.
def report = new File(basedir, 'target/surefire-reports/TEST-com.example.vault.VaultAppletTest.xml')
assert report.isFile() : 'the applet test did not run'
def suite = (report.text =~ /<testsuite\b[^>]*>/)[0]
def attr = { String name -> (suite =~ /\b${name}="(\d+)"/)[0][1] }
// the report is the one of the second build (simulated-gp): the test that changes the simulated Security Domain of
// the default backend is disabled there
assert attr('tests') == '3' && attr('failures') == '0' && attr('errors') == '0' && attr('skipped') == '1' :
        "applet test: ${suite}"

// The CAP file imports org.globalplatform (AID A00000015100) from the converter's built-in data, in version 1.0, the
// lowest that has everything the applet uses (JCVM 3.1 4.5.2, 6.7: minor version, major version, AID)
def cap = new File(basedir, 'target/vault-applet-1.0-SNAPSHOT.cap')
assert cap.isFile() : 'mvn package produced no CAP file'
new ZipFile(cap).withCloseable { zip ->
    def names = zip.entries().collect { it.name }
    def imports = zip.getInputStream(zip.getEntry(names.find { it.endsWith('/javacard/Import.cap') })).bytes
    def hex = imports.collect { String.format('%02X', it & 0xFF) }.join()
    assert hex.contains('000106A00000015100') : "org.globalplatform 1.0 not in the Import component: ${hex}"
}
def log = new File(basedir, 'build.log').text
assert log.contains('GlobalPlatform API: org.globalplatform 1.0 (the lowest version that has everything the package uses')
assert !log.contains('Internal reflection error')

// The second build ran the same test class on the simulated GlobalPlatform card: the package was converted, loaded
// and installed through the Issuer Security Domain, and the INITIALIZE UPDATE and EXTERNAL AUTHENTICATE the test sent
// to the applet passed the APDU guard, which verified the card cryptogram itself
def transcript = new File(basedir, 'target/livecard-transcripts/com.example.vault.VaultAppletTest/card.txt')
assert transcript.isFile() : 'the test did not run on the simulated GlobalPlatform card'
def card = transcript.text
assert card.contains('imports javacard.framework') && card.contains('org.globalplatform 1.0')
assert card.contains('test applet') && card.contains('card cryptogram independently VERIFIED')
assert card.contains('cleanup verified with GET STATUS')
return true
