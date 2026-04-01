package io.github.mattmck.events.cursor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CursorCodec} encode/decode round-trips and error handling.
 */
class CursorCodecTests {

    @Test
    @DisplayName("encode produces a URL-safe Base64 string without padding")
    void encodeProducesUrlSafeBase64() {
        var encoded = CursorCodec.encode(1708646400L, "evt-a1");

        assertThat(encoded)
                .isNotBlank()
                .doesNotContain("+", "/", "=");
    }

    @Test
    @DisplayName("decode reverses encode for a round-trip")
    void decodeReversesEncode() {
        var startTime = 1708646400L;
        var id = "evt-c3";

        var encoded = CursorCodec.encode(startTime, id);
        var decoded = CursorCodec.decode(encoded);

        assertThat(decoded.startTime()).isEqualTo(startTime);
        assertThat(decoded.id()).isEqualTo(id);
    }

    @Test
    @DisplayName("decode throws on malformed Base64 input")
    void decodeThrowsOnMalformedBase64() {
        assertThatThrownBy(() -> CursorCodec.decode("!!!not-base64!!!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not valid Base64");
    }

    @Test
    @DisplayName("decode throws when separator is missing")
    void decodeThrowsOnMissingSeparator() {
        var noSeparator = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("noseparatorhere".getBytes());

        assertThatThrownBy(() -> CursorCodec.decode(noSeparator))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing separator");
    }

    @Test
    @DisplayName("decode throws when timestamp is not numeric")
    void decodeThrowsOnNonNumericTimestamp() {
        var badTimestamp = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("abc|evt-1".getBytes());

        assertThatThrownBy(() -> CursorCodec.decode(badTimestamp))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-numeric timestamp");
    }

    @Test
    @DisplayName("decode throws on null input")
    void decodeThrowsOnNull() {
        assertThatThrownBy(() -> CursorCodec.decode(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("decode throws on empty string")
    void decodeThrowsOnEmptyString() {
        assertThatThrownBy(() -> CursorCodec.decode(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("encode handles IDs with hyphens and numbers")
    void encodeHandlesSpecialCharactersInId() {
        var encoded = CursorCodec.encode(1709424000L, "evt-t20");
        var decoded = CursorCodec.decode(encoded);

        assertThat(decoded.id()).isEqualTo("evt-t20");
    }
}
