import name.velikodniy.jcexpress.plugin.testing.ItChecks

// Without the build extension, the explicit POM's own compiler block runs the javac check (JCVM 3.1 2.2)
def bad = new File(basedir, 'src/main/java/com/example/bad/BadApplet.java').canonicalPath
ItChecks.assertLogContains(basedir,
        "[ERROR] ${bad}:[7,18] Java Card: long type not supported in JavaCard (JCVM 3.1 §2.2.1.3)",
        'COMPILATION ERROR')
ItChecks.assertLogLacks(basedir, ' set up com.example:hello-applet:')
return true
