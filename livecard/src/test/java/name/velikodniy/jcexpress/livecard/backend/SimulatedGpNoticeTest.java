package name.velikodniy.jcexpress.livecard.backend;

import name.velikodniy.jcexpress.Isolation;
import name.velikodniy.jcexpress.backend.AppletDeclaration;
import name.velikodniy.jcexpress.backend.CardRequest;
import name.velikodniy.jcexpress.backend.TestCard;
import name.velikodniy.jcexpress.livecard.model.applet.ModelApplet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * simulated-gp loads CAP files without Oracle's off-card verifier unless {@code jcx.livecard.verifierSdk} names a
 * kit, as it should (there is no card to protect). It says so once per JVM, in one line worded for the simulated
 * card, not once per test class with the live card's advice.
 */
class SimulatedGpNoticeTest {

    private static final Logger LOGGER = Logger.getLogger("name.velikodniy.jcexpress");

    @TempDir
    Path transcripts;

    private void runClassWithOneApplet() {
        Map<String, String> settings = Map.of("jcx.livecard.transcriptDir", transcripts.toString());
        try (TestCard card = new SimulatedGpBackend().open(new CardRequest(SimulatedGpNoticeTest.class,
                key -> Optional.ofNullable(settings.get(key)), List.of(ModelApplet.class)))) {
            AppletDeclaration applet = AppletDeclaration.of(card.aids(), ModelApplet.class, null, null,
                    Isolation.PER_TEST);
            card.install(applet, List.of(applet));
        }
    }

    @Test
    void theUnverifiedLoadIsNotedOncePerJvmForTheSimulatedCard() {
        SimulatedGpBackend.UNVERIFIED_NOTICE_GIVEN.set(false);
        List<LogRecord> records = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord logRecord) {
                records.add(logRecord);
            }

            @Override
            public void flush() {
                // nothing buffered
            }

            @Override
            public void close() {
                // nothing to release
            }
        };
        LOGGER.addHandler(handler);
        try {
            runClassWithOneApplet();
            runClassWithOneApplet();
        } finally {
            LOGGER.removeHandler(handler);
        }

        assertThat(records).filteredOn(logRecord -> logRecord.getMessage().contains("verif")).singleElement()
                .satisfies(logRecord -> {
                    assertThat(logRecord.getLevel()).isEqualTo(Level.INFO);
                    assertThat(logRecord.getMessage()).startsWith("simulated-gp: ")
                            .contains("loaded onto the simulated card without an off-card verifier check")
                            .contains("run on jCardSim").contains("jcx.livecard.verifierSdk")
                            .doesNotContain("build/oracle-sdks").doesNotContain("Live card").doesNotContain("\n");
                });
    }
}
