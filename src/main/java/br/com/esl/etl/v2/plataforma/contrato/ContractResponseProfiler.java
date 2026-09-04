package br.com.esl.etl.v2.plataforma.contrato;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Perfila somente o shape de uma resposta já limitada, descartando todos os valores. */
public final class ContractResponseProfiler {

    private final ContractObservationLimits limits;
    private final ContractResponsePathBoundary pathBoundary;

    private ContractResponseProfiler(
            final ContractObservationLimits limits,
            final ContractResponsePathBoundary pathBoundary) {
        this.limits = Objects.requireNonNull(limits, "Os limites de observação são obrigatórios.");
        this.pathBoundary =
                Objects.requireNonNull(pathBoundary, "A fronteira de paths é obrigatória.");
    }

    public static ContractResponseProfiler forRuntime(
            final ContractObservationLimits limits,
            final ContractResponsePathBoundary pathBoundary) {
        if (!Objects.requireNonNull(pathBoundary, "A fronteira de paths é obrigatória.")
                .runtimeBound()) {
            throw new IllegalArgumentException("O profiler runtime exige uma fronteira vinculada.");
        }
        return new ContractResponseProfiler(limits, pathBoundary);
    }

    /** Cria fingerprints apenas de fixtures sintéticas já revisadas, nunca de payload remoto. */
    public static ContractResponseProfiler forSyntheticFixtures(
            final ContractObservationLimits limits) {
        return new ContractResponseProfiler(
                limits, ContractResponsePathBoundary.syntheticFixtures());
    }

    public ContractResponse profile(
            final JsonNode recordContainer,
            final String recordRoot,
            final ContractResponse.Cardinality rootCardinality,
            final String keyPath) {
        if (!"$".equals(recordRoot)) {
            throw new IllegalArgumentException(
                    "Uma resposta sem envelope deve usar a raiz de registros '$'.");
        }
        return profileInternal(recordContainer, recordRoot, rootCardinality, keyPath, null);
    }

    public ContractResponse profileWithEnvelope(
            final JsonNode recordContainer,
            final String recordRoot,
            final ContractResponse.Cardinality rootCardinality,
            final String keyPath,
            final JsonNode envelope) {
        Objects.requireNonNull(envelope, "O envelope observado é obrigatório.");
        final String normalizedRoot = ContractText.pointer(recordRoot);
        if (!envelope.isObject() || !envelope.at(normalizedRoot).equals(recordContainer)) {
            throw new IllegalArgumentException("O envelope não corresponde à raiz de registros.");
        }
        return profileInternal(recordContainer, normalizedRoot, rootCardinality, keyPath, envelope);
    }

    private ContractResponse profileInternal(
            final JsonNode recordContainer,
            final String recordRoot,
            final ContractResponse.Cardinality rootCardinality,
            final String keyPath,
            final JsonNode envelope) {
        Objects.requireNonNull(recordContainer, "A raiz de registros é obrigatória.");
        Objects.requireNonNull(rootCardinality, "A cardinalidade da raiz é obrigatória.");
        if (!"$".equals(recordRoot)) {
            ContractText.pointer(recordRoot);
        }
        final String normalizedKeyPath = ContractText.pointer(keyPath);
        rejectWildcardKey(normalizedKeyPath);

        final ObservationBudget budget = new ObservationBudget(limits);
        final ProfileState state =
                new ProfileState(limits, ContractResponse.FieldScope.RECORD, pathBoundary, budget);
        final boolean populated;
        if (rootCardinality == ContractResponse.Cardinality.ARRAY) {
            if (!recordContainer.isArray()) {
                throw new IllegalArgumentException(
                        "A raiz observada não possui cardinalidade ARRAY.");
            }
            populated = !recordContainer.isEmpty();
            for (final JsonNode record : recordContainer) {
                state.observeRecord(record);
            }
        } else if (rootCardinality == ContractResponse.Cardinality.OBJECT) {
            if (!recordContainer.isObject() || recordContainer.isEmpty()) {
                throw new IllegalArgumentException(
                        "A raiz observada não possui um registro OBJECT.");
            }
            populated = true;
            state.observeRecord(recordContainer);
        } else {
            throw new IllegalArgumentException("A cardinalidade da raiz observada é inválida.");
        }

        final List<ContractResponse.Field> fields = new ArrayList<>();
        if (populated) {
            fields.addAll(state.fields());
        }
        if (envelope != null) {
            final ProfileState envelopeState =
                    new ProfileState(
                            limits, ContractResponse.FieldScope.ENVELOPE, pathBoundary, budget);
            envelopeState.observeEnvelope(envelope, recordRoot);
            fields.addAll(envelopeState.fields());
        }
        return new ContractResponse(
                recordRoot,
                rootCardinality,
                populated
                        ? ContractResponse.ObservationState.POPULATED
                        : ContractResponse.ObservationState.EMPTY,
                normalizedKeyPath,
                fields);
    }

    private static void rejectWildcardKey(final String keyPath) {
        for (final String segment : ContractText.pointerSegments(keyPath)) {
            if ("*".equals(segment)) {
                throw new IllegalArgumentException(
                        "O path da chave não pode atravessar coleção repetida.");
            }
        }
    }

