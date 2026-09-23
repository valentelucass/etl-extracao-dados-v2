package br.com.esl.etl.v2.plataforma.qualificacao;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Independent expected source records. Only bounded descriptors survive between row reads. */
public final class DeclaredWireRows {
    private final Path directory;
    private final List<Row> rows;

    public DeclaredWireRows(final Path path) throws IOException {
        this(PinnedLocalJson.open(path, 262144));
    }

    public DeclaredWireRows(final PinnedLocalJson pin) throws IOException {
        final var manifest = pin.read();
        QualificationJson.fields(manifest, "version", "origin", "rows");
        if (!"local-wire-expectations-v1".equals(QualificationJson.text(manifest, "version", 40))
                || !"INDEPENDENT_SYNTHETIC_RULES_V1"
                        .equals(QualificationJson.text(manifest, "origin", 40))) {
            throw new IllegalArgumentException("LOCAL_WIRE_ORIGIN");
        }
        QualificationJson.array(manifest.path("rows"), 1, 512);
        final var descriptors = new ArrayList<Row>();
        for (final var node : manifest.path("rows")) {
            QualificationJson.fields(
                    node,
                    "entity",
                    "root",
                    "component",
                    "revision",
                    "correction",
                    "rootKey",
                    "rootType",
                    "partKey",
                    "partType",
                    "componentKey",
                    "componentType",
                    "file",
                    "sha256");
            final String entity = QualificationJson.text(node, "entity", 3);
            final String file = QualificationJson.text(node, "file", 80);
            if (!Set.of("CAP", "FAT", "INV", "SIN", "MAN", "COL", "COT", "FRE", "LOC")
                            .contains(entity)
                    || !file.matches("[a-z0-9][a-z0-9-]{0,70}\\.json")) {
                throw new IllegalArgumentException("LOCAL_WIRE_ROW_SCOPE");
            }
            final var row =
                    new Row(
                            entity,
                            QualificationJson.number(node, "root", 1, 32),
                            QualificationJson.number(node, "component", 1, 2),
                            QualificationJson.number(node, "revision", 1, 1000),
                            QualificationJson.flag(node, "correction"),
                            key(node, "root"),
                            key(node, "part"),
                            key(node, "component"),
                            file,
                            QualificationJson.digest(node, "sha256"));
            if (descriptors.stream().anyMatch(existing -> existing.sameCoordinate(row))) {
                throw new IllegalArgumentException("LOCAL_WIRE_DUPLICATE_COORDINATE");
            }
            descriptors.add(row);
        }
        rows = List.copyOf(descriptors);
        directory = pin.file().toPath().getParent();
        // Fail before physical execution, then recheck the same pins on every consumed row.
        for (final var row : rows) {
            read(row);
        }
    }

    public void verifyFiles(
            final br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken token) {
        for (final var row : rows) {
            token.throwIfCancellationRequested();
            read(row);
        }
    }

    public ObjectNode source(
            final String entity,
            final int root,
            final int component,
            final int revision,
            final boolean correction) {
        return read(
                rows.stream()
                        .filter(
                                row ->
                                        row.entity().equals(entity)
                                                && row.root() == root
                                                && row.component() == component
                                                && row.revision() == revision
                                                && row.correction() == correction)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "LOCAL_WIRE_EXPECTATION_MISSING")));
    }

    public int rootOrdinal(final String entity, final String rootType, final String rootKey) {
        final var found =
                rows.stream()
                        .filter(
                                row ->
                                        row.entity().equals(entity)
                                                && row.rootKey().kind().name().equals(rootType)
                                                && row.rootKey().value().equals(rootKey))
                        .mapToInt(Row::root)
                        .distinct()
                        .toArray();
        if (found.length != 1) {
            throw new IllegalArgumentException("LOCAL_WIRE_ROOT_BINDING");
        }
        return found[0];
    }

    public int componentOrdinal(
            final String entity,
            final String rootType,
            final String rootKey,
            final String partType,
            final String partKey,
            final String componentType,
            final String componentKey) {
        final var found =
                rows.stream()
                        .filter(
                                row ->
                                        row.entity().equals(entity)
                                                && row.rootKey().kind().name().equals(rootType)
                                                && row.rootKey().value().equals(rootKey)
                                                && row.partKey().kind().name().equals(partType)
                                                && row.partKey().value().equals(partKey)
                                                && row.componentKey()
                                                        .kind()
                                                        .name()
                                                        .equals(componentType)
                                                && row.componentKey().value().equals(componentKey))
                        .mapToInt(Row::component)
                        .distinct()
                        .toArray();
        if (found.length != 1) {
            throw new IllegalArgumentException("LOCAL_WIRE_COMPONENT_BINDING");
        }
        return found[0];
    }

    public String componentIdentity(final String entity, final int root, final int component) {
        final var identities =
                rows.stream()
                        .filter(
                                row ->
                                        row.entity().equals(entity)
                                                && row.root() == root
                                                && row.component() == component)
                        .map(
                                row ->
                                        tagged(row.rootKey())
                                                + "/"
                                                + tagged(row.partKey())
                                                + "/"
                                                + tagged(row.componentKey()))
                        .distinct()
                        .toList();
        if (identities.size() != 1) {
            throw new IllegalArgumentException("LOCAL_WIRE_COMPONENT_IDENTITY");
        }
        return identities.get(0);
    }

    private static ExpansionKey key(final JsonNode node, final String prefix) {
        return new ExpansionKey(
                ExpansionKey.Kind.valueOf(QualificationJson.text(node, prefix + "Type", 7)),
                QualificationJson.text(node, prefix + "Key", 64));
    }

    private static String tagged(final ExpansionKey key) {
        return key.kind().name() + ":" + key.value();
    }

    private ObjectNode read(final Row row) {
        try {
            final Path path = directory.resolve(row.file());
            QualificationJson.regular(path);
            final byte[] bytes;
            try (var stream = Files.newInputStream(path)) {
                bytes = stream.readNBytes(32769);
            }
            final var data = QualificationJson.parse(bytes, 32768);
            if (!QualificationJson.sha256(bytes).equals(row.sha256()) || !data.isObject()) {
                throw new IllegalArgumentException("LOCAL_WIRE_EXPECTATION_CHANGED");
            }
            return (ObjectNode) data;
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private record Row(
            String entity,
            int root,
            int component,
            int revision,
            boolean correction,
            ExpansionKey rootKey,
            ExpansionKey partKey,
            ExpansionKey componentKey,
            String file,
            String sha256) {
        boolean sameCoordinate(final Row other) {
            return entity.equals(other.entity)
                    && root == other.root
                    && component == other.component
                    && revision == other.revision
                    && correction == other.correction;
        }
    }
}
