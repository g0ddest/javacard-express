package name.velikodniy.jcexpress.livecard;

import name.velikodniy.jcexpress.converter.JavaCardVersion;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Finds the Oracle Java Card development kit whose off-card verifier checks CAP files when the {@code verifierSdk}
 * setting is not given: {@code build/oracle-sdks/<kit>} under the project root, the place where this project's
 * optional Oracle checks keep the kits (tools/oracle/README.md). The kit must match the conversion target:
 * {@code jc303_kit} for 3.0.3, {@code jc304_kit} for 3.0.4, a {@code jc305u*_kit} for 3.0.5. Nothing is found for
 * other targets or when the directory is missing; the project never needs Oracle tools.
 */
final class VerifierKits {

    /** Where the kits are expected, relative to the project root. */
    static final Path KITS = Path.of("build", "oracle-sdks");

    private VerifierKits() {
    }

    /**
     * Finds the kit for a conversion target.
     *
     * @param projectRoot the project root
     * @param version     the Java Card version CAP files are converted for
     * @return the kit directory (it has a {@code lib} directory), or empty
     */
    static Optional<Path> find(Path projectRoot, JavaCardVersion version) {
        return names(version).stream().map(name -> projectRoot.resolve(KITS).resolve(name))
                .filter(kit -> Files.isDirectory(kit.resolve("lib"))).findFirst()
                .map(kit -> kit.toAbsolutePath().normalize());
    }

    private static List<String> names(JavaCardVersion version) {
        return switch (version) {
            case V3_0_3 -> List.of("jc303_kit");
            case V3_0_4 -> List.of("jc304_kit");
            case V3_0_5 -> List.of("jc305u3_kit", "jc305u4_kit", "jc305u2_kit", "jc305u1_kit");
            default -> List.of();
        };
    }
}
