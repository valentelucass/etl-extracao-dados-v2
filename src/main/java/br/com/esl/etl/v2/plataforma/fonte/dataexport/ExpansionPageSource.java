package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.util.function.IntFunction;

/**
 * Versioned fixture transport. Every generated page traverses the real parser and contract gate.
 */
public class ExpansionPageSource implements ExpansionCaptureSource {

    private final IntFunction<String> pages;
    private final Observer observer;
    private long bytes;
    private int fetched;
    private int maximumPageBytes;

    public ExpansionPageSource(final IntFunction<String> pages) {
        this(pages, Observer.NONE);
    }

    protected ExpansionPageSource(final IntFunction<String> pages, final Observer observer) {
        this.pages = java.util.Objects.requireNonNull(pages);
        this.observer = java.util.Objects.requireNonNull(observer);
    }

    public ExpansionPageSource observed(final Observer measurement) {
        return new ExpansionPageSource(pages, measurement);
    }

    @Override
    public SourceContractRelease contract(final DataExportTemplate template) {
        return ExpansionLocalContract.release(template);
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
                                throw new IllegalArgumentException("EXP_LAB_FIXTURE_PAGE_BOUND");
                            }
                            final int size = page.getBytes(StandardCharsets.UTF_8).length;
                            if (size > 65_536) {
                                throw new IllegalArgumentException("EXP_LAB_FIXTURE_PAGE_BOUND");
                            }
                            try {
                                final var tree = DataExportStrictJsonParser.readTree(page);
                                if (!tree.isArray()) {
                                    throw new IllegalArgumentException(
                                            "EXP_LAB_FIXTURE_ARRAY_REQUIRED");
                                }
                                for (final var row : tree) {
                                    if (!"FIXTURE_SINTETICA_EXPLICITA"
                                            .equals(row.path("provenance").asText())) {
                                        throw new IllegalArgumentException(
                                                "EXP_LAB_SYNTHETIC_MARKER_REQUIRED");
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
                                        "EXP_LAB_FIXTURE_JSON_INVALID", failure);
                            }
                        },
                        ignored -> {
                            throw new IllegalStateException("EXP_LAB_NO_METADATA_IO");
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

    public Metrics metrics() {
        return new Metrics(fetched, bytes, maximumPageBytes);
    }
}
