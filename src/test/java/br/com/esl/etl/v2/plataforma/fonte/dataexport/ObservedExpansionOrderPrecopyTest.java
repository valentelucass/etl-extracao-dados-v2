package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ObservedExpansionOrderPrecopyTest {
    @Test
    void twoOrderExpressionsAreRefusedBeforeElementCopyOrIteration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ObservedExpansionCapturePlan.OrderBy(poison(2)));
    }

    @Test
    void excessiveDeclaredOrderSizeCannotAllocateOrTouchElements() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ObservedExpansionCapturePlan.OrderBy(poison(Integer.MAX_VALUE)));
    }

    @Test
    void oneIndexedOnlyExpressionRemainsAcceptedWithoutCallerToArrayOrIterator() {
        final List<String> source =
                new AbstractList<>() {
                    @Override
                    public int size() {
                        return 1;
                    }

                    @Override
                    public String get(final int index) {
                        return "issue_date desc";
                    }

                    @Override
                    public Object[] toArray() {
                        throw new AssertionError("Caller copy must not run");
                    }

                    @Override
                    public Iterator<String> iterator() {
                        throw new AssertionError("Caller iteration must not run");
                    }
                };
        final var order = new ObservedExpansionCapturePlan.OrderBy(source);
        assertEquals(List.of("issue_date desc"), order.expressions());
        assertThrows(UnsupportedOperationException.class, () -> order.expressions().clear());
    }

    @Test
    void absenceAndTheSingleExpressionValidationKeepTheirSemantics() {
        assertEquals(List.of(), ObservedExpansionCapturePlan.OrderBy.absent().expressions());
        assertThrows(
                IllegalArgumentException.class,
                () -> ObservedExpansionCapturePlan.OrderBy.explicit(" "));
        assertThrows(
                IllegalArgumentException.class,
                () -> ObservedExpansionCapturePlan.OrderBy.explicit("x".repeat(81)));
        assertThrows(
                NullPointerException.class,
                () ->
                        new ObservedExpansionCapturePlan.OrderBy(
                                java.util.Arrays.asList((String) null)));
    }

    private static List<String> poison(final int size) {
        return new AbstractList<>() {
            @Override
            public int size() {
                return size;
            }

            @Override
            public String get(final int index) {
                throw new AssertionError("Order element accessed before size bound");
            }

            @Override
            public Object[] toArray() {
                throw new AssertionError("Unbounded order copy attempted");
            }

            @Override
            public Iterator<String> iterator() {
                throw new AssertionError("Unbounded order iterator attempted");
            }
        };
    }
}
