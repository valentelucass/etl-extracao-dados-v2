package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Página normalizada e defensivamente copiada, inclusive para uma raiz objeto. */
public final class DataExportPageResponse {

    private final List<JsonNode> records;
    private final Optional<ContractResponse> contractObservation;
    private final Optional<ContractObservationLimits> observationLimits;
    private final Optional<ImmutableFingerprint> observationBoundaryFingerprint;

    public DataExportPageResponse(final List<JsonNode> records) {
        this(records, Optional.empty(), Optional.empty(), Optional.empty());
    }

    static DataExportPageResponse observed(
            final List<JsonNode> records,
            final ContractResponse observation,
            final ContractObservationLimits limits,
            final ContractResponsePathBoundary pathBoundary) {
        return new DataExportPageResponse(
                records,
                Optional.of(observation),
                Optional.of(limits),
                Optional.of(pathBoundary.fingerprint()));
    }

    DataExportPageResponse withObservation(
            final ContractResponse observation,
            final ContractObservationLimits limits,
            final ContractResponsePathBoundary pathBoundary) {
        return new DataExportPageResponse(
                records,
                Optional.of(observation),
                Optional.of(limits),
                Optional.of(pathBoundary.fingerprint()),
                false);
    }

    private DataExportPageResponse(
            final List<JsonNode> records,
            final Optional<ContractResponse> contractObservation,
            final Optional<ContractObservationLimits> observationLimits,
            final Optional<ImmutableFingerprint> observationBoundaryFingerprint) {
        this(records, contractObservation, observationLimits, observationBoundaryFingerprint, true);
    }

    private DataExportPageResponse(
            final List<JsonNode> records,
            final Optional<ContractResponse> contractObservation,
            final Optional<ContractObservationLimits> observationLimits,
            final Optional<ImmutableFingerprint> observationBoundaryFingerprint,
            final boolean defensiveCopy) {
        Objects.requireNonNull(records, "Os registros são obrigatórios.");
        if (defensiveCopy) {
            final List<JsonNode> copied = new ArrayList<>(records.size());
            for (final JsonNode record : records) {
                copied.add(
                        Objects.requireNonNull(record, "A página não aceita registro nulo.")
                                .deepCopy());
            }
            this.records = List.copyOf(copied);
        } else {
            this.records = records;
        }
        this.contractObservation =
                Objects.requireNonNull(contractObservation, "A observação é obrigatória.");
        this.observationLimits =
                Objects.requireNonNull(observationLimits, "Os limites são obrigatórios.");
        this.observationBoundaryFingerprint =
                Objects.requireNonNull(
                        observationBoundaryFingerprint,
                        "O fingerprint da fronteira é obrigatório.");
        if (contractObservation.isPresent() != observationLimits.isPresent()
                || contractObservation.isPresent() != observationBoundaryFingerprint.isPresent()) {
            throw new IllegalArgumentException(
                    "A observação de contrato exige seus limites vinculados.");
        }
        contractObservation.ifPresent(
                observation -> {
                    final boolean empty = this.records.isEmpty();
                    if (empty
                            != (observation.observationState()
                                    == ContractResponse.ObservationState.EMPTY)) {
                        throw new IllegalArgumentException(
                                "A observação de contrato não corresponde à página normalizada.");
                    }
                });
    }

    public List<JsonNode> records() {
        return records.stream().map(record -> record.<JsonNode>deepCopy()).toList();
    }

    /** Entrega uma cópia defensiva por linha, sem duplicar a página física inteira. */
    public void forEachRecord(final Consumer<JsonNode> consumer) {
        final Consumer<JsonNode> required =
                Objects.requireNonNull(consumer, "O consumidor de registros é obrigatório.");
        for (final JsonNode record : records) {
            required.accept(record.deepCopy());
        }
    }

    /** Contagem O(1), sem copiar a página apenas para auditoria ou logs. */
    public int recordCount() {
        return records.size();
    }

    public Optional<ContractResponse> contractObservation() {
        return contractObservation;
    }

    public Optional<ContractObservationLimits> observationLimits() {
        return observationLimits;
    }

    public Optional<ImmutableFingerprint> observationBoundaryFingerprint() {
        return observationBoundaryFingerprint;
    }

    @Override
    public String toString() {
        return "DataExportPageResponse[recordCount="
                + records.size()
                + ", contractObservation="
                + contractObservation.isPresent()
                + "]";
    }
}
