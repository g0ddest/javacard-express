package name.velikodniy.jcexpress.idea;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What the check of one package needs, collected from the IDE: the current texts of the package's sources (unsaved
 * edits included) and the settings of the module's main compilation. Plain data, so that {@link CheckRun} does not
 * depend on the IDE.
 *
 * @param module        the IDE module, to tell packages of different modules apart
 * @param packageName   the package, in dot notation
 * @param sources       the package's Java sources of the main code, as the editor has them
 * @param classpath     the module's compile class path of the main code (the API stubs among it)
 * @param processorPath the annotation processor path of the main compilation: the project's own javac check
 * @param options       the processor options ({@code -Akey=value}) the build passes, by key
 * @param release       the Java release of the main compilation ({@code --release})
 */
record CheckRequest(String module, String packageName, List<Source> sources, List<String> classpath,
                    List<String> processorPath, Map<String, String> options, int release) {

    /**
     * One source file.
     *
     * @param path the file's path, which the problems name
     * @param text its current text
     */
    record Source(String path, String text) {
    }

    CheckRequest {
        sources = List.copyOf(sources);
        classpath = List.copyOf(classpath);
        processorPath = List.copyOf(processorPath);
        options = Map.copyOf(new TreeMap<>(options));
    }

    /**
     * Returns a digest of everything the result depends on: equal keys give equal results.
     *
     * @return a SHA-256 in hex
     */
    String key() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, module, packageName, String.valueOf(release));
            sources.forEach(source -> update(digest, source.path(), source.text()));
            update(digest, String.join("\n", classpath), String.join("\n", processorPath));
            new TreeMap<>(options).forEach((key, value) -> update(digest, key, value));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }

    private static void update(MessageDigest digest, String... parts) {
        for (String part : parts) {
            digest.update(part.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
    }
}