    private static final class ProfileState {

        private final ContractObservationLimits limits;
        private final ContractResponse.FieldScope scope;
        private final ContractResponsePathBoundary pathBoundary;
        private final ObservationBudget budget;
        private final Map<String, FieldAccumulator> fields = new TreeMap<>();
        private final Map<String, Integer> parentOccurrences = new TreeMap<>();

        private ProfileState(
                final ContractObservationLimits limits,
                final ContractResponse.FieldScope scope,
                final ContractResponsePathBoundary pathBoundary,
                final ObservationBudget budget) {
            this.limits = limits;
            this.scope = scope;
            this.pathBoundary = pathBoundary;
            this.budget = budget;
        }

        private void observeEnvelope(final JsonNode envelope, final String recordRoot) {
            observeNodeBudget();
            observeEnvelopeObject(envelope, "", recordRoot, 0);
        }

        private void observeEnvelopeObject(
                final JsonNode object,
                final String parentPath,
                final String recordRoot,
                final int depth) {
            validateDepth(depth);
            if (!object.isObject()) {
                throw new IllegalArgumentException(
                        "A raiz de registros possui ancestral inválido.");
            }
            parentOccurrences.merge(parentPath, 1, Math::addExact);
            final Iterator<Map.Entry<String, JsonNode>> properties = object.fields();
            while (properties.hasNext()) {
                final Map.Entry<String, JsonNode> property = properties.next();
                final String path = ContractText.appendPointer(parentPath, property.getKey());
                final FieldAccumulator accumulator = field(path, parentPath);
                accumulator.markPresent();
                if (path.equals(recordRoot)) {
                    observeShallow(accumulator, property.getValue());
                } else if (recordRoot.startsWith(path + "/")) {
                    observeNodeBudget();
                    accumulator.observe(ContractResponse.JsonType.OBJECT);
                    observeEnvelopeObject(property.getValue(), path, recordRoot, depth + 1);
                } else {
                    observeValue(accumulator, property.getValue(), path, depth + 1);
                }
            }
        }

        private void observeRecord(final JsonNode record) {
            observeNodeBudget();
            if (!record.isObject() || record.isEmpty()) {
                throw new IllegalArgumentException("Cada registro observado deve ser um objeto.");
            }
            observeObject(record, "", 0);
        }

        private void observeObject(
                final JsonNode object, final String parentPath, final int depth) {
            validateDepth(depth);
            parentOccurrences.merge(parentPath, 1, Math::addExact);
            final Iterator<Map.Entry<String, JsonNode>> properties = object.fields();
            while (properties.hasNext()) {
                final Map.Entry<String, JsonNode> property = properties.next();
                final String path = ContractText.appendPointer(parentPath, property.getKey());
                final FieldAccumulator accumulator = field(path, parentPath);
                accumulator.markPresent();
                observeValue(accumulator, property.getValue(), path, depth + 1);
            }
        }

        private void observeValue(
                final FieldAccumulator accumulator,
                final JsonNode value,
                final String path,
                final int depth) {
            observeNodeBudget();
            validateDepth(depth);
            if (value == null || value.isNull()) {
                accumulator.observeNull();
                return;
            }
            if (value.isObject()) {
                accumulator.observe(ContractResponse.JsonType.OBJECT);
                if (pathBoundary.shouldTraverse(scope, path)) {
                    observeObject(value, path, depth);
                } else {
                    observeOpaqueDescendants(value, depth);
                }
                return;
            }
            if (value.isArray()) {
                accumulator.observe(ContractResponse.JsonType.ARRAY);
                if (pathBoundary.shouldTraverse(scope, path)) {
                    observeArray(value, path, depth);
                }
                return;
            }
            accumulator.observe(jsonType(value));
        }

        /**
         * Conta a árvore de um objeto-mapa opaco sem materializar nomes, paths ou fields. Mesmo
         * conteúdo fora do contrato continua sujeito aos tetos vinculados de nós e profundidade.
         */
        private void observeOpaqueDescendants(final JsonNode container, final int depth) {
            final Iterator<JsonNode> children = container.elements();
            while (children.hasNext()) {
                final JsonNode child = children.next();
                final int childDepth = Math.incrementExact(depth);
                observeNodeBudget();
                validateDepth(childDepth);
                if (child.isContainerNode()) {
                    observeOpaqueDescendants(child, childDepth);
                } else if (!child.isNull()) {
                    jsonType(child);
                }
            }
        }

        private void observeShallow(final FieldAccumulator accumulator, final JsonNode value) {
            observeNodeBudget();
            if (value == null || value.isNull()) {
                accumulator.observeNull();
            } else if (value.isObject()) {
                accumulator.observe(ContractResponse.JsonType.OBJECT);
            } else if (value.isArray()) {
                accumulator.observe(ContractResponse.JsonType.ARRAY);
            } else {
                accumulator.observe(jsonType(value));
            }
        }

