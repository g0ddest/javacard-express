package name.velikodniy.jcexpress;

import name.velikodniy.jcexpress.assertions.APDUResponseAssert;
import org.junit.jupiter.api.Test;

import static name.velikodniy.jcexpress.assertions.JCXAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class APDUResponseAssertTest {

    @Test
    void isSuccessShouldPassFor9000() {
        APDUResponse response = new APDUResponse(new byte[]{0x48, 0x65}, 0x9000);
        assertThat(response).isSuccess();
    }

    @Test
    void isSuccessShouldFailForNon9000() {
        APDUResponse response = new APDUResponse(new byte[0], 0x6D00);
        assertThatThrownBy(() -> assertThat(response).isSuccess())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("9000")
                .hasMessageContaining("6D00");
    }

    @Test
    void statusWordShouldPassOnMatch() {
        APDUResponse response = new APDUResponse(new byte[0], 0x6D00);
        assertThat(response).statusWord(0x6D00);
    }

    @Test
    void statusWordShouldFailOnMismatch() {
        APDUResponse response = new APDUResponse(new byte[0], 0x6A82);
        assertThatThrownBy(() -> assertThat(response).statusWord(0x9000))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("6A82")
                .hasMessageContaining("file not found");
    }

    @Test
    void hasDataLengthShouldPass() {
        APDUResponse response = new APDUResponse(new byte[]{1, 2, 3}, 0x9000);
        assertThat(response).hasDataLength(3);
    }

    @Test
    void hasDataLengthShouldFail() {
        APDUResponse response = new APDUResponse(new byte[]{1, 2}, 0x9000);
        assertThatThrownBy(() -> assertThat(response).hasDataLength(5))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void dataEqualsShouldPass() {
        APDUResponse response = new APDUResponse(new byte[]{0x48, 0x65, 0x6C, 0x6C, 0x6F}, 0x9000);
        assertThat(response).dataEquals(0x48, 0x65, 0x6C, 0x6C, 0x6F);
    }

    @Test
    void dataEqualsShouldFail() {
        APDUResponse response = new APDUResponse(new byte[]{0x01}, 0x9000);
        assertThatThrownBy(() -> assertThat(response).dataEquals(0xFF))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void dataAsStringShouldPass() {
        APDUResponse response = new APDUResponse("Hello".getBytes(), 0x9000);
        assertThat(response).dataAsString().isEqualTo("Hello");
    }

    @Test
    void dataAsHexShouldPass() {
        APDUResponse response = new APDUResponse(new byte[]{0x48, 0x65, 0x6C, 0x6C, 0x6F}, 0x9000);
        assertThat(response).dataAsHex().isEqualTo("48656C6C6F");
    }

    @Test
    void chainingAssertionsShouldWork() {
        APDUResponse response = new APDUResponse(new byte[]{0x48, 0x65, 0x6C, 0x6C, 0x6F}, 0x9000);
        assertThat(response)
                .isSuccess()
                .hasDataLength(5)
                .dataEquals(0x48, 0x65, 0x6C, 0x6C, 0x6F);
    }
}
