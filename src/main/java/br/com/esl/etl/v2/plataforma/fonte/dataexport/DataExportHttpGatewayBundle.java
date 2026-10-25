package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** Gateways de um único workload; ambos compartilham admissão, circuito e binding estrutural. */
public final class DataExportHttpGatewayBundle {

    private final DataExportGateway dataGateway;
    private final DataExportTemplateInfoGateway templateInfoGateway;
    private final Optional<DataExportContractObservationConfiguration> observationConfiguration;
    private final Optional<Consumer<DataExportRequestProfile>> requestProfileVerifier;

    /** Bundle sem binding de contrato, utilizável somente fora do caminho promovível. */
    public DataExportHttpGatewayBundle(
            final DataExportGateway dataGateway,
            final DataExportTemplateInfoGateway templateInfoGateway) {
        this(dataGateway, templateInfoGateway, Optional.empty(), Optional.empty());
    }

    private DataExportHttpGatewayBundle(
            final DataExportGateway dataGateway,
            final DataExportTemplateInfoGateway templateInfoGateway,
            final Optional<DataExportContractObservationConfiguration> observationConfiguration,
            final Optional<Consumer<DataExportRequestProfile>> requestProfileVerifier) {
        this.dataGateway = Objects.requireNonNull(dataGateway, "O gateway de dados é obrigatório.");
        this.templateInfoGateway =
                Objects.requireNonNull(
                        templateInfoGateway, "O gateway de metadados é obrigatório.");
        this.observationConfiguration =
                Objects.requireNonNull(
                        observationConfiguration,
                        "O binding opcional de observação é obrigatório.");
        this.requestProfileVerifier = Objects.requireNonNull(requestProfileVerifier);
    }

    static DataExportHttpGatewayBundle contractBound(
            final DataExportGateway dataGateway,
            final DataExportTemplateInfoGateway templateInfoGateway,
            final DataExportContractObservationConfiguration observationConfiguration) {
        return new DataExportHttpGatewayBundle(
                dataGateway,
                templateInfoGateway,
                Optional.of(
                        Objects.requireNonNull(
                                observationConfiguration,
                                "A configuração de observação é obrigatória.")),
                Optional.empty());
    }

    /** Only concrete final HTTP adapters can supply the closed policy callback. */
    static DataExportHttpGatewayBundle materialized(
            final HttpDataExportGateway rawData,
            final HttpDataExportTemplateInfoGateway rawInfo,
            final DataExportCircuitBreakerRegistry circuitRegistry,
            final DataExportContractObservationConfiguration observationConfiguration) {
        Objects.requireNonNull(rawData);
        Objects.requireNonNull(rawInfo);
        Objects.requireNonNull(circuitRegistry);
        return new DataExportHttpGatewayBundle(
                new CircuitBreakingDataExportGateway(rawData, circuitRegistry),
                new CircuitBreakingDataExportTemplateInfoGateway(rawInfo, circuitRegistry),
                Optional.of(Objects.requireNonNull(observationConfiguration)),
                Optional.of(
                        profile -> {
                            rawData.requireRequestProfile(profile);
                            rawInfo.requireRequestProfile(profile);
                        }));
    }

    /** A contract wrapper retains the real factory's policy without accepting caller assertions. */
    static DataExportHttpGatewayBundle contractBound(
            final DataExportGateway dataGateway,
            final DataExportTemplateInfoGateway templateInfoGateway,
            final DataExportContractObservationConfiguration observationConfiguration,
            final DataExportHttpGatewayBundle materializedBundle) {
        return new DataExportHttpGatewayBundle(
                dataGateway,
                templateInfoGateway,
                Optional.of(Objects.requireNonNull(observationConfiguration)),
                Objects.requireNonNull(materializedBundle).requestProfileVerifier);
    }

    void requireRequestProfile(final DataExportRequestProfile profile) {
        if (Objects.requireNonNull(profile).observed()) {
            requestProfileVerifier
                    .orElseThrow(
                            () ->
                                    new IllegalArgumentException(
                                            "OBSERVED_REQUEST_POLICY_EVIDENCE_REQUIRED"))
                    .accept(profile);
        }
    }

    public DataExportGateway dataGateway() {
        return dataGateway;
    }

    public DataExportTemplateInfoGateway templateInfoGateway() {
        return templateInfoGateway;
    }

    Optional<DataExportContractObservationConfiguration> observationConfiguration() {
        return observationConfiguration;
    }

    @Override
    public String toString() {
        return "DataExportHttpGatewayBundle[contractBound="
                + observationConfiguration.isPresent()
                + "]";
    }
}
