package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.IntegralArtifactFixtures.array;
import static br.com.esl.etl.v2.bootstrap.IntegralArtifactFixtures.object;
import static br.com.esl.etl.v2.bootstrap.IntegralArtifactFixtures.pin;
import static br.com.esl.etl.v2.bootstrap.IntegralArtifactFixtures.resource;
import static br.com.esl.etl.v2.bootstrap.IntegralArtifactFixtures.save;

import br.com.esl.etl.v2.bootstrap.IntegralArtifactFixtures.Spec;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlValue;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationOracles;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationWireOracle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Independent literal/rule oracle authoring. Reads frozen expectations, never the generated input
 * or SQL results.
 */
final class IntegralArtifactOracleFixtures {
    private IntegralArtifactOracleFixtures() {}

    static void write(final Path folder, final Spec spec) throws Exception {
        wire(folder, spec);
        facts(folder, spec);
        outputs(folder, spec);
        final var manifest =
                object().put("version", "local-artifact-oracles-v2")
                        .put("origin", "INDEPENDENT_SYNTHETIC_RULES_V1")
                        .put("inputSha256", QualificationJson.sha256(folder.resolve("input.json")))
                        .put("schemaSha256", LocalArtifactScenario.schemaFingerprint())
                        .put("runtimeSha256", LocalArtifactScenario.runtimeFingerprint());
        manifest.set("outputs", pin(folder, folder.resolve("outputs/manifest.json")));
        manifest.set("wire", pin(folder, folder.resolve("wire/manifest.json")));
        manifest.set("facts", pin(folder, folder.resolve("facts/manifest.json")));
        save(folder.resolve("oracle.json"), manifest);
    }

