package br.com.esl.etl.v2.contratos;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageFetch;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplateInfo;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.SourceDateTimeRange;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Coleta evidência remota exclusivamente em memória e grava somente o summary sanitizado em target.
 *
 * <p>A execução só é alcançável pelo profile, flag explícita e configuração CONTRACT_* do Failsafe.
 * Qualquer 429 ou teto consumido interrompe o contexto compartilhado antes da próxima conexão.
 */
public final class ContractRemoteEvidenceRunner {

    private final Clock clock;

    public ContractRemoteEvidenceRunner() {
        this(Clock.systemUTC());
    }

    ContractRemoteEvidenceRunner(final Clock clock) {
        this.clock = Objects.requireNonNull(clock, "O relógio da evidência é obrigatório.");
    }

    public Path collectAndWrite(final ContractTestConfiguration configuration) {
        final ContractEvidenceSummary summary = collect(configuration);
        return new ContractEvidenceWriter().write(summary);
    }

    public ContractEvidenceSummary collect(final ContractTestConfiguration configuration) {
        Objects.requireNonNull(configuration, "A configuração de contrato é obrigatória.");
        final ContractRemoteExecution execution = ContractRemoteExecution.open(configuration);
        return collect(configuration, execution);
    }

    ContractEvidenceSummary collect(
            final ContractTestConfiguration configuration,
            final ContractRemoteExecution execution) {
        Objects.requireNonNull(configuration, "A configuração de contrato é obrigatória.");
        Objects.requireNonNull(execution, "A execução remota é obrigatória.");
        final List<ContractTemplateEvidence> templates = new ArrayList<>();
        for (final DataExportTemplate template : DataExportTemplate.values()) {
            templates.add(collectTemplate(configuration, execution, template));
        }
        configuration
                .auxiliary4924()
                .ifPresent(
                        auxiliary ->
                                templates.add(
                                        collectAuxiliary4924(
                                                execution.auxiliary4924Probe().orElseThrow(),
                                                auxiliary)));
        return new ContractEvidenceSummary(
                "contract-" + UUID.randomUUID(),
                Instant.now(clock).toString(),
                List.copyOf(templates));
    }

    private static ContractTemplateEvidence collectAuxiliary4924(
            final Contract4924DataExportProbe probe,
            final Contract4924Configuration configuration) {
        final Contract4924TemplateInfo info = probe.fetchInfo();
        final Contract4924PageObservation sample = probe.fetchClosedWindowSample();
        final ContractPayloadEvidence payload = sample.asPayloadEvidence();
        final Map<ContractEvidenceCheck, Boolean> checks = new LinkedHashMap<>();
        checks.put(ContractEvidenceCheck.METADATA_OBSERVED, true);
        checks.put(
                ContractEvidenceCheck.REQUIRED_FILTERS_DECLARED,
                auxiliaryFilterDeclared(info, configuration));
        checks.put(ContractEvidenceCheck.ENVELOPE_VALID, true);
        checks.put(
                ContractEvidenceCheck.TRANSPORT_CONFIRMED,
                payload.acceptedTransport() == configuration.approvedTransport());
        checks.put(
                ContractEvidenceCheck.JSON_CONTENT_TYPE_DECLARED,
                info.jsonContentTypeDeclared() && payload.jsonContentTypeDeclared());
        checks.put(
                ContractEvidenceCheck.RETRY_AFTER_OBSERVED,
                info.retryAfterObserved() || sample.retryAfterObserved());
        checks.put(
                ContractEvidenceCheck.PAGE_SIZE_RESPECTED,
                payload.recordCountWithinRequestedPageSize());
        checks.put(ContractEvidenceCheck.FINANCIAL_EQUIVALENCE_CONFIRMED, false);
        checks.put(ContractEvidenceCheck.CTE_INVOICE_RELATION_CONFIRMED, false);
        return new ContractTemplateEvidence(
                Contract4924Configuration.TEMPLATE_ID,
                info.httpStatus(),
                info.retryAfterObserved() || sample.retryAfterObserved(),
                info.jsonContentTypeDeclared(),
                info.declaredFields(),
                info.declaredFilters(),
                List.of(configuration.approvedTransport()),
                List.of(payload),
                Map.copyOf(checks));
    }

