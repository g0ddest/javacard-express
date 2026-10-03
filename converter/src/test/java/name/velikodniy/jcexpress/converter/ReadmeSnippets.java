package name.velikodniy.jcexpress.converter;

import java.nio.file.Files;
import java.nio.file.Path;
import name.velikodniy.jcexpress.converter.Converter;
import name.velikodniy.jcexpress.converter.ConverterException;
import name.velikodniy.jcexpress.converter.ConverterResult;
import name.velikodniy.jcexpress.converter.JavaCardVersion;

/**
 * The code blocks of converter/README.md, compiled against the current API. {@link ReadmeSnippetsTest} checks
 * that every README code line occurs here.
 */
final class ReadmeSnippets {

    private ReadmeSnippets() {
    }

    /** "Usage". */
    static void usage() throws Exception {
        ConverterResult result = Converter.builder()
            .classesDirectory(Path.of("target/classes"))
            .packageName("com.example.myapplet")
            .packageAid("A00000006212")
            .packageVersion(1, 0)
            .applet("com.example.myapplet.MyApplet", "A0000000621201")
            .javaCardVersion(JavaCardVersion.V3_0_4)
            .build()
            .convert();                                  // throws ConverterException

        Files.write(Path.of("myapplet.cap"), result.capFile());
        result.warnings().forEach(System.err::println);
    }

    /** "A rejected conversion reports what the failing check found". */
    static void rejected(Converter converter) {
        try {
            converter.convert();
        } catch (ConverterException e) {
            if (e.violations().isEmpty()) {
                System.err.println(e.getMessage());          // AID, link, limit and other errors
            } else {
                e.violations().forEach(System.err::println); // class, method, bytecode index, source file and line, reason
            }
        }
    }
}
