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
    private final List<SyntheticCollectionSnapshot.Root> universe;
    private final br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope sourceScope;
    private final int revision;

    public CollectionSweepArtifact(final Path path, final CancellationToken token)
            throws IOException {
        this(PinnedLocalJson.open(path, 524288), token);
    }

    public CollectionSweepArtifact(final PinnedLocalJson pin, final CancellationToken token)
            throws IOException {
        manifest = pin;
        final var input = pin.read();
        final Path path = pin.file().toPath();
        final boolean integral =
                "local-collection-sweep-v2".equals(QualificationJson.text(input, "version", 40));
        if (integral) {
            QualificationJson.fields(
                    input,
                    "version",
                    "origin",
                    "date",
                    "universeRoots",
                    "omitFirst",
                    "pageSize",
                    "contractSha256",
                    "observations",
                    "universe",
                    "source",
                    "tenant",
                    "revision");
        } else {
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
        }
        if (!(integral ? "local-collection-sweep-v2" : "local-collection-sweep-v1")
                        .equals(QualificationJson.text(input, "version", 40))
                || !(integral
                                ? "SYNTHETIC_DECLARED_COLLECTION_PREVIEW_V1"
                                : "SYNTHETIC_CLOSED_COLLECTION_UNIVERSE_V1")
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
        if (integral) {
            sourceScope =
                    new br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope(
                            DeclaredCapturePages.scope(QualificationJson.text(input, "source", 40)),
                            DeclaredCapturePages.scope(
                                    QualificationJson.text(input, "tenant", 40)));
            revision = QualificationJson.number(input, "revision", 1, 1000);
            QualificationJson.array(input.path("universe"), 2, 32);
            final var entries = new ArrayList<SyntheticCollectionSnapshot.Root>();
            for (final var entry : input.path("universe")) {
                QualificationJson.fields(entry, "key", "rows");
                entries.add(
                        new SyntheticCollectionSnapshot.Root(
                                QualificationJson.text(entry, "key", 40),
                                QualificationJson.number(entry, "rows", 0, 1000)));
            }
            universe = List.copyOf(entries);
            snapshot(new UUID(0, 0));
        } else {
            universe = null;
            sourceScope = null;
            revision = 1;
        }
        final var declared = input.path("observations");
        QualificationJson.array(declared, 4, 4);
        final var traversals = new ArrayList<List<PinnedLocalJson>>(4);
        for (final var observation : declared) {
            QualificationJson.array(observation, 2, 1000);
            final var pages = new ArrayList<PinnedLocalJson>(observation.size());
            long count = 0;
            final var observed = new java.util.HashMap<String, Integer>();
            for (int index = 0; index < observation.size(); index++) {
                token.throwIfCancellationRequested();
                final var page =
                        PinnedLocalJson.reference(
                                path.toAbsolutePath().getParent(), observation.get(index), 65536);
                final var records = page.read();
                QualificationJson.array(
                        records,
                        index == observation.size() - 1 ? 0 : 1,
                        index == observation.size() - 1 ? 0 : integral ? 1000 : pageSize);
                final var pageKeys = new java.util.HashSet<String>();
                for (final var record : records) {
                    if (!record.isObject()
                            || !date.toString().equals(record.path("request_date").asText())
                            || !record.path("id").isIntegralNumber()) {
                        throw new IllegalArgumentException("LOCAL_SWEEP_ROW_SCOPE");
                    }
                    final String key = "INTEGER:" + record.path("id").asText();
                    pageKeys.add(key);
                    observed.merge(key, 1, Integer::sum);
                }
                if (pageKeys.size() > pageSize) {
                    throw new IllegalArgumentException("LOCAL_SWEEP_ENTITY_PAGE_BOUND");
                }
                count += records.size();
                pages.add(page);
            }
            if (count != snapshot(new UUID(0, 0)).expectedRows()) {
                throw new IllegalArgumentException("LOCAL_SWEEP_TRAVERSAL_COUNT");
            }
            if (integral) {
                for (final var entry : universe) {
                    if (observed.getOrDefault(entry.key(), 0) != entry.rows()) {
                        throw new IllegalArgumentException("LOCAL_SWEEP_UNIVERSE_MISMATCH");
                    }
                    observed.remove(entry.key());
                }
                if (!observed.isEmpty()) {
                    throw new IllegalArgumentException("LOCAL_SWEEP_UNIVERSE_MISMATCH");
                }
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
        return new SyntheticCollectionSnapshot(run, date, roots, omitFirst, universe);
    }

    public void requireIntegralScope(
            final br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope scope,
            final LocalDate start,
            final int expectedRevision,
            final int expectedPageSize,
            final int expectedRoots) {
        if (universe == null
                || !scope.equals(sourceScope)
                || !date.equals(start)
                || revision != expectedRevision
                || pageSize != expectedPageSize
                || roots != expectedRoots) {
            throw new IllegalArgumentException("INTEGRAL_INPUT_SWEEP_SELECTION");
        }
    }

    List<CollectionSweepInput> inputs(final UUID run, final CancellationToken token) {
        return inputs(run, token, AnalyticScenarioObserver.NONE);
    }

    List<CollectionSweepInput> inputs(
            final UUID run,
            final CancellationToken token,
            final AnalyticScenarioObserver observer) {
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
                            .withAnalyticCollectionDetails()
                            .observed(observer.forInput(AnalyticScenarioObserver.Input.COL));
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
