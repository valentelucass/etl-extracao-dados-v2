package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Authors eleven explicit input families before execution; no SQL, mapper or query feeds the
 * expected output.
 */
final class IntegralArtifactFixtures {
    private IntegralArtifactFixtures() {}

    record Spec(
            boolean alternate,
            int roots,
            boolean advanced,
            boolean decimalBoundaries,
            boolean fiscalTupleBoundaries) {
        Spec(
                final boolean alternate,
                final int roots,
                final boolean advanced,
                final boolean decimalBoundaries) {
            this(alternate, roots, advanced, decimalBoundaries, false);
        }

        Spec(final boolean alternate, final int roots, final boolean advanced) {
            this(alternate, roots, advanced, false);
        }

        Spec(final boolean alternate, final int roots) {
            this(alternate, roots, false);
        }

        String tag() {
            return alternate ? "b" : "a";
        }

        LocalDate start() {
            return LocalDate.parse(alternate ? "2039-02-05" : "2037-08-11");
        }

        LocalDate end() {
            return start().plusDays(3);
        }

        int revision() {
            return (alternate ? 5 : 3) + (advanced ? 1 : 0);
        }

        int pageSize() {
            return alternate ? 4 : 2;
        }

        String source() {
            return alternate ? "SYNTHETIC_CARRIER_B" : "SYNTHETIC_CARRIER_A";
        }

        String tenant() {
            return alternate ? "SYNTHETIC_EAST" : "SYNTHETIC_WEST";
        }

        long id(final String family, final int root) {
            final int lane =
                    switch (family) {
                        case "MAN" -> 1;
                        case "COL" -> 2;
                        case "COLSEQ" -> 3;
                        case "ITEM" -> 4;
                        case "FRE" -> 5;
                        case "FRESEQ" -> 6;
                        case "LOC" -> 7;
                        case "COT" -> 8;
                        case "RAS" -> 9;
                        case "CAP" -> 10;
                        case "FAT" -> 11;
                        case "INV" -> 12;
                        case "SIN" -> 13;
                        default -> throw new IllegalArgumentException(family);
                    };
            return (alternate ? 700000L : 100000L) + lane * 10000L + root * (alternate ? 43 : 31);
        }

        int collectionForManifest(final int root) {
            return alternate ? root % roots + 1 : roots + 1 - root;
        }

        int manifestForCollection(final int root) {
            return alternate ? (root + roots - 2) % roots + 1 : roots + 1 - root;
        }

        String root(final String family, final int root) {
            if (fiscalTupleBoundaries && family.equals("FAT")) {
                final int first = root % 2 == 0 ? root - 1 : root;
                return "FAT-"
                        + tag()
                        + "-"
                        + id(family, first)
                        + (root % 2 == 0 ? ":STRING:segment-" + tag() : "");
            }
            return family + "-" + tag() + "-" + id(family, root);
        }

        String part(final String family, final int root) {
            if (fiscalTupleBoundaries && family.equals("FAT")) {
                final int first = root % 2 == 0 ? root - 1 : root;
                return (root % 2 == 0 ? "" : "segment-" + tag() + ":STRING:")
                        + "part-"
                        + tag()
                        + "-"
                        + id(family, first);
            }
            return "part-" + tag() + "-" + id(family, root);
        }

        String component(final int component) {
            return "component-" + tag() + "-" + (component == 1 ? 17 : 93);
        }

        String user(final int root) {
            return "synthetic-person-" + tag() + "-" + (700 + root * 17);
        }

        String userName(final int root) {
            return "USUÁRIO EXPLÍCITO " + tag().toUpperCase(Locale.ROOT) + " " + root;
        }

        String freightAmount() {
            if (advanced) {
                return alternate ? "210.62500000" : "166.37500000";
            }
            return alternate ? "187.50000000" : "143.25000000";
        }

        String invoiceAmount() {
            return alternate ? "277.25" : "163.75";
        }
    }

    static Path write(final Path folder, final boolean alternate, final int roots)
            throws Exception {
        return write(folder, new Spec(alternate, roots));
    }

