package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaTemporalReferenceStore;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ExtrairColetasDataExport;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.PersistirReferenciasTemporaisColetas;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaTemporalIdentityBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionContext;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlPageStreamer;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.JdbcColetaTemporalLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.JdbcSqlServerColetaStagingGateway;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.JdbcSqlServerColetaTemporalGateway;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Objects;

/**
 * Explicit synthetic composition over the existing extraction pipelines. No CLI/default binding.
 */
public final class LocalColetasTemporalRuntime {
    public static final String SOURCE = "SYNTHETIC_COLETAS_TEMPORAL_LAB";
    public static final String TENANT = "SYNTHETIC_COLETAS_TENANT";
    private final ColetaTemporalLaboratorySession session;
    private final ExtrairColetasDataExport dataExport;
    private final PersistirReferenciasTemporaisColetas reference;
    private final ColetaTemporalReferenceStore temporal;
    private final JdbcColetaTemporalLaboratory consumer;
    private final boolean enabled;

    public LocalColetasTemporalRuntime(
            final ColetaTemporalLaboratorySession session,
            final DataExportPageStreamer dataStreamer,
            final GraphQlPageStreamer referenceStreamer,
            final Clock clock,
            final boolean enabled) {
        this.session = Objects.requireNonNull(session);
        this.enabled = enabled;
        temporal = new JdbcSqlServerColetaTemporalGateway(session, true);
        dataExport =
                new ExtrairColetasDataExport(
                        dataStreamer,
                        new ColetaDataExportRecordMapper(),
                        new JdbcSqlServerColetaStagingGateway(session, true));
        reference = new PersistirReferenciasTemporaisColetas(referenceStreamer, temporal, clock);
        consumer = new JdbcColetaTemporalLaboratory(session, enabled);
    }

    public JdbcColetaTemporalLaboratory.Result execute(
            final Input input,
            final Iterable<ColetaTemporalIdentityBinding> bindings,
            final CancellationToken cancellation) {
        if (!enabled) {
            throw new IllegalStateException("COL_LAB_DISABLED");
        }
        Objects.requireNonNull(input);
        Objects.requireNonNull(bindings);
        Objects.requireNonNull(cancellation);
        try {
            cancellation.throwIfCancellationRequested();
            final var data =
                    dataExport.execute(
                            input.dataGuard(), input.request(), input.dataLimits(), cancellation);
            consumer.completeDataExport(data.executionId(), cancellation);
            final var graph =
                    reference.execute(
                            input.referenceContext(),
                            SOURCE,
                            TENANT,
                            input.request().businessDateWindow().startInclusive(),
                            input.referenceLimits(),
                            cancellation);
            for (final var binding : bindings) {
                temporal.bind(binding, cancellation);
            }
            temporal.qualify(data.executionId(), graph.executionId(), cancellation);
            return consumer.consume(data.executionId(), graph.executionId(), cancellation);
        } catch (final RuntimeException failure) {
            input.dataGuard().invalidateEvidence();
            input.referenceContext().traversalFailed();
            try {
                session.rollback();
            } catch (final SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
    }

    public record Input(
            ContractRunGuard dataGuard,
            DataExportPageRequest request,
            DataExportExtractionLimits dataLimits,
            ContractExecutionContext referenceContext,
            GraphQlExtractionLimits referenceLimits) {
        public Input {
            Objects.requireNonNull(dataGuard);
            Objects.requireNonNull(request);
            Objects.requireNonNull(dataLimits);
            Objects.requireNonNull(referenceContext);
            Objects.requireNonNull(referenceLimits);
            if (!request.businessDateWindow()
                    .startInclusive()
                    .equals(request.businessDateWindow().endInclusive())) {
                throw new IllegalArgumentException("COL_LAB_SINGLE_DATE_REQUIRED");
            }
        }
    }
}
