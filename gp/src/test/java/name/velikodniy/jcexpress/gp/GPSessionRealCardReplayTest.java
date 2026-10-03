package name.velikodniy.jcexpress.gp;

import name.velikodniy.jcexpress.Hex;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Replays public sessions of a real SCP02 card (GlobalPlatformPro debug log, pastebin ZQSDaJFm, keys
 * 40..4F, i=15) through the high-level {@link GPSession} API: every command GPSession sends, including its
 * C-MAC with ICV encryption, must be byte-identical to what the real card accepted, and the card's real
 * GET STATUS responses must be parsed per GPCS v2.3.1 Tables 11-36 and 11-37.
 */
class GPSessionRealCardReplayTest {

    @Test
    void session0_issuerSecurityDomainAndLoadFilesOfTheRealCard() {
        ScpTranscript t = ScpTranscript.named("SCP02_real_card_i15_session0");
        TranscriptCard card = new TranscriptCard(t);
        GPSession gp = open(card, t);

        AppletInfo isd = single(gp.getStatus(Lifecycle.SCOPE_ISD));
        List<AppletInfo> applications = gp.getStatus();
        AppletInfo loadFile = single(gp.getLoadFiles());
        AppletInfo withModules = single(gp.getStatus(Lifecycle.SCOPE_LOAD_FILES_AND_MODULES));

        assertThat(card.mismatches()).isEmpty();
        assertThat(card.completed()).isTrue();
        assertThat(isd.aidHex()).isEqualTo("A000000003000000");
        assertThat(isd.lifeCycleState()).isEqualTo(0x07);
        assertThat(Hex.encode(isd.privilegeBytes())).isEqualTo("9EFF80");
        assertThat(isd.isSecurityDomain()).isTrue();
        assertThat(Hex.encode(isd.executableLoadFileAid())).isEqualTo("A0000000620001");
        assertThat(Hex.encode(isd.versionNumber())).isEqualTo("0100");
        assertThat(Hex.encode(isd.associatedSecurityDomainAid())).isEqualTo("A000000003000000");
        assertThat(applications).isEmpty();
        assertThat(loadFile.aidHex()).isEqualTo("A0000001515350");
        assertThat(loadFile.lifeCycleState()).isEqualTo(0x01);
        assertThat(withModules.executableModuleAids()).extracting(Hex::encode).containsExactly("A000000151535041");
    }

    @Test
    void session2_twoLoadFilesAndTheirExecutableModules() {
        ScpTranscript t = ScpTranscript.named("SCP02_real_card_i15_session2");
        TranscriptCard card = new TranscriptCard(t);
        GPSession gp = open(card, t);

        gp.getStatus(Lifecycle.SCOPE_ISD);
        gp.getStatus();
        List<AppletInfo> loadFiles = gp.getLoadFiles();
        List<AppletInfo> modules = gp.getStatus(Lifecycle.SCOPE_LOAD_FILES_AND_MODULES);

        assertThat(card.mismatches()).isEmpty();
        assertThat(card.completed()).isTrue();
        assertThat(loadFiles).extracting(AppletInfo::aidHex).containsExactly("A0000001515350", "D27600012401");
        assertThat(modules.get(1).executableModuleAids()).extracting(Hex::encode)
                .containsExactly("D2760001240102000000000000010000");
    }

    /**
     * The INSTALL [for load] of session 1 is GlobalPlatformPro's command without Le; GPSession adds the
     * Le '00' of GPCS v2.3.1 Table 11-40, which the C-MAC does not cover (E.4.4), so the following LOAD
     * block still carries exactly the C-MAC the real card verified.
     */
    @Test
    void session1_installForLoadAndFirstLoadBlockAsAcceptedByTheRealCard() {
        ScpTranscript t = ScpTranscript.named("SCP02_real_card_i15_session1");
        TranscriptCard card = new TranscriptCard(t).allowAddedLe();
        GPSession gp = open(card, t);

        gp.getStatus(Lifecycle.SCOPE_ISD);
        gp.getStatus();
        gp.getLoadFiles();
        gp.getStatus(Lifecycle.SCOPE_LOAD_FILES_AND_MODULES);
        gp.installForLoad("D27600012401", "A000000003000000");
        byte[] loadResponse = gp.transmit(t.commands().getLast().plain());

        assertThat(card.mismatches()).isEmpty();
        assertThat(card.completed()).isTrue();
        assertThat(Hex.encode(card.sent().get(6))).endsWith("B36FB4B11F053F24" + "00");
        assertThat(Hex.encode(loadResponse)).isEqualTo("009000");
    }

    private static GPSession open(TranscriptCard card, ScpTranscript t) {
        return GPSession.on(card).keys(t.keys()).keyVersion(t.keyVersion()).hostChallenge(t.hostChallenge())
                .scp02Option(t.option()).securityLevel(t.level()).open();
    }

    private static AppletInfo single(List<AppletInfo> entries) {
        assertThat(entries).hasSize(1);
        return entries.getFirst();
    }
}