    static Path write(final Path folder, final Spec spec) throws Exception {
        final boolean alternate = spec.alternate();
        final int roots = spec.roots();
        QualificationArtifactScenarioFixtures.write(folder, false, roots);
        final var input = (ObjectNode) read(folder.resolve("input.json"));
        input.put("version", "local-artifact-scenario-v2")
                .put("support", "EXPLICIT_INTEGRAL_INPUTS_V1")
                .put("source", spec.source())
                .put("tenant", spec.tenant())
                .put("windowStart", spec.start().toString())
                .put("windowEndExclusive", spec.end().toString())
                .put("zone", "America/Sao_Paulo")
                .put("logicalClock", spec.start().plusDays(14) + "T12:00:00Z")
                .put("revision", spec.revision())
                .put("pageSize", spec.pageSize())
                .put("fiscalPolicy", "SYNTHETIC_CTE");
        final var expansionPins = input.putArray("expansions");
        for (final String family : List.of("CAP", "FAT", "INV", "SIN")) {
            final Path directory = folder.resolve(family.toLowerCase(Locale.ROOT));
            final var manifest = (ObjectNode) read(directory.resolve("capture.json"));
            manifest.put("version", 2)
                    .put("pageSize", spec.pageSize())
                    .put("source", spec.source())
                    .put("tenant", spec.tenant())
                    .put("date", spec.start().toString());
            final var occurrences = array();
            for (final var descriptor : manifest.path("pages")) {
                final Path file = directory.resolve(descriptor.path("file").asText());
                final var rows = read(file);
                for (final var value : rows) {
                    final var envelope = (ObjectNode) value;
                    final var binding = (ObjectNode) envelope.path("binding");
                    final String oldRoot = binding.path("root").asText();
                    final int root =
                            Integer.parseInt(oldRoot.substring(oldRoot.lastIndexOf('-') + 1));
                    final int component = binding.path("component").asText().endsWith("-2") ? 2 : 1;
                    binding.put("root", spec.root(family, root))
                            .put("part", spec.part(family, root))
                            .put("component", spec.component(component))
                            .put("revision", spec.revision());
                    final var data = (ObjectNode) envelope.path("data");
                    sourceIdentity(data, family, root, component, spec);
                    data.put(
                            switch (family) {
                                case "CAP" -> "value";
                                case "FAT" -> "fit_ant_value";
                                case "INV" -> "cnr_c_s_fit_invoices_value";
                                default -> "insurance_claim_total";
                            },
                            spec.invoiceAmount());
                    shiftDates(data, spec);
                    if (spec.decimalBoundaries()) {
                        data.put(
                                switch (family) {
                                    case "CAP" -> "interest_value";
                                    case "FAT" -> "third_party_ctes_value";
                                    case "INV" -> "cnr_c_s_fit_total_cubic_volume";
                                    default -> "customer_debits_subtotal";
                                },
                                new java.math.BigDecimal(
                                        spec.alternate() ? "0.00000002" : "0.00000001"));
                    }
                }
                occurrences.addAll((ArrayNode) rows);
            }
            saveDataPages(directory, manifest.putArray("pages"), occurrences, spec.pageSize());
            save(directory.resolve("capture.json"), manifest);
            expansionPins.add(pin(folder, directory.resolve("capture.json")));
        }
        final var sources = input.putObject("sources");
        for (final String family : List.of("COL", "FRE", "MAN", "COT", "LOC", "USER")) {
            final var manifest =
                    object().put("version", "local-capture-pages-v1")
                            .put("origin", "LOCAL_SYNTHETIC_ARTIFACT_V1")
                            .put("family", family)
                            .put("source", spec.source())
                            .put("tenant", spec.tenant())
                            .put("date", spec.start().toString())
                            .put("revision", spec.revision())
                            .put(
                                    "contractSha256",
                                    DeclaredCapturePages.release(family)
                                            .contractFingerprint()
                                            .sha256())
                            .put("pageSize", family.equals("USER") ? 20 : spec.pageSize())
                            .put("maximumPages", 256)
                            .put("maximumRows", 10000)
                            .put("complete", true);
            final var pages = manifest.putArray("pages");
            final var all = array();
            // Capture order differs from the SQL order, including a reversed family and crossed
            // relations in both sets.
            for (int index = 0; index < roots; index++) {
                final int root = alternate ? roots - index : index + 1;
                if (family.equals("USER")) {
                    all.addObject()
                            .putObject("node")
                            .put("id", spec.user(root))
                            .put("name", spec.userName(root));
                } else {
                    final var data =
                            switch (family) {
                                case "MAN" -> AnalyticScenarioFixtures.manifest();
                                case "COL" -> AnalyticCollectionsFixtures.data();
                                case "COT" -> AnalyticQuotesFixtures.data();
                                case "FRE" ->
                                        ExpansionDependencyFixtures.data(
                                                DataExportTemplate.FRETES, 1);
                                default ->
                                        ExpansionDependencyFixtures.data(
                                                DataExportTemplate.LOCALIZACAO_CARGAS, 1);
                            };
                    sourceIdentity(data, family, root, 1, spec);
                    if (family.equals("FRE")) {
                        data.put("synthetic_fixture", true)
                                .put("synthetic_pick_item", "pick-" + spec.tag() + "-" + root)
                                .put("criado_em", spec.start() + "T10:00:00Z")
                                .put("total", spec.freightAmount());
                        if (spec.advanced()) {
                            data.put("criado_em", spec.start() + "T13:00:00Z")
                                    .put("cte_created_at", spec.start() + "T14:00:00.123456789Z");
                        }
                    }
                    if (family.equals("MAN")) {
                        data.put(
                                "finished_at",
                                spec.start() + "T12:00:00.00000000" + spec.revision() + "Z");
                    }
                    if (family.equals("COT")) {
                        data.put("qoe_qes_total", alternate ? "291.2500" : "176.5000");
                    }
                    shiftDates(data, spec);
                    final int copies = Set.of("FRE", "LOC").contains(family) ? 2 : 3;
                    for (int copy = 0; copy < copies; copy++) {
                        all.add(data.deepCopy());
                    }
                }
            }
            manifest.put("expectedRows", all.size());
            final Path directory = folder.resolve("sources/" + family.toLowerCase(Locale.ROOT));
            if (family.equals("USER")) {
                for (int first = 0, ordinal = 1; first < all.size(); first += 20, ordinal++) {
                    final var page = object();
                    final var individual = page.putObject("data").putObject("individual");
                    final var edges = individual.putArray("edges");
                    for (int index = first; index < Math.min(first + 20, all.size()); index++) {
                        edges.add(all.path(index));
                    }
                    final boolean next = first + 20 < all.size();
                    final var info = individual.putObject("pageInfo").put("hasNextPage", next);
                    if (next) {
                        info.put("endCursor", "declared-cursor-" + spec.tag() + "-" + ordinal);
                    } else {
                        info.putNull("endCursor");
                    }
                    final Path file = directory.resolve("page-" + ordinal + ".json");
                    save(file, page);
                    pages.add(pin(directory, file));
                }
            } else {
                saveDataPages(directory, pages, all, spec.pageSize());
            }
            save(directory.resolve("capture.json"), manifest);
            sources.set(family, pin(folder, directory.resolve("capture.json")));
        }
        raster(folder, input, spec);
        expansionRelations(folder, input, spec);
        references(folder, input, spec);
        support(folder, input, spec);
        sweep(folder, input, spec);
        save(folder.resolve("input.json"), input);
        IntegralArtifactOracleFixtures.write(folder, spec);
        IntegralArtifactPackageFixtures.recordInputs(folder);
        return folder.resolve("input.json");
    }

