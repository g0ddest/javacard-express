package name.velikodniy.jcexpress.plugin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The build descriptor is a properties file that {@link Properties#load} reads back exactly, whatever characters
 * the package and class names have (Java identifiers may be any Unicode letters).
 */
class ClassesOutputTest {

    @Test
    void namesOutsidePrintableAsciiAreEscaped() throws Exception {
        String text = descriptor("com.example.café", "com.example.café.Applet").text();
        String cyrillic = descriptor("com.example.карта", "com.example.карта.Applet").text();

        assertThat(text).matches("(?s)\\p{ASCII}*").contains("package=com.example.caf\\u00E9\n")
                .contains("applet.com.example.caf\\u00E9.Applet=A0000000621201\n");
        assertThat(load(text).getProperty("package")).isEqualTo("com.example.café");
        assertThat(load(text).getProperty("applet.com.example.café.Applet")).isEqualTo("A0000000621201");
        assertThat(cyrillic).matches("(?s)\\p{ASCII}*");
        assertThat(load(cyrillic).getProperty("package")).isEqualTo("com.example.карта");
    }

    @Test
    void theCapFileIsWhereTheExportFileLookupLooksForTheExportFile() {
        assertThat(ClassesOutput.resource("com.example.wallet", "exp"))
                .isEqualTo(ExportFileLookup.location("com.example.wallet"));
        assertThat(ClassesOutput.resource("com.example.wallet", "cap")).isEqualTo("com/example/wallet/javacard/wallet.cap");
        assertThat(ClassesOutput.descriptorResource("com.example.wallet"))
                .isEqualTo("META-INF/javacard/com.example.wallet.properties");
    }

    private static ClassesOutput.Descriptor descriptor(String pkg, String applet) throws Exception {
        return new ClassesOutput.Descriptor(pkg, Aid.parse("packageAid", "A00000006212"), "1.0", "3.0.4", false, false,
                List.of(new AppletAids.Assigned(applet, Aid.parse("aid", "A0000000621201"), true)),
                "com.example:wallet", null);
    }

    private static Properties load(String text) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(text));
        return properties;
    }
}
