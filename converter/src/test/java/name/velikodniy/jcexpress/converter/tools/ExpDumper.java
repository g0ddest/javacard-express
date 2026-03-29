package name.velikodniy.jcexpress.converter.tools;

import name.velikodniy.jcexpress.converter.token.ExportFile;
import name.velikodniy.jcexpress.converter.token.ExportFileReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.stream.Stream;

/**
 * Temporary tool: dumps all Oracle .exp files from JC 3.0.5u3 SDK.
 * Run as: mvn exec:java or as a test.
 */
public class ExpDumper {
    public static void main(String[] args) throws Exception {
        Path sdkBase = Path.of("build/oracle-sdks/jc305u3_kit/api_export_files");
        if (!Files.isDirectory(sdkBase)) {
            System.err.println("SDK not found at: " + sdkBase);
            return;
        }

        var expFiles = Files.walk(sdkBase)
                .filter(p -> p.toString().endsWith(".exp"))
                .sorted()
                .toList();
        for (Path p : expFiles) {
            dumpExpFile(p);
        }
    }

    private static void dumpExpFile(Path path) {
        try {
            ExportFile ef = ExportFileReader.readFile(path);
            System.out.println("=== PACKAGE: " + ef.packageName() + " ===");
            System.out.println("    AID: " + HexFormat.of().formatHex(ef.aid()));
            System.out.println("    Version: " + ef.majorVersion() + "." + ef.minorVersion());
            System.out.println("    Classes: " + ef.classes().size());

            for (var cls : ef.classes()) {
                String type = (cls.accessFlags() & 0x0200) != 0 ? "interface" : "class";
                System.out.printf("    [%d] %s %s (flags=0x%04X)%n",
                        cls.token(), type, cls.name(), cls.accessFlags());

                for (var m : cls.methods()) {
                    String kind = (m.accessFlags() & 0x0008) != 0 ? "static" : "virtual";
                    System.out.printf("        method [%d] %s %s%s (flags=0x%04X)%n",
                            m.token(), kind, m.name(), m.descriptor(), m.accessFlags());
                }

                for (var f : cls.fields()) {
                    System.out.printf("        field  [%d] %s %s (flags=0x%04X)%n",
                            f.token(), f.name(), f.descriptor(), f.accessFlags());
                }
            }
            System.out.println();
        } catch (Exception e) {
            System.err.println("ERROR parsing " + path + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
}