    private static void sweep(final Path folder, final ObjectNode input, final Spec spec)
            throws Exception {
        final var manifest =
                object().put("version", "local-collection-sweep-v2")
                        .put("origin", "SYNTHETIC_DECLARED_COLLECTION_PREVIEW_V1")
                        .put("source", spec.source())
                        .put("tenant", spec.tenant())
                        .put("revision", spec.revision())
                        .put("date", spec.start().toString())
                        .put("universeRoots", spec.roots())
                        .put("omitFirst", false)
                        .put("pageSize", spec.pageSize())
                        .put(
                                "contractSha256",
                                DeclaredCapturePages.release("COL").contractFingerprint().sha256());
        final var universe = manifest.putArray("universe");
        for (int root = 1; root <= spec.roots(); root++) {
            universe.addObject().put("key", "INTEGER:" + spec.id("COL", root)).put("rows", 3);
        }
        final var observations = manifest.putArray("observations");
        final var capturePin = input.path("sources").path("COL");
        final Path captureFile = folder.resolve(capturePin.path("file").asText());
        final var capture = read(captureFile);
        final Path directory = folder.resolve("sweep");
        for (int observation = 1; observation <= 4; observation++) {
            final var traversal = observations.addArray();
            int page = 0;
            for (final var sourcePage : capture.path("pages")) {
                final var rows =
                        read(captureFile.getParent().resolve(sourcePage.path("file").asText()));
                final Path file =
                        directory.resolve(
                                "observation-" + observation + "-page-" + ++page + ".json");
                save(file, rows);
                traversal.add(pin(directory, file));
            }
        }
        final Path file = directory.resolve("manifest.json");
        save(file, manifest);
        input.set("sweep", pin(folder, file));
    }

