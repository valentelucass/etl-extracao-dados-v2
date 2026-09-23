package br.com.esl.etl.v2.modulos.coletas.aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.LigarReferenciaTemporalColeta.Outcome;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaAttributePresence;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaFreshnessOrigin;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageRecord;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaTemporalIdentityBinding;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaTemporalObservation;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ColetaTemporalLinkTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final UUID DATA_RUN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REF_RUN = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final LocalDate DATE = LocalDate.of(2026, 9, 9);
    private static final Instant CAPTURE = Instant.parse("2026-09-10T18:00:00Z");
    private static final String EVENT = "2026-09-09T17:02:03.123456789-03:00";
    private static final ImmutableFingerprint EVIDENCE =
            new ImmutableFingerprint("synthetic-pair-v1", "a".repeat(64));
    private final ColetaGraphQlTemporalMapper mapper = new ColetaGraphQlTemporalMapper();
    private final LigarReferenciaTemporalColeta linker = new LigarReferenciaTemporalColeta();

    @Test
    void malformedPreservedPayloadIsRejectedWithoutRetainingItsSensitiveCause() {
        final var valid = data("pending", null);
        final String sensitive = "synthetic-sensitive-payload";
        final var malformed =
                ColetaStageRecord.valid(
                        valid.inputOrdinal(),
                        valid.sourceKey(),
                        valid.sequenceCodePresence(),
                        valid.sequenceCodeJson(),
                        "{\"request_date\":\"" + sensitive,
                        valid.fieldPresenceJson(),
                        valid.relationCandidatesJson(),
                        valid.status(),
                        valid.freshnessRaw(),
                        valid.freshnessAtUtc(),
                        valid.freshnessOrigin());

        final var failure =
                assertThrows(IllegalArgumentException.class, () -> link(malformed, null));

        assertNull(failure.getCause());
        assertEquals(0, failure.getSuppressed().length);
        assertFalse(failure.toString().contains(sensitive));
    }

    @Test
    void complementsExplicitTypedPairWithoutChangingDataExportPayloadPresenceOrFallback()
            throws Exception {
        final var data = data("pending", null);
        final String payload = data.payloadJson();
        final String presence = data.fieldPresenceJson();
        final var ref = reference(node("pending", EVENT));
        final var result = link(data, ref);
        assertEquals(Outcome.COMPLEMENTED, result.outcome());
        assertEquals(Instant.parse("2026-09-09T20:02:03.123456789Z"), result.candidateAtUtc());
        assertSame(data, result.dataExport());
        assertSame(ref, result.reference());
        assertEquals(payload, data.payloadJson());
        assertEquals(presence, data.fieldPresenceJson());
        assertFalse(JSON.readTree(payload).has("status_updated_at"));
        assertEquals("ABSENT", JSON.readTree(presence).path("status_updated_at").asText());
        assertEquals(ColetaFreshnessOrigin.REQUEST_DATE, data.freshnessOrigin());
        assertEquals(Instant.parse("2026-09-09T03:00:00Z"), data.freshnessAtUtc());
        assertEquals(ColetaAttributePresence.VALUE, ref.statusUpdatedAt().presence());
        assertEquals("\"" + EVENT + "\"", ref.statusUpdatedAt().rawJson());
        assertEquals(CAPTURE, ref.observedAt());
        assertEquals(ScopedSourceIdentity.WireType.STRING, ref.identity().sourceKey().wireType());
        assertEquals(ScopedSourceIdentity.WireType.INTEGER, data.sourceKey().wireType());
        assertFalse(result.toString().contains(EVENT));
        assertFalse(ref.toString().contains(EVENT));
        assertFalse(ref.statusUpdatedAt().toString().contains(EVENT));
        assertFalse(result.binding().toString().contains("SYNTHETIC_TENANT"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "broken",
                "2026-09-09",
                "2026-09-09T17:02:03",
                "2018-11-04T00:30:00",
                "2019-02-16T23:30:00",
                "2026-02-30T10:00:00Z",
                "2026-09-09T17:02:03.1234567890Z"
            })
    void invalidOrOffsetlessReferenceNeverUsesCivilDateUpdatedAtOrCapture(final String timestamp) {
        final var raw = node("pending", timestamp);
        raw.put("updatedAt", EVENT);
        final var ref = reference(raw);
        assertNull(ref.statusAtUtc());
        final var result = link(data("pending", null), ref);
        assertEquals(Outcome.REFERENCE_INVALID, result.outcome());
        assertNull(result.candidateAtUtc());
        assertEquals(timestamp, ref.statusUpdatedAt().text());
    }

    @Test
    void absenceNullAndWrongTypeStayDistinct() {
        final var absent = node("pending", EVENT);
        absent.remove("statusUpdatedAt");
        final var explicitNull = node("pending", EVENT).putNull("statusUpdatedAt");
        final var wrongType = node("pending", EVENT).put("statusUpdatedAt", 17);
        assertEquals(
                ColetaAttributePresence.ABSENT, reference(absent).statusUpdatedAt().presence());
        assertEquals(
                ColetaAttributePresence.NULL, reference(explicitNull).statusUpdatedAt().presence());
        final var invalid = reference(wrongType);
        assertEquals(ColetaAttributePresence.VALUE, invalid.statusUpdatedAt().presence());
        assertEquals("17", invalid.statusUpdatedAt().rawJson());
        assertNull(invalid.statusUpdatedAt().text());
        for (final var raw : List.of(absent, explicitNull, wrongType)) {
            assertEquals(
                    Outcome.REFERENCE_INVALID,
                    link(data("pending", null), reference(raw)).outcome());
        }
        assertEquals(Outcome.REFERENCE_ABSENT, link(data("pending", null), null).outcome());
    }

    @ParameterizedTest
    @ValueSource(strings = {"done", "finished", "canceled", "new_supplier_code", ""})
    void statusRaceAndUnknownCodesBlockTheComplement(final String status) {
        final var result = link(data("pending", null), reference(node(status, EVENT)));
        assertEquals(Outcome.STATUS_MISMATCH, result.outcome());
        assertNull(result.candidateAtUtc());
    }

    @Test
    void matchingUnknownStatusesDoNotCreateEquivalence() {
        assertEquals(
                Outcome.STATUS_MISMATCH,
                link(data("unknown", null), reference(node("unknown", EVENT))).outcome());
    }

    @Test
    void nativeStatusTimeIsRetainedAndConflictingSourcesAreBlocked() {
        final var data = data("pending", "2026-09-09T20:02:03.123456789Z");
        assertEquals(
                Outcome.DATA_EXPORT_TIME_RETAINED,
                link(data, reference(node("pending", EVENT))).outcome());
        assertEquals(data.freshnessAtUtc(), link(data, null).candidateAtUtc());
        final var conflict = link(data, reference(node("pending", "2026-09-09T20:02:04Z")));
        assertEquals(Outcome.SOURCE_TIME_CONFLICT, conflict.outcome());
        assertNull(conflict.candidateAtUtc());
    }

    @Test
    void repeatedRowsAndReverseArrivalPreserveIndividualEventsWithoutChoosingAWinner() {
        final var older = reference(node("pending", "2026-09-09T10:00:00.1231Z"));
        final var newer = reference(node("pending", "2026-09-09T10:00:00.1234Z"));
        final var data = data("pending", null);
        for (final var ref : List.of(newer, older, older, newer)) {
            assertEquals(ref.statusAtUtc(), link(data, ref).candidateAtUtc());
        }
        // Instantes distintos que colidem em DATETIME2(3) continuam distintos aqui.
        assertEquals(300_000, newer.statusAtUtc().getNano() - older.statusAtUtc().getNano());
    }

    @Test
    void typedKeyCannotBeCoercedEvenWhenItsPrintedDigitsMatch() {
        final var integerRef = reference(node("pending", EVENT).put("id", 17));
        assertEquals(Outcome.IDENTITY_MISMATCH, link(data("pending", null), integerRef).outcome());
        final var wrongKey = reference(node("pending", EVENT).put("id", "18"));
        assertEquals(Outcome.IDENTITY_MISMATCH, link(data("pending", null), wrongKey).outcome());
    }

    @Test
    void identityAndExecutionAndWindowAreCheckedBeforeUse() {
        final var data = data("pending", null);
        final var raw = node("pending", EVENT);
        final var otherScope =
                mapper.map(REF_RUN, "SYNTHETIC_SOURCE", "ANOTHER_TENANT", DATE, 1, 1, CAPTURE, raw);
        assertEquals(Outcome.IDENTITY_MISMATCH, link(data, otherScope).outcome());
        final var otherRun =
                mapper.map(
                        DATA_RUN, "SYNTHETIC_SOURCE", "SYNTHETIC_TENANT", DATE, 1, 1, CAPTURE, raw);
        assertEquals(Outcome.EXECUTION_MISMATCH, link(data, otherRun).outcome());
        final var otherQuery =
                mapper.map(
                        REF_RUN,
                        "SYNTHETIC_SOURCE",
                        "SYNTHETIC_TENANT",
                        DATE.minusDays(1),
                        1,
                        1,
                        CAPTURE,
                        raw);
        assertEquals(Outcome.WINDOW_MISMATCH, link(data, otherQuery).outcome());
        assertEquals(
                Outcome.WINDOW_MISMATCH,
                link(data, reference(raw.put("requestDate", "2026-09-08"))).outcome());
        final var wrongBatch = new ColetaStageBatch(REF_RUN, 1, List.of(data), CAPTURE);
        assertEquals(
                Outcome.EXECUTION_MISMATCH,
                linker.execute(wrongBatch, 0, reference(node("pending", EVENT)), binding())
                        .outcome());
    }

    @Test
    void incompatibleContractAndQuarantinedInputHaveNoCandidate() {
        final var ref = reference(node("pending", EVENT));
        final var wrong =
                new ColetaTemporalObservation(
                        ref.executionId(),
                        ref.identity(),
                        ref.queryDate(),
                        "wrong-version",
                        ref.selectionFingerprint(),
                        1,
                        1,
                        ref.observedAt(),
                        ref.status(),
                        ref.statusUpdatedAt(),
                        ref.requestDate(),
                        ref.statusAtUtc());
        assertEquals(Outcome.CONTRACT_MISMATCH, link(data("pending", null), wrong).outcome());
        final var quarantine = ColetaStageRecord.quarantine(1, null, "INVALID_SOURCE_KEY");
        final var result = link(quarantine, ref);
        assertEquals(Outcome.DATA_EXPORT_QUARANTINED, result.outcome());
        assertNull(result.candidateAtUtc());
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEFAULT", "global", "SINGLETON", "", " invalid "})
    void globalOrImplicitTenantIsRejected(final String tenant) {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mapper.map(
                                REF_RUN,
                                "SYNTHETIC_SOURCE",
                                tenant,
                                DATE,
                                1,
                                1,
                                CAPTURE,
                                node("pending", EVENT)));
    }

    @Test
    void bindingCannotCrossTenantOrEntity() {
        final var current = binding();
        final var other =
                new ScopedSourceIdentity(
                        "SYNTHETIC_SOURCE",
                        "ANOTHER",
                        FirstWaveIdentityContract.Entity.COLETAS,
                        current.referenceIdentity().sourceKey());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ColetaTemporalIdentityBinding(
                                DATA_RUN,
                                REF_RUN,
                                current.dataExportIdentity(),
                                other,
                                DATE,
                                EVIDENCE));
        final var freight =
                new ScopedSourceIdentity(
                        "SYNTHETIC_SOURCE",
                        "SYNTHETIC_TENANT",
                        FirstWaveIdentityContract.Entity.FRETES,
                        current.referenceIdentity().sourceKey());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ColetaTemporalIdentityBinding(
                                DATA_RUN,
                                REF_RUN,
                                current.dataExportIdentity(),
                                freight,
                                DATE,
                                EVIDENCE));
    }

    private LigarReferenciaTemporalColeta.Result link(
            final ColetaStageRecord data, final ColetaTemporalObservation reference) {
        return linker.execute(
                new ColetaStageBatch(DATA_RUN, 1, List.of(data), CAPTURE), 0, reference, binding());
    }

    private static ColetaTemporalIdentityBinding binding() {
        final var data =
                new ScopedSourceIdentity(
                        "SYNTHETIC_SOURCE",
                        "SYNTHETIC_TENANT",
                        FirstWaveIdentityContract.Entity.COLETAS,
                        new ScopedSourceIdentity.SourceKey(
                                ScopedSourceIdentity.WireType.INTEGER, "INTEGER:17"));
        final var ref =
                new ScopedSourceIdentity(
                        "SYNTHETIC_SOURCE",
                        "SYNTHETIC_TENANT",
                        FirstWaveIdentityContract.Entity.COLETAS,
                        new ScopedSourceIdentity.SourceKey(
                                ScopedSourceIdentity.WireType.STRING, "STRING:17"));
        return new ColetaTemporalIdentityBinding(DATA_RUN, REF_RUN, data, ref, DATE, EVIDENCE);
    }

    private ColetaTemporalObservation reference(final ObjectNode node) {
        return mapper.map(
                REF_RUN, "SYNTHETIC_SOURCE", "SYNTHETIC_TENANT", DATE, 1, 1, CAPTURE, node);
    }

    private static ObjectNode node(final String status, final String timestamp) {
        return JSON.createObjectNode()
                .put("id", "17")
                .put("status", status)
                .put("statusUpdatedAt", timestamp)
                .put("requestDate", DATE.toString());
    }

    private static ColetaStageRecord data(final String status, final String timestamp) {
        final var raw =
                JSON.createObjectNode()
                        .put("id", 17)
                        .put("status", status)
                        .put("request_date", DATE.toString())
                        .put("updated_at", "2026-09-10T12:00:00Z");
        if (timestamp != null) {
            raw.put("status_updated_at", timestamp);
        }
        return new ColetaDataExportRecordMapper().map(1, raw);
    }
}
