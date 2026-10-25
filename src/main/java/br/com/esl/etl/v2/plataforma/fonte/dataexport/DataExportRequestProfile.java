package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportProperties;
import br.com.esl.etl.v2.plataforma.contrato.ContractSemanticsFingerprint;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceSelection;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Closed observed request selection. LEGACY never loads an observed catalog or resource. */
public final class DataExportRequestProfile {
    public static final DataExportRequestProfile LEGACY =
            new DataExportRequestProfile(Kind.LEGACY, null, null, 0);

    private final Kind kind;
    private final ImmutableFingerprint baseRuntimeFingerprint;
    private final BusinessDateRange dates;
    private final int pageSize;
    private final ImmutableFingerprint semanticsFingerprint;
    private final ImmutableFingerprint runtimeFingerprint;

    private DataExportRequestProfile(
            final Kind kind,
            final ImmutableFingerprint baseRuntimeFingerprint,
            final BusinessDateRange dates,
            final int pageSize) {
        this.kind = kind;
        this.baseRuntimeFingerprint = baseRuntimeFingerprint;
        this.dates = dates;
        this.pageSize = pageSize;
        if (kind == Kind.LEGACY) {
            semanticsFingerprint = null;
            runtimeFingerprint = null;
        } else {
            semanticsFingerprint =
                    ContractSemanticsFingerprint.create(
                            "dataexport-observed-request-v1",
                            kind.name(),
                            kind.template.name(),
                            Integer.toString(kind.template.templateId()),
                            kind.version,
                            kind.releaseSha256,
                            kind.template.businessDateFilter().asQueryParameterName(),
                            dates.startInclusive().toString(),
                            dates.endInclusive().toString(),
                            "UPDATED_AT_NOT_CONTRACTED",
                            "INITIAL_PAGE_1",
                            Integer.toString(pageSize),
                            "MAXIMUM_PER_100",
                            "NO_ORDER_BY",
                            "GET_WITH_BODY",
                            "MAXIMUM_ATTEMPTS_1",
                            "PREFERRED_TRANSPORT_ONLY",
                            "$",
                            "/" + kind.template.paginationEntityField(),
                            rawRecordsRequired()
                                    ? "LOCALIZACAO_8656_26_PATHS_OBSERVED_20261024"
                                    : "EXISTING_TYPED_MAPPER",
                            rawRecordsRequired() ? "RAW_STRING_SLICE_V1" : "JSON_TREE_V1");
            runtimeFingerprint =
                    ContractSemanticsFingerprint.create(
                            "dataexport-observed-runtime-v1",
                            baseRuntimeFingerprint.version(),
                            baseRuntimeFingerprint.sha256(),
                            semanticsFingerprint.version(),
                            semanticsFingerprint.sha256());
        }
    }

    /** Loads only the explicitly selected observed catalog. No fallback exists on this path. */
    public static SourceContractRelease observedRelease(
            final DataExportTemplate template, final String version) {
        for (final Kind candidate : Kind.values()) {
            if (candidate != Kind.LEGACY
                    && candidate.template == template
                    && candidate.version.equals(version)) {
                final var release = DataExportObservedEntityContractCatalog.release(template);
                if (selectedKind(release) != candidate) {
                    throw new IllegalArgumentException("OBSERVED_REQUEST_RELEASE_MISMATCH");
                }
                return release;
            }
        }
        throw new IllegalArgumentException("OBSERVED_REQUEST_SELECTION_REJECTED");
    }

    static DataExportRequestProfile observed(
            final DataExportTemplate template,
            final SourceContractRelease release,
            final ImmutableFingerprint baseRuntimeFingerprint,
            final BusinessDateRange dates,
            final int pageSize) {
        final Kind selected = selectedKind(Objects.requireNonNull(release));
        if (selected == Kind.LEGACY
                || selected.template != template
                || pageSize < 1
                || pageSize > 100) {
            throw new IllegalArgumentException("OBSERVED_REQUEST_SHAPE_REJECTED");
        }
        return new DataExportRequestProfile(
                selected,
                Objects.requireNonNull(baseRuntimeFingerprint),
                Objects.requireNonNull(dates),
                pageSize);
    }

    /** Does not load resources; useful when selecting a profile from an already loaded release. */
    public static boolean observedSelection(final SourceContractRelease release) {
        return selectedKind(Objects.requireNonNull(release)) != Kind.LEGACY;
    }

    public boolean observed() {
        return kind != Kind.LEGACY;
    }

    public boolean rawRecordsRequired() {
        return kind == Kind.LOCALIZACAO_P1B;
    }

    public ImmutableFingerprint semanticsFingerprint() {
        if (!observed()) {
            throw new IllegalStateException("OBSERVED_REQUEST_PROFILE_REQUIRED");
        }
        return semanticsFingerprint;
    }

    public ImmutableFingerprint runtimeFingerprint(final ImmutableFingerprint actualBase) {
        if (!observed()) {
            return Objects.requireNonNull(actualBase);
        }
        requireRuntimeBase(actualBase);
        return runtimeFingerprint;
    }

    public void requireRuntimeBase(final ImmutableFingerprint actualBase) {
        if (!observed() || !baseRuntimeFingerprint.equals(actualBase)) {
            throw new IllegalArgumentException("OBSERVED_REQUEST_RUNTIME_MISMATCH");
        }
    }

