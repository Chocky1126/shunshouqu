package cn.pickup.pocket;

import org.junit.Test;
import static org.junit.Assert.*;

public class CodeRulesTest {
    @Test public void generatesExactLengthRulesFromNormalizedExampleCodes() {
        assertEquals("xx-x-xxx", CodeRules.formatFromExample("１２－２－５４２"));
        assertEquals("xxxxxx", CodeRules.formatFromExample("001234"));
        assertEquals("x-x-xxxx", CodeRules.formatFromExample(" 1-2-0034 "));
        assertEquals("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", CodeRules.formatFromExample("12345678901234567890123456789012"));
        for (String value : new String[]{"", "取件码123456", "1--2", "-123", "123-", "1 2", "1\n2", "123456789012345678901234567890123"}) {
            assertThrows(value, IllegalArgumentException.class, () -> CodeRules.formatFromExample(value));
        }
    }

    @Test public void previewsAreValidCodesForTheExactSingleRule() {
        assertEquals("12-3-456", CodeRules.exampleForFormat("xx-x-xxx"));
        assertEquals("123456", CodeRules.exampleForFormat("XXXXXX"));
        assertEquals("1-2-3456", CodeRules.exampleForFormat("x-x-xxxx"));
        assertThrows(IllegalArgumentException.class, () -> CodeRules.exampleForFormat("x-x\nxx"));
    }

    @Test public void normalizesDigitsHyphensAndOnlyEdgeWhitespace() {
        assertEquals("1-2-0034", CodeRules.normalize("\u0085\u00a0\u3000１－２－００３４\u202f"));
        for (String dash : new String[]{"－", "﹣", "‐", "‑", "‒", "–", "—", "−"}) {
            assertEquals("1-2", CodeRules.normalize("１" + dash + "２"));
        }
        assertEquals("001234", CodeRules.normalize("001234"));
        assertThrows(IllegalArgumentException.class, () -> CodeRules.normalize(null));
        assertFalse(CodeRules.matches("001 234", "xxxxxx"));
        assertFalse(CodeRules.matches("001\u00a0234", "xxxxxx"));
    }

    @Test public void canonicalizesMultipleRulesAndDeduplicates() {
        assertEquals("xx-x-xxx\nx-x-xxx\nxxxxxx", CodeRules.normalizeFormats(" XX－X－XXX，x-x-xxx;xxxxxx / xx-x-xxx、\n"));
        assertEquals("x\nxx\nxxx", CodeRules.normalizeFormats("x；xx\r\nXXX"));
    }

    @Test public void rejectsInvalidFormatsAndExcessiveAlternatives() {
        for (String value : new String[]{"", " ,; / ", "x x", "x--x", "-xx", "xx-", "xx?", "[0-9]", "123", "x_x", "x\u0000", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", "x/xx/xxx/xxxx/xxxxx/xxxxxx/xxxxxxx/xxxxxxxx/xxxxxxxxx/xxxxxxxxxx/xxxxxxxxxxx"}) {
            assertThrows(value, IllegalArgumentException.class, () -> CodeRules.normalizeFormats(value));
        }
        assertThrows(IllegalArgumentException.class, () -> CodeRules.normalizeFormats(null));
        assertEquals("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", CodeRules.normalizeFormats("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"));
    }

    @Test public void matchesEachXAsExactlyOneAsciiDigit() {
        assertTrue(CodeRules.matches(" ０１－２－０３４ ", "x-x-xxx\nxx-x-xxx"));
        assertTrue(CodeRules.matches("1-2-034", "xx-x-xxx/x-x-xxx"));
        assertTrue(CodeRules.matches("1-2-0034", "x-x-xxxx"));
        assertTrue(CodeRules.matches("001234", "xxxxxx"));
        assertTrue(CodeRules.matches("123-45", "xxx-xx"));
        for (String value : new String[]{"", "12345", "1234567", "٠٠١٢٣٤", "001234\u200b", "001234\n123456", "取件码001234"}) {
            assertFalse(value, CodeRules.matches(value, "xxxxxx"));
        }
        assertFalse(CodeRules.matches(null, "xxxxxx"));
        assertFalse(CodeRules.matches("123-2-034", "x-x-xxx/xx-x-xxx"));
    }

    @Test public void comparesSegmentsNumericallyWithoutIntegerOverflow() {
        assertTrue(CodeRules.compareCodes("1-2-10", "1-10-2") < 0);
        assertTrue(CodeRules.compareCodes("9-999", "10-1") < 0);
        assertTrue(CodeRules.compareCodes("999999999999999999999999999999", "1000000000000000000000000000000") < 0);
        assertTrue(CodeRules.compareCodes("001", "01") < 0);
        assertTrue(CodeRules.compareCodes("01", "1") < 0);
        assertTrue(CodeRules.compareCodes("1", "1-0") < 0);
        assertTrue(CodeRules.compareCodes("0-2", "00-1") > 0);
        assertEquals(0, CodeRules.compareCodes(" ０１－２ ", "01-2"));
    }
}