    private static void saveDataPages(
            final Path directory, final ArrayNode pages, final ArrayNode all, final int size)
            throws Exception {
        for (int first = 0, ordinal = 1; ; first += size, ordinal++) {
            final var rows = array();
            for (int index = first; index < Math.min(first + size, all.size()); index++) {
                rows.add(all.path(index));
            }
            final Path file = directory.resolve("page-" + ordinal + ".json");
            save(file, rows);
            pages.add(pin(directory, file));
            if (rows.isEmpty()) {
                return;
            }
        }
    }

    static void sourceIdentity(
            final ObjectNode row,
            final String family,
            final int root,
            final int component,
            final Spec spec) {
        switch (family) {
            case "MAN" ->
                    row.put("sequence_code", spec.id("MAN", root))
                            .put("manifest_freights_total", spec.freightAmount())
                            .put(
                                    "mft_pfs_pck_sequence_code",
                                    spec.id("COLSEQ", spec.collectionForManifest(root)))
                            .put(
                                    "mft_mfs_key",
                                    "1".repeat(36)
                                            + String.format(
                                                    Locale.ROOT, "%08d", spec.id("MAN", root)));
            case "COL" ->
                    row.put("id", spec.id("COL", root))
                            .put("sequence_code", spec.id("COLSEQ", root))
                            .put("synthetic_item_key", spec.id("ITEM", root));
            case "FRE" ->
                    row.put("id", spec.id("FRE", root))
                            .put("corporation_sequence_number", spec.id("FRESEQ", root));
            case "LOC" -> row.put("corporation_sequence_number", spec.id("LOC", root));
            case "COT" -> row.put("sequence_code", spec.id("COT", root));
            case "CAP" -> row.put("ant_ils_sequence_code", spec.id("CAP", root));
            case "FAT" ->
                    row.put("id", spec.id("FAT", root) * 10 + component)
                            .put("corporation_sequence_number", spec.id("FAT", root));
            case "INV" ->
                    row.put("sequence_code", spec.id("INV", root))
                            .put(
                                    "cnr_c_s_fit_corporation_sequence_number",
                                    spec.id("INV", root) * 10 + component);
            case "SIN" ->
                    row.put("sequence_code", spec.id("SIN", root))
                            .put(
                                    "icm_fis_fit_corporation_sequence_number",
                                    spec.id("SIN", root) * 10 + component);
            default -> throw new IllegalArgumentException(family);
        }
    }

