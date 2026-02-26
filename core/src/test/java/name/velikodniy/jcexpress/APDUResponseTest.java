package name.velikodniy.jcexpress;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class APDUResponseTest {

    @Test
    void shouldParseRawBytes() {
        byte[] raw = {0x48, 0x65, 0x6C, 0x6C, 0x6F, (byte) 0x90, 0x00};
        APDUResponse response = new APDUResponse(raw);

        assertThat(response.sw()).isEqualTo(0x9000);
        assertThat(response.sw1()).isEqualTo(0x90);
        assertThat(response.sw2()).isEqualTo(0x00);
        assertThat(response.data()).containsExactly(0x48, 0x65, 0x6C, 0x6C, 0x6F);
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void shouldCreateFromDataAndSw() {
        APDUResponse response = new APDUResponse(new byte[]{0x01, 0x02}, 0x9000);
        assertThat(response.sw()).isEqualTo(0x9000);
        assertThat(response.data()).containsExactly(0x01, 0x02);
    }

    @Test
    void shouldReturnDataAsHex() {
        APDUResponse response = new APDUResponse(new byte[]{0x48, 0x65, 0x6C, 0x6C, 0x6F}, 0x9000);
        assertThat(response.dataAsHex()).isEqualTo("48656C6C6F");
    }

    @Test
    void shouldReturnDataAsString() {
        APDUResponse response = new APDUResponse(new byte[]{0x48, 0x65, 0x6C, 0x6C, 0x6F}, 0x9000);
        assertThat(response.dataAsString()).isEqualTo("Hello");
    }

    @Test
    void shouldDetectFailure() {
        APDUResponse response = new APDUResponse(new byte[0], 0x6D00);
        assertThat(response.isSuccess()).isFalse();
    }

    @Test
    void shouldHandleSwOnly() {
        byte[] raw = {(byte) 0x6D, 0x00};
        APDUResponse response = new APDUResponse(raw);
        assertThat(response.sw()).isEqualTo(0x6D00);
        assertThat(response.data()).isEmpty();
    }

    @Test
    void shouldHaveReadableToString() {
        APDUResponse response = new APDUResponse(new byte[]{0x48, 0x65}, 0x9000);
        assertThat(response.toString()).contains("4865").contains("9000");
    }
}
