package br.com.esl.etl.v2.plataforma.qualificacao;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The six dimensions are output nodes, never six extra units or a second construction universe. */
public final class QualificationTopology {
    public enum Kind {
        INPUT,
        DIMENSION_OUTPUT,
        FACT,
        OUTPUT
    }

    public record Node(String id, Kind kind, List<String> dependencies) {
        public Node {
            if (dependencies == null || dependencies.size() > 35) {
                throw new IllegalArgumentException("QUAL_TOPOLOGY_DEPENDENCY_BOUND");
            }
            dependencies = List.copyOf(dependencies);
        }
    }

    private static final List<Node> NODES = create();

    private QualificationTopology() {}

    public static List<Node> nodes() {
        return NODES;
    }

    private static List<Node> create() {
        final var result = new ArrayList<Node>();
        for (final var input :
                List.of(
                        "CAP", "FAT", "INV", "SIN", "LOC", "FRE", "MAN", "COL", "COT", "USUARIO",
                        "RASTER")) {
            result.add(new Node("INPUT_" + input, Kind.INPUT, List.of()));
        }
        for (final var output :
                List.of("SQL-14", "SQL-15", "SQL-16", "SQL-17", "SQL-18", "SQL-19")) {
            result.add(
                    new Node(
                            output,
                            Kind.DIMENSION_OUTPUT,
                            output.equals("SQL-19") ? List.of("INPUT_USUARIO") : List.of()));
        }
        result.add(
                new Node(
                        "MAT01",
                        Kind.FACT,
                        List.of("INPUT_FRE", "INPUT_LOC", "INPUT_INV", "SQL-14", "SQL-15")));
        result.add(
                new Node(
                        "MAT02",
                        Kind.FACT,
                        List.of("INPUT_FRE", "INPUT_INV", "INPUT_MAN", "SQL-14")));
        result.add(
                new Node(
                        "MAT03", Kind.FACT, List.of("INPUT_FRE", "INPUT_LOC", "SQL-14", "SQL-15")));
        result.add(
                new Node(
                        "MAT04", Kind.FACT, List.of("INPUT_FAT", "INPUT_FRE", "SQL-14", "SQL-15")));
        result.add(
                new Node(
                        "MAT05",
                        Kind.FACT,
                        List.of(
                                "INPUT_MAN",
                                "INPUT_FRE",
                                "INPUT_COL",
                                "SQL-14",
                                "SQL-16",
                                "SQL-17")));
        result.add(new Node("SQL-01", Kind.OUTPUT, List.of("INPUT_FAT", "SQL-14", "SQL-15")));
        result.add(new Node("SQL-02", Kind.OUTPUT, List.of("MAT01")));
        result.add(
                new Node(
                        "SQL-03",
                        Kind.OUTPUT,
                        List.of("INPUT_COL", "INPUT_MAN", "SQL-14", "SQL-19")));
        result.add(new Node("SQL-04", Kind.OUTPUT, List.of("INPUT_COL", "SQL-14")));
        result.add(new Node("SQL-05", Kind.OUTPUT, List.of("INPUT_COT", "SQL-14")));
        result.add(new Node("SQL-06", Kind.OUTPUT, List.of("INPUT_CAP", "SQL-14", "SQL-18")));
        result.add(new Node("SQL-07", Kind.OUTPUT, List.of("INPUT_LOC", "SQL-14")));
        result.add(new Node("SQL-08", Kind.OUTPUT, List.of("MAT05")));
        result.add(new Node("SQL-09", Kind.OUTPUT, List.of("MAT05")));
        // Monitoring describes failures too; it must independently compare their exact states.
        result.add(new Node("SQL-10", Kind.OUTPUT, List.of()));
        result.add(new Node("SQL-11", Kind.OUTPUT, List.of("INPUT_INV", "SQL-14")));
        result.add(new Node("SQL-12", Kind.OUTPUT, List.of("INPUT_SIN", "SQL-14", "SQL-16")));
        result.add(new Node("SQL-13", Kind.OUTPUT, List.of("INPUT_RASTER")));
        final var seen = new java.util.HashSet<String>();
        for (final var node : result) {
            if (!seen.containsAll(node.dependencies()) || !seen.add(node.id())) {
                throw new ExceptionInInitializerError("QUAL_TOPOLOGY_DAG");
            }
        }
        if (seen.size() != 35) {
            throw new ExceptionInInitializerError("QUAL_TOPOLOGY_UNIVERSE");
        }
        return List.copyOf(result);
    }

    public static Map<String, QualificationGate> evaluate(
            final Map<String, QualificationGate> evidence) {
        Objects.requireNonNull(evidence);
        if (evidence.keySet().stream()
                .anyMatch(id -> NODES.stream().noneMatch(node -> node.id().equals(id)))) {
            throw new IllegalArgumentException("QUAL_TOPOLOGY_UNKNOWN_SCOPE");
        }
        final var result = new LinkedHashMap<String, QualificationGate>();
        for (final var node : NODES) {
            final var own = evidence.get(node.id());
            if (own != null && !node.id().equals(own.scope())) {
                throw new IllegalArgumentException("QUAL_TOPOLOGY_SCOPE_BINDING");
            }
            if (!node.dependencies().isEmpty()) {
                final var aggregate =
                        QualificationGate.aggregate(node.id(), node.dependencies(), result);
                if (aggregate.state() != QualificationGate.State.PASS_LOCAL) {
                    result.put(node.id(), aggregate);
                    continue;
                }
            }
            result.put(
                    node.id(),
                    own == null
                            ? new QualificationGate(
                                    node.id(),
                                    QualificationGate.State.BLOCKED_DEPENDENCY,
                                    "SCOPE_EVIDENCE_MISSING",
                                    "CONTRACT")
                            : own);
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    public static QualificationGate selected(
            final List<AnalyticSqlContract> required, final Map<String, QualificationGate> gates) {
        if (required.isEmpty()) {
            throw new IllegalArgumentException("QUAL_REQUIRED_OUTPUTS_EMPTY");
        }
        if (required.size() == 19 && new java.util.HashSet<>(required).size() == 19) {
            return QualificationGate.aggregate(
                    "COMPLETE_WAVE", NODES.stream().map(Node::id).toList(), gates);
        }
        return QualificationGate.aggregate(
                "SELECTED_OUTPUTS", required.stream().map(AnalyticSqlContract::id).toList(), gates);
    }
}
