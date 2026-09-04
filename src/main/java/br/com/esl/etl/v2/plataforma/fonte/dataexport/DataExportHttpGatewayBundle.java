package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.util.Objects;
import java.util.Optional;

/** Gateways de um único workload; ambos compartilham admissão, circuito e binding estrutural. */
public final class DataExportHttpGatewayBundle {

    private final DataExportGateway dataGateway;
    private final DataExportTemplateInfoGateway templateInfoGateway;
    private final Optional<DataExportContractObservationConfiguration> observationConfiguration;

    /** Bundle sem binding de contrato, utilizável somente fora do caminho promovível. */
    public DataExportHttpGatewayBundle(
            final DataExportGateway dataGateway,
            final DataExportTemplateInfoGateway templateInfoGateway) {
        this(dataGateway, templateInfoGateway, Optional.empty());
    }

    private DataExportHttpGatewayBundle(
            final DataExportGateway dataGateway,
            final DataExportTemplateInfoGateway templateInfoGateway,
            final Optional<DataExportContractObservationConfiguration> observationConfiguration) {
        this.dataGateway = Objects.requireNonNull(dataGateway, "O gateway de dados é obrigatório.");
        this.templateInfoGateway =
                Objects.requireNonNull(
                        templateInfoGateway, "O gateway de metadados é obrigatório.");
        this.observationConfiguration =
                Objects.requireNonNull(
                        observationConfiguration,
                        "O binding opcional de observação é obrigatório.");
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
                                "A configuração de observação é obrigatória.")));
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
