package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportSourceConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.EslRequestGovernor;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Internal RAM algorithm only. Root's independent finite dispatch remains DEPENDENCY_PENDING. */
final class ObservedExpansionCaptureDriver {
    private final ObservedExpansionCapturePlan plan;
    private final ObservedExpansionSchemaCatalog.Selection selection;
    private final ObservedExpansionHttpTransport transport;
    private final DataExportTemplateInfoGateway infoGateway;
    private final DataExportTemplate template;
    private final CancellationToken cancellation;
    private final DataExportHttpGatewayFactory factory;
    private final Attempts attempts;
    private boolean started;

    /**
     * Calls the actual closed configuration parser. No JSON or boolean is accepted as authority.
     */
    static ObservedExpansionCaptureDriver loadAndPrepare(
            final RuntimeConfigurationFactory loader,
            final Path configurationFile,
            final Properties systemProperties,
            final Map<String, String> environment,
            final Clock clock,
            final DataExportHttpGatewayFactory factory,
            final EslRequestGovernor governor,
            final EslRequestGovernor.Cycle cycle,
            final CancellationToken cancellation,
            final ObservedExpansionCapturePlan plan,
            final DataExportHttpAttemptObserver observer) {
        final RuntimeConfiguration loaded =
                Objects.requireNonNull(loader)
                        .load(configurationFile, systemProperties, environment, clock);
        return new ObservedExpansionCaptureDriver(
                loaded, factory, governor, cycle, cancellation, plan, observer);
    }