    private static void raster(final Path folder, final ObjectNode input, final Spec spec)
            throws Exception {
        final Path directory = folder.resolve("raster");
        final var manifest = (ObjectNode) read(directory.resolve("raster.json"));
        shiftDates(manifest, spec);
        manifest.put("version", "local-raster-artifact-v2")
                .put("source", spec.source())
                .put("tenant", spec.tenant())
                .put("revision", spec.revision());
        final var body = read(directory.resolve("body.json"));
        final var bindings = read(directory.resolve("bindings.json"));
        int index = 0;
        for (final var value : body) {
            final int root = index++ / 3 + 1;
            final var row = (ObjectNode) value;
            row.put("CodSolicitacao", Long.toString(spec.id("RAS", root)))
                    .put("Sequencial", Long.toString(spec.id("RAS", root) + 37))
                    .put("PlacaVeiculo", spec.alternate() ? "SYN0009" : "SYN0008")
                    .put("TempoTotalViagem", spec.alternate() ? 125 : 97);
            shiftDates(row, spec);
            if (spec.decimalBoundaries()) {
                row.put(
                        "PercentualAtraso",
                        new java.math.BigDecimal(
                                spec.alternate() ? "87654321.87654321" : "12345678.12345678"));
                for (final var stop : row.path("ColetasEntregas")) {
                    ((ObjectNode) stop)
                            .put(
                                    "KmPercorridoEntrega",
                                    new java.math.BigDecimal(
                                            spec.alternate() ? "0.00000002" : "0.00000001"));
                }
            }
        }
        for (final var value : bindings) {
            final var row = (ObjectNode) value;
            final int root = (row.path("tripPosition").intValue() - 1) / 3 + 1;
            row.put("tripKey", "synthetic-journey-" + spec.tag() + "-" + spec.id("RAS", root))
                    .put("source", spec.source())
                    .put("tenant", spec.tenant())
                    .put("revision", spec.revision());
            if (!row.path("stopKey").isNull()) {
                row.put("stopKey", "synthetic-stop-" + spec.tag() + "-93");
            }
        }
        save(directory.resolve("body.json"), body);
        save(directory.resolve("bindings.json"), bindings);
        final var page = (ObjectNode) manifest.path("pages").path(0);
        page.set("body", pin(directory, directory.resolve("body.json")));
        page.set("bindings", pin(directory, directory.resolve("bindings.json")));
        save(directory.resolve("raster.json"), manifest);
        input.set("raster", pin(folder, directory.resolve("raster.json")));
    }

    private static void expansionRelations(
            final Path folder, final ObjectNode input, final Spec spec) throws Exception {
        final Path directory = folder.resolve("relations");
        final var manifest = (ObjectNode) read(directory.resolve("manifest.json"));
        manifest.put("date", spec.start().toString());
        for (final var descriptor : manifest.path("batches")) {
            final Path file = directory.resolve(descriptor.path("file").asText());
            final var rows = read(file);
            for (final var value : rows) {
                final var row = (ObjectNode) value;
                final String family = row.path("kind").asText().substring(0, 3);
                final String binding = row.path("bindingKey").asText();
                final String[] pieces = binding.split("-");
                final int root = Integer.parseInt(pieces[3]),
                        component = Integer.parseInt(pieces[4]);
                row.put("revision", spec.revision())
                        .put("targetDate", spec.start().toString())
                        .put("targetKey", "INTEGER:" + spec.id("FRE", root));
                ((ObjectNode) row.path("root"))
                        .put(
                                "value",
                                family.equals("LOC")
                                        ? Long.toString(spec.id("LOC", root))
                                        : spec.root(family, root));
                ((ObjectNode) row.path("part"))
                        .put(
                                "value",
                                family.equals("LOC")
                                        ? "loc-part-" + spec.tag()
                                        : spec.part(family, root));
                ((ObjectNode) row.path("component")).put("value", spec.component(component));
                ((ObjectNode) row.path("document"))
                        .put("value", "document-" + spec.tag() + "-" + root + "-" + component);
            }
            save(file, rows);
            ((ObjectNode) descriptor).put("sha256", QualificationJson.sha256(file));
        }
        save(directory.resolve("manifest.json"), manifest);
        input.set("relations", pin(folder, directory.resolve("manifest.json")));
    }

