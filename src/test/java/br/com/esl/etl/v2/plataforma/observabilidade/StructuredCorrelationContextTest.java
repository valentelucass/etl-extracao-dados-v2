package br.com.esl.etl.v2.plataforma.observabilidade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StructuredCorrelationContextTest {

    @Test
    void enforcesLifoAndRestoresTheOpaqueOuterFrame() {
        final CorrelationReference outer = CorrelationReference.fromTechnicalScope("OUTER_SCOPE");
        final CorrelationReference inner = CorrelationReference.fromTechnicalScope("INNER_SCOPE");
        final StructuredCorrelationContext.Scope outerScope =
                StructuredCorrelationContext.open(outer);
        final StructuredCorrelationContext.Scope innerScope =
                StructuredCorrelationContext.open(inner);

        assertEquals(inner, StructuredCorrelationContext.current().orElseThrow());
        assertThrows(IllegalStateException.class, outerScope::close);
        assertEquals(inner, StructuredCorrelationContext.current().orElseThrow());

        innerScope.close();
        assertEquals(outer, StructuredCorrelationContext.current().orElseThrow());
        outerScope.close();
        assertTrue(StructuredCorrelationContext.current().isEmpty());
    }

    @Test
    void usesOnlyAnOpaqueTechnicalFallbackWhenNoFrameExists() {
        assertTrue(StructuredCorrelationContext.current().isEmpty());
        final CorrelationReference fallback =
                StructuredCorrelationContext.currentOrTechnicalScope("TECHNICAL_SCOPE");

        assertEquals(64, fallback.sha256().length());
        assertEquals(CorrelationReference.fromTechnicalScope("TECHNICAL_SCOPE"), fallback);
        assertTrue(StructuredCorrelationContext.current().isEmpty());
    }
}
