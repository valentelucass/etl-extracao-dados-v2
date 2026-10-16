package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageRecord;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportClientSettings;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportRuntimeConfigurationFingerprint;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportSourceConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.SecretProvider;
import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.contrato.ContractValidator;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportColetasContractCatalog;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportContractAdapter;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportContractObservationConfiguration;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpAttempt;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpAttemptObserver;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpGatewayBundle;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpGatewayFactory;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageEntityLimitValidator;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRetryPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.EslRequestGovernor;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.EslWorkload;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceSleeper;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** One metadata request and one populated page, with no terminal or promotion claim. */
final class Coletas6908TestRetainedSource {
    private Coletas6908TestRetainedSource() {}

    static DataExportSourceConfiguration configuration(final Map<String, String> environment) {
        final String raw = environment.get("V2_DATAEXPORT_BASE_URL");
        final URI uri = URI.create(raw == null ? "" : raw);
        if (!"https".equals(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("TEST_SOURCE_HTTPS_REQUIRED");
        }
        final Duration timeout = Duration.ofSeconds(30);
        final var settings =
                new DataExportClientSettings(
                        uri,
                        ZoneId.of("America/Sao_Paulo"),
                        timeout,
                        DataExportTransport.GET_WITH_QUERY,
                        new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO),
                        Coletas6908TestRetainedRequest.MAXIMUM_BYTES);
        final var resilience =
                new EslResiliencePolicy(
                        Duration.ofSeconds(2),
                        1,
                        2,
                        2,
                        timeout,
                        Duration.ofSeconds(60),
                        Duration.ofSeconds(60),
                        Duration.ZERO,
                        0,
                        1,
                        Duration.ofSeconds(1));
        return new DataExportSourceConfiguration(
                Coletas6908TestRetainedRequest.SOURCE,
                Coletas6908TestRetainedRequest.TENANT,
                settings,
                resilience);
    }

    static ContractCompatibilityPolicy policy() {
        return ContractCompatibilityPolicy.create(
                "test-retained-coletas-v1",
                DataExportColetasContractCatalog.release().contractFingerprint(),
                List.of());
    }

    static ContractExecutionBinding binding(
            final Coletas6908TestRetainedRequest request,
            final DataExportSourceConfiguration configuration) {
        return ContractExecutionBinding.create(
                request.executionId,
                DataExportColetasContractCatalog.release(),
                policy(),
                DataExportRuntimeConfigurationFingerprint.from(configuration));
    }

    static DataExportHttpGatewayBundle gateways(
            final DataExportSourceConfiguration configuration,
            final SecretProvider secrets,
            final Clock clock,
            final Attempts attempts,
            final Runnable checkpoint) {
        final var release = DataExportColetasContractCatalog.release();
        final var observation =
                DataExportContractObservationConfiguration.forRelease(
                        DataExportTemplate.COLETAS,
                        release,
                        ContractObservationLimits.runtimeDefaults(),
                        ContractResponsePathBoundary.forRuntime(release, policy()),
                        DataExportRuntimeConfigurationFingerprint.from(configuration));
        final CancellationToken cancellation =
                () -> {
                    checkpoint.run();
                    return false;
                };
        final var governor =
                new EslRequestGovernor(
                        configuration.resiliencePolicy(),
                        MonotonicTicker.systemTicker(),
                        ResilienceSleeper.threadSleeper());
        return new DataExportHttpGatewayFactory(configuration, secrets, clock)
                .forWorkload(
                        governor.beginCycle(cancellation),
                        EslWorkload.COLETAS,
                        attempts,
                        observation);
    }

    static Capture capture(
            final DataExportHttpGatewayBundle gateways,
            final Clock clock,
            final Attempts attempts,
            final Runnable checkpoint) {
        return capture(gateways, clock, attempts, checkpoint, () -> {});
    }