        private void observeArray(final JsonNode array, final String path, final int depth) {
            parentOccurrences.merge(path, 1, Math::addExact);
            if (array.isEmpty()) {
                return;
            }
            final String elementPath = ContractText.appendArrayElement(path);
            final FieldAccumulator elements = field(elementPath, path);
            elements.markPresent();
            for (final JsonNode element : array) {
                observeValue(elements, element, elementPath, depth + 1);
            }
        }

        private FieldAccumulator field(final String path, final String parentPath) {
            pathBoundary.requireAllowed(scope, path);
            final FieldAccumulator existing = fields.get(path);
            if (existing != null) {
                if (!existing.parentPath().equals(parentPath)) {
                    throw new IllegalArgumentException("O shape observado contém path ambíguo.");
                }
                return existing;
            }
            budget.observePath();
            final FieldAccumulator created = new FieldAccumulator(scope, path, parentPath);
            fields.put(path, created);
            return created;
        }

        private List<ContractResponse.Field> fields() {
            final List<ContractResponse.Field> profiled = new ArrayList<>(fields.size());
            for (final FieldAccumulator accumulator : fields.values()) {
                final int parents = parentOccurrences.getOrDefault(accumulator.parentPath(), 0);
                if (parents == 0 || accumulator.presentParents() > parents) {
                    throw new IllegalArgumentException(
                            "O shape observado possui presença inválida.");
                }
                profiled.add(accumulator.toField(parents));
            }
            return List.copyOf(profiled);
        }

        private void observeNodeBudget() {
            budget.observeNode();
        }

        private void validateDepth(final int depth) {
            if (depth > limits.maximumDepth()) {
                throw new IllegalArgumentException("A resposta excede o limite de profundidade.");
            }
        }

        private static ContractResponse.JsonType jsonType(final JsonNode value) {
            if (value.isTextual()) {
                return ContractResponse.JsonType.STRING;
            }
            if (value.isIntegralNumber()) {
                return ContractResponse.JsonType.INTEGER;
            }
            if (value.isFloatingPointNumber() || value.isBigDecimal()) {
                return ContractResponse.JsonType.NUMBER;
            }
            if (value.isBoolean()) {
                return ContractResponse.JsonType.BOOLEAN;
            }
            throw new IllegalArgumentException("A resposta contém um tipo JSON não suportado.");
        }
    }

    private static final class FieldAccumulator {

        private final ContractResponse.FieldScope scope;
        private final String path;
        private final String parentPath;
        private final EnumSet<ContractResponse.JsonType> jsonTypes =
                EnumSet.noneOf(ContractResponse.JsonType.class);
        private int presentParents;
        private boolean nullable;

        private FieldAccumulator(
                final ContractResponse.FieldScope scope,
                final String path,
                final String parentPath) {
            this.scope = scope;
            this.path = path;
            this.parentPath = parentPath;
        }

        private String parentPath() {
            return parentPath;
        }

        private int presentParents() {
            return presentParents;
        }

        private void markPresent() {
            presentParents = Math.incrementExact(presentParents);
        }

        private void observeNull() {
            nullable = true;
        }

        private void observe(final ContractResponse.JsonType jsonType) {
            jsonTypes.add(jsonType);
        }

        private ContractResponse.Field toField(final int parentCount) {
            final List<ContractResponse.JsonType> types = jsonTypes.stream().sorted().toList();
            return new ContractResponse.Field(
                    scope,
                    path,
                    cardinality(types),
                    presentParents == parentCount
                            ? ContractResponse.Presence.REQUIRED
                            : ContractResponse.Presence.OPTIONAL,
                    nullable,
                    types);
        }

        private static ContractResponse.Cardinality cardinality(
                final List<ContractResponse.JsonType> types) {
            if (types.isEmpty()) {
                return ContractResponse.Cardinality.UNOBSERVED;
            }
            final boolean containsObject = types.contains(ContractResponse.JsonType.OBJECT);
            final boolean containsArray = types.contains(ContractResponse.JsonType.ARRAY);
            final boolean containsScalar =
                    types.stream().anyMatch(ContractResponse.JsonType::isScalar);
            final int shapes =
                    (containsObject ? 1 : 0) + (containsArray ? 1 : 0) + (containsScalar ? 1 : 0);
            if (shapes > 1) {
                return ContractResponse.Cardinality.MIXED;
            }
            if (containsObject) {
                return ContractResponse.Cardinality.OBJECT;
            }
            if (containsArray) {
                return ContractResponse.Cardinality.ARRAY;
            }
            return ContractResponse.Cardinality.SCALAR;
        }
    }

    private static final class ObservationBudget {

        private final ContractObservationLimits limits;
        private int nodes;
        private int paths;

        private ObservationBudget(final ContractObservationLimits limits) {
            this.limits = limits;
        }

        private void observeNode() {
            nodes = Math.incrementExact(nodes);
            if (nodes > limits.maximumNodes()) {
                throw new IllegalArgumentException("A resposta excede o limite de nós observados.");
            }
        }

        private void observePath() {
            paths = Math.incrementExact(paths);
            if (paths > limits.maximumPaths()) {
                throw new IllegalArgumentException(
                        "A resposta excede o limite de paths observados.");
            }
        }
    }
}
