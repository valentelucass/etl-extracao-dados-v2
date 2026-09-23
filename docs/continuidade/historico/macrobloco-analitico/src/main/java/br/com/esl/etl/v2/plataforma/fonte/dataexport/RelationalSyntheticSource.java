package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.relacional.RelationalCaptureContracts;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;

/**
 * Versioned fixture transport. Every generated page traverses the real parser and contract gate.
 */
public final class RelationalSyntheticSource {
    public static RelationalCaptureContracts contracts() {
        return new RelationalCaptureContracts(
                release(DataExportTemplate.MANIFESTOS).contractFingerprint().sha256(),
                release(DataExportTemplate.COLETAS).contractFingerprint().sha256(),
                release(DataExportTemplate.FRETES).contractFingerprint().sha256());
    }

    private final IntFunction<String> pages;
    private final Observer observer;
    private long bytes;
    private int fetched;
    private int maximumPageBytes;

    public RelationalSyntheticSource(final IntFunction<String> pages) {
        this(pages, Observer.NONE);
    }

    private RelationalSyntheticSource(final IntFunction<String> pages, final Observer observer) {
        this.pages = java.util.Objects.requireNonNull(pages);
        this.observer = java.util.Objects.requireNonNull(observer);
    }

    public RelationalSyntheticSource observed(final Observer measurement) {
        return new RelationalSyntheticSource(pages, measurement);
    }

    public static SourceContractRelease release(final DataExportTemplate template) {
        final List<String> integers;
        final List<String> strings;
        switch (template) {
            case COLETAS -> {
                integers = List.of("id", "sequence_code");
                strings =
                        List.of(
                                "status",
                                "request_date",
                                "service_date",
                                "finish_date",
                                "status_updated_at",
                                "pck_prn_name");
            }
            case FRETES -> {
                integers =
                        List.of("id", "corporation_sequence_number", "fit_p_m_pck_sequence_code");
                strings =
                        List.of(
                                "status",
                                "cte_created_at",
                                "cte_issued_at",
                                "criado_em",
                                "servico_em",
                                "finished_at",
                                "fit_dpn_performance_finished_at");
            }
            case MANIFESTOS -> {
                integers = List.of("sequence_code", "mft_pfs_pck_sequence_code", "mft_mfs_number");
                strings =
                        List.of(
                                "created_at",
                                "departured_at",
                                "closed_at",
                                "finished_at",
                                "status",
                                "mdfe_status",
                                "mft_mfs_key",
                                "km",
                                "total_cost",
                                "manifest_freights_total",
                                "total_taxed_weight",
                                "manifest_items_count",
                                "finalized_manifest_items_count");
            }
            default -> throw new IllegalArgumentException("REL_LAB_ENTITY_DENIED");
        }
        final var fields = new ArrayList<ContractResponse.Field>();
        final var metadata = new ArrayList<ContractMetadata.Element>();
        for (final String name : integers) {
            fields.add(
                    field(
                            name,
                            name.equals(template.paginationEntityField()),
                            List.of(ContractResponse.JsonType.INTEGER)));
            metadata.add(
                    ContractMetadata.Element.fromDeclaredType(
                            ContractMetadata.ElementKind.DATA_FIELD, name, Optional.of("integer")));
        }
        for (final String name : strings) {
            fields.add(field(name, false, List.of(ContractResponse.JsonType.STRING)));
            metadata.add(
                    ContractMetadata.Element.fromDeclaredType(
                            ContractMetadata.ElementKind.DATA_FIELD, name, Optional.of("string")));
        }
        fields.add(field("synthetic_fixture", true, List.of(ContractResponse.JsonType.BOOLEAN)));
        if (template != DataExportTemplate.MANIFESTOS) {
            fields.add(
                    field(
                            template == DataExportTemplate.COLETAS
                                    ? "synthetic_item_key"
                                    : "synthetic_pick_item",
                            false,
                            List.of(
                                    ContractResponse.JsonType.INTEGER,
                                    ContractResponse.JsonType.STRING,
                                    ContractResponse.JsonType.BOOLEAN)));
        }
        metadata.add(
                ContractMetadata.Element.fromDeclaredType(
                        ContractMetadata.ElementKind.DATA_FILTER,
                        template.metadataBusinessDateFilterName(),
                        Optional.of("date")));
        return SourceContractRelease.create(
                ContractSourceKind.DATA_EXPORT,
                DataExportContractAdapter.documentReference(template),
                JdbcRelationalLaboratory.VERSION,
                new ContractMetadata(metadata, Optional.empty()),
                new ContractResponse(
                        "$",
                        ContractResponse.Cardinality.ARRAY,
                        ContractResponse.ObservationState.POPULATED,
                        "/" + template.paginationEntityField(),
                        fields));
    }