    private static void wire(final Path folder, final Spec spec) throws Exception {
        final var oracle = new QualificationWireOracle();
        final var manifest =
                object().put("version", "local-wire-expectations-v2")
                        .put("origin", "INDEPENDENT_SYNTHETIC_RULES_V1");
        final var descriptors = manifest.putArray("rows");
        for (final String entity :
                List.of("CAP", "FAT", "INV", "SIN", "FRE", "LOC", "MAN", "COL", "COT")) {
            final boolean expansion = Set.of("CAP", "FAT", "INV", "SIN").contains(entity);
            for (int root = 1; root <= spec.roots(); root++) {
                for (int component = 1; component <= (expansion ? 2 : 1); component++) {
                    final var expected =
                            oracle.source(entity, root, component, spec.revision(), false);
                    // Expected keys are individually assigned; they do not use sourceIdentity or
                    // source pages.
                    switch (entity) {
                        case "CAP" ->
                                expected.put("ant_ils_sequence_code", spec.id("CAP", root))
                                        .put("value", spec.alternate() ? "277.25" : "163.75");
                        case "FAT" ->
                                expected.put("id", spec.id("FAT", root) * 10 + component)
                                        .put("corporation_sequence_number", spec.id("FAT", root))
                                        .put(
                                                "fit_ant_value",
                                                spec.alternate() ? "277.25" : "163.75");
                        case "INV" ->
                                expected.put("sequence_code", spec.id("INV", root))
                                        .put(
                                                "cnr_c_s_fit_corporation_sequence_number",
                                                spec.id("INV", root) * 10 + component)
                                        .put(
                                                "cnr_c_s_fit_invoices_value",
                                                spec.alternate() ? "277.25" : "163.75");
                        case "SIN" ->
                                expected.put("sequence_code", spec.id("SIN", root))
                                        .put(
                                                "icm_fis_fit_corporation_sequence_number",
                                                spec.id("SIN", root) * 10 + component)
                                        .put(
                                                "insurance_claim_total",
                                                spec.alternate() ? "277.25" : "163.75");
                        case "FRE" ->
                                expected.put("id", spec.id("FRE", root))
                                        .put("corporation_sequence_number", spec.id("FRESEQ", root))
                                        .put("total", freightExpected(spec))
                                        .put("synthetic_fixture", true)
                                        .put(
                                                "synthetic_pick_item",
                                                "pick-" + spec.tag() + "-" + root)
                                        .put("criado_em", spec.start() + "T10:00:00Z");
                        case "LOC" ->
                                expected.put("corporation_sequence_number", spec.id("LOC", root));
                        case "COL" ->
                                expected.put("id", spec.id("COL", root))
                                        .put("sequence_code", spec.id("COLSEQ", root))
                                        .put("synthetic_item_key", spec.id("ITEM", root));
                        case "COT" ->
                                expected.put("sequence_code", spec.id("COT", root))
                                        .put(
                                                "qoe_qes_total",
                                                spec.alternate() ? "291.2500" : "176.5000");
                        case "MAN" ->
                                expected.put("sequence_code", spec.id("MAN", root))
                                        .put("manifest_freights_total", freightExpected(spec))
                                        .put(
                                                "mft_pfs_pck_sequence_code",
                                                spec.id("COLSEQ", spec.collectionForManifest(root)))
                                        .put(
                                                "mft_mfs_key",
                                                "1".repeat(36)
                                                        + String.format(
                                                                Locale.ROOT,
                                                                "%08d",
                                                                spec.id("MAN", root)));
                        default -> throw new IllegalArgumentException(entity);
                    }
                    if (entity.equals("FRE") && spec.advanced()) {
                        expected.put("criado_em", spec.start() + "T13:00:00Z")
                                .put("cte_created_at", spec.start() + "T14:00:00.123456789Z");
                    }
                    IntegralArtifactFixtures.shiftDates(expected, spec);
                    if (spec.decimalBoundaries() && expansion) {
                        // Independent wire literals: never obtained from the input or mapper.
                        expected.put(
                                switch (entity) {
                                    case "CAP" -> "interest_value";
                                    case "FAT" -> "third_party_ctes_value";
                                    case "INV" -> "cnr_c_s_fit_total_cubic_volume";
                                    default -> "customer_debits_subtotal";
                                },
                                new java.math.BigDecimal(
                                        spec.alternate() ? "0.00000002" : "0.00000001"));
                    }
                    final var envelope = object();
                    envelope.set("wire", expected);
                    final var lateral = envelope.putObject("lateral");
                    if (entity.equals("COL")) {
                        lateral.setAll(
                                (ObjectNode)
                                        resource(
                                                        "/analytic-laboratory/collection-supplement.synthetic.json")
                                                .path("data"));
                        ((ObjectNode) lateral.path("pickAddress"))
                                .put(
                                        "line2",
                                        "COMPLEMENTO EXPLÍCITO "
                                                + spec.tag().toUpperCase(Locale.ROOT));
                        IntegralArtifactFixtures.shiftDates(lateral, spec);
                        envelope.put(
                                "manifestKey",
                                "INTEGER:" + spec.id("MAN", spec.manifestForCollection(root)));
                    } else {
                        envelope.putNull("manifestKey");
                    }
                    envelope.put("sourceRows", Set.of("FRE", "LOC").contains(entity) ? 2 : 3);
                    final Path file =
                            folder.resolve(
                                    "wire/"
                                            + entity.toLowerCase(Locale.ROOT)
                                            + "-"
                                            + root
                                            + "-"
                                            + component
                                            + ".json");
                    save(file, envelope);
                    descriptors
                            .addObject()
                            .put("entity", entity)
                            .put("root", root)
                            .put("component", component)
                            .put("revision", spec.revision())
                            .put("correction", false)
                            .put("rootType", expansion ? "STRING" : "INTEGER")
                            .put(
                                    "rootKey",
                                    expansion
                                            ? spec.root(entity, root)
                                            : Long.toString(spec.id(entity, root)))
                            .put("partType", "STRING")
                            .put("partKey", expansion ? spec.part(entity, root) : "unused-part")
                            .put("componentType", "STRING")
                            .put("componentKey", spec.component(component))
                            .put("file", file.getFileName().toString())
                            .put("sha256", QualificationJson.sha256(file));
                }
            }
        }
        save(folder.resolve("wire/manifest.json"), manifest);
    }

