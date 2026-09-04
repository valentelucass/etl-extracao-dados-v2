package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DataExportHttpAttemptTest {

    @Test
    void rejectsANonPositiveTemplateIdentifier() {
        assertThrows(IllegalArgumentException.class, () -> new DataExportHttpAttempt(0, "GET"));
    }

    @Test
    void rejectsABlankOperation() {
        assertThrows(IllegalArgumentException.class, () -> new DataExportHttpAttempt(1, "   "));
    }

    @Test
    void normalizesTheOperationWithoutExposingRequestDetails() {
        final DataExportHttpAttempt attempt = new DataExportHttpAttempt(6389, "  POST_JSON  ");

        assertEquals(6389, attempt.templateId());
        assertEquals("POST_JSON", attempt.operation());
    }
}