    public static boolean legacySelection(final ContractSourceSelection selection) {
        try {
            return selectedKind(selection) == Kind.LEGACY;
        } catch (final IllegalArgumentException invalid) {
            return false;
        }
    }

    /** Same check used by Input, the contract gate and the staging pipeline. */
    public boolean approves(
            final DataExportPageRequest request, final ContractSourceSelection selection) {
        try {
            if (selectedKind(selection) != kind) {
                return false;
            }
            if (!observed()) {
                return request.template()
                        .approvesRequestSemantics(request.pageSize(), request.orderBy());
            }
            if (selection.sourceKind() != ContractSourceKind.DATA_EXPORT
                    || !selection
                            .documentReference()
                            .equals(
                                    DataExportContractAdapter.documentReference(
                                            request.template()))) {
                return false;
            }
            requireShape(
                    request.template(),
                    request.businessDateWindow(),
                    request.updatedAtWindow(),
                    request.page(),
                    request.pageSize(),
                    request.orderBy());
            return runtimeFingerprint.equals(selection.runtimeConfigurationFingerprint());
        } catch (final IllegalArgumentException invalid) {
            return false;
        }
    }

    void requireShape(
            final DataExportTemplate template,
            final BusinessDateRange actualDates,
            final Optional<SourceDateTimeRange> updatedAt,
            final int page,
            final int actualPer,
            final List<String> orderBy) {
        if (observed()
                && (template != kind.template
                        || !dates.equals(actualDates)
                        || updatedAt.isPresent()
                        || page < 1
                        || actualPer != pageSize
                        || actualPer < 1
                        || actualPer > 100
                        || orderBy == null
                        || !orderBy.isEmpty())) {
            throw new IllegalArgumentException("OBSERVED_REQUEST_SHAPE_REJECTED");
        }
    }

    void requireTransport(
            final DataExportProperties properties,
            final DataExportTransportFallbackPolicy fallback) {
        if (observed()
                && (properties.preferredTransport() != DataExportTransport.GET_WITH_BODY
                        || properties.retryPolicy().maxAttempts() != 1
                        || fallback
                                != DataExportTransportFallbackPolicy.PREFERRED_TRANSPORT_ONLY)) {
            throw new IllegalArgumentException("OBSERVED_REQUEST_TRANSPORT_REJECTED");
        }
    }

    private static Kind selectedKind(final SourceContractRelease release) {
        return selectedKind(
                new ContractSourceSelection(
                        release.sourceKind(),
                        release.documentReference(),
                        release.contractVersion(),
                        release.contractFingerprint(),
                        release.contractFingerprint()));
    }

    private static Kind selectedKind(final ContractSourceSelection selection) {
        Objects.requireNonNull(selection);
        for (final Kind candidate : Kind.values()) {
            if (candidate != Kind.LEGACY
                    && (candidate.version.equals(selection.contractVersion())
                            || candidate.releaseSha256.equals(
                                    selection.contractFingerprint().sha256()))) {
                if (selection.sourceKind() != ContractSourceKind.DATA_EXPORT
                        || !candidate.version.equals(selection.contractVersion())
                        || !candidate.version.equals(selection.contractFingerprint().version())
                        || !candidate.releaseSha256.equals(selection.contractFingerprint().sha256())
                        || !DataExportContractAdapter.documentReference(candidate.template)
                                .equals(selection.documentReference())) {
                    throw new IllegalArgumentException("OBSERVED_REQUEST_RELEASE_MISMATCH");
                }
                return candidate;
            }
        }
        return Kind.LEGACY;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof DataExportRequestProfile value
                && kind == value.kind
                && Objects.equals(baseRuntimeFingerprint, value.baseRuntimeFingerprint)
                && Objects.equals(dates, value.dates)
                && pageSize == value.pageSize;
    }

    @Override
    public int hashCode() {
        return observed() ? Objects.hash(kind, baseRuntimeFingerprint, dates, pageSize) : 0;
    }

    @Override
    public String toString() {
        return "DataExportRequestProfile[selection=" + kind + "]";
    }

    private enum Kind {
        LEGACY(null, "", ""),
        MANIFESTOS_P1B(
                DataExportTemplate.MANIFESTOS,
                "2026-10-24.p1b-manifestos-observed.v1",
                "6fbc71f29c4062324ec5ccd248b7adf9638cc2c60c81d69379b429fbeba4f77f"),
        COTACOES_P1B(
                DataExportTemplate.COTACOES,
                "2026-10-24.p1b-cotacoes-observed.v1",
                "6dd5197a7fa7ef035c4d0c5420b2403da7260c9ae38e0c3d5e8da72d6f5523f2"),
        LOCALIZACAO_P1B(
                DataExportTemplate.LOCALIZACAO_CARGAS,
                "2026-10-24.p1b-localizacao-cargas-observed.v1",
                "24373b7c25cb4c7df64e260fbd3c0624c05255aeb2dc28be3b774a1216c8482f");

        private final DataExportTemplate template;
        private final String version;
        private final String releaseSha256;

        Kind(final DataExportTemplate template, final String version, final String releaseSha256) {
            this.template = template;
            this.version = version;
            this.releaseSha256 = releaseSha256;
        }
    }
}
