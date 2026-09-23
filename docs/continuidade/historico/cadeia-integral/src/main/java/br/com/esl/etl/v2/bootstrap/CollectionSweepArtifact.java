package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.SyntheticCollectionSnapshot;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Four explicitly declared file traversals in the existing closed synthetic Coletas universe. */
public final class CollectionSweepArtifact {
    private final PinnedLocalJson manifest;
    private final LocalDate date;
    private final int roots;
    private final boolean omitFirst;
    private final int pageSize;
    private final List<List<PinnedLocalJson>> observations;

    public CollectionSweepArtifact(final Path path, final CancellationToken token)
            throws IOException {
        this(PinnedLocalJson.open(path, 524288), token);
    }

    public CollectionSweepArtifact(final PinnedLocalJson pin, final CancellationToken token)
            throws IOException {
        manifest = pin;
        final var input = pin.read();
        final Path path = pin.file().toPath();
        QualificationJson.fields(
                input,
                "version",
                "origin",
                "date",
                "universeRoots",
                "omitFirst",
                "pageSize",
                "contractSha256",
                "observations");
        if (!"local-collection-sweep-v1".equals(QualificationJson.text(input, "version", 40))
                || !"SYNTHETIC_CLOSED_COLLECTION_UNIVERSE_V1"
                        .equals(QualificationJson.text(input, "origin", 60))) {
            throw new IllegalArgumentException("LOCAL_SWEEP_ORIGIN");
        }
        final var release =
                new RelationalSyntheticSource(page -> "[]")
                        .withAnalyticCollectionDetails()
                        .contractRelease(DataExportTemplate.COLETAS);
        if (!release.contractFingerprint()
                .sha256()
                .equals(QualificationJson.digest(input, "contractSha256"))) {
            throw new IllegalArgumentException("LOCAL_SWEEP_CONTRACT");
        }
        date = LocalDate.parse(QualificationJson.text(input, "date", 10));
        roots = QualificationJson.number(input, "universeRoots", 2, 4096);
        omitFirst = QualificationJson.flag(input, "omitFirst");
        pageSize = QualificationJson.number(input, "pageSize", 1, 16);
        final var declared = input.path("observations");
        QualificationJson.array(declared, 4, 4);
        final var traversals = new ArrayList<List<PinnedLocalJson>>(4);
        for (final var observation : declared) {
            QualificationJson.array(observation, 2, 1000);
            final var pages = new ArrayList<PinnedLocalJson>(observation.size());
            long count = 0;
            for (int index = 0; index < observation.size(); index++) {
                token.throwIfCancellationRequested();
                final var page =
                        PinnedLocalJson.reference(
                                path.toAbsolutePath().getParent(), observation.get(index), 65536);
                final var records = page.read();
                QualificationJson.array(
                        records,
                        index == observation.size() - 1 ? 0 : 1,
                        index == observation.size() - 1 ? 0 : pageSize);
                for (final var record : records) {
                    if (!record.isObject()
                            || !date.toString().equals(record.path("request_date").asText())
                            || !record.path("id").isIntegralNumber()) {
                        throw new IllegalArgumentException("LOCAL_SWEEP_ROW_SCOPE");
                    }
                }
                count += records.size();
                pages.add(page);
            }
            if (count != (roots - (omitFirst ? 1 : 0)) * 3L) {
                throw new IllegalArgumentException("LOCAL_SWEEP_TRAVERSAL_COUNT");
            }
            traversals.add(List.copyOf(pages));
        }
        observations = List.copyOf(traversals);
    }

    public LocalDate date() {
        return date;
    }

    public int pageSize() {
        return pageSize;
    }

    public SyntheticCollectionSnapshot snapshot(final UUID run) {
        return new SyntheticCollectionSnapshot(run, date, roots, omitFirst);
    }

    List<CollectionSweepInput> inputs(final UUID run, final CancellationToken token) {
        final var snapshot = snapshot(run);
        final var result = new ArrayList<CollectionSweepInput>(4);
        for (final var pages : observations) {
            final var source =
                    new RelationalSyntheticSource(
                                    page -> {
                                        token.throwIfCancellationRequested();
                                        if (page < 1 || page > pages.size()) {
                                            throw new IllegalArgumentException(
                                                    "LOCAL_SWEEP_PAGE_BOUND");
                                        }
                                        try {
                                            return pages.get(page - 1)
                                                    .consume(
                                                            bytes ->
                                                                    new String(
                                                                            bytes,
                                                                            StandardCharsets
                                                                                    .UTF_8));
                                        } catch (final IOException failure) {
                                            throw new UncheckedIOException(failure);
                                        }
                                    })
                            .withAnalyticCollectionDetails();
            result.add(new CollectionSweepInput(run, date, snapshot.fingerprint(), source));
        }
        return List.copyOf(result);
    }

    public void verifyFiles(final CancellationToken token) throws IOException {
        manifest.read();
        for (final var traversal : observations) {
            for (final var page : traversal) {
                token.throwIfCancellationRequested();
                page.verify();
            }
        }
    }
}
