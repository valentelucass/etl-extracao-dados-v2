package br.com.esl.etl.v2.plataforma.qualidade;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Resumo fixo O(1) devolvido pelo SQL Server. Não contém chave de origem, amostra nem payload. */
public record DataQualityRunSummary(
        UUID executionId,
        DataQualityPolicyReference policyReference,
        String evaluationSha256,
        int expectedChecks,
        int completedChecks,
        int passedChecks,
        int failedChecks,
        long evaluatedRows,
        long failedRows,
        DataQualityState state,
        Instant evaluatedAt) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public DataQualityRunSummary {
        executionId = Objects.requireNonNull(executionId, "O execution_id é obrigatório.");
        policyReference =
                Objects.requireNonNull(policyReference, "A referência da policy é obrigatória.");
        evaluationSha256 =
                Objects.requireNonNull(
                        evaluationSha256, "O fingerprint da avaliação é obrigatório.");
        state = Objects.requireNonNull(state, "O estado de Data Quality é obrigatório.");
        evaluatedAt = Objects.requireNonNull(evaluatedAt, "O horário da avaliação é obrigatório.");

        if (!SHA_256.matcher(evaluationSha256).matches()) {
            throw new IllegalArgumentException(
                    "O fingerprint da avaliação deve ser SHA-256 hexadecimal minúsculo.");
        }
        if (expectedChecks != 4) {
            throw new IllegalArgumentException("O framework comum exige exatamente quatro checks.");
        }
        if (completedChecks < 0
                || passedChecks < 0
                || failedChecks < 0
                || completedChecks > expectedChecks
                || passedChecks + failedChecks != completedChecks) {
            throw new IllegalArgumentException("As contagens de checks não reconciliam.");
        }
        if (evaluatedRows < 0 || failedRows < 0 || failedRows > evaluatedRows) {
            throw new IllegalArgumentException("As contagens avaliadas são inválidas.");
        }
        if (state == DataQualityState.PASSED
                && (completedChecks != expectedChecks || failedChecks != 0)) {
            throw new IllegalArgumentException("Uma avaliação parcial ou falha não pode passar.");
        }
        if (state == DataQualityState.FAILED
                && completedChecks == expectedChecks
                && failedChecks == 0) {
            throw new IllegalArgumentException("Uma avaliação íntegra não pode terminar falha.");
        }
    }

    void requireExactSuccessfulMatch(final DataQualityEvaluationRequest request) {
        Objects.requireNonNull(request, "O pedido de Data Quality é obrigatório.");
        if (!executionId.equals(request.executionId())
                || !policyReference.equals(request.policyReference())) {
            throw new IllegalArgumentException("O resumo de Data Quality diverge do pedido.");
        }
        if (state != DataQualityState.PASSED
                || completedChecks != expectedChecks
                || passedChecks != expectedChecks
                || failedChecks != 0) {
            throw new IllegalStateException(
                    "O gate de Data Quality não foi aprovado integralmente.");
        }
    }

    @Override
    public String toString() {
        return "DataQualityRunSummary[state="
                + state
                + ", completedChecks="
                + completedChecks
                + ", failedChecks="
                + failedChecks
                + "]";
    }
}