    private static void references(final Path folder, final ObjectNode input, final Spec spec)
            throws Exception {
        final var manifest =
                object().put("version", "local-analytic-references-v1")
                        .put("origin", "LOCAL_SYNTHETIC_ARTIFACT_V1")
                        .put("revision", 2)
                        .put("validFrom", spec.start().toString())
                        .put("validToExclusive", spec.end().toString());
        manifest.putObject("policies")
                .put("fiscal", "CTE_FIRST_REAL_DOCUMENT")
                .put("branch", "EXPLICIT_ASSIGNMENT")
                .put("driver", "INCLUDE_ALL_BOUND");
        final var files = manifest.putObject("files");
        final Path directory = folder.resolve("references");
        final var paths =
                Map.of(
                        "expansion",
                        "/expansion-laboratory/references.synthetic.json",
                        "dimensions",
                        "/analytic-laboratory/manifest-references.synthetic.json",
                        "fleet",
                        "/analytic-laboratory/fleet-references.synthetic.json",
                        "regions",
                        "/analytic-laboratory/collection-regions.synthetic.json",
                        "tariffs",
                        "/analytic-laboratory/quote-tariffs.synthetic.json");
        for (final var entry : paths.entrySet()) {
            final var data = resource(entry.getValue());
            shiftDates(data, spec);
            if (entry.getKey().equals("regions")) {
                for (final var row : data.path("rows")) {
                    if (row.path("kind").asText().equals("CEP")) {
                        ((ObjectNode) row)
                                .put(
                                        "region",
                                        "REGIAO_EXPLICITA_" + spec.tag().toUpperCase(Locale.ROOT));
                    }
                }
            }
            if (entry.getKey().equals("tariffs")) {
                for (final var row : data.path("rows")) {
                    if (row.path("origin").asText().equals("SP")
                            && row.path("destination").asText().equals("RJ")) {
                        ((ObjectNode) row).put("amount", spec.alternate() ? "2.17" : "1.53");
                    }
                }
            }
            final Path file = directory.resolve(entry.getKey() + ".json");
            save(file, data);
            files.set(entry.getKey(), pin(directory, file));
        }
        save(directory.resolve("manifest.json"), manifest);
        input.set("references", pin(folder, directory.resolve("manifest.json")));
    }

