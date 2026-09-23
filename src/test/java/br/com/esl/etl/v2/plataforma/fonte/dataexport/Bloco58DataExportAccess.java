package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.contratos.bloco58.LocalCharacterization.Layer;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import java.util.function.Consumer;

/** Minimal test bridge to the existing normalization and contract-bound gateway. No HTTP. */
public final class Bloco58DataExportAccess {
    private Bloco58DataExportAccess() {}

    public static DataExportPageResponse parse(
            final String document,
            final DataExportPageRequest request,
            final DataExportContractObservationConfiguration configuration,
            final Consumer<Layer> layer) {
        try {
            layer.accept(Layer.PARSER);
            final var envelope = DataExportStrictJsonParser.readTree(document, 4_096);
            if (DataExportResponseForm.from(envelope) != configuration.expectedResponseForm()) {
                throw new IllegalArgumentException("ENVELOPE_FORM");
            }
            layer.accept(Layer.CONTRACT);
            final var adapter =
                    new DataExportContractAdapter(
                            configuration.observationLimits(),
                            configuration.responsePathBoundary());
            final var observation =
                    adapter.response(
                            envelope,
                            configuration.expectedResponseForm(),
                            configuration.approvedKeyPath());
            layer.accept(Layer.PARSER);
            final var normalized = new DataExportResponseNormalizer().normalize(envelope);
            layer.accept(Layer.PAGE_LIMIT);
            DataExportPageEntityLimitValidator.validate(request, normalized);
            layer.accept(Layer.CONTRACT);
            return normalized.withObservation(
                    observation,
                    configuration.observationLimits(),
                    configuration.responsePathBoundary());
        } catch (final java.io.IOException failure) {
            throw new IllegalArgumentException("INVALID_JSON");
        }
    }

    public static DataExportGateway enforce(
            final DataExportGateway source,
            final DataExportContractObservationConfiguration configuration,
            final ContractRunGuard guard) {
        final var bundle =
                DataExportHttpGatewayBundle.contractBound(
                        source,
                        ignored -> {
                            throw new IllegalStateException("NO_METADATA_IO");
                        },
                        configuration);
        return DataExportContractGate.enforce(bundle, configuration.template(), guard)
                .dataGateway();
    }
}
