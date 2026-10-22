package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Offline namespace/observation checks; the DTO is no Root permit or supplier quota. */
class CurrentSourceInfoEvidenceTest {
    @ParameterizedTest
    @CsvSource({
        "hermesc54d6a25880d, HERMES_c54d6a25880d",
        "user20261016-continuar-v2, USER_20261016_CONTINUAR_V2"
    })
    void eachDeclaredUnitSelectsOnlyItsExactRequest(String unit, String request) {
        assertTrue(CurrentSourceInfoEvidence.supportsUnit(unit));
        assertEquals(request, CurrentSourceInfoEvidence.requestForUnit(unit));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
            strings = {
                "",
                "user20261016-continuar-v2/child",
                "user20261016-continuar-v2-extra",
                "USER20261016-CONTINUAR-V2",
                " user20261016-continuar-v2",
                "hermes3cdb8d7b3d12"
            })
    void unknownOrPartialUnitCannotSelectAnyRequest(String unit) {
        assertFalse(CurrentSourceInfoEvidence.supportsUnit(unit));
        final var refused =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> CurrentSourceInfoEvidence.requestForUnit(unit));
        assertEquals("CURRENT_SOURCE_UNIT_NOT_SUPPORTED", refused.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"HERMES_c54d6a25880d", "USER_20261016_CONTINUAR_V2"})
    void sanitizedObservationCanRepresentEitherDeclaredRequest(String request) {
        assertEquals(request, observation(request).requestId());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "USER_20261016_CONTINUAR_V2_extra",
                "user_20261016_continuar_v2",
                "HERMES_3cdb8d7b3d12"
            })
    void sanitizedObservationRefusesUndeclaredRequest(String request) {
        final var refused =
                assertThrows(IllegalArgumentException.class, () -> observation(request));
        assertEquals("CURRENT_SOURCE_INFO_OBSERVATION_INVALID", refused.getMessage());
    }

    private static CurrentSourceInfoEvidence.AcceptedInfo observation(String request) {
        return new CurrentSourceInfoEvidence.AcceptedInfo(
                request, "a".repeat(32), 6908, 200, 128, 1, 1, Instant.EPOCH);
    }
}