    private ContractTemplateEvidence collectTemplate(
            final ContractTestConfiguration configuration,
            final ContractRemoteExecution execution,
            final DataExportTemplate template) {
        final ContractEntityWindows windows = configuration.windowsFor(template);
        final int firstPageSize = configuration.firstApprovedPageSize();
        final int secondPageSize = configuration.secondApprovedPageSize();
        final DataExportTemplateInfo info = execution.fetchInfo(template);
        final DataExportPageFetch firstSample =
                execution.fetchDataExportPage(
                        pageRequest(
                                template, windows.populated(), Optional.empty(), 1, firstPageSize));
        final DataExportPageFetch secondSample =
                execution.fetchDataExportPage(
                        pageRequest(
                                template,
                                windows.populated(),
                                Optional.empty(),
                                1,
                                secondPageSize));
        final DataExportPageFetch emptySample =
                execution.fetchDataExportPage(
                        pageRequest(template, windows.empty(), Optional.empty(), 1, firstPageSize));
        final DataExportPageFetch lateChangeSample =
                execution.fetchDataExportPage(
                        pageRequest(
                                template,
                                windows.lateChangeBusiness(),
                                Optional.of(windows.lateChangeUpdatedAt().asSourceDateTimeRange()),
                                1,
                                firstPageSize));
        final List<ContractObservedPage> firstFirstSizeTraversal =
                traverse(execution, template, windows.populated(), firstPageSize);
        final List<ContractObservedPage> secondFirstSizeTraversal =
                traverse(execution, template, windows.populated(), firstPageSize);
        final List<ContractObservedPage> firstSecondSizeTraversal =
                traverse(execution, template, windows.populated(), secondPageSize);
        final List<ContractObservedPage> secondSecondSizeTraversal =
                traverse(execution, template, windows.populated(), secondPageSize);
        final String naturalKey = naturalKey(template);
        final ContractPaginationResult firstSizePagination =
                ContractParityComparator.compareRepeatedPagination(
                        firstFirstSizeTraversal, secondFirstSizeTraversal, template, naturalKey);
        final ContractPaginationResult crossSizePagination =
                ContractParityComparator.compareRepeatedPagination(
                        firstFirstSizeTraversal, firstSecondSizeTraversal, template, naturalKey);
        final ContractPaginationResult secondSizePagination =
                ContractParityComparator.compareRepeatedPagination(
                        firstSecondSizeTraversal, secondSecondSizeTraversal, template, naturalKey);
        final ContractIdentityParityResult identity =
                identityFor(
                        execution,
                        template,
                        windows.populated(),
                        firstPageSize,
                        dataExportRowsForIdentity(
                                execution,
                                template,
                                windows.populated(),
                                firstPageSize,
                                firstFirstSizeTraversal));
        final List<DataExportPageFetch> samples =
                List.of(firstSample, secondSample, emptySample, lateChangeSample);
        final boolean retryAfterObserved =
                info.retryAfter().isPresent()
                        || samples.stream().anyMatch(sample -> sample.retryAfter().isPresent());
        final List<ContractPayloadEvidence> payloads =
                List.of(
                        ContractPayloadEvidence.from(
                                ContractPayloadObservation.POPULATED_FIRST_APPROVED_PAGE_SIZE,
                                template,
                                firstPageSize,
                                firstSample),
                        ContractPayloadEvidence.from(
                                ContractPayloadObservation.POPULATED_SECOND_APPROVED_PAGE_SIZE,
                                template,
                                secondPageSize,
                                secondSample),
                        ContractPayloadEvidence.from(
                                ContractPayloadObservation.EMPTY_DECLARED_WINDOW,
                                template,
                                firstPageSize,
                                emptySample),
                        ContractPayloadEvidence.from(
                                ContractPayloadObservation.LATE_CHANGE_UPDATED_AT_WINDOW,
                                template,
                                firstPageSize,
                                lateChangeSample));
        return new ContractTemplateEvidence(
                template.templateId(),
                info.httpStatus(),
                retryAfterObserved,
                info.jsonContentTypeDeclared(),
                info.fields().stream().map(ContractMetadataEvidence::from).toList(),
                info.filters().stream().map(ContractMetadataEvidence::from).toList(),
                acceptedTransports(samples),
                payloads,
                checks(
                        configuration,
                        template,
                        info,
                        payloads,
                        emptySample,
                        lateChangeSample,
                        retryAfterObserved,
                        firstSizePagination,
                        crossSizePagination,
                        secondSizePagination,
                        identity));
    }

    private static List<ContractObservedPage> traverse(
            final ContractRemoteExecution execution,
            final DataExportTemplate template,
            final ContractDateWindow window,
            final int pageSize) {
        final List<ContractObservedPage> pages = new ArrayList<>();
        int page = 1;
        while (true) {
            final DataExportPageRequest request =
                    pageRequest(template, window, Optional.empty(), page, pageSize);
            final DataExportPageFetch response = execution.fetchDataExportPage(request);
            final ContractObservedPage observedPage = ContractObservedPage.from(request, response);
            pages.add(observedPage);
            if (observedPage.records().isEmpty()) {
                return List.copyOf(pages);
            }
            page = Math.addExact(page, 1);
        }
    }

