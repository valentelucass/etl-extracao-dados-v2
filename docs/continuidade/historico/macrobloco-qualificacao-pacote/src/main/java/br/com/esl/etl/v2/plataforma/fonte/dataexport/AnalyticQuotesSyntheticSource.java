package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntFunction;

/** Closed synthetic 6906 transport: one bounded page and the real metadata/response gate. */
public final class AnalyticQuotesSyntheticSource {
    public static final String VERSION = "synthetic-analytic-quotes-v1";
    private final IntFunction<String> pages;
    private long bytes;
    private int fetchedPages;
    private int maximumPageBytes;

    public AnalyticQuotesSyntheticSource(final IntFunction<String> pages) {
        this.pages = Objects.requireNonNull(pages);
    }

    public static SourceContractRelease release() {
        final var fields = new ArrayList<ContractResponse.Field>();
        final var metadata = new ArrayList<ContractMetadata.Element>();
        for (final var field : declarations()) {
            final boolean key = field.name().equals("sequence_code");
            fields.add(
                    new ContractResponse.Field(
                            "/" + field.name(),
                            ContractResponse.Cardinality.SCALAR,
                            key
                                    ? ContractResponse.Presence.REQUIRED
                                    : ContractResponse.Presence.OPTIONAL,
                            !key,
                            List.of(field.wire())));
            metadata.add(
                    ContractMetadata.Element.fromDeclaredType(
                            ContractMetadata.ElementKind.DATA_FIELD,
                            field.name(),
                            Optional.of(
                                    field.wire() == ContractResponse.JsonType.INTEGER
                                            ? "integer"
                                            : "string")));
        }
        fields.add(
                new ContractResponse.Field(
                        "/synthetic_fixture",
                        ContractResponse.Cardinality.SCALAR,
                        ContractResponse.Presence.REQUIRED,
                        false,
                        List.of(ContractResponse.JsonType.BOOLEAN)));
        metadata.add(
                ContractMetadata.Element.fromDeclaredType(
                        ContractMetadata.ElementKind.DATA_FIELD,
                        "synthetic_fixture",
                        Optional.of("boolean")));
        metadata.add(
                ContractMetadata.Element.fromDeclaredType(
                        ContractMetadata.ElementKind.DATA_FILTER,
                        DataExportTemplate.COTACOES.metadataBusinessDateFilterName(),
                        Optional.of("date")));
        return SourceContractRelease.create(
                ContractSourceKind.DATA_EXPORT,
                DataExportContractAdapter.documentReference(DataExportTemplate.COTACOES),
                VERSION,
                new ContractMetadata(metadata, Optional.empty()),
                new ContractResponse(
                        "$",
                        ContractResponse.Cardinality.ARRAY,
                        ContractResponse.ObservationState.POPULATED,
                        "/sequence_code",
                        fields));
    }

