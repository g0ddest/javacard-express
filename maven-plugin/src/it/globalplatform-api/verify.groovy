import name.velikodniy.jcexpress.converter.JavaCardVersion
import name.velikodniy.jcexpress.converter.resolve.GlobalPlatformApi
import name.velikodniy.jcexpress.plugin.testing.CapFile
import name.velikodniy.jcexpress.plugin.testing.ItChecks

// The applet uses GPSystem and SecureChannel of org.globalplatform, and the POM has nothing for that package: the
// stubs come with javacard-express-api and the converter links the package from its built-in data
CapFile cap = ItChecks.cap(basedir, 'target/globalplatform-api-1.0.cap')
assert cap.applets()*.aid() == ['A0000000627001']
// JCVM 3.1 4.5.2, 6.7: the Import component names the lowest version of the package that has everything the applet
// uses, here 1.0 (the version of GlobalPlatform 2.1.1 cards), so every card with the package can link the CAP file
def globalPlatform = cap.imports().find { it.aid() == 'A00000015100' }
assert globalPlatform != null : "no org.globalplatform import in ${cap.imports()}"
assert globalPlatform.major() == 1 && globalPlatform.minor() == 0 :
        "org.globalplatform ${globalPlatform.major()}.${globalPlatform.minor()}"
ItChecks.assertLogContains(basedir, 'GlobalPlatform API: org.globalplatform 1.0 (the lowest version that has'
        + ' everything the package uses')

// the build descriptor names a version only when the POM fixes one with <globalPlatformApi>
def descriptor = new Properties()
new File(basedir, 'target/classes/META-INF/javacard/com.example.vault.properties').withInputStream { descriptor.load(it) }
assert descriptor.getProperty('package') == 'com.example.vault'
assert descriptor.getProperty('globalPlatformApi') == null

// Oracle's verifier reads the export file of every imported package from a file; a kit has none for this
// package, so it gets the one written from the converter's built-in data
File exportFile = new File(basedir, 'target/globalplatform.exp')
exportFile.bytes = GlobalPlatformApi.exportFileBytes('1.0', JavaCardVersion.V3_0_5)
ItChecks.verifycap(basedir, verifycapMode, oracleSdk, new File(basedir, 'target/globalplatform-api-1.0.cap'), exportFile)
return true
