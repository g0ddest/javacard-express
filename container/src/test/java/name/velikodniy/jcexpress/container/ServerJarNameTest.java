package name.velikodniy.jcexpress.container;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Selection of the shaded server jar in {@code docker/target} (used by {@link SmartCardContainer#SmartCardContainer(
 * java.nio.file.Path)}).
 */
class ServerJarNameTest {

    @ParameterizedTest
    @ValueSource(strings = {"jcx-simulator.jar", "jcx-simulator-1.2.3.jar", "jcx-simulator-4.5.6-SNAPSHOT.jar"})
    void acceptsTheShadedServerJar(String name) {
        assertThat(SmartCardContainer.isServerJarName(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "original-jcx-simulator.jar", "original-jcx-simulator-1.2.3.jar",
            "jcx-simulator-4.5.6-SNAPSHOT-sources.jar", "jcx-simulator-4.5.6-SNAPSHOT-javadoc.jar",
            "jcx-simulator.pom", "other.jar"})
    void rejectsEverythingElse(String name) {
        assertThat(SmartCardContainer.isServerJarName(name)).isFalse();
    }
}
