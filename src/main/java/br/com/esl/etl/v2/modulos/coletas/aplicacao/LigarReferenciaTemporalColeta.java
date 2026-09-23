package br.com.esl.etl.v2.modulos.coletas.aplicacao;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaFreshnessOrigin;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageDisposition;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageRecord;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStatus;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaTemporalIdentityBinding;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaTemporalObservation;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlReadOperation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Objects;

/**
 * Verifica um par já selecionado. Não procura registros, resolve duplicatas ou promove staging. O
 * resultado preserva as duas fontes para o consumidor SQL futuro (COL-TIME-06).
 */
public final class LigarReferenciaTemporalColeta {
    private static final ObjectMapper JSON = new ObjectMapper();

    public Result execute(
            final ColetaStageBatch batch,
            final int zeroBasedIndex,
            final ColetaTemporalObservation reference,
            final ColetaTemporalIdentityBinding binding) {
        Objects.requireNonNull(batch, "O batch é obrigatório.");
        Objects.requireNonNull(binding, "A correspondência é obrigatória.");
        final var record = batch.recordAt(zeroBasedIndex);
        if (record.disposition() != ColetaStageDisposition.VALID) {
            return result(record, reference, binding, Outcome.DATA_EXPORT_QUARANTINED);
        }
        if (!batch.executionId().equals(binding.dataExportExecutionId())
                || reference != null
                        && !reference.executionId().equals(binding.referenceExecutionId())) {
            return result(record, reference, binding, Outcome.EXECUTION_MISMATCH);
        }
        if (!record.sourceKey().equals(binding.dataExportIdentity().sourceKey())
                || reference != null && !reference.identity().equals(binding.referenceIdentity())) {
            return result(record, reference, binding, Outcome.IDENTITY_MISMATCH);
        }
        if (!binding.requestDate().toString().equals(dataExportRequestDate(record))
                || reference != null
                        && (!binding.requestDate().equals(reference.queryDate())
                                || !binding.requestDate()
                                        .toString()
                                        .equals(reference.requestDate().text()))) {
            return result(record, reference, binding, Outcome.WINDOW_MISMATCH);
        }
        final boolean nativeTime =
                record.freshnessOrigin() == ColetaFreshnessOrigin.STATUS_UPDATED_AT;
        if (reference == null) {
            return result(
                    record,
                    null,
                    binding,
                    nativeTime ? Outcome.DATA_EXPORT_TIME_RETAINED : Outcome.REFERENCE_ABSENT);
        }
        final var operation = GraphQlReadOperation.PICKS_TEMPORAL_REFERENCE;
        if (!operation.contractVersion().equals(reference.contractVersion())
                || !operation
                        .approvedDocument()
                        .fingerprint()
                        .equals(reference.selectionFingerprint())) {
            return result(record, reference, binding, Outcome.CONTRACT_MISMATCH);
        }
        final String referenceStatus =
                ColetaStatus.normalizeKnown(reference.status().text()).orElse(null);
        if (referenceStatus == null || !referenceStatus.equals(record.status().code())) {
            return result(record, reference, binding, Outcome.STATUS_MISMATCH);
        }
        if (reference.statusAtUtc() == null) {
            return result(record, reference, binding, Outcome.REFERENCE_INVALID);
        }
        if (nativeTime && !record.freshnessAtUtc().equals(reference.statusAtUtc())) {
            return result(record, reference, binding, Outcome.SOURCE_TIME_CONFLICT);
        }
        return result(
                record,
                reference,
                binding,
                nativeTime ? Outcome.DATA_EXPORT_TIME_RETAINED : Outcome.COMPLEMENTED);
    }

    private static String dataExportRequestDate(final ColetaStageRecord record) {
        try {
            final var field = JSON.readTree(record.payloadJson()).get("request_date");
            return field != null && field.isTextual() ? field.textValue() : null;
        } catch (final JsonProcessingException exception) {
            throw new IllegalArgumentException("O payload preservado de Coletas é inválido.");
        }
    }

    private static Result result(
            final ColetaStageRecord record,
            final ColetaTemporalObservation reference,
            final ColetaTemporalIdentityBinding binding,
            final Outcome outcome) {
        final Instant candidate =
                switch (outcome) {
                    case COMPLEMENTED -> reference.statusAtUtc();
                    case DATA_EXPORT_TIME_RETAINED -> record.freshnessAtUtc();
                    default -> null;
                };
        return new Result(record, reference, binding, outcome, candidate);
    }

    public enum Outcome {
        COMPLEMENTED,
        DATA_EXPORT_TIME_RETAINED,
        REFERENCE_ABSENT,
        REFERENCE_INVALID,
        DATA_EXPORT_QUARANTINED,
        IDENTITY_MISMATCH,
        EXECUTION_MISMATCH,
        WINDOW_MISMATCH,
        CONTRACT_MISMATCH,
        STATUS_MISMATCH,
        SOURCE_TIME_CONFLICT
    }

    /** O tipo separado impede que JDBC antigo trate o complemento como campo recebido no 6908. */
    public record Result(
            ColetaStageRecord dataExport,
            ColetaTemporalObservation reference,
            ColetaTemporalIdentityBinding binding,
            Outcome outcome,
            Instant candidateAtUtc) {
        @Override
        public String toString() {
            return "ColetaTemporalLink[outcome=" + outcome + ", sources=<redacted>]";
        }
    }
}