    private static String freightExpected(final Spec spec) {
        // Manual next-revision example: original total + 23.125; independently declared here.
        if (spec.advanced()) {
            return spec.alternate() ? "210.62500000" : "166.37500000";
        }
        return spec.alternate() ? "187.50000000" : "143.25000000";
    }

    private static void facts(final Path folder, final Spec spec) throws Exception {
        // Frozen tuple expectations, not sums or identities read from any materialization.
        final var manifest =
                object().put("version", "local-fact-oracles-v1")
                        .put("origin", "INDEPENDENT_SYNTHETIC_RULES_V1");
        final var entries = manifest.putArray("facts");
        for (final String fact : List.of("MAT01", "MAT02", "MAT03", "MAT04", "MAT05")) {
            final var rows = array();
            if (fact.equals("MAT02")) {
                rows.addObject()
                        .put("date", spec.start().toString())
                        .put("branch", "synthetic-branch-a")
                        .put("issued", spec.roots())
                        .put("unloaded", 0)
                        .put("scanned", spec.roots())
                        .put("incomplete", 0)
                        .put("total", spec.roots())
                        .put("percentage", "100.00000000");
                rows.addObject()
                        .put("date", spec.start().toString())
                        .put("branch", "synthetic-branch-b")
                        .put("issued", 0)
                        .put("unloaded", spec.roots())
                        .put("scanned", 0)
                        .put("incomplete", 0)
                        .put("total", spec.roots())
                        .put("percentage", "0.00000000");
            } else {
                for (int root = 1; root <= spec.roots(); root++) {
                    final String amount =
                            fact.equals("MAT04")
                                    ? spec.alternate() ? "277.25" : "163.75"
                                    : freightExpected(spec);
                    final String sourceKey =
                            fact.equals("MAT04")
                                    ? spec.root("FAT", root)
                                    : "INTEGER:"
                                            + spec.id(fact.equals("MAT05") ? "MAN" : "FRE", root);
                    final var row =
                            rows.addObject().put("sourceKey", sourceKey).put("amount", amount);
                    if (fact.equals("MAT01")) {
                        row.put("indicator", "PE");
                        rows.add(row.deepCopy().put("indicator", "CB"));
                    }
                    if (fact.equals("MAT05")) {
                        row.put("date", spec.start().toString());
                    }
                }
            }
            final Path file = folder.resolve("facts/" + fact.toLowerCase(Locale.ROOT) + ".json");
            save(file, rows);
            entries.add(
                    pin(file.getParent(), file)
                            .put("id", fact)
                            .put(
                                    "grain",
                                    fact.equals("MAT01")
                                            ? "sourceKey,indicator"
                                            : fact.equals("MAT02") ? "date,branch" : "sourceKey"));
        }
        save(folder.resolve("facts/manifest.json"), manifest);
    }

