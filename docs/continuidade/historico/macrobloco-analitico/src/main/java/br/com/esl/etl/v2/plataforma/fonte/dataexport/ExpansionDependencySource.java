package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;

/**
 * Versioned fixture transport. Every generated page traverses the real parser and contract gate.
 */
public final class ExpansionDependencySource {
    private final IntFunction<String> pages;
    private final Observer observer;
    private final java.util.function.Function<
                    String, br.com.esl.etl.v2.plataforma.expansao.ExpansionFreightTerms>
            financialBindings;
    private long bytes;
    private int fetched;
    private int maximumPageBytes;

    public ExpansionDependencySource(final IntFunction<String> pages) {
        this(pages, Observer.NONE, ignored -> null);
    }

    private ExpansionDependencySource(
            final IntFunction<String> pages,
            final Observer observer,
            final java.util.function.Function<
                            String, br.com.esl.etl.v2.plataforma.expansao.ExpansionFreightTerms>
                    financialBindings) {
        this.pages = java.util.Objects.requireNonNull(pages);
        this.observer = java.util.Objects.requireNonNull(observer);
        this.financialBindings = java.util.Objects.requireNonNull(financialBindings);
    }

    public ExpansionDependencySource observed(final Observer measurement) {
        return new ExpansionDependencySource(pages, measurement, financialBindings);
    }

    public ExpansionDependencySource withFinancialBindings(
            final java.util.function.Function<
                            String, br.com.esl.etl.v2.plataforma.expansao.ExpansionFreightTerms>
                    bindings) {
        return new ExpansionDependencySource(pages, observer, bindings);
    }

    public br.com.esl.etl.v2.plataforma.expansao.ExpansionFreightTerms financialTerms(
            final String sourceKey) {
        final var terms = financialBindings.apply(sourceKey);
        if (terms != null && !terms.sourceKey().equals(sourceKey)) {
            throw new IllegalArgumentException("EXP_TERMS_TARGET_MISMATCH");
        }
        return terms;
    }

    public static SourceContractRelease release(final DataExportTemplate template) {
        final List<String> integers;
        final List<String> strings;
        switch (template) {
            case FRETES -> {
                integers = List.of("id", "corporation_sequence_number");
                strings =
                        List.of(
                                "status",
                                "cte_created_at",
                                "cte_issued_at",
                                "criado_em",
                                "servico_em",
                                "total",
                                "reference_number");
            }
            case LOCALIZACAO_CARGAS -> {
                integers = List.of("corporation_sequence_number");
                strings =
                        List.of(
                                "type",
                                "service_at",
                                "invoices_volumes",
                                "taxed_weight",
                                "invoices_value",
                                "total",
                                "service_type",
                                "fit_crn_psn_nickname",
                                "fit_dpn_delivery_prediction_at",
                                "fit_dyn_name",
                                "fit_dyn_drt_nickname",
                                "fit_fsn_name",
                                "fit_fln_status",
                                "fit_fln_cln_nickname",
                                "fit_o_n_name",
                                "fit_o_n_drt_nickname");
            }
            default -> throw new IllegalArgumentException("EXP_DEP_ENTITY_DENIED");
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
        metadata.add(
                ContractMetadata.Element.fromDeclaredType(
                        ContractMetadata.ElementKind.DATA_FILTER,
                        template.metadataBusinessDateFilterName(),
                        Optional.of("date")));
        return SourceContractRelease.create(
                ContractSourceKind.DATA_EXPORT,
                DataExportContractAdapter.documentReference(template),
                "expansion-dependency-v1",
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
                                throw new IllegalArgumentException("EXP_DEP_FIXTURE_PAGE_BOUND");
                            }
                            final int size = page.getBytes(StandardCharsets.UTF_8).length;
                            if (size > 65_536) {
                                throw new IllegalArgumentException("EXP_DEP_FIXTURE_PAGE_BOUND");
                            }
                            try {
                                final var tree = DataExportStrictJsonParser.readTree(page);
                                if (!tree.isArray()) {
                                    throw new IllegalArgumentException(
                                            "EXP_DEP_FIXTURE_ARRAY_REQUIRED");
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
                                        "EXP_DEP_FIXTURE_JSON_INVALID", failure);
                            }
                        },
                        ignored -> {
                            throw new IllegalStateException("EXP_DEP_NO_METADATA_IO");
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
