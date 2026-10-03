import name.velikodniy.jcexpress.plugin.testing.CapFile
import name.velikodniy.jcexpress.plugin.testing.ItChecks

// 1: JCVM 3.1 4.1.2 - a CAP file holds one package; the plugin does not guess
ItChecks.assertLogContains(basedir,
        'Classes found in 2 packages: com.example.wallet (1 class, applet com.example.wallet.WalletApplet),'
                + ' com.example.wallet.extra (1 class, applet com.example.wallet.extra.ExtraApplet)',
        'set <packageName> to the package to convert')

// 2: the configured package is built, the sub-package applet is reported
ItChecks.assertLogContains(basedir, 'Package com.example.wallet.extra (1 class, applet'
        + ' com.example.wallet.extra.ExtraApplet) is not part of this CAP file')
def capFile = new File(basedir, 'target/sub-package-applets-1.0.cap')
CapFile cap = ItChecks.cap(basedir, 'target/sub-package-applets-1.0.cap')
assert cap.header().packageAid() == 'A00000006240'
assert cap.applets()*.aid() == ['A0000000624001']

// 3: two executions, two CAP files, each with its own package and applet
CapFile wallet = ItChecks.cap(basedir, 'target/sub-package-applets-1.0-wallet.cap')
assert wallet.header().packageAid() == 'A00000006240'
assert wallet.applets()*.aid() == ['A0000000624001']
CapFile extra = ItChecks.cap(basedir, 'target/sub-package-applets-1.0-extra.cap')
assert extra.header().packageAid() == 'A00000006241'
assert extra.applets()*.aid() == ['A0000000624101']

ItChecks.verifycap(basedir, verifycapMode, oracleSdk, capFile)
return true
