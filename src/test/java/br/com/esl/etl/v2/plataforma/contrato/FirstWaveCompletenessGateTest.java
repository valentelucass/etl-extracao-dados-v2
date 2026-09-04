package br.com.esl.etl.v2.plataforma.contrato;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class FirstWaveCompletenessGateTest {

    @Test
    void classificationVocabularyIsClosedAndCompletenessSeparatesEffects() {
        assertEquals(
                Set.of("PROVEN", "TRANSITIONAL", "ABSENT", "BUSINESS_DECISION_PENDING"),
                java.util.Arrays.stream(ContractClassification.values())
                        .map(Enum::name)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));

        final SourceCompletenessStatus status =
                SourceCompletenessStatus.BLOCKED_NO_COMPLETENESS_PROOF;
        assertTrue(status.permits(SourceDataEffect.SHADOW_UPSERT));
        assertFalse(status.permits(SourceDataEffect.SWEEP_OR_DEACTIVATION));
        assertFalse(status.permits(SourceDataEffect.CUTOVER));
        assertThrows(NullPointerException.class, () -> status.permits(null));

        for (final SourceDataEffect effect : SourceDataEffect.values()) {
            assertTrue(SourceCompletenessStatus.PROVEN_COMPLETE.permits(effect));
        }
    }

    @Test
    void semanticFingerprintIsOrderedBoundedAndRejectsInvalidUnicode() {
        assertEquals(
                ContractSemanticsFingerprint.create("v1", "a", "b"),
                ContractSemanticsFingerprint.create("v1", "a", "b"));
        assertFalse(
                ContractSemanticsFingerprint.create("v1", "a", "b")
                        .equals(ContractSemanticsFingerprint.create("v1", "b", "a")));
        assertFalse(
                ContractSemanticsFingerprint.create("v1", "a", "b")
                        .equals(ContractSemanticsFingerprint.create("v2", "a", "b")));
        assertThrows(
                IllegalArgumentException.class, () -> ContractSemanticsFingerprint.create("v1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ContractSemanticsFingerprint.create("v1", "\uD800"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ContractSemanticsFingerprint.create(
                                "v1", new String[ContractMetadata.MAXIMUM_ELEMENTS]));
    }
}