    public DataExportHttpGatewayBundle bundle(
            final ContractRunGuard guard, final ImmutableFingerprint configuration) {
        final var release = release();
        final var limits = ContractObservationLimits.runtimeDefaults();
        final var adapter = new DataExportContractAdapter(limits, guard.responsePathBoundary());
        final var observation =
                DataExportContractObservationConfiguration.forRelease(
                        DataExportTemplate.COTACOES,
                        release,
                        limits,
                        guard.responsePathBoundary(),
                        configuration);
        return DataExportHttpGatewayBundle.contractBound(
                request -> {
                    if (request.template() != DataExportTemplate.COTACOES
                            || request.page() > 10000) {
                        throw new IllegalArgumentException("ANA_QUOTE_REQUEST_SCOPE");
                    }
                    final String page = pages.apply(request.page());
                    if (page == null || page.length() > 65536) {
                        throw new IllegalArgumentException("ANA_QUOTE_PAGE_BOUND");
                    }
                    final int size = page.getBytes(StandardCharsets.UTF_8).length;
                    if (size > 65536) {
                        throw new IllegalArgumentException("ANA_QUOTE_PAGE_BOUND");
                    }
                    try {
                        final var tree = DataExportStrictJsonParser.readTree(page);
                        if (!tree.isArray()) {
                            throw new IllegalArgumentException("ANA_QUOTE_ARRAY_REQUIRED");
                        }
                        for (final var row : tree) {
                            if (!row.path("synthetic_fixture").isBoolean()
                                    || !row.path("synthetic_fixture").booleanValue()) {
                                throw new IllegalArgumentException(
                                        "ANA_QUOTE_SYNTHETIC_MARKER_REQUIRED");
                            }
                        }
                        final var normalized = new DataExportResponseNormalizer().normalize(tree);
                        DataExportPageEntityLimitValidator.validate(request, normalized);
                        fetchedPages = Math.incrementExact(fetchedPages);
                        bytes = Math.addExact(bytes, size);
                        maximumPageBytes = Math.max(maximumPageBytes, size);
                        return normalized.withObservation(
                                adapter.response(
                                        tree, DataExportResponseForm.ROOT_ARRAY, "/sequence_code"),
                                limits,
                                guard.responsePathBoundary());
                    } catch (final JsonProcessingException failure) {
                        throw new IllegalArgumentException("ANA_QUOTE_JSON_INVALID", failure);
                    }
                },
                template -> {
                    if (template != DataExportTemplate.COTACOES) {
                        throw new IllegalArgumentException("ANA_QUOTE_METADATA_SCOPE");
                    }
                    final var metadata = new ArrayList<DataExportMetadataField>();
                    for (final var field : declarations()) {
                        metadata.add(
                                new DataExportMetadataField(
                                        field.name(),
                                        Optional.of(
                                                field.wire() == ContractResponse.JsonType.INTEGER
                                                        ? "integer"
                                                        : "string"),
                                        Optional.empty()));
                    }
                    metadata.add(
                            new DataExportMetadataField(
                                    "synthetic_fixture", Optional.of("boolean"), Optional.empty()));
                    return new DataExportTemplateInfo(
                            template,
                            200,
                            Optional.empty(),
                            true,
                            metadata,
                            List.of(
                                    new DataExportMetadataField(
                                            template.metadataBusinessDateFilterName(),
                                            Optional.of("date"),
                                            Optional.empty())));
                },
                observation);
    }

    private static List<Field> declarations() {
        try (var input =
                AnalyticQuotesSyntheticSource.class.getResourceAsStream(
                        "/analytic-laboratory/quote-fields.synthetic.json")) {
            if (input == null) {
                throw new IllegalStateException("ANA_QUOTE_CONTRACT_MISSING");
            }
            final byte[] bytes = input.readNBytes(16385);
            if (bytes.length > 16384) {
                throw new IllegalStateException("ANA_QUOTE_CONTRACT_BOUND");
            }
            final var root =
                    DataExportStrictJsonParser.readTree(new String(bytes, StandardCharsets.UTF_8));
            if (!root.path("version").asText().equals(VERSION)
                    || !root.path("provenance").asText().equals("FIXTURE_SINTETICA_EXPLICITA")
                    || !root.path("fields").isArray()
                    || root.path("fields").size() != 36) {
                throw new IllegalStateException("ANA_QUOTE_CONTRACT_DECLARATION");
            }
            final var names = new HashSet<String>();
            final var fields = new ArrayList<Field>();
            for (final var field : root.path("fields")) {
                final String name = field.path("name").asText();
                final String wire = field.path("wire").asText();
                if (!name.matches("[a-z][a-z0-9_]{1,95}")
                        || !names.add(name)
                        || !List.of("STRING", "INTEGER").contains(wire)) {
                    throw new IllegalStateException("ANA_QUOTE_CONTRACT_FIELD");
                }
                fields.add(new Field(name, ContractResponse.JsonType.valueOf(wire)));
            }
            return List.copyOf(fields);
        } catch (final IOException failure) {
            throw new IllegalStateException("ANA_QUOTE_CONTRACT_INVALID", failure);
        }
    }

    public Metrics metrics() {
        return new Metrics(fetchedPages, bytes, maximumPageBytes);
    }

    private record Field(String name, ContractResponse.JsonType wire) {}

    public record Metrics(int fetchedPages, long bytes, int maximumPageBytes) {}
}
