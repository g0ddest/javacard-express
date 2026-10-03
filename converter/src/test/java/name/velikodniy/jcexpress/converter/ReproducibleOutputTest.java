package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.testutil.JavaSources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same input must give byte-identical CAP and export files: CAP hashes, DAP signatures and
 * reproducible builds depend on it. Applets keep their registration order (Applet component,
 * JCVM 3.1 §6.6) and JAR entries carry a fixed timestamp.
 */
class ReproducibleOutputTest {

    @TempDir
    Path classes;

    private ConverterResult convert() throws ConverterException {
        return Converter.builder()
                .classesDirectory(classes)
                .packageName("com.acme.repro")
                .packageAid("A000000FFE10")
                .applet("com.acme.repro.B", "A000000FFE1002")
                .applet("com.acme.repro.A", "A000000FFE1001")
                .applet("com.acme.repro.C", "A000000FFE1003")
                .build().convert();
    }

    @Test
    void twoConversionsOfTheSameInputAreByteIdentical() throws Exception {
        JavaSources.compile(classes, Map.of("com.acme.repro.A", """
                package com.acme.repro;
                import javacard.framework.*;
                public class A extends Applet {
                    public static void install(byte[] b, short o, byte l) { new A().register(); }
                    public void process(APDU apdu) { }
                }
                class B extends A {
                    public static void install(byte[] b, short o, byte l) { new B().register(); }
                }
                class C extends A {
                    public static void install(byte[] b, short o, byte l) { new C().register(); }
                }
                """));
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            ConverterResult first = convert();
            TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
            ConverterResult second = convert();

            assertThat(second.capFile()).isEqualTo(first.capFile());
            assertThat(second.exportFile()).isEqualTo(first.exportFile());
            assertEntryTimesAreFixed(first.capFile());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private static void assertEntryTimesAreFixed(byte[] cap) throws Exception {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(cap))) {
            for (ZipEntry e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) {
                assertThat(e.getTimeLocal()).as(e.getName()).isEqualTo(LocalDateTime.of(1980, 2, 1, 0, 0));
            }
        }
    }
}
