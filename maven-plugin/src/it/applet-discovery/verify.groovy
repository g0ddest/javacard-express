import name.velikodniy.jcexpress.plugin.testing.CapFile
import name.velikodniy.jcexpress.plugin.testing.ItChecks

def capFile = new File(basedir, 'target/applet-discovery-1.0.cap')
CapFile cap = ItChecks.cap(basedir, 'target/applet-discovery-1.0.cap')

// JCVM 3.1 6.6: applets are the non-abstract (direct or indirect) subclasses of Applet
ItChecks.assertLogContains(basedir, 'Applet: com.example.wallet.LoyaltyApplet', 'Applet: com.example.wallet.WalletApplet')
ItChecks.assertLogLacks(basedir, 'Applet: com.example.wallet.BaseApplet', 'Applet: com.example.wallet.Amounts')
assert cap.applets().size() == 2

// No <packageAid>: a development AID F0... is derived from the package name, and the applet AIDs
// extend it, so all of them share the package RID (JCVM 3.1 4.2.2.2, 6.6)
String packageAid = cap.header().packageAid()
assert packageAid.startsWith('F0') && packageAid.length() == 16
assert cap.applets()*.aid().sort() == [packageAid + '01', packageAid + '02']
ItChecks.assertLogContains(basedir, 'No <packageAid> configured')

// Each entry points at the static install(byte[],short,byte) method (type nibbles B431, 6.14.5)
cap.applets().each { applet ->
    def install = cap.methodAt(applet.installMethodOffset()).orElseThrow {
        new AssertionError("no method at install offset ${applet.installMethodOffset()}")
    }
    assert (install.method().flags() & CapFile.MethodDescriptor.ACC_STATIC) != 0
    assert (install.method().flags() & CapFile.MethodDescriptor.ACC_INIT) == 0
    assert cap.typeNibbles(install.method().typeOffset()) == 'B431'
}
assert !cap.has('Export')

ItChecks.verifycap(basedir, verifycapMode, oracleSdk, capFile)
return true