    private static void outputs(final Path folder, final Spec spec) throws Exception {
        final var frozen = resource("/qualification-laboratory/outputs.synthetic.json");
        final var literals = QualificationOracles.parse(frozen);
        final UUID technical = new UUID(0, 0);
        final var context =
                new QualificationOracles.Context(
                        technical,
                        spec.roots(),
                        spec.revision(),
                        false,
                        0,
                        null,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        new QualificationOracles.Evidence() {
                            @Override
                            public List<AnalyticSqlValue> monitor(final long row) {
                                throw new IllegalStateException("NO_SQL_CONTEXT_IN_ORACLE_AUTHOR");
                            }

                            @Override
                            public int monitorCount() {
                                return 20;
                            }

                            @Override
                            public boolean lineage(
                                    final String entity,
                                    final int root,
                                    final int component,
                                    final JsonNode actual) {
                                throw new IllegalStateException();
                            }

                            @Override
                            public String locationHash(final int root) {
                                try {
                                    return locationHashExpected(spec, root);
                                } catch (final Exception error) {
                                    throw new IllegalStateException(error);
                                }
                            }

                            @Override
                            public String componentIdentity(
                                    final String entity, final int root, final int component) {
                                return "STRING:"
                                        + spec.root(entity, root)
                                        + "/STRING:"
                                        + spec.part(entity, root)
                                        + "/STRING:"
                                        + spec.component(component);
                            }
                        });
        final var manifest =
                object().put("version", "local-sql-oracles-v1")
                        .put("origin", "INDEPENDENT_SYNTHETIC_RULES_V1");
        manifest.putObject("facts")
                .put("MAT01", 2 * spec.roots())
                .put("MAT02", 3 * spec.roots())
                .put("MAT03", spec.roots())
                .put("MAT04", spec.roots())
                .put("MAT05", spec.roots());
        monitor(manifest.putArray("monitor"), spec);
        final var descriptors = manifest.putArray("outputs");
        for (final var contract : AnalyticSqlContract.values()) {
            final var descriptor = descriptors.addObject().put("id", contract.id());
            if (contract == AnalyticSqlContract.SQL_10) {
                descriptor.put("rows", 20).putArray("batches");
                continue;
            }
            final var expected = literals.expected(contract, context);
            final var specification = frozen.path("contracts").path(contract.ordinal());
            final boolean componentRows =
                    Set.of(
                                    AnalyticSqlContract.SQL_01,
                                    AnalyticSqlContract.SQL_06,
                                    AnalyticSqlContract.SQL_11,
                                    AnalyticSqlContract.SQL_12)
                            .contains(contract);
            final var rows = array();
            final var userOrder =
                    java.util.stream.IntStream.rangeClosed(1, spec.roots())
                            .boxed()
                            .sorted(java.util.Comparator.comparing(spec::user))
                            .toList();
            for (int ordinal = 0; ordinal < expected.count(); ordinal++) {
                final int root =
                        contract == AnalyticSqlContract.SQL_19
                                ? userOrder.get(ordinal)
                                : componentRows ? ordinal / 2 + 1 : ordinal + 1;
                final int component = componentRows ? ordinal % 2 + 1 : 1;
                final var tuple = rows.addArray();
                final var cells = expected.at(ordinal);
                for (int index = 0; index < cells.size(); index++) {
                    final var column = AnalyticSqlCatalog.columns(contract).get(index);
                    final var rule = specification.path("columns").path(index);
                    JsonNode value = json(cells.get(index));
                    if (value.isTextual()) {
                        value =
                                JsonNodeFactory.instance.textNode(
                                        IntegralArtifactFixtures.shifted(value.textValue(), spec));
                    }
                    if (rule.path("rule").asText().equals("OBSERVED_TIME")) {
                        value = object().put("kind", "observedTime");
                    }
                    if (rule.path("rule").asText().equals("STRUCTURED_LINEAGE")) {
                        value =
                                object().put("kind", "lineage")
                                        .put("entity", rule.path("value").asText())
                                        .put("root", root)
                                        .put("component", component);
                    }
                    if (rule.path("rule").asText().equals("CONTEXT")) {
                        value =
                                contextual(
                                        contract,
                                        rule.path("value").asText(),
                                        value,
                                        root,
                                        component,
                                        spec);
                    }
                    if (value.isTextual()
                            && value.textValue().startsWith(technical.toString() + "/")) {
                        value =
                                object().put("kind", "runKey")
                                        .put("suffix", value.textValue().substring(36));
                    }
                    value = changes(contract, column.name(), value, root, spec);
                    tuple.add(value);
                }
            }
            descriptor
                    .put("rows", rows.size())
                    .set(
                            "batches",
                            IntegralArtifactFixtures.saveBatches(
                                    folder.resolve("outputs"),
                                    contract.id().toLowerCase(Locale.ROOT),
                                    rows));
        }
        save(folder.resolve("outputs/manifest.json"), manifest);
    }

