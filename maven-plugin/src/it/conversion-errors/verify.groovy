import name.velikodniy.jcexpress.plugin.testing.ItChecks

def bad = new File(basedir, 'src/main/java/com/example/bad/BadApplet.java').canonicalPath
def jdk = new File(basedir, 'src/main/java/com/example/jdk/JdkApplet.java').canonicalPath

// 1: like a compiler error: file, line, class.method, reason; the failure message does not repeat it
ItChecks.assertLogContains(basedir,
        "[ERROR] ${bad}:[16] com.example.bad.BadApplet.process(): long type not supported",
        "[ERROR] ${bad} com.example.bad.BadApplet (field counter): long type not supported",
        'Cannot convert package com.example.bad: 2 uses of features the Java Card platform does not support')
ItChecks.assertLogLacks(basedir, '[bci ', 'JavaCard subset violations found')

// 2: the class the converter cannot resolve, where it is used, and what to do (the converter's own
// wording, which names the class or only its package, is not pinned here)
ItChecks.assertLogContains(basedir,
        'Cannot convert package com.example.jdk. ',
        "java.util.Arrays is used at ${jdk}:[16] com.example.jdk.JdkApplet.process()",
        '<exportPath>')
return true
