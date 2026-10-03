package name.velikodniy.jcexpress.livecard;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The off-card verifier is a third-party program: it runs without the live-card settings of this process in its
 * environment, so card keys given as {@code JCX_LIVECARD_KEYS} never reach it.
 */
class CapVerifierTest {

    @Test
    void verifierProcessDoesNotInheritTheLiveCardSettings() {
        Map<String, String> environment = new HashMap<>(Map.of("JCX_LIVECARD_KEYS", "00112233445566778899AABBCCDDEEFF",
                "JCX_LIVECARD_ENABLED", "true", "jcx_livecard_reader", "ACR", "PATH", "/usr/bin", "HOME", "/home/dev"));

        CapVerifier.withoutLiveCardSettings(environment);

        assertThat(environment).containsOnlyKeys("PATH", "HOME");
    }
}
