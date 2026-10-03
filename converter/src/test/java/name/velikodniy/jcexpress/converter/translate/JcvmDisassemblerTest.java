package name.velikodniy.jcexpress.converter.translate;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** Self-check of the test-scope disassembler against JCVM 3.1 Table 8-1 as encoded in {@link JcvmOpcode}. */
class JcvmDisassemblerTest {

    @Test
    void mnemonicTableMatchesJcvmOpcodeConstants() throws Exception {
        int checked = 0;
        for (Field f : JcvmOpcode.class.getDeclaredFields()) {
            if (f.getType() != int.class || !Modifier.isStatic(f.getModifiers()) || !Modifier.isPublic(f.getModifiers())) continue;
            int opcode = f.getInt(null);
            assertThat(JcvmDisassembler.mnemonic(opcode))
                    .as("opcode 0x%02x", opcode)
                    .isEqualTo(f.getName().toLowerCase(Locale.ROOT));
            checked++;
        }
        assertThat(checked).isGreaterThan(180);
    }

    @Test
    void decodesBranchSwitchAndCpOperands() {
        byte[] code = {
                0x73, 0x00, 0x0D, 0x00, 0x01, 0x00, 0x02, 0x00, 0x0B, 0x00, 0x0C, // stableswitch
                0x7A,                                                            // return @11
                0x7A,                                                            // return @12
                0x7A,                                                            // return @13
                (byte) 0x8B, 0x01, 0x02,                                         // invokevirtual #258
                0x60, (byte) 0xFD,                                               // ifeq -> 14
                (byte) 0xAD, 0x07                                                // getfield_a_this #7
        };
        assertThat(JcvmDisassembler.lines(code)).containsExactly(
                "stableswitch default->13 low=1 high=2 [11, 12]", "return", "return", "return",
                "invokevirtual #258", "ifeq -> 14", "getfield_a_this #7");
    }
}
