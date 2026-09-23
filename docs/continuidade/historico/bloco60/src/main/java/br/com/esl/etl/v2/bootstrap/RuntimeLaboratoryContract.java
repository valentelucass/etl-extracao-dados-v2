package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportFirstWaveContractCatalog;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Separate, exact synthetic schema. Activation is an administered artifact resource, never an
 * override.
 */
final class RuntimeLaboratoryContract {
    private RuntimeLaboratoryContract() {}

    static SourceContractRelease resolve(
            final RuntimeConfiguration configuration, final DataExportTemplate template) {
        try (var input =
                RuntimeLaboratoryContract.class.getResourceAsStream(
                        "/runtime-laboratory.properties")) {
            if (input == null) {
                return DataExportFirstWaveContractCatalog.release(template);
            }
            final byte[] bytes = input.readNBytes(257);
            final String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            if (bytes.length > 256
                    || !text.matches("bloco5[45]-v1\\nvalid-until=[0-9TZ:.+-]+\\n")) {
                throw new IllegalArgumentException("LABORATORY_PROFILE_INVALID");
            }
            final Instant until = Instant.parse(text.substring(text.indexOf('=') + 1).strip());
            validate(configuration, until);
            if (template.laboratoryBackfillOnly()) {
                if (!text.startsWith("bloco55-v1\n")) {
                    throw new IllegalArgumentException("B55_ADMINISTERED_PROFILE_REQUIRED");
                }
                return RuntimeFiveVerticalContract.release(template);
            }
            return release(template);
        } catch (final java.io.IOException failure) {
            throw new IllegalArgumentException("LABORATORY_PROFILE_UNAVAILABLE", failure);
        }
    }

    static void validate(final RuntimeConfiguration configuration, final Instant until) {
        final var source = configuration.dataExport().orElseThrow();
        final var settings = source.settings();
        final var uri = settings.baseUri();
        if (!configuration.clock().instant().isBefore(until)
                || !"LOCAL_SHADOW".equals(configuration.environment().name())
                || !"LOCAL_V2".equals(source.sourceInstance())
                || !"LOCAL_V2".equals(source.tenantScope())
                || !"http".equals(uri.getScheme())
                || !"127.0.0.1".equals(uri.getHost())
                || uri.getPort() < 1
                || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                || settings.maxResponseBytes() > 1048576
                || !settings.sourceZone().getId().equals("America/Sao_Paulo")
                || settings.preferredTransport()
                        != br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport
                                .GET_WITH_QUERY
                || settings.retryPolicy().maxAttempts() != 1
                || source.resiliencePolicy().maxRequestsPerCycle() > 5
                || source.resiliencePolicy().maxRequestsPerWorkload() > 5
                || source.resiliencePolicy().maxRepartitions() != 0
                || source.resiliencePolicy()
                                .cycleTimeout()
                                .compareTo(java.time.Duration.ofSeconds(60))
                        > 0) {
            throw new IllegalArgumentException("LABORATORY_SCOPE_OR_VALIDITY_REJECTED");
        }
    }

    static SourceContractRelease release(final DataExportTemplate template) {
        final var original = DataExportFirstWaveContractCatalog.release(template);
        final var metadata = new ArrayList<>(original.metadata().elements());
        final var fields = new ArrayList<>(original.response().fields());
        final String date = template == DataExportTemplate.COLETAS ? "request_date" : "servico_em";
        metadata.add(
                ContractMetadata.Element.fromDeclaredType(
                        ContractMetadata.ElementKind.DATA_FIELD, date, Optional.of("datetime")));
        metadata.add(
                ContractMetadata.Element.fromDeclaredType(
                        ContractMetadata.ElementKind.DATA_FIELD, "status", Optional.of("string")));
        for (final String path : List.of("/" + date, "/status")) {
            fields.add(
                    new ContractResponse.Field(
                            path,
                            ContractResponse.Cardinality.SCALAR,
                            ContractResponse.Presence.REQUIRED,
                            false,
                            List.of(ContractResponse.JsonType.STRING)));
        }
        return SourceContractRelease.create(
                original.sourceKind(),
                original.documentReference(),
                "bloco54-synthetic-v1",
                new ContractMetadata(metadata, Optional.empty()),
                new ContractResponse(
                        "/data",
                        ContractResponse.Cardinality.ARRAY,
                        ContractResponse.ObservationState.POPULATED,
                        "/id",
                        fields));
    }
}