    private static JsonNode contextual(
            final AnalyticSqlContract contract,
            final String selector,
            final JsonNode initial,
            final int root,
            final int component,
            final Spec spec) {
        final long value;
        switch (selector) {
            case "COMPONENT_ID" -> {
                final String family =
                        switch (contract) {
                            case SQL_01 -> "FAT";
                            case SQL_06 -> "CAP";
                            case SQL_11 -> "INV";
                            case SQL_12 -> "SIN";
                            default -> throw new IllegalArgumentException("COMPONENT_CONTRACT");
                        };
                return object().put("kind", "runKey")
                        .put(
                                "suffix",
                                "/STRING:"
                                        + spec.root(family, root)
                                        + "/STRING:"
                                        + spec.part(family, root)
                                        + "/STRING:"
                                        + spec.component(component));
            }
            case "FREIGHT_ID" -> value = spec.id("FRE", root);
            case "FREIGHT_SEQUENCE" ->
                    value =
                            spec.id(
                                    contract == AnalyticSqlContract.SQL_07 ? "LOC" : "FRESEQ",
                                    root);
            case "COLLECTION_ID" -> value = spec.id("COL", root);
            case "COLLECTION_NUMBER" ->
                    value =
                            spec.id(
                                    "COLSEQ",
                                    contract == AnalyticSqlContract.SQL_08
                                                    || contract == AnalyticSqlContract.SQL_09
                                            ? spec.collectionForManifest(root)
                                            : root);
            case "QUOTE_SEQUENCE" -> value = spec.id("COT", root);
            case "RASTER_SEQUENCE" -> value = spec.id("RAS", root);
            case "ROOT" ->
                    value =
                            switch (contract) {
                                case SQL_03 -> spec.id("MAN", spec.manifestForCollection(root));
                                case SQL_06 -> spec.id("CAP", root);
                                case SQL_08, SQL_09 -> spec.id("MAN", root);
                                case SQL_11 -> spec.id("INV", root);
                                case SQL_12 -> spec.id("SIN", root);
                                default -> throw new IllegalArgumentException(selector);
                            };
            case "COMPONENT_SEQUENCE" ->
                    value =
                            spec.id(contract == AnalyticSqlContract.SQL_11 ? "INV" : "SIN", root)
                                            * 10
                                    + component;
            case "MANIFEST_ID" -> {
                return object().put("kind", "runKey")
                        .put("suffix", "/INTEGER:" + spec.id("MAN", root));
            }
            case "MDFE_KEY" -> {
                return JsonNodeFactory.instance.textNode(
                        "1".repeat(36) + String.format(Locale.ROOT, "%08d", spec.id("MAN", root)));
            }
            case "USER_ID" -> {
                return JsonNodeFactory.instance.textNode("STRING:" + spec.user(root));
            }
            case "USER_NAME" -> {
                return JsonNodeFactory.instance.textNode(spec.userName(root));
            }
            default -> {
                return initial;
            }
        }
        return initial.isIntegralNumber()
                ? JsonNodeFactory.instance.numberNode(value)
                : JsonNodeFactory.instance.textNode(Long.toString(value));
    }

