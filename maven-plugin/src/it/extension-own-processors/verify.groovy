import name.velikodniy.jcexpress.plugin.testing.ItChecks

// The processor of each applet module ran: named with <annotationProcessors>, and discovered with <proc>full</proc>
ItChecks.assertLogContains(basedir, 'OWN-PROCESSOR-RAN for com.example.named.NamedApplet',
        'OWN-PROCESSOR-RAN for com.example.discovered.DiscoveredApplet')
// the extension set both modules up without the javac check, and said why and how to get it
def reason = 'no javac check: the project runs annotation processors from its class path, which a processor path' +
        ' would hide from javac; to get the check, list them and name.velikodniy:javacard-express-javac-plugin in' +
        ' annotationProcessorPaths of default-compile'
ItChecks.assertLogContains(basedir, ' set up com.example:named:', ' set up com.example:discovered:', reason)
ItChecks.assertLogLacks(basedir, 'Annotation processor \'com.example.proc.OwnProcessor\' not found')
// and both packages were converted
assert new File(basedir, 'named/target/named-1.0-SNAPSHOT.cap').isFile()
assert new File(basedir, 'discovered/target/discovered-1.0-SNAPSHOT.cap').isFile()
return true