    private static DataExportPageRequest pageRequest(
            final DataExportTemplate template,
            final ContractDateWindow businessWindow,
            final Optional<SourceDateTimeRange> updatedAtWindow,
            final int page,
            final int pageSize) {
        return new DataExportPageRequest(
                template,
                new BusinessDateRange(
                        businessWindow.startInclusive(), businessWindow.endInclusive()),
                updatedAtWindow,
                page,
                pageSize,
                template.defaultOrderBy());
    }

    private static ContractIdentityParityResult identityFor(
            final ContractRemoteExecution execution,
            final DataExportTemplate template,
            final ContractDateWindow window,
            final int pageSize,
            final List<JsonNode> dataExportRows) {
        return switch (template) {
            case COLETAS ->
                    ContractParityComparator.compareColetas(
                            dataExportRows, execution.fetchAllColetas(window, pageSize));
            case FRETES ->
                    ContractParityComparator.compareFretes(
                            dataExportRows, execution.fetchAllFretes(window, pageSize));
        };
    }

    private static List<JsonNode> dataExportRowsForIdentity(
            final ContractRemoteExecution execution,
            final DataExportTemplate template,
            final ContractDateWindow window,
            final int pageSize,
            final List<ContractObservedPage> fullWindowTraversal) {
        if (template != DataExportTemplate.COLETAS
                || window.startInclusive().equals(window.endInclusive())) {
            return flatten(fullWindowTraversal);
        }
        final List<JsonNode> rows = new ArrayList<>();
        for (final ContractDateWindow dailyWindow : window.asDailyWindows()) {
            rows.addAll(flatten(traverse(execution, template, dailyWindow, pageSize)));
        }
        return List.copyOf(rows);
    }

    private static List<JsonNode> flatten(final List<ContractObservedPage> pages) {
        return pages.stream().flatMap(page -> page.records().stream()).toList();
    }

    private static List<DataExportTransport> acceptedTransports(
            final List<DataExportPageFetch> samples) {
        final EnumSet<DataExportTransport> transports = EnumSet.noneOf(DataExportTransport.class);
        for (final DataExportPageFetch sample : samples) {
            transports.add(sample.acceptedTransport());
        }
        return List.copyOf(transports);
    }

