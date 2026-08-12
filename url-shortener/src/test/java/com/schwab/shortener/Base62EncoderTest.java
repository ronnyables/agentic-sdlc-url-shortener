package com.schwab.shortener;

import com.schwab.shortener.core.Base62Encoder;
import static com.schwab.shortener.testkit.MiniTest.*;

public class Base62EncoderTest {

    public void testZeroEncodesToFirstAlphabetChar() {
        assertEquals("0", Base62Encoder.encode(0), "encode(0)");
    }

    public void testRoundTripsForVariousValues() {
        long[] values = {1, 61, 62, 63, 12345, 1_000_000L, Long.MAX_VALUE / 2};
        for (long v : values) {
            String encoded = Base62Encoder.encode(v);
            long decoded = Base62Encoder.decode(encoded);
            assertEquals(v, decoded, "round-trip for " + v);
        }
    }

    public void testMonotonicIncreasingLengthDoesNotDecrease() {
        String a = Base62Encoder.encode(1_000_000L);
        String b = Base62Encoder.encode(1_000_001L);
        assertTrue(b.length() >= a.length(), "encoded length should not shrink for the next sequence value");
    }

    public void testRejectsNegativeInput() {
        assertThrows(IllegalArgumentException.class, () -> Base62Encoder.encode(-1), "negative values are invalid");
    }

    public void testRejectsInvalidCharacterOnDecode() {
        assertThrows(IllegalArgumentException.class, () -> Base62Encoder.decode("!!!"), "invalid base62 chars should fail to decode");
    }
}
