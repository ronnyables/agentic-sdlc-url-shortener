package com.schwab.shortener.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base62EncoderTest {

    @Test
    void zeroEncodesToFirstAlphabetChar() {
        assertThat(Base62Encoder.encode(0)).isEqualTo("0");
    }

    @Test
    void roundTripsForVariousValues() {
        long[] values = {1, 61, 62, 63, 12345, 1_000_000L, Long.MAX_VALUE / 2};
        for (long v : values) {
            String encoded = Base62Encoder.encode(v);
            assertThat(Base62Encoder.decode(encoded)).isEqualTo(v);
        }
    }

    @Test
    void rejectsNegativeInput() {
        assertThatThrownBy(() -> Base62Encoder.encode(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidCharacterOnDecode() {
        assertThatThrownBy(() -> Base62Encoder.decode("!!!")).isInstanceOf(IllegalArgumentException.class);
    }
}
