import name.velikodniy.jcexpress.plugin.testing.CapFile
import name.velikodniy.jcexpress.plugin.testing.ItChecks

def libDir = new File(basedir, 'counter-lib')
def appDir = new File(basedir, 'counter-app')

// The library package is exported: Export component + export file (JCVM 3.1 6.13)
CapFile lib = ItChecks.cap(libDir, 'target/counter-lib-1.0.cap')
assert lib.has('Export') && lib.applets().isEmpty()
def libExp = new File(libDir, 'target/counter-lib-1.0.exp')
assert libExp.isFile()

// The library's jar holds its export file and build descriptor where importing packages and the
// card test backends look for them (JCVM 3.1 5.2)
def libJar = new java.util.jar.JarFile(new File(libDir, 'target/counter-lib-1.0.jar'))
try {
    assert libJar.getEntry('com/example/counter/lib/javacard/lib.exp') != null
    assert libJar.getEntry('com/example/counter/lib/javacard/lib.cap') != null
    def descriptor = new Properties()
    descriptor.load(libJar.getInputStream(libJar.getEntry('META-INF/javacard/com.example.counter.lib.properties')))
    assert descriptor.getProperty('packageAid') == 'A0000000625001'
    assert descriptor.getProperty('export') == 'true'
    assert descriptor.getProperty('project') == 'com.example.its:counter-lib'
} finally {
    libJar.close()
}

// The plugin found the library's export file on the class path (no dependency of type exp), so the
// applet package imports the library package (JCVM 3.1 6.7). The imported version is the
// converter's business (it must be the package version 1.0, not the export file format version).
CapFile app = ItChecks.cap(appDir, 'target/counter-app-1.0.cap')
def imported = app.imports().find { it.aid() == 'A0000000625001' }
assert imported != null : "counter-app does not import counter-lib: ${app.imports()}"
assert app.applets()*.aid() == ['A000000062510101']
assert !app.has('Export')

ItChecks.verifycap(libDir, verifycapMode, oracleSdk, new File(libDir, 'target/counter-lib-1.0.cap'), libExp)
ItChecks.verifycap(appDir, verifycapMode, oracleSdk, new File(appDir, 'target/counter-app-1.0.cap'), libExp)
return true
