package cn.pickup.pocket;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
public class BatchParserTest {
    private final List<Station> stations = Arrays.asList(new Station(0,"东门","x-x-xxxx"),new Station(1,"西门","x-x-xxx\nxx-x-xxx"),new Station(2,"柜机","xxxxxx"));
    @Test public void extractsCompleteTokensAndDeduplicates() {
        assertEquals(Arrays.asList("2-1-003","001234","10-1-001"), BatchParser.extract("取件码2-1-003，柜机001234\n10-1-001 2-1-003 手机13800138000 单号1234567890", stations));
    }
    @Test public void rejectsPartialNumbersMalformedTokensAndAsciiIdentifiers() {
        assertTrue(BatchParser.extract("A001234B 1234567 123456-78 -001234 001234- 1--2-003",stations).isEmpty());
    }
    @Test public void normalizesFullwidthButDoesNotGuessAmbiguousOcrDigits() {
        assertEquals(Arrays.asList("1-2-0034"), BatchParser.extract("取件：１－２－００３４\n00I234",stations));
    }
    @Test public void doesNotSaveUnknownFormatsAndBoundsInput() {
        assertTrue(BatchParser.extract("12345 1-1-1",stations).isEmpty());
        assertThrows(IllegalArgumentException.class,()->BatchParser.extract(new String(new char[100001]),stations));
    }
}