    private static JsonNode changes(
            final AnalyticSqlContract contract,
            final String column,
            final JsonNode initial,
            final int root,
            final Spec spec) {
        String text = null;
        if (spec.decimalBoundaries()) {
            if (contract == AnalyticSqlContract.SQL_01 && column.equals("Terceiros/Valor CT-es")
                    || contract == AnalyticSqlContract.SQL_06 && column.equals("Juros")
                    || contract == AnalyticSqlContract.SQL_11 && column.equals("M³")
                    || contract == AnalyticSqlContract.SQL_12
                            && column.equals("valor a pagar ao cliente")) {
                text = spec.alternate() ? "0.00000002" : "0.00000001";
            }
            if (contract == AnalyticSqlContract.SQL_13
                    && column.equals("percentual_atraso_raster")) {
                text = spec.alternate() ? "87654321.87654321" : "12345678.12345678";
            }
        }
        if (contract == AnalyticSqlContract.SQL_01
                        && Set.of("Fatura/Valor", "Fatura/Valor Total").contains(column)
                || contract == AnalyticSqlContract.SQL_06 && column.equals("Valor")
                || contract == AnalyticSqlContract.SQL_11 && column.equals("Valor de NF")
                || contract == AnalyticSqlContract.SQL_12 && column.equals("Resultado final")) {
            text = spec.alternate() ? "277.25" : "163.75";
        }
        if (contract == AnalyticSqlContract.SQL_02 && column.equals("Valor Total do Serviço")
                || (contract == AnalyticSqlContract.SQL_08
                                || contract == AnalyticSqlContract.SQL_09)
                        && Set.of("Fretes/Total", "Coletas/Total", "Receita Total Transportada")
                                .contains(column)) {
            text = freightExpected(spec);
        }
        if (contract == AnalyticSqlContract.SQL_02
                && column.equals("data_referencia_faturamento")) {
            text = spec.start().plusDays(1).toString();
        }
        if (contract == AnalyticSqlContract.SQL_02 && column.equals("Criado em")) {
            text = spec.start() + (spec.advanced() ? "T10:00:00-03:00" : "T07:00:00-03:00");
        }
        if (contract == AnalyticSqlContract.SQL_02
                && column.equals("CT-e Criado em")
                && spec.advanced()) {
            text = spec.start() + "T11:00:00.1234567-03:00";
        }
        if (contract == AnalyticSqlContract.SQL_02 && column.equals("KM")) {
            text = spec.alternate() ? "29.375" : "16.875";
        }
        if (contract == AnalyticSqlContract.SQL_01 && column.equals("NFS-e/Série")) {
            text =
                    spec.fiscalTupleBoundaries()
                            ? "SERIE-TUPLA-" + (spec.alternate() ? "B" : "A") + "-" + root
                            : "SERIE-EXPLICITA-" + spec.tag().toUpperCase(Locale.ROOT);
        }
        if (contract == AnalyticSqlContract.SQL_03) {
            if (column.equals("Complemento")) {
                text = "COMPLEMENTO EXPLÍCITO " + spec.tag().toUpperCase(Locale.ROOT);
            }
            if (column.equals("Região Logística")) {
                text = "REGIAO_EXPLICITA_" + spec.tag().toUpperCase(Locale.ROOT);
            }
            if (column.equals("Usuario Cancel. Nome")) {
                text = spec.userName(spec.alternate() ? spec.roots() : 1);
            }
            if (column.equals("Usuario Exclusao Nome")) {
                text = spec.userName(1);
            }
        }
        if (contract == AnalyticSqlContract.SQL_05 && column.equals("Min. Frete/KG")) {
            text = spec.alternate() ? "2.17" : "1.53";
        }
        if (contract == AnalyticSqlContract.SQL_05 && column.equals("Valor frete")) {
            text = spec.alternate() ? "291.25" : "176.50";
        }
        if (contract == AnalyticSqlContract.SQL_13) {
            if (column.equals("placa_veiculo")) {
                text = spec.alternate() ? "SYN0009" : "SYN0008";
            }
            if (Set.of("TRANSIT TIME", "transit_time_texto").contains(column)) {
                text = spec.alternate() ? "02:05" : "01:37";
            }
        }
        return text == null ? initial : JsonNodeFactory.instance.textNode(text);
    }