    static Capture capture(
            final DataExportHttpGatewayBundle gateways,
            final Clock clock,
            final Attempts attempts,
            final Runnable checkpoint,
            final Runnable sqlBoundary) {
        String stage = "INFO";
        try {
            checkpoint.run();
            final var validator =
                    new ContractValidator(DataExportColetasContractCatalog.release(), policy());
            final var info = gateways.templateInfoGateway().fetchInfo(DataExportTemplate.COLETAS);
            checkpoint.run();
            sqlBoundary.run();
            if (info.template() != DataExportTemplate.COLETAS
                    || info.httpStatus() < 200
                    || info.httpStatus() >= 300) {
                throw new IllegalStateException("TEST_SOURCE_INFO_REFUSED");
            }
            stage = "METADATA_CONTRACT";
            validator.validateMetadata(DataExportContractAdapter.metadata(info));
            final var request =
                    new DataExportPageRequest(
                            DataExportTemplate.COLETAS,
                            new BusinessDateRange(
                                    Coletas6908TestRetainedRequest.DAY,
                                    Coletas6908TestRetainedRequest.DAY),
                            Optional.empty(),
                            1,
                            1,
                            DataExportTemplate.COLETAS.defaultOrderBy());
            checkpoint.run();
            stage = "DATA_PAGE_ONE";
            final var page = gateways.dataGateway().fetch(request);
            checkpoint.run();
            sqlBoundary.run();
            stage = "ENTITY_BOUND";
            if (page.recordCount() < 1
                    || page.recordCount() > Coletas6908TestRetainedRequest.MAXIMUM_ROWS) {
                throw new IllegalStateException("TEST_SOURCE_ROW_BOUND");
            }
            final int distinct =
                    DataExportPageEntityLimitValidator.countDistinctEntities(request, page);
            stage = "RESPONSE_CONTRACT";
            validator.validateResponse(
                    page.contractObservation()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "TEST_SOURCE_OBSERVATION_REQUIRED")));
            stage = "REAL_MAPPER";
            final List<ColetaStageRecord> records = new ArrayList<>(page.recordCount());
            final var mapper = new ColetaDataExportRecordMapper();
            page.forEachRecord(
                    row -> {
                        checkpoint.run();
                        final var record =
                                mapper.map(
                                        (records.size() % ColetaStageRecord.MAXIMUM_PAGE_SIZE) + 1,
                                        row);
                        if (record.quarantineReasonCode() != null) {
                            throw new MappingFailure(record.quarantineReasonCode());
                        }
                        records.add(record);
                    });
            checkpoint.run();
            if (attempts.info != 1 || attempts.data != 1 || records.size() != page.recordCount()) {
                throw new IllegalStateException("TEST_SOURCE_ATTEMPT_COUNT");
            }
            return new Capture(
                    info.httpStatus(), info.fields().size(), distinct, clock.instant(), records);
        } catch (final RuntimeException failure) {
            throw new SourceFailure(stage, failure);
        }
    }

    static final class SourceFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final String stage;

        SourceFailure(final String stage, final RuntimeException cause) {
            super("TEST_SOURCE_REFUSED", cause);
            this.stage = stage;
        }
    }

    static final class MappingFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final String reason;

        MappingFailure(final String reason) {
            super("TEST_SOURCE_MAPPER_QUARANTINE");
            this.reason =
                    java.util.Set.of(
                                            "INVALID_COLETA_RECORD",
                                            "INVALID_SEQUENCE_CODE_TYPE",
                                            "INVALID_STATUS_TYPE",
                                            "INVALID_CANCELLATION_REASON_TYPE",
                                            "MISSING_SOURCE_KEY",
                                            "INVALID_SOURCE_KEY_TYPE",
                                            "INVALID_SOURCE_KEY_VALUE",
                                            "SOURCE_KEY_TOO_LONG")
                                    .contains(reason)
                            ? reason
                            : "MAPPER_REASON_NOT_ALLOWLISTED";
        }
    }

    record Capture(
            int infoHttpStatus,
            int metadataFields,
            int distinctRoots,
            Instant observedAt,
            List<ColetaStageRecord> records) {
        Capture {
            records = List.copyOf(records);
        }

        @Override
        public String toString() {
            return "RetainedCapture[redacted]";
        }
    }

    static final class Attempts implements DataExportHttpAttemptObserver {
        private final Runnable checkpoint;
        int info;
        int data;

        Attempts(final Runnable checkpoint) {
            this.checkpoint = checkpoint;
        }

        @Override
        public void beforeAttempt(final DataExportHttpAttempt attempt) {
            checkpoint.run();
            if (info + data >= 2) {
                throw new IllegalStateException("TEST_HTTP_BUDGET");
            }
            if (attempt.templateId() != 6908) {
                throw new IllegalStateException("TEST_HTTP_TEMPLATE");
            }
            if ("info".equals(attempt.operation())) {
                if (info != 0 || data != 0) {
                    throw new IllegalStateException("TEST_HTTP_ORDER");
                }
                info++;
            } else if ("data-GET_WITH_QUERY".equals(attempt.operation())) {
                if (info != 1 || data != 0) {
                    throw new IllegalStateException("TEST_HTTP_ORDER");
                }
                data++;
            } else {
                throw new IllegalStateException("TEST_HTTP_OPERATION");
            }
        }
    }
}
