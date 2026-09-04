package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.resiliencia.FailureKind;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DataExport422FailureClassifierTest {

    private final DataExport422FailureClassifier classifier = new DataExport422FailureClassifier();

    @Test
    void classifiesOnlyStructuredWindowCategoryAsRepartitionable() {
        assertEquals(
                FailureKind.WINDOW_TOO_LARGE_HTTP_422,
                classifier.classify(422, Optional.of(DataExport422ErrorCategory.WINDOW_TOO_LARGE)));
        assertEquals(FailureKind.UNCLASSIFIED_HTTP_422, classifier.classify(422, Optional.empty()));
        assertEquals(
                FailureKind.UNCLASSIFIED_HTTP_422,
                classifier.classify(422, Optional.of(DataExport422ErrorCategory.OTHER)));
    }

    @Test
    void refusesStatusesOutsideItsBoundary() {
        assertThrows(
                IllegalArgumentException.class, () -> classifier.classify(400, Optional.empty()));
    }
}
