import name.velikodniy.jcexpress.plugin.testing.CapFile
import name.velikodniy.jcexpress.plugin.testing.ItChecks

CapFile cap = ItChecks.cap(basedir, 'target/library-export-1.2.cap')
assert cap.header().packageAid() == 'A0000000623301'
assert cap.header().packageMajor() == 1 && cap.header().packageMinor() == 2
// JCVM 3.1 6.13: a library package exports its public classes; ACC_EXPORT flags the component (6.4)
assert cap.applets().isEmpty()
assert cap.has('Export')
assert (cap.header().flags() & CapFile.ACC_EXPORT) == CapFile.ACC_EXPORT
assert (cap.header().flags() & CapFile.ACC_APPLET) == 0
def exp = new File(basedir, 'target/library-export-1.2.exp')
assert exp.isFile() && exp.length() > 0

// attached artifacts (types cap and exp) are installed next to the jar
def installed = new File(localRepositoryPath, 'com/example/its/library-export/1.2')
assert new File(installed, 'library-export-1.2.cap').bytes == new File(basedir, 'target/library-export-1.2.cap').bytes
assert new File(installed, 'library-export-1.2.exp').bytes == exp.bytes
assert new File(installed, 'library-export-1.2.jar').isFile()

ItChecks.verifycap(basedir, verifycapMode, oracleSdk, new File(basedir, 'target/library-export-1.2.cap'), exp)
return true
