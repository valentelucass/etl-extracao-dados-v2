package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Presence;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Wire;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AnalyticFieldComparisonTest {
    @Test
    void lengthPrefixesKeepNullEmptyAndFieldBoundariesDistinct() {
        final var nullText = new AnalyticFieldComparison();
        nullText.text(null);
        final var emptyText = new AnalyticFieldComparison();
        emptyText.text("");
        assertFalse(Arrays.equals(nullText.bytesLimited(8), emptyText.bytesLimited(8)));

        final var combined = new AnalyticFieldComparison();
        combined.text("a|b");
        final var separate = new AnalyticFieldComparison();
        separate.text("a");
        separate.text("b");
        assertFalse(Arrays.equals(combined.bytesLimited(20), separate.bytesLimited(20)));
        assertArrayEquals(combined.bytesLimited(20), combined.bytesLimited(20));
    }

    @Test
    void presenceWireParseIssueAndFlagsChangeComparisonBytes() {
        final var absent = new AnalyticFieldComparison();
        absent.value(new ExpansionValue<>(Presence.ABSENT, Wire.ABSENT, null, null, null));
        absent.flag(null);
        final var valid = new AnalyticFieldComparison();
        valid.value(new ExpansionValue<>(Presence.VALUE, Wire.STRING, "x", "x", null));
        valid.flag(Boolean.TRUE);
        final var invalid = new AnalyticFieldComparison();
        invalid.value(new ExpansionValue<>(Presence.VALUE, Wire.STRING, "x", null, "INVALID"));
        invalid.flag(Boolean.FALSE);
        assertFalse(Arrays.equals(absent.bytesLimited(256), valid.bytesLimited(256)));
        assertFalse(Arrays.equals(valid.bytesLimited(256), invalid.bytesLimited(256)));
    }

    @Test
    void enforcesTheComparisonBufferLimitBeforeSerialization() {
        final var comparison = new AnalyticFieldComparison();
        comparison.text("abc");
        assertThrows(IllegalArgumentException.class, () -> comparison.bytesLimited(0));
        assertThrows(IllegalArgumentException.class, () -> comparison.bytesLimited(262145));
        assertThrows(IllegalArgumentException.class, () -> comparison.bytesLimited(6));
        assertThrows(IllegalArgumentException.class, () -> comparison.text("x".repeat(262144)));
    }
}
