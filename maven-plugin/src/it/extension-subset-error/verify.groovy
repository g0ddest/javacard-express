import name.velikodniy.jcexpress.plugin.testing.ItChecks

// javac reports each problem like a compile error: file, [line,column] (of a declaration: its name), the
// converter's message (JCVM 3.1 2.2)
def bad = new File(basedir, 'src/main/java/com/example/bad/BadApplet.java').canonicalPath
ItChecks.assertLogContains(basedir,
        "[ERROR] ${bad}:[7,18] Java Card: long type not supported in JavaCard (JCVM 3.1 §2.2.1.3)",
        "[ERROR] ${bad}:[14,16] Java Card: String constants are not supported (JCVM 3.1 §2.2.1.4)",
        'COMPILATION ERROR')
// the compilation failed, so the plugin never ran, and the class of the source with errors has no class file
ItChecks.assertLogLacks(basedir, 'javacard-express:' + projectVersion + ':build')
assert !new File(basedir, 'target/classes/com/example/bad/BadApplet.class').exists()
return true
