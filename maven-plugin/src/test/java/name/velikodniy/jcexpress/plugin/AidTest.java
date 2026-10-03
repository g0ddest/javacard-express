package name.velikodniy.jcexpress.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AID syntax and arithmetic (JCVM 3.1 &sect;4.2.1: 5-byte RID + 0..11-byte PIX). */
class AidTest {

    @Test
    void parsesHexWithOptionalSeparators() throws Exception {
        assertThat(Aid.parse("aid", "A0:00:00:00:62 12").hex()).isEqualTo("A00000006212");
        assertThat(Aid.parse("aid", "a00000006212").hex()).isEqualTo("A00000006212");
    }

    @Test
    void acceptsFiveToSixteenBytes() throws Exception {
        assertThat(Aid.parse("aid", "A000000062").hex()).hasSize(10);
        assertThat(Aid.parse("aid", "A0000000620102030405060708091011").hex()).hasSize(32);
    }

    @Test
    void rejectsAidsOutsideFiveToSixteenBytes() {
        assertThatThrownBy(() -> Aid.parse("packageAid", "A0000000")).isInstanceOf(MojoExecutionException.class)
                .hasMessageContaining("packageAid").hasMessageContaining("4 bytes long")
                .hasMessageContaining("§4.2.1");
        assertThatThrownBy(() -> Aid.parse("aid", "A0000000620102030405060708091011FF"))
                .hasMessageContaining("17 bytes long");
        assertThatThrownBy(() -> Aid.parse("aid", "")).hasMessageContaining("0 bytes long");
        assertThatThrownBy(() -> Aid.parse("aid", null)).isInstanceOf(MojoExecutionException.class);
    }

    @Test
    void rejectsNonHexAndOddDigitCounts() {
        assertThatThrownBy(() -> Aid.parse("aid", "A0000000ZZ")).hasMessageContaining("not hex digits");
        assertThatThrownBy(() -> Aid.parse("aid", "A00000006")).hasMessageContaining("odd number");
    }

    @Test
    void suffixKeepsTheRid() throws Exception {
        Aid pkg = Aid.parse("aid", "A00000006212");
        Aid applet = pkg.withSuffix(1);

        assertThat(applet.hex()).isEqualTo("A0000000621201");
        assertThat(applet.sameRid(pkg)).isTrue();
        assertThat(applet.ridHex()).isEqualTo("A000000062");
    }

    @Test
    void sixteenByteAidCannotBeExtended() throws Exception {
        Aid full = Aid.parse("aid", "A0000000620102030405060708091011");

        assertThat(full.isMaximumLength()).isTrue();
        assertThatThrownBy(() -> full.withSuffix(1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void developmentAidIsAnEightByteProprietaryAid() {
        Aid aid = Aid.derivedFromPackageName("com.example");

        assertThat(aid.hex()).hasSize(16).startsWith("F0");
        assertThat(Aid.derivedFromPackageName("com.example")).isEqualTo(aid);
        assertThat(Aid.derivedFromPackageName("com.other")).isNotEqualTo(aid);
    }

    @Test
    void differentRidsAreDetected() throws Exception {
        assertThat(Aid.parse("a", "A00000006212").sameRid(Aid.parse("b", "A00000007712"))).isFalse();
    }
}