    private static void support(final Path folder, final ObjectNode input, final Spec spec)
            throws Exception {
        final var groups = new java.util.LinkedHashMap<String, ArrayNode>();
        for (final var kind :
                List.of(
                        "financial",
                        "dimensions",
                        "relational",
                        "freight",
                        "collections",
                        "manifestStates",
                        "freightRelations",
                        "compositions",
                        "fiscal")) {
            groups.put(kind, array());
        }
        for (int root = 1; root <= spec.roots(); root++) {
            final var terms =
                    (ObjectNode) resource("/expansion-laboratory/freight-terms.synthetic.json");
            terms.remove(List.of("provenance", "version"));
            terms.set("payerToken", terms.remove("payerReference"));
            terms.put("sourceKey", "INTEGER:" + spec.id("FRE", root))
                    .put("revision", spec.revision());
            shiftDates(terms, spec);
            terms.put("billingReferenceDate", spec.start().plusDays(1).toString());
            groups.get("financial").add(terms);
            final var attributes =
                    resource("/analytic-laboratory/freight-attributes.synthetic.json");
            shiftDates(attributes, spec);
            ((ObjectNode) attributes.path("attributes"))
                    .put("km", spec.alternate() ? "29.37500000" : "16.87500000");
            groups.get("freight")
                    .addObject()
                    .put("sourceKey", "INTEGER:" + spec.id("FRE", root))
                    .put("revision", spec.revision())
                    .put("evidence", "synthetic-integral-freight")
                    .set("attributes", attributes);
            final var collection =
                    resource("/analytic-laboratory/collection-supplement.synthetic.json");
            shiftDates(collection, spec);
            ((ObjectNode) collection.path("data").path("pickAddress"))
                    .put("line2", "COMPLEMENTO EXPLÍCITO " + spec.tag().toUpperCase(Locale.ROOT));
            groups.get("collections")
                    .addObject()
                    .put("sourceKey", "INTEGER:" + spec.id("COL", root))
                    .put("revision", spec.revision())
                    .put(
                            "cancellationUserKey",
                            "STRING:" + spec.user(spec.alternate() ? spec.roots() : 1))
                    .put("destroyUserKey", "STRING:" + spec.user(1))
                    .set("attributes", collection);
            groups.get("manifestStates")
                    .addObject()
                    .put("sourceKey", "INTEGER:" + spec.id("MAN", root))
                    .put("revision", spec.revision())
                    .put("active", true)
                    .put("reactivate", false);
            groups.get("compositions")
                    .addObject()
                    .put("sourceKey", "INTEGER:" + spec.id("MAN", root))
                    .put("revision", spec.revision())
                    .put("expectedFreights", 1);
            for (final String kind : List.of("DIRECT", "CROSSWALK")) {
                groups.get("freightRelations")
                        .addObject()
                        .put("kind", kind)
                        .put(
                                "originKey",
                                "INTEGER:" + spec.id(kind.equals("DIRECT") ? "MAN" : "FRE", root))
                        .put("freightKey", "INTEGER:" + spec.id("FRE", root))
                        .put("revision", spec.revision())
                        .put("active", true)
                        .putNull("previousFreightKey");
            }
            for (int component = 1; component <= 2; component++) {
                final var row =
                        groups.get("fiscal")
                                .addObject()
                                .put("revision", spec.revision())
                                .put(
                                        "nfseSeries",
                                        spec.fiscalTupleBoundaries()
                                                ? "SERIE-TUPLA-"
                                                        + spec.tag().toUpperCase(Locale.ROOT)
                                                        + "-"
                                                        + root
                                                : "SERIE-EXPLICITA-"
                                                        + spec.tag().toUpperCase(Locale.ROOT));
                row.set("root", key("STRING", spec.root("FAT", root)));
                row.set("part", key("STRING", spec.part("FAT", root)));
                row.set("component", key("STRING", spec.component(component)));
            }
            final int collected = spec.collectionForManifest(root);
            relational(
                    groups.get("relational"),
                    spec,
                    root,
                    "MC",
                    spec.id("MAN", root),
                    key("INTEGER", Long.toString(spec.id("COLSEQ", collected))),
                    spec.id("COL", collected),
                    key("ROOT", "ROOT"));
            relational(
                    groups.get("relational"),
                    spec,
                    root,
                    "CF",
                    spec.id("COL", collected),
                    key("INTEGER", Long.toString(spec.id("ITEM", collected))),
                    spec.id("FRE", root),
                    key("STRING", "pick-" + spec.tag() + "-" + root));
            for (final String entity :
                    List.of("FRETE", "LOC", "MAN", "COL", "CAP", "FAT", "INV", "SIN", "COT")) {
                final var roles =
                        switch (entity) {
                            case "FRETE" ->
                                    List.of(
                                            "BRANCH",
                                            "DEST_BRANCH",
                                            "PERFORMANCE_BRANCH",
                                            "CURRENT_BRANCH",
                                            "PAYER",
                                            "SENDER",
                                            "RECIPIENT",
                                            "TRACTOR",
                                            "DRIVER");
                            case "MAN" ->
                                    List.of(
                                            "BRANCH",
                                            "UNLOADING_BRANCH",
                                            "TRACTOR",
                                            "TRAILER1",
                                            "TRAILER2",
                                            "DRIVER");
                            case "LOC" -> List.of("BRANCH", "CURRENT_BRANCH", "DEST_BRANCH");
                            case "CAP" -> List.of("BRANCH", "ACCOUNT");
                            case "FAT" -> List.of("BRANCH", "PAYER");
                            case "SIN" -> List.of("BRANCH", "TRACTOR");
                            default -> List.of("BRANCH");
                        };
                for (final String role : roles) {
                    final String registry =
                            switch (role) {
                                case "BRANCH" ->
                                        entity.equals("SIN")
                                                ? "synthetic-branch-b"
                                                : "synthetic-branch-a";
                                case "DEST_BRANCH",
                                                "PERFORMANCE_BRANCH",
                                                "CURRENT_BRANCH",
                                                "UNLOADING_BRANCH" ->
                                        "synthetic-branch-b";
                                case "PAYER", "SENDER" -> "synthetic-client-a";
                                case "RECIPIENT" -> "synthetic-client-b";
                                case "TRACTOR" -> "synthetic-tractor-a";
                                case "TRAILER1" -> "synthetic-trailer-a";
                                case "TRAILER2" -> "synthetic-trailer-b";
                                case "DRIVER" -> "synthetic-driver-a";
                                default -> "synthetic-account-a";
                            };
                    final String sourceKey =
                            Set.of("CAP", "FAT", "INV", "SIN").contains(entity)
                                    ? "STRING:" + spec.root(entity, root)
                                    : "INTEGER:"
                                            + spec.id(
                                                    entity.equals("FRETE") ? "FRE" : entity, root);
                    groups.get("dimensions")
                            .addObject()
                            .put("entity", entity)
                            .put("sourceKey", sourceKey)
                            .put("role", role)
                            .put("entityKey", registry)
                            .put("revision", spec.revision())
                            .put("from", spec.start().toString())
                            .put("toExclusive", spec.end().toString())
                            .put("active", true)
                            .putNull("previousEntityKey")
                            .put("evidence", "synthetic-integral-dimension");
                }
            }
        }
        final var manifest =
                object().put("version", "local-analytic-support-v1")
                        .put("origin", "LOCAL_SYNTHETIC_ARTIFACT_V1")
                        .put("complete", true);
        final var batches = manifest.putObject("batches");
        final Path directory = folder.resolve("support");
        for (final var entry : groups.entrySet()) {
            batches.set(entry.getKey(), saveBatches(directory, entry.getKey(), entry.getValue()));
        }
        save(directory.resolve("manifest.json"), manifest);
        input.set("supplements", pin(folder, directory.resolve("manifest.json")));
    }