    private ObservedExpansionCaptureDriver(
            final RuntimeConfiguration loaded,
            final DataExportHttpGatewayFactory factory,
            final EslRequestGovernor governor,
            final EslRequestGovernor.Cycle cycle,
            final CancellationToken cancellation,
            final ObservedExpansionCapturePlan plan,
            final DataExportHttpAttemptObserver observer) {
        this.plan = Objects.requireNonNull(plan);
        this.cancellation = Objects.requireNonNull(cancellation);
        this.factory = Objects.requireNonNull(factory);
        final DataExportSourceConfiguration source =
                loaded.dataExport()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "OBS_DRIVER_DATAEXPORT_REQUIRED"));
        factory.verifyObservedRuntimeConfiguration(source);
        if (source.settings().maxResponseBytes() > 8L * 1024 * 1024
                || plan.per() != 50
                || plan.orderBy().present()
                || plan.envelope() != ObservedExpansionCapturePlan.Envelope.ROOT_ARRAY
                || !loaded.businessZone().equals(plan.sourceZone())
                || source.resiliencePolicy().maxRequestsPerWorkload() > 5
                || source.resiliencePolicy().maxRequestsPerCycle() > 20
                || source.resiliencePolicy().stepTimeout().compareTo(Duration.ofSeconds(180)) > 0
                || source.resiliencePolicy().cycleTimeout().compareTo(Duration.ofSeconds(1080))
                        > 0) {
            throw new IllegalArgumentException("OBS_DRIVER_FINITE_PLAN_REQUIRED");
        }
        Objects.requireNonNull(governor).verifyPolicy(source.resiliencePolicy());
        Objects.requireNonNull(cycle).verifyGovernor(governor);
        cycle.verifyPolicy(source.resiliencePolicy());
        cycle.verifyCancellationToken(cancellation);
        cancellation.throwIfCancellationRequested();
        selection = ObservedExpansionSchemaCatalog.select(plan.template());
        // Pinned resources and their closed rules must load BEFORE any INFO admission.
        ObservedExpansionSchemaCatalog.schema(selection);
        attempts = new Attempts(plan, cancellation, Objects.requireNonNull(observer));
        // The real factory verifies its materialized source/tenant/zone/policy and shared cycle.
        transport =
                Objects.requireNonNull(factory)
                        .prepareObservedTransport(plan, cycle, governor, cancellation, attempts);
        template =
                switch (plan.template()) {
                    case CAP -> DataExportTemplate.CONTAS_A_PAGAR;
                    case FAT -> DataExportTemplate.FATURAS_POR_CLIENTE;
                    case INV -> DataExportTemplate.INVENTARIO;
                    case SIN -> DataExportTemplate.SINISTROS;
                };
        infoGateway =
                factory.prepareObservedInfoGateway(plan, cycle, governor, cancellation, attempts);
    }

    /**
     * No public selector/entry point. Must only be called by a future separately admitted Root
     * unit.
     */
    synchronized ObservedExpansionCaptureResult collect() {
        if (started) {
            throw new IllegalStateException("OBS_DRIVER_SINGLE_USE");
        }
        started = true;
        final List<ObservedExpansionCaptureResult.Page> pages = new ArrayList<>();
        final DataExportTemplateInfo info;
        try {
            cancellation.throwIfCancellationRequested();
            attempts.expectInfo();
            info = infoGateway.fetchInfo(template);
        } catch (final RuntimeException failure) {
            if (contains429(failure)) {
                transport.stopSource();
                return finish(ObservedExpansionCaptureResult.Outcome.INFO_429, pages);
            }
            if (factory.observedSourceStopped()) {
                return finish(ObservedExpansionCaptureResult.Outcome.INFO_SOURCE_STOPPED, pages);
            }
            return finish(
                    cancelled(failure)
                            ? ObservedExpansionCaptureResult.Outcome.CANCELLED
                            : ObservedExpansionCaptureResult.Outcome.INFO_FAILED,
                    pages);
        }
        final ObservedExpansionSchemaGuard.BoundSchema guard;
        try {
            cancellation.throwIfCancellationRequested();
            if (info.template() != template
                    || info.httpStatus() != 200
                    || !info.jsonContentTypeDeclared()) {
                return finish(ObservedExpansionCaptureResult.Outcome.INFO_SCHEMA_REJECTED, pages);
            }
            guard =
                    ObservedExpansionSchemaGuard.bind(
                            plan,
                            selection,
                            info.template().templateId(),
                            DataExportContractAdapter.metadata(info));
        } catch (final RuntimeException failure) {
            return finish(
                    cancelled(failure)
                            ? ObservedExpansionCaptureResult.Outcome.CANCELLED
                            : ObservedExpansionCaptureResult.Outcome.INFO_SCHEMA_REJECTED,
                    pages);
        }
        long envelopeBytes = 0;
        for (int page = 1; page <= plan.limits().maximumPages(); page++) {
            final ObservedExpansionRawPage raw;
            try {
                cancellation.throwIfCancellationRequested();
                attempts.expectData(page);
                raw = transport.fetch(plan.request(page));
            } catch (final RuntimeException failure) {
                if (contains429(failure)) {
                    transport.stopSource();
                }
                return finish(
                        cancelled(failure)
                                ? ObservedExpansionCaptureResult.Outcome.CANCELLED
                                : ObservedExpansionCaptureResult.Outcome.DATA_FAILED,
                        pages);
            }
            final int index = pages.size();
            pages.add(ObservedExpansionCaptureResult.Page.rawOnly(raw));
            final ObservedExpansionSchemaGuard.Inspection inspection;
            try {
                cancellation.throwIfCancellationRequested();
                inspection = guard.inspect(raw);
                pages.set(index, ObservedExpansionCaptureResult.Page.inspected(inspection));
            } catch (final RuntimeException failure) {
                return finish(
                        cancelled(failure)
                                ? ObservedExpansionCaptureResult.Outcome.CANCELLED
                                : ObservedExpansionCaptureResult.Outcome.SCHEMA_STRUCTURE_ISSUE,
                        pages);
            }
            if (!inspection.schemaMatched() || inspection.checkedPage().isEmpty()) {
                // Do not invoke the unqualified core to replace a failed observed-schema check.
                return finish(ObservedExpansionCaptureResult.Outcome.SCHEMA_STRUCTURE_ISSUE, pages);
            }
            final ObservedExpansionPageProjector.ProvenProjection step;
            final ObservedExpansionPageProjection core;
            try {
                cancellation.throwIfCancellationRequested();
                step = guard.project(inspection);
                core = step.projection();
                pages.set(index, ObservedExpansionCaptureResult.Page.projected(inspection, step));
            } catch (final RuntimeException failure) {
                return finish(
                        cancelled(failure)
                                ? ObservedExpansionCaptureResult.Outcome.CANCELLED
                                : ObservedExpansionCaptureResult.Outcome.CORE_FAILED,
                        pages);
            }
            final ObservedExpansionAdmissionEmitter.AdmissionCapability envelope;
            try {
                cancellation.throwIfCancellationRequested();
                envelope = guard.admit(inspection, step);
                if (envelope.byteLengthUtf16Le() > plan.limits().maximumProjectionBytes()
                        || envelopeBytes + envelope.byteLengthUtf16Le()
                                > plan.limits().maximumProjectionTotalBytes()) {
                    return finish(
                            ObservedExpansionCaptureResult.Outcome.PROJECTION_TOTAL_BOUND, pages);
                }
                pages.set(
                        index,
                        ObservedExpansionCaptureResult.Page.admitted(inspection, step, envelope));
                envelopeBytes += envelope.byteLengthUtf16Le();
                cancellation.throwIfCancellationRequested();
            } catch (final RuntimeException failure) {
                return finish(
                        cancelled(failure)
                                ? ObservedExpansionCaptureResult.Outcome.CANCELLED
                                : ObservedExpansionCaptureResult.Outcome.ENVELOPE_FAILED,
                        pages);
            }
            if (core.status() == ObservedExpansionPageProjection.Status.STRUCTURE_ISSUE) {
                return finish(ObservedExpansionCaptureResult.Outcome.CORE_STRUCTURE_ISSUE, pages);
            }
            if (core.physicalRows().orElseThrow() == 0) {
                return finish(ObservedExpansionCaptureResult.Outcome.LOCAL_TERMINAL, pages);
            }
        }
        return finish(ObservedExpansionCaptureResult.Outcome.PARTIAL_PAGE_LIMIT, pages);
    }

    private ObservedExpansionCaptureResult finish(
            final ObservedExpansionCaptureResult.Outcome outcome,
            final List<ObservedExpansionCaptureResult.Page> pages) {
        attempts.finish();
        return new ObservedExpansionCaptureResult(
                plan, outcome, pages, attempts.info, attempts.data);
    }

    private boolean cancelled(final RuntimeException failure) {
        return failure instanceof ResilienceCancelledException
                || cancellation.isCancellationRequested();
    }

    private static boolean contains429(final RuntimeException failure) {
        // A malformed Retry-After may wrap the typed HTTP status. Never inspect provider messages.
        Throwable current = failure;
        for (int depth = 0; current != null && depth < 6; depth++) {
            if (current instanceof DataExportUnavailableException unavailable
                    && unavailable.httpStatus().orElse(0) == 429) {
                return true;
            }
            final Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
        }
        return false;
    }

    @Override
    public String toString() {
        return "ObservedExpansionCaptureDriver[template="
                + plan.template().templateId()
                + ",started="
                + started
                + ",scope=RAM_ONLY_NO_AUTHORITY]";
    }

    private static final class Attempts implements DataExportHttpAttemptObserver {
        private enum Phase {
            PREPARED,
            INFO,
            DATA,
            FINISHED
        }

        private final ObservedExpansionCapturePlan plan;
        private final CancellationToken cancellation;
        private final DataExportHttpAttemptObserver observer;
        private Phase phase = Phase.PREPARED;
        private int info;
        private int data;
        private int expectedPage;

        private Attempts(
                final ObservedExpansionCapturePlan plan,
                final CancellationToken cancellation,
                final DataExportHttpAttemptObserver observer) {
            this.plan = plan;
            this.cancellation = cancellation;
            this.observer = observer;
        }

        synchronized void expectInfo() {
            if (phase != Phase.PREPARED) {
                throw new IllegalStateException("OBS_DRIVER_INFO_SEQUENCE");
            }
            phase = Phase.INFO;
        }

        synchronized void expectData(final int page) {
            if (info != 1 || page != data + 1 || page > plan.limits().maximumPages()) {
                throw new IllegalStateException("OBS_DRIVER_DATA_SEQUENCE");
            }
            phase = Phase.DATA;
            expectedPage = page;
        }

        @Override
        public synchronized void beforeAttempt(final DataExportHttpAttempt attempt) {
            cancellation.throwIfCancellationRequested();
            if (attempt.templateId() != plan.template().templateId() || info + data >= 5) {
                throw new IllegalStateException("OBS_DRIVER_ATTEMPT_BOUND");
            }
            if (phase == Phase.INFO && info == 0 && attempt.operation().equals("info")) {
                observer.beforeAttempt(attempt);
                info++;
                return;
            }
            if (phase == Phase.DATA
                    && info == 1
                    && data + 1 == expectedPage
                    && data < plan.limits().maximumPages()
                    && attempt.operation().equals("observed-data-GET_WITH_BODY")) {
                observer.beforeAttempt(attempt);
                data++;
                return;
            }
            throw new IllegalStateException("OBS_DRIVER_ATTEMPT_PHASE");
        }

        synchronized void finish() {
            phase = Phase.FINISHED;
        }
    }
}
