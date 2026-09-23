package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionKey;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionRelation;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRelations;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Explicit relation evidence in bounded files; no canonical identity is reconstructed. */
public final class DeclaredExpansionRelations implements AnalyticExpansionRelations {
    private final List<PinnedLocalJson> batches;
    private final LocalDate date;

    public DeclaredExpansionRelations(final PinnedLocalJson pin, final CancellationToken token)
            throws IOException {
        final var root = pin.read();
        QualificationJson.fields(root, "version", "origin", "date", "batches");
        if (!"local-expansion-relations-v1".equals(QualificationJson.text(root, "version", 40))
                || !"LOCAL_SYNTHETIC_ARTIFACT_V1"
                        .equals(QualificationJson.text(root, "origin", 40))) {
            throw new IllegalArgumentException("LOCAL_RELATIONS_ORIGIN");
        }
        date = LocalDate.parse(QualificationJson.text(root, "date", 10));
        QualificationJson.array(root.path("batches"), 1, 128);
        final var files = new ArrayList<PinnedLocalJson>();
        final Path directory = pin.file().toPath().getParent();
        for (final var entry : root.path("batches")) {
            token.throwIfCancellationRequested();
            final var batch = PinnedLocalJson.reference(directory, entry, 131072);
            read(batch);
            files.add(batch);
        }
        batches = List.copyOf(files);
    }

    @Override
    public void bind(
            final JdbcExpansionRelations repository,
            final UUID run,
            final LocalDate executionDate,
            final int roots,
            final CancellationToken token)
            throws SQLException {
        if (!date.equals(executionDate)) {
            throw new IllegalArgumentException("LOCAL_RELATIONS_WINDOW");
        }
        for (final var batch : batches) {
            token.throwIfCancellationRequested();
            try {
                repository.bind(run, read(batch), token);
            } catch (final IOException failure) {
                throw new SQLException("LOCAL_RELATIONS_FILE_CHANGED", failure);
            }
        }
    }

    private List<ExpansionRelation> read(final PinnedLocalJson pin) throws IOException {
        final var rows = pin.read();
        QualificationJson.array(rows, 1, 98);
        final var result = new ArrayList<ExpansionRelation>(rows.size());
        for (final var row : rows) {
            QualificationJson.fields(
                    row,
                    "bindingKey",
                    "revision",
                    "kind",
                    "root",
                    "part",
                    "component",
                    "document",
                    "targetKey",
                    "targetDate",
                    "cardinality",
                    "active",
                    "evidence");
            final var relation =
                    new ExpansionRelation(
                            QualificationJson.text(row, "bindingKey", 64),
                            QualificationJson.number(row, "revision", 1, 1000),
                            ExpansionRelation.Kind.valueOf(QualificationJson.text(row, "kind", 40)),
                            key(row.path("root")),
                            key(row.path("part")),
                            key(row.path("component")),
                            key(row.path("document")),
                            QualificationJson.text(row, "targetKey", 64),
                            LocalDate.parse(QualificationJson.text(row, "targetDate", 10)),
                            ExpansionRelation.Cardinality.valueOf(
                                    QualificationJson.text(row, "cardinality", 40)),
                            QualificationJson.flag(row, "active"),
                            QualificationJson.text(row, "evidence", 64));
            if (!relation.targetDate().equals(date)) {
                throw new IllegalArgumentException("LOCAL_RELATIONS_TARGET_DATE");
            }
            result.add(relation);
        }
        return List.copyOf(result);
    }

    public void verifyFiles(final CancellationToken token) throws IOException {
        for (final var pin : batches) {
            token.throwIfCancellationRequested();
            read(pin);
        }
    }

    private static ExpansionKey key(final JsonNode value) {
        QualificationJson.fields(value, "kind", "value");
        return new ExpansionKey(
                ExpansionKey.Kind.valueOf(QualificationJson.text(value, "kind", 10)),
                QualificationJson.text(value, "value", 64));
    }
}
