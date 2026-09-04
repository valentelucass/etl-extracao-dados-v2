package br.com.esl.etl.v2.plataforma.contrato;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Shape sanitizado de uma resposta observada, sem valores ou contagens de negócio. */
public record ContractResponse(
        String recordRoot,
        Cardinality rootCardinality,
        ObservationState observationState,
        String keyPath,
        List<Field> fields) {

    public static final int MAXIMUM_FIELDS = 4096;

    public ContractResponse {
        recordRoot = "$".equals(recordRoot) ? recordRoot : ContractText.pointer(recordRoot);
        rootCardinality =
                Objects.requireNonNull(rootCardinality, "A cardinalidade da raiz é obrigatória.");
        if (rootCardinality != Cardinality.OBJECT && rootCardinality != Cardinality.ARRAY) {
            throw new IllegalArgumentException("A cardinalidade da raiz do contrato é inválida.");
        }
        observationState =
                Objects.requireNonNull(observationState, "O estado da observação é obrigatório.");
        keyPath = requireKeyPath(keyPath);
        Objects.requireNonNull(fields, "Os fields observados são obrigatórios.");
        if (fields.size() > MAXIMUM_FIELDS) {
            throw new IllegalArgumentException("A resposta excede o limite de fields.");
        }
        final List<Field> sorted = new ArrayList<>(fields.size());
        final Set<String> identities = new HashSet<>();
        for (final Field field : fields) {
            final Field required =
                    Objects.requireNonNull(field, "A resposta não pode conter field nulo.");
            final String identity =
                    required.scope().name() + ':' + required.path().toLowerCase(Locale.ROOT);
            if (!identities.add(identity)) {
                throw new IllegalArgumentException(
                        "A resposta contém path técnico duplicado ou ambíguo.");
            }
            sorted.add(required);
        }
        sorted.sort(Comparator.comparing(Field::scope).thenComparing(Field::path));
        fields = List.copyOf(sorted);
        if (observationState == ObservationState.EMPTY
                && fields.stream().anyMatch(field -> field.scope() == FieldScope.RECORD)) {
            throw new IllegalArgumentException(
                    "Uma resposta vazia não pode declarar fields de registro.");
        }
        if (observationState == ObservationState.POPULATED
                && fields.stream().noneMatch(field -> field.scope() == FieldScope.RECORD)) {
            throw new IllegalArgumentException(
                    "Uma resposta populada deve declarar fields de registro.");
        }
        validateHierarchy(fields);
        validateRecordRoot(recordRoot, rootCardinality, fields);
    }

    public Optional<Field> find(final String path) {
        return find(FieldScope.RECORD, path);
    }

    public static String requireKeyPath(final String value) {
        final String normalized = ContractText.pointer(value);
        for (final String segment : ContractText.pointerSegments(normalized)) {
            if ("*".equals(segment)) {
                throw new IllegalArgumentException(
                        "O path da chave não pode atravessar coleção repetida.");
            }
        }
        return normalized;
    }

    public Optional<Field> find(final FieldScope scope, final String path) {
        Objects.requireNonNull(scope, "O escopo do field é obrigatório.");
        Objects.requireNonNull(path, "O path de resposta é obrigatório.");
        return fields.stream()
                .filter(field -> field.scope() == scope && field.path().equals(path))
                .findFirst();
    }

    @Override
    public String toString() {
        return "ContractResponse[root="
                + recordRoot
                + ", rootCardinality="
                + rootCardinality
                + ", state="
                + observationState
                + ", fieldCount="
                + fields.size()
                + "]";
    }

    /** Shape de um path relativo ao registro raiz. */
    public record Field(
            FieldScope scope,
            String path,
            Cardinality cardinality,
            Presence presence,
            boolean nullable,
            List<JsonType> jsonTypes) {

        public Field(
                final String path,
                final Cardinality cardinality,
                final Presence presence,
                final boolean nullable,
                final List<JsonType> jsonTypes) {
            this(FieldScope.RECORD, path, cardinality, presence, nullable, jsonTypes);
        }

        public Field {
            scope = Objects.requireNonNull(scope, "O escopo do field é obrigatório.");
            path = ContractText.pointer(path);
            cardinality = Objects.requireNonNull(cardinality, "A cardinalidade é obrigatória.");
            presence = Objects.requireNonNull(presence, "A presença é obrigatória.");
            Objects.requireNonNull(jsonTypes, "Os tipos JSON são obrigatórios.");
            final List<JsonType> sorted =
                    jsonTypes.stream()
                            .map(type -> Objects.requireNonNull(type, "O tipo JSON é obrigatório."))
                            .distinct()
                            .sorted()
                            .toList();
            if (sorted.size() != jsonTypes.size()) {
                throw new IllegalArgumentException("Os tipos JSON não podem se repetir.");
            }
            validateShape(cardinality, nullable, sorted);
            jsonTypes = List.copyOf(sorted);
        }

        private static void validateShape(
                final Cardinality cardinality,
                final boolean nullable,
                final List<JsonType> jsonTypes) {
            if (cardinality == Cardinality.UNOBSERVED) {
                if (!nullable || !jsonTypes.isEmpty()) {
                    throw new IllegalArgumentException("Um field não observado deve ser nulo.");
                }
                return;
            }
            if (jsonTypes.isEmpty()) {
                throw new IllegalArgumentException("Um field observado exige tipo JSON.");
            }
            if (cardinality == Cardinality.OBJECT && !jsonTypes.equals(List.of(JsonType.OBJECT))) {
                throw new IllegalArgumentException("A cardinalidade OBJECT exige tipo object.");
            }
            if (cardinality == Cardinality.ARRAY && !jsonTypes.equals(List.of(JsonType.ARRAY))) {
                throw new IllegalArgumentException("A cardinalidade ARRAY exige tipo array.");
            }
            if (cardinality == Cardinality.SCALAR
                    && jsonTypes.stream().anyMatch(type -> !type.isScalar())) {
                throw new IllegalArgumentException("A cardinalidade SCALAR exige tipos escalares.");
            }
            if (cardinality == Cardinality.MIXED && shapeCount(jsonTypes) < 2) {
                throw new IllegalArgumentException("A cardinalidade MIXED exige shapes distintos.");
            }
        }

        private static int shapeCount(final List<JsonType> jsonTypes) {
            final boolean object = jsonTypes.contains(JsonType.OBJECT);
            final boolean array = jsonTypes.contains(JsonType.ARRAY);
            final boolean scalar = jsonTypes.stream().anyMatch(JsonType::isScalar);
            return (object ? 1 : 0) + (array ? 1 : 0) + (scalar ? 1 : 0);
        }
    }

    public enum FieldScope {
        RECORD,
        ENVELOPE
    }

    public enum Cardinality {
        SCALAR,
        OBJECT,
        ARRAY,
        MIXED,
        UNOBSERVED
    }

    public enum Presence {
        REQUIRED,
        OPTIONAL
    }

    public enum ObservationState {
        POPULATED,
        EMPTY
    }

    public enum JsonType {
        STRING,
        INTEGER,
        NUMBER,
        BOOLEAN,
        OBJECT,
        ARRAY;

        public boolean isScalar() {
            return this != OBJECT && this != ARRAY;
        }
    }

    private static void validateHierarchy(final List<Field> fields) {
        final java.util.Map<String, Field> indexed = new java.util.HashMap<>();
        for (final Field field : fields) {
            indexed.put(field.scope().name() + ':' + field.path(), field);
        }
        for (final Field field : fields) {
            final String parentPath = ContractText.parentPointer(field.path());
            if (parentPath.isEmpty()) {
                continue;
            }
            final Field parent = indexed.get(field.scope().name() + ':' + parentPath);
            if (parent == null) {
                throw new IllegalArgumentException("O shape contém field sem ancestral.");
            }
            final String[] segments = ContractText.pointerSegments(field.path());
            final boolean arrayElement = "*".equals(segments[segments.length - 1]);
            final JsonType requiredParentType = arrayElement ? JsonType.ARRAY : JsonType.OBJECT;
            if (!parent.jsonTypes().contains(requiredParentType)) {
                throw new IllegalArgumentException("O shape contém relação pai/filho inválida.");
            }
        }
    }

    private static void validateRecordRoot(
            final String recordRoot, final Cardinality rootCardinality, final List<Field> fields) {
        if ("$".equals(recordRoot)) {
            if (fields.stream().anyMatch(field -> field.scope() == FieldScope.ENVELOPE)) {
                throw new IllegalArgumentException(
                        "Uma resposta sem envelope não pode declarar fields de envelope.");
            }
            return;
        }
        final Field root =
                fields.stream()
                        .filter(
                                field ->
                                        field.scope() == FieldScope.ENVELOPE
                                                && field.path().equals(recordRoot))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "A raiz de registros exige evidência no envelope."));
        if (root.cardinality() != rootCardinality
                || root.presence() != Presence.REQUIRED
                || root.nullable()) {
            throw new IllegalArgumentException(
                    "A raiz de registros não corresponde ao shape do envelope.");
        }
    }
}
