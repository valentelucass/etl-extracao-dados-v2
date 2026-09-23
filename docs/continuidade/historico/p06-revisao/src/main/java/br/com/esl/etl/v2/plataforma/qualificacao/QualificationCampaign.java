package br.com.esl.etl.v2.plataforma.qualificacao;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Closed ordered DAG. It contains data and enum selectors, never a command, SQL or class name. */
public record QualificationCampaign(
        String id, Pins pins, int roots, int pageSize, int maximumSeconds, List<Case> cases) {
    public enum Action {
        SCENARIO,
        ARTIFACT,
        REPLAY,
        RECOMPOSE,
        QUERY,
        ABSENCE,
        VARIANTS,
        TEMPORAL,
        DEGRADATION,
        CONCURRENCY
    }

    public enum Fault {
        NONE,
        RASTER_INCOMPLETE,
        MANIFEST_FLEET_MISSING,
        FINANCIAL_REFERENCE_MISSING,
        COLLECTION_SNAPSHOT_INVALID
    }

    public enum Barrier {
        NONE,
        BEFORE_SQL,
        DURING_CAPTURE,
        AFTER_PREPARATION,
        BEFORE_RECEIPT
    }

    public record Pins(
            String revision,
            String jar,
            String schema,
            String contracts,
            String fixture,
            String oracle) {}

    public record Case(
            String id,
            String wave,
            List<String> dependencies,
            Action action,
            ExecutionMode mode,
            Fault fault,
            Barrier barrier,
            List<AnalyticSqlContract> outputs,
            Instant tick,
            LocalDate start,
            LocalDate endExclusive,
            ZoneId zone,
            int lookbackSeconds,
            int deadlineSeconds,
            int maximumCatchUp,
            List<LocalDate> blackouts,
            QualificationGate.State expected) {
        public Case {
            if (dependencies == null
                    || dependencies.size() > 35
                    || outputs == null
                    || outputs.isEmpty()
                    || outputs.size() > 19
                    || blackouts == null
                    || blackouts.size() > 8) {
                throw new IllegalArgumentException("QUAL_CAMPAIGN_CASE_BOUND");
            }
            dependencies = List.copyOf(dependencies);
            outputs = List.copyOf(outputs);
            blackouts = List.copyOf(blackouts);
        }
    }

    public QualificationCampaign {
        if (cases == null || cases.isEmpty() || cases.size() > 64) {
            throw new IllegalArgumentException("QUAL_CAMPAIGN_CASES_BOUND");
        }
        cases = List.copyOf(cases);
    }

    public static QualificationCampaign read(final Path path) throws IOException {
        return parse(QualificationJson.read(path, 131072));
    }

    public static QualificationCampaign parse(final JsonNode json) {
        QualificationJson.fields(
                json, "version", "id", "pins", "roots", "pageSize", "maximumSeconds", "cases");
        if (!"qualification-campaign-v1".equals(QualificationJson.text(json, "version", 40))) {
            throw new IllegalArgumentException("QUAL_CAMPAIGN_VERSION");
        }
        final var pin = json.path("pins");
        QualificationJson.fields(
                pin, "revision", "jar", "schema", "contracts", "fixture", "oracle");
        final var pins =
                new Pins(
                        QualificationJson.digest(pin, "revision"),
                        QualificationJson.digest(pin, "jar"),
                        QualificationJson.digest(pin, "schema"),
                        QualificationJson.digest(pin, "contracts"),
                        QualificationJson.digest(pin, "fixture"),
                        QualificationJson.digest(pin, "oracle"));
        QualificationJson.array(json.path("cases"), 1, 64);
        final var cases = new ArrayList<Case>();
        final Set<String> seen = new HashSet<>();
        for (final var node : json.path("cases")) {
            final var item = readCase(node);
            if (seen.contains(item.id())
                    || !seen.containsAll(item.dependencies())
                    || new HashSet<>(item.dependencies()).size() != item.dependencies().size()) {
                throw new IllegalArgumentException("QUAL_CAMPAIGN_DAG");
            }
            seen.add(item.id());
            cases.add(item);
        }
        return new QualificationCampaign(
                identifier(json, "id"),
                pins,
                QualificationJson.number(json, "roots", 2, 32),
                QualificationJson.number(json, "pageSize", 1, 16),
                QualificationJson.number(json, "maximumSeconds", 60, 3600),
                cases);
    }

    private static Case readCase(final JsonNode node) {
        QualificationJson.fields(
                node,
                "id",
                "wave",
                "dependsOn",
                "action",
                "mode",
                "fault",
                "barrier",
                "outputs",
                "tick",
                "start",
                "endExclusive",
                "zone",
                "lookbackSeconds",
                "deadlineSeconds",
                "maximumCatchUp",
                "blackouts",
                "expected");
        final var action = Action.valueOf(QualificationJson.text(node, "action", 32));
        final var mode = ExecutionMode.valueOf(QualificationJson.text(node, "mode", 32));
        final var fault = Fault.valueOf(QualificationJson.text(node, "fault", 40));
        final var barrier = Barrier.valueOf(QualificationJson.text(node, "barrier", 32));
        final var expected =
                QualificationGate.State.valueOf(QualificationJson.text(node, "expected", 32));
        if (mode == ExecutionMode.SWEEP || action == Action.DEGRADATION != (fault != Fault.NONE)) {
            throw new IllegalArgumentException("QUAL_CAMPAIGN_MODE");
        }
        if ((action == Action.REPLAY) != (mode == ExecutionMode.REPLAY)
                || action == Action.RECOMPOSE && mode != ExecutionMode.BACKFILL
                || (action == Action.ABSENCE
                                || action == Action.ARTIFACT
                                || action == Action.VARIANTS
                                || action == Action.TEMPORAL
                                || action == Action.DEGRADATION
                                || action == Action.CONCURRENCY)
                        && mode != ExecutionMode.BOOTSTRAP) {
            throw new IllegalArgumentException("QUAL_CAMPAIGN_ACTION_MODE");
        }
        final var start = LocalDate.parse(QualificationJson.text(node, "start", 10));
        final var end = LocalDate.parse(QualificationJson.text(node, "endExclusive", 10));
        if (!start.isBefore(end)
                || start.plusDays(3).isBefore(end)
                || start.getYear() < 2000
                || end.getYear() > 2040) {
            throw new IllegalArgumentException("QUAL_CAMPAIGN_WINDOW");
        }
        final var dependencies = strings(node.path("dependsOn"), 0, 35);
        final var outputNames = strings(node.path("outputs"), 1, 19);
        final var outputs =
                outputNames.stream()
                        .map(value -> AnalyticSqlContract.valueOf(value.replace('-', '_')))
                        .toList();
        if (new HashSet<>(outputs).size() != outputs.size()) {
            throw new IllegalArgumentException("QUAL_CAMPAIGN_OUTPUT_DUPLICATE");
        }
        final var blackoutNames = strings(node.path("blackouts"), 0, 8);
        final var blackouts = blackoutNames.stream().map(LocalDate::parse).toList();
        return new Case(
                identifier(node, "id"),
                identifier(node, "wave"),
                dependencies,
                action,
                mode,
                fault,
                barrier,
                outputs,
                Instant.parse(QualificationJson.text(node, "tick", 30)),
                start,
                end,
                ZoneId.of(QualificationJson.text(node, "zone", 64)),
                QualificationJson.number(node, "lookbackSeconds", 0, 259200),
                QualificationJson.number(node, "deadlineSeconds", 1, 2678400),
                QualificationJson.number(node, "maximumCatchUp", 1, 3),
                blackouts,
                expected);
    }

    private static List<String> strings(final JsonNode array, final int min, final int max) {
        QualificationJson.array(array, min, max);
        final var result = new ArrayList<String>();
        for (final var item : array) {
            if (!item.isTextual() || !item.textValue().matches("[A-Za-z0-9_-]{1,40}")) {
                throw new IllegalArgumentException("QUAL_CAMPAIGN_IDENTIFIER");
            }
            result.add(item.textValue());
        }
        return result;
    }

    private static String identifier(final JsonNode node, final String key) {
        final var value = QualificationJson.text(node, key, 40);
        if (!value.matches("[a-z][a-z0-9-]{0,39}")) {
            throw new IllegalArgumentException("QUAL_CAMPAIGN_IDENTIFIER");
        }
        return value;
    }
}
