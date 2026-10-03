import name.velikodniy.jcexpress.plugin.testing.CapFile
import name.velikodniy.jcexpress.plugin.testing.ItChecks

ItChecks.assertLogLacks(basedir, "Parameter 'javaCardVersion' is unknown")

CapFile cap = ItChecks.cap(basedir, 'target/javacard-version-1.0.cap')
assert cap.applets()*.aid() == ['A0000000622201']
// JCVM 3.1 6.4: Java Card 2.2.2 uses CAP format 2.1
assert cap.header().formatMajor() == 2
assert cap.header().formatMinor() == 1
// Java Card 2.2.2 API: javacard.framework (AID A0000000620101) is version 1.3, java.lang 1.0
def framework = cap.imports().find { it.aid() == 'A0000000620101' }
assert framework != null : "no javacard.framework import in ${cap.imports()}"
assert framework.major() == 1 && framework.minor() == 3 : "javacard.framework ${framework.major()}.${framework.minor()}"
def lang = cap.imports().find { it.aid() == 'A0000000620001' }
assert lang != null && lang.major() == 1 && lang.minor() == 0

ItChecks.verifycap(basedir, verifycapMode, oracleSdk, new File(basedir, 'target/javacard-version-1.0.cap'))
return true