    private static Map<ContractEvidenceCheck, Boolean> checks(
            final ContractTestConfiguration configuration,
            final DataExportTemplate template,
            final DataExportTemplateInfo info,
            final List<ContractPayloadEvidence> payloads,
            final DataExportPageFetch emptySample,
            final DataExportPageFetch lateChangeSample,
            final boolean retryAfterObserved,
            final ContractPaginationResult firstSizePagination,
            final ContractPaginationResult crossSizePagination,
            final ContractPaginationResult secondSizePagination,
            final ContractIdentityParityResult identity) {
        final boolean pageSizeRespected =
                payloads.stream()
                                .allMatch(
                                        ContractPayloadEvidence
                                                ::paginationEntityCountWithinRequestedPageSize)
                        && firstSizePagination.firstRunEntityCountsWithinPageSize()
                        && firstSizePagination.secondRunEntityCountsWithinPageSize()
                        && crossSizePagination.firstRunEntityCountsWithinPageSize()
                        && crossSizePagination.secondRunEntityCountsWithinPageSize()
                        && secondSizePagination.firstRunEntityCountsWithinPageSize()
                        && secondSizePagination.secondRunEntityCountsWithinPageSize();
        final boolean populatedWindowHasAtLeastThreePages =
                firstSizePagination.hasAtLeastThreeNonEmptyPagesInBothRuns();
        final boolean traversalStable =
                populatedWindowHasAtLeastThreePages
                        && firstSizePagination.hasInternallyConsistentTraversal()
                        && crossSizePagination.hasInternallyConsistentTraversal()
                        && secondSizePagination.hasInternallyConsistentTraversal();
        final boolean terminalEmptyPages =
                hasProperTerminalEmptyPage(firstSizePagination)
                        && hasProperTerminalEmptyPage(crossSizePagination)
                        && hasProperTerminalEmptyPage(secondSizePagination);
        final ContractTemplateConfirmation confirmation = configuration.confirmationsFor(template);
        final Map<ContractEvidenceCheck, Boolean> checks = new LinkedHashMap<>();
        checks.put(ContractEvidenceCheck.METADATA_OBSERVED, true);
        checks.put(
                ContractEvidenceCheck.REQUIRED_FILTERS_DECLARED,
                requiredFiltersDeclared(template, info));
        checks.put(
                ContractEvidenceCheck.EMPTY_WINDOW_CONFIRMED,
                emptySample.response().records().isEmpty() && emptySample.responseForm().isArray());
        checks.put(ContractEvidenceCheck.ENVELOPE_VALID, true);
        checks.put(
                ContractEvidenceCheck.TRANSPORT_CONFIRMED,
                payloads.stream()
                        .allMatch(
                                payload ->
                                        payload.acceptedTransport()
                                                == configuration.approvedTransport()));
        checks.put(
                ContractEvidenceCheck.JSON_CONTENT_TYPE_DECLARED,
                info.jsonContentTypeDeclared()
                        && payloads.stream()
                                .allMatch(ContractPayloadEvidence::jsonContentTypeDeclared));
        checks.put(ContractEvidenceCheck.RETRY_AFTER_OBSERVED, retryAfterObserved);
        checks.put(
                ContractEvidenceCheck.LATE_CHANGE_SCOPE_OBSERVED,
                lateChangeScopeObserved(lateChangeSample));
        checks.put(ContractEvidenceCheck.PAGE_SIZE_RESPECTED, pageSizeRespected);
        checks.put(
                ContractEvidenceCheck.POPULATED_WINDOW_HAS_AT_LEAST_THREE_PAGES,
                populatedWindowHasAtLeastThreePages);
        checks.put(ContractEvidenceCheck.TERMINAL_EMPTY_PAGE_OBSERVED, terminalEmptyPages);
        checks.put(ContractEvidenceCheck.PAGINATION_STABLE, traversalStable);
        checks.put(
                ContractEvidenceCheck.ORDER_TOTAL_OR_CURSOR_CONFIRMED,
                confirmation.orderTotalOrCursorConfirmed());
        checks.put(
                ContractEvidenceCheck.COVERAGE_CONFIRMED,
                traversalStable
                        && confirmation.orderTotalOrCursorConfirmed()
                        && confirmation.coverageConfirmed());
        checks.put(
                ContractEvidenceCheck.SOURCE_TIMEZONE_FORMALLY_CONFIRMED,
                configuration.sourceTimezoneFormallyConfirmed());
        checks.put(ContractEvidenceCheck.IDENTITY_VOLUME_EQUAL, identity.volumesEqual());
        checks.put(
                ContractEvidenceCheck.IDENTITY_CROSSWALK_ONE_TO_ONE,
                identity.naturalKeyToGraphQlSourceIdOneToOne());
        checks.put(ContractEvidenceCheck.IDENTITY_SOURCE_IDS_EQUAL, identity.sourceIdsEqual());
        checks.put(ContractEvidenceCheck.FINANCIAL_EQUIVALENCE_CONFIRMED, false);
        checks.put(ContractEvidenceCheck.FRETE_COLETA_RELATION_CONFIRMED, false);
        checks.put(ContractEvidenceCheck.CTE_INVOICE_RELATION_CONFIRMED, false);
        return Map.copyOf(checks);
    }

    static boolean requiredFiltersDeclared(
            final DataExportTemplate template, final DataExportTemplateInfo info) {
        Objects.requireNonNull(template, "O template Data Export é obrigatório.");
        Objects.requireNonNull(info, "Os metadados Data Export são obrigatórios.");
        return declaresFilter(info, template.businessDateFilter())
                && declaresFilter(info, template.updatedAtFilter());
    }

    static boolean lateChangeScopeObserved(final DataExportPageFetch lateChangeSample) {
        Objects.requireNonNull(lateChangeSample, "A amostra de alteração tardia é obrigatória.");
        return !lateChangeSample.response().records().isEmpty();
    }

    private static boolean auxiliaryFilterDeclared(
            final Contract4924TemplateInfo info, final Contract4924Configuration configuration) {
        final String fullName = configuration.root() + "." + configuration.businessFilter();
        return info.declaredFilters().stream()
                .map(ContractMetadataEvidence::technicalName)
                .anyMatch(
                        technicalName ->
                                technicalName.equals(fullName)
                                        || technicalName.equals(configuration.businessFilter()));
    }

    private static boolean declaresFilter(
            final DataExportTemplateInfo info,
            final br.com.esl.etl.v2.plataforma.fonte.dataexport.SearchPath filter) {
        final String qualifiedName = filter.root() + "." + filter.field();
        return info.filters().stream()
                .map(field -> field.technicalName())
                .anyMatch(name -> name.equals(qualifiedName) || name.equals(filter.field()));
    }

    private static boolean hasProperTerminalEmptyPage(final ContractPaginationResult pagination) {
        return pagination.firstRunHasTerminalEmptyPage()
                && pagination.secondRunHasTerminalEmptyPage()
                && pagination.firstRunHasNoIntermediateEmptyPage()
                && pagination.secondRunHasNoIntermediateEmptyPage();
    }

    private static String naturalKey(final DataExportTemplate template) {
        return switch (template) {
            case COLETAS -> "sequence_code";
            case FRETES -> "corporation_sequence_number";
        };
    }
}
