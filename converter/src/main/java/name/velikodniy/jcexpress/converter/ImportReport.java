package name.velikodniy.jcexpress.converter;

import name.velikodniy.jcexpress.converter.resolve.BuiltinExports;
import name.velikodniy.jcexpress.converter.resolve.GlobalPlatformApi;
import name.velikodniy.jcexpress.converter.resolve.ImportedPackage;
import name.velikodniy.jcexpress.converter.token.ExportFile;

import java.util.HexFormat;
import java.util.List;

/**
 * Describes the packages of a CAP file's Import component (JCVM 3.1 §6.7) for
 * {@link ConverterResult#imports()}: what a card must hold, in which version at least (§4.5.2), to load the file.
 */
final class ImportReport {

    private ImportReport() {}

    /**
     * Describes the final imports.
     *
     * @param imports the imported packages in Import component order
     * @param version the target Java Card version, whose built-in API data may describe a package
     * @return one entry per imported package, in the same order
     */
    static List<ConverterResult.Import> of(List<ImportedPackage> imports, JavaCardVersion version) {
        return imports.stream().map(imported -> new ConverterResult.Import(
                imported.exportFile().packageName().replace('/', '.'),
                HexFormat.of().withUpperCase().formatHex(imported.aid()),
                imported.majorVersion(), imported.minorVersion(), isBuiltIn(imported, version))).toList();
    }

    private static boolean isBuiltIn(ImportedPackage imported, JavaCardVersion version) {
        ExportFile api = BuiltinExports.getExport(imported.exportFile().packageName(), version);
        return GlobalPlatformApi.isBuiltin(imported) || (api != null && api.equals(imported.exportFile()));
    }
}