    private static ContractResponse.Field field(
            final String name,
            final boolean required,
            final List<ContractResponse.JsonType> types) {
        return new ContractResponse.Field(
                "/" + name,
                ContractResponse.Cardinality.SCALAR,
                required ? ContractResponse.Presence.REQUIRED : ContractResponse.Presence.OPTIONAL,
                !required,
                types);
    }

    public DataExportGateway gateway(
            final DataExportTemplate template,
            final ContractRunGuard guard,
            final SourceContractRelease release,
            final ImmutableFingerprint configuration) {
        final var limits = ContractObservationLimits.runtimeDefaults();
        final var adapter = new DataExportContractAdapter(limits, guard.responsePathBoundary());
        final var observation =
                DataExportContractObservationConfiguration.forRelease(
                        template, release, limits, guard.responsePathBoundary(), configuration);
        final var bundle =
                DataExportHttpGatewayBundle.contractBound(
                        request -> {
                            final String page = pages.apply(request.page());
                            if (page == null || page.length() > 65_536) {
                                throw new IllegalArgumentException("REL_LAB_FIXTURE_PAGE_BOUND");
                            }
                            final int size = page.getBytes(StandardCharsets.UTF_8).length;
                            if (size > 65_536) {
                                throw new IllegalArgumentException("REL_LAB_FIXTURE_PAGE_BOUND");
                            }
                            try {
                                final var tree = DataExportStrictJsonParser.readTree(page);
                                if (!tree.isArray()) {
                                    throw new IllegalArgumentException(
                                            "REL_LAB_FIXTURE_ARRAY_REQUIRED");
                                }
                                for (final var row : tree) {
                                    if (!row.path("synthetic_fixture").isBoolean()
                                            || !row.path("synthetic_fixture").booleanValue()) {
                                        throw new IllegalArgumentException(
                                                "REL_LAB_SYNTHETIC_MARKER_REQUIRED");
                                    }
                                }
                                final var normalized =
                                        new DataExportResponseNormalizer().normalize(tree);
                                DataExportPageEntityLimitValidator.validate(request, normalized);
                                fetched = Math.incrementExact(fetched);
                                bytes = Math.addExact(bytes, size);
                                maximumPageBytes = Math.max(maximumPageBytes, size);
                                return normalized.withObservation(
                                        adapter.response(
                                                tree,
                                                DataExportResponseForm.ROOT_ARRAY,
                                                "/" + template.paginationEntityField()),
                                        limits,
                                        guard.responsePathBoundary());
                            } catch (final JsonProcessingException failure) {
                                throw new IllegalArgumentException(
                                        "REL_LAB_FIXTURE_JSON_INVALID", failure);
                            }
                        },
                        ignored -> {
                            throw new IllegalStateException("REL_LAB_NO_METADATA_IO");
                        },
                        observation);
        final var secured = DataExportContractGate.enforce(bundle, template, guard);
        guard.validateMetadata(release.metadata());
        return request -> {
            observer.beforeFetch();
            final long previousBytes = bytes;
            final var response = secured.dataGateway().fetch(request);
            observer.pageFetched(response, bytes - previousBytes);
            return response;
        };
    }

    public void batchStaged(final int records) {
        observer.batchStaged(records);
    }

    public void batchStarted(final int records) {
        observer.batchStarted(records);
    }

    public void captureClosed() {
        observer.captureClosed();
    }

    public interface Observer {
        Observer NONE = new Observer() {};

        default void beforeFetch() {}

        default void pageFetched(DataExportPageResponse page, long bytes) {}

        default void batchStarted(int records) {}

        default void batchStaged(int records) {}

        default void captureClosed() {}
    }

    public Metrics metrics() {
        return new Metrics(fetched, bytes, maximumPageBytes);
    }

    public record Metrics(int fetchedPages, long bytes, int maximumPageBytes) {}
}
