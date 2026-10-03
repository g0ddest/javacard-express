package name.velikodniy.jcexpress.livecard.backend;

import name.velikodniy.jcexpress.Mode;
import name.velikodniy.jcexpress.backend.CardBackend;
import name.velikodniy.jcexpress.backend.CardRequest;
import name.velikodniy.jcexpress.backend.TestCard;
import name.velikodniy.jcexpress.livecard.ConfigSources;
import name.velikodniy.jcexpress.livecard.LiveCard;
import name.velikodniy.jcexpress.livecard.LiveCardConfig;
import name.velikodniy.jcexpress.livecard.LiveCardException;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The backend {@code livecard}: the card in a PC/SC reader, through the live-card harness and all its safety
 * rules (APDU guard, own AID prefix, authentication budget, verified cleanup, refusal on CI). It is selected only
 * with the JVM system property {@code -Djcx.backend=livecard}, which also switches live-card mode on; the card's
 * settings (keys, reader, AID prefix, verifier) are the live-card settings (LIVE_CARD_TESTING.md). Where they leave
 * the AID prefix and the Java Card version at their defaults, the run's prefix ({@link CardRequest#aidScheme()}) and
 * the Java Card version of the build ({@link name.velikodniy.jcexpress.backend.BuildDescriptor}) apply.
 */
public final class LiveCardBackend implements CardBackend {

    /** Creates the backend ({@link java.util.ServiceLoader} instantiates it). */
    public LiveCardBackend() {
        // stateless
    }

    @Override
    public Mode mode() {
        return Mode.LIVECARD;
    }

    @Override
    public Set<Class<?>> parameterTypes() {
        return Set.of(LiveCard.class);
    }

    @Override
    public TestCard open(CardRequest request) {
        Map<LiveCardConfig.Setting, String> runDefaults = new EnumMap<>(LiveCardConfig.Setting.class);
        runDefaults.put(LiveCardConfig.Setting.AID_PREFIX, request.aidScheme().prefix());
        GpTestCard.buildVersion(request).ifPresent(version ->
                runDefaults.put(LiveCardConfig.Setting.JAVA_CARD_VERSION, version));
        LiveCardConfig config = LiveCardConfig.load(ConfigSources.standard(), runDefaults);
        if (!config.enabled()) {
            throw new LiveCardException("The livecard backend needs live-card mode: run with -Djcx.backend=livecard"
                    + " as a JVM system property");
        }
        Optional<Boolean> intSupport = request.setting(SimulatedGpBackend.INT_SETTING).map(Boolean::parseBoolean);
        LiveCard live = LiveCard.connect(config);
        GpTestCard.transcribe(live, request);
        return new GpTestCard(Mode.LIVECARD, live, intSupport, load -> { });
    }
}