    private static void monitor(final ArrayNode rows, final Spec spec) {
        for (final String entity : List.of("CAP", "FAT", "INV", "SIN")) {
            event(rows, entity, entity, "EXPANSION_CAPTURE", 3 * spec.roots(), "COMPLETE");
        }
        event(rows, "MAN", "manifestos", "RELATIONAL_CAPTURE", 3 * spec.roots(), "DEGRADED");
        event(rows, "COL", "coletas", "RELATIONAL_CAPTURE", 3 * spec.roots(), "DEGRADED");
        event(rows, "REL_FRE", "fretes", "RELATIONAL_CAPTURE", 2 * spec.roots(), "DEGRADED");
        event(rows, "FRETE", "FRETE", "DEPENDENCY_CAPTURE", 2 * spec.roots(), "DEGRADED");
        event(rows, "LOC", "LOC", "DEPENDENCY_CAPTURE", 2 * spec.roots(), "DEGRADED");
        event(rows, "USER", "USUARIO", "ATTACHED_RUNTIME", spec.roots(), "PUBLISHED");
        event(rows, "COT", "COT", "ATTACHED_RUNTIME", 3 * spec.roots(), "PUBLISHED");
        event(rows, "RAS", "RASTER", "RASTER_CAPTURE", 6 * spec.roots(), "APPLIED");
        event(rows, "MAT01", "MAT01", "ANALYTIC_MATERIALIZATION", 2 * spec.roots(), "COMPLETE");
        event(rows, "MAT02", "MAT02", "ANALYTIC_MATERIALIZATION", 3 * spec.roots(), "COMPLETE");
        event(rows, "MAT05", "MAT05", "ANALYTIC_MATERIALIZATION", spec.roots(), "COMPLETE");
        for (final String selector :
                List.of("MAT03", "MAT04", "PARTITION_INVOICE", "PARTITION_REVENUE")) {
            event(
                    rows,
                    selector,
                    selector.equals("PARTITION_INVOICE")
                            ? "MAT04"
                            : selector.equals("PARTITION_REVENUE") ? "MAT03" : selector,
                    "EXPANSION_MATERIALIZATION",
                    spec.roots(),
                    "COMPLETE");
        }
        event(
                rows,
                "SCENARIO",
                "SCENARIO",
                "ANALYTIC_SCENARIO",
                16 * spec.roots() + 27 + 20,
                "COMPLETE");
    }

    private static void event(
            final ArrayNode rows,
            final String selector,
            final String entity,
            final String family,
            final int count,
            final String state) {
        rows.addObject()
                .put("selector", selector)
                .put("entity", entity)
                .put("family", family)
                .put("rows", count)
                .put("state", state);
    }

    private static JsonNode json(final AnalyticSqlValue value) {
        if (value instanceof AnalyticSqlValue.Missing) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (value instanceof AnalyticSqlValue.IntegerValue integer) {
            return JsonNodeFactory.instance.numberNode(integer.value());
        }
        if (value instanceof AnalyticSqlValue.Flag flag) {
            return JsonNodeFactory.instance.booleanNode(flag.value());
        }
        final String text;
        if (value instanceof AnalyticSqlValue.Text v) {
            text = v.value();
        } else if (value instanceof AnalyticSqlValue.Decimal v) {
            text = v.value().toPlainString();
        } else if (value instanceof AnalyticSqlValue.Date v) {
            text = v.value().toString();
        } else if (value instanceof AnalyticSqlValue.Time v) {
            text = v.value().toString();
        } else if (value instanceof AnalyticSqlValue.CivilDateTime v) {
            text = v.value().toString();
        } else if (value instanceof AnalyticSqlValue.OffsetDateTimeValue v) {
            text = v.value().toString();
        } else if (value instanceof AnalyticSqlValue.Identifier v) {
            text = v.value().toString();
        } else {
            throw new IllegalArgumentException();
        }
        return JsonNodeFactory.instance.textNode(text);
    }

    private static String locationHashExpected(final Spec spec, final int root) throws Exception {
        final var frozen = resource("/qualification-laboratory/location-hashes.synthetic.json");
        final var original = new ArrayList<String>();
        final var tokens = new ArrayList<String>();
        for (final var token : frozen.path("example").path("tokens")) {
            original.add(token.textValue());
            tokens.add(
                    token.textValue()
                            .replace("600001", Long.toString(spec.id("LOC", root)))
                            .replace("2036-04-01", spec.start().toString()));
        }
        if (!QualificationJson.sha256(
                        String.join("|", original).getBytes(StandardCharsets.UTF_16LE))
                .equals(frozen.path("hashes").path(0).path("sha256").asText())) {
            throw new IllegalStateException("INDEPENDENT_HASH_EXAMPLE_DRIFT");
        }
        return QualificationJson.sha256(
                String.join("|", tokens).getBytes(StandardCharsets.UTF_16LE));
    }
}