    private static void relational(
            final ArrayNode rows,
            final Spec spec,
            final int root,
            final String relation,
            final long origin,
            final ObjectNode originComponent,
            final long target,
            final ObjectNode targetComponent) {
        final var row =
                rows.addObject()
                        .put(
                                "evidenceId",
                                "synthetic-integral-"
                                        + relation
                                        + "-"
                                        + root
                                        + "-r"
                                        + spec.revision())
                        .put("relation", relation)
                        .put("targetDate", spec.start().toString())
                        .put("revision", spec.revision())
                        .put("cardinality", "ONE_TO_ONE");
        row.set("origin", key("INTEGER", Long.toString(origin)));
        row.set("originComponent", originComponent);
        row.set("target", key("INTEGER", Long.toString(target)));
        row.set("targetComponent", targetComponent);
    }

    static ArrayNode saveBatches(final Path folder, final String name, final ArrayNode rows)
            throws Exception {
        final var result = array();
        for (int first = 0; first < Math.max(1, rows.size()); first += 32) {
            final var batch = array();
            for (int i = first; i < Math.min(first + 32, rows.size()); i++) {
                batch.add(rows.path(i));
            }
            final Path file = folder.resolve(name.toLowerCase(Locale.ROOT) + "-" + first + ".json");
            save(file, batch);
            result.add(pin(folder, file));
        }
        return result;
    }

    static void shiftDates(final JsonNode node, final Spec spec) {
        if (node.isObject()) {
            final var names = new ArrayList<String>();
            node.fieldNames().forEachRemaining(names::add);
            for (final String name : names) {
                final var value = node.path(name);
                if (value.isTextual()) {
                    ((ObjectNode) node).put(name, shifted(value.textValue(), spec));
                } else {
                    shiftDates(value, spec);
                }
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                if (node.path(i).isTextual()) {
                    ((ArrayNode) node)
                            .set(
                                    i,
                                    JsonNodeFactory.instance.textNode(
                                            shifted(node.path(i).textValue(), spec)));
                } else {
                    shiftDates(node.path(i), spec);
                }
            }
        }
    }

    static String shifted(final String value, final Spec spec) {
        if (value.matches("2036-[0-9]{2}-[0-9]{2}.*")) {
            final var date = LocalDate.parse(value.substring(0, 10));
            return spec.start().plusDays(ChronoUnit.DAYS.between(LocalDate.of(2036, 4, 1), date))
                    + value.substring(10);
        }
        return value;
    }

    static JsonNode resource(final String path) throws Exception {
        try (var stream = IntegralArtifactFixtures.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalArgumentException(path);
            }
            return QualificationJson.parse(stream.readAllBytes(), 524288);
        }
    }

    static JsonNode read(final Path path) throws Exception {
        return QualificationJson.read(path, 2097152);
    }

    static ObjectNode object() {
        return JsonNodeFactory.instance.objectNode();
    }

    static ArrayNode array() {
        return JsonNodeFactory.instance.arrayNode();
    }

    static ObjectNode key(final String type, final String value) {
        return object().put("type", type).put("value", value);
    }

    static ObjectNode pin(final Path folder, final Path path) throws Exception {
        return object().put("file", folder.relativize(path).toString().replace('\\', '/'))
                .put("sha256", QualificationJson.sha256(path));
    }

    static void save(final Path path, final JsonNode value) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, value.toString());
    }
}
