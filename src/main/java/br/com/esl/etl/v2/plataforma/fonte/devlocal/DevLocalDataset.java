package br.com.esl.etl.v2.plataforma.fonte.devlocal;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.contasapagar.aplicacao.ContaPagarDataExportMapper;
import br.com.esl.etl.v2.modulos.cotacoes.aplicacao.CotacaoDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.faturasporcliente.aplicacao.FaturaClienteDataExportMapper;
import br.com.esl.etl.v2.modulos.fretes.aplicacao.FreteDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.inventario.aplicacao.InventarioDataExportMapper;
import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.LocalizacaoCargaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaFreshnessPolicy;
import br.com.esl.etl.v2.modulos.manifestos.aplicacao.ManifestoDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.raster.domain.RasterStop;
import br.com.esl.etl.v2.modulos.raster.domain.RasterTripObservation;
import br.com.esl.etl.v2.modulos.sinistros.aplicacao.SinistroDataExportMapper;
import br.com.esl.etl.v2.modulos.usuarios.aplicacao.UsuarioGraphQlNodeMapper;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterResponseParser;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

/** DEV-SOURCE-001: bounded SYNTHETIC local pages processed by delivered domain mappers. */
public final class DevLocalDataset {
    public static final int MAX_BYTES = 65_536;
    public static final int MAX_RESULT_BYTES = 524_288;
    public static final int MAX_ROWS_PER_ENTITY = 16;
    public static final int MAX_PAGES_PER_ENTITY = 8;
    private static final Set<String> ENTITIES =
            Set.of(
                    "Users",
                    "Manifestos",
                    "Coletas",
                    "Fretes",
                    "Cotacoes",
                    "Localizacao",
                    "CAP",
                    "Faturas",
                    "Inventario",
                    "Sinistros",
                    "Raster");
    private static final JsonMapper JSON =
            JsonMapper.builder(
                            JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxNestingDepth(16)
                                                    .maxStringLength(4000)
                                                    .maxNumberLength(40)
                                                    .build())
                                    .build())
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                    .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
                    .build();

    private DevLocalDataset() {}

    public static JsonNode sample() {
        return parse(DevLocalSamples.SAMPLE);
    }

    /** Independent literal assertions; never computed from mapper output. */
    public static JsonNode expected() {
        return parse(DevLocalSamples.EXPECTED);
    }

    public static JsonNode characterize() {
        return characterize(sample());
    }

    public static JsonNode parse(final String document) {
        if (document == null
                || document.isEmpty()
                || document.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw rejected("DEV_DATASET_BYTE_LIMIT");
        }
        try {
            final JsonNode result = JSON.readTree(document);
            if (result == null) {
                throw rejected("DEV_DATASET_INVALID_JSON");
            }
            return result;
        } catch (final IOException failure) {
            throw rejected("DEV_DATASET_INVALID_JSON");
        }
    }

    public static JsonNode characterize(final JsonNode supplied) {
        if (supplied == null) {
            throw rejected("DEV_DATASET_REQUIRED");
        }
        final JsonNode dataset = parse(supplied.toString());
        schema(
                dataset,
                dataset.has("relationBindings")
                        ? Set.of(
                                "synthetic",
                                "datasetVersion",
                                "filter",
                                "entities",
                                "relationBindings")
                        : Set.of("synthetic", "datasetVersion", "filter", "entities"));
        if (!dataset.path("synthetic").isBoolean()
                || !dataset.path("synthetic").booleanValue()
                || !"DEV_LOCAL_V1".equals(dataset.path("datasetVersion").asText())) {
            throw rejected("DEV_SYNTHETIC_OPT_IN_REQUIRED");
        }
        final JsonNode filter = dataset.get("filter");
        schema(filter, Set.of("startInclusive", "endExclusive", "zone"));
        if (!"America/Sao_Paulo".equals(filter.path("zone").asText())) {
            throw rejected("DEV_FILTER_ZONE");
        }
        final Instant start = instant(filter.path("startInclusive").asText());
        final Instant end = instant(filter.path("endExclusive").asText());
        if (!start.isBefore(end)
                || Duration.between(start, end).compareTo(Duration.ofDays(31)) > 0) {
            throw rejected("DEV_FILTER_BOUND");
        }
        final JsonNode inputs = dataset.get("entities");
        if (inputs == null || !inputs.isArray() || inputs.size() != ENTITIES.size()) {
            throw rejected("DEV_ENTITY_INVENTORY");
        }
        final ObjectNode result = JSON.createObjectNode();
        result.put("synthetic", true);
        result.put("datasetVersion", "DEV_LOCAL_V1");
        final ArrayNode entities = result.putArray("entities");
        final Set<String> seen = new HashSet<>();
        for (final JsonNode input : inputs) {
            schema(input, Set.of("entity", "pages"));
            final String name = input.path("entity").asText();
            if (!ENTITIES.contains(name) || !seen.add(name)) {
                throw rejected("DEV_ENTITY_INVENTORY");
            }
            entities.add(entity(name, input.get("pages"), start, end));
        }
        result.set("freshnessChecks", freshnessChecks());
        final ObjectNode relations =
                DevLocalRelationBindings.characterize(
                        dataset.path("relationBindings"), entities, start, end);
        result.set("relationBindings", relations.get("relationBindings"));
        result.set("relationBindingObservations", relations.get("relationBindingObservations"));
        if (result.toString().getBytes(StandardCharsets.UTF_8).length > MAX_RESULT_BYTES) {
            throw rejected("DEV_RESULT_BYTE_LIMIT");
        }
        return result;
    }

    /**
     * Bounded assertion projection for Engine: expected JSON pointers versus actual rich results.
     */
    public static JsonNode verify(final JsonNode actual) {
        if (actual == null
                || !actual.path("synthetic").asBoolean()
                || !actual.path("entities").isArray()
                || actual.path("entities").size() != 11) {
            throw rejected("DEV_RESULT_SCHEMA");
        }
        final ObjectNode result = JSON.createObjectNode();
        result.put("synthetic", true);
        final ArrayNode differences = result.putArray("differences");
        int comparisons = 0;
        for (final JsonNode oracle : expected().path("entities")) {
            JsonNode found = null;
            for (final JsonNode entity : actual.path("entities")) {
                if (oracle.path("entity").equals(entity.path("entity"))) {
                    if (found != null) {
                        throw rejected("DEV_RESULT_DUPLICATE_ENTITY");
                    }
                    found = entity;
                }
            }
            for (final String metric :
                    Set.of("inputRows", "pages", "accepted", "quarantined", "filtered")) {
                comparisons++;
                compare(
                        differences,
                        oracle.path("entity").asText(),
                        "/" + metric,
                        oracle.path(metric),
                        found == null ? null : found.get(metric));
            }
            for (final JsonNode assertion : oracle.path("checks")) {
                comparisons++;
                compare(
                        differences,
                        oracle.path("entity").asText(),
                        assertion.path("path").asText(),
                        assertion.get("value"),
                        found == null ? null : found.at(assertion.path("path").asText()));
            }
        }
        for (final JsonNode assertion : expected().path("freshnessChecks")) {
            comparisons++;
            compare(
                    differences,
                    "Localizacao",
                    assertion.path("path").asText(),
                    assertion.get("value"),
                    actual.path("freshnessChecks").at(assertion.path("path").asText()));
        }
        for (final JsonNode assertion : expected().path("relationBindingChecks")) {
            comparisons++;
            compare(
                    differences,
                    "DeclaredSyntheticRelations",
                    assertion.path("path").asText(),
                    assertion.get("value"),
                    actual.at(assertion.path("path").asText()));
        }
        result.put("comparisons", comparisons);
        result.put("status", differences.isEmpty() ? "MATCH" : "DIVERGED");
        return result;
    }

    private static void compare(
            final ArrayNode differences,
            final String entity,
            final String path,
            final JsonNode expected,
            final JsonNode actual) {
        if (!expected.equals(actual)) {
            final ObjectNode difference = differences.addObject();
            difference.put("entity", entity);
            difference.put("path", path);
        }
    }

    private static ObjectNode entity(
            final String name, final JsonNode pages, final Instant start, final Instant end) {
        if (pages == null
                || !pages.isArray()
                || pages.isEmpty()
                || pages.size() > MAX_PAGES_PER_ENTITY) {
            throw rejected("DEV_PAGE_BOUND");
        }
        final ObjectNode result = JSON.createObjectNode();
        result.put("entity", name);
        result.put("synthetic", true);
        result.put("identityScope", "SYNTHETIC_LOCAL_ONLY");
        result.put("canonicalIdentityProven", false);
        result.put("providerEvidence", "NOT_EXECUTED");
        final ArrayNode records = result.putArray("rows");
        int accepted = 0;
        int quarantined = 0;
        int filtered = 0;
        int pageNumber = 0;
        for (final JsonNode page : pages) {
            pageNumber++;
            schema(page, Set.of("number", "terminal", "rows"));
            if (!page.path("number").isIntegralNumber()
                    || page.path("number").intValue() != pageNumber
                    || !page.path("terminal").isBoolean()
                    || page.path("terminal").booleanValue() != (pageNumber == pages.size())) {
                throw rejected("DEV_PAGE_SEQUENCE_OR_TERMINAL");
            }
            final JsonNode rows = page.get("rows");
            if (rows == null
                    || !rows.isArray()
                    || rows.size() > MAX_ROWS_PER_ENTITY - records.size()) {
                throw rejected("DEV_ROW_BOUND");
            }
            for (final JsonNode row : rows) {
                if (!row.isObject()) {
                    throw rejected("DEV_ROW_SHAPE");
                }
                final ObjectNode mapped = project(name, records.size() + 1, row);
                final boolean valid = mapped.path("valid").asBoolean();
                final String fresh = mapped.path("freshness").asText(null);
                final boolean inWindow =
                        fresh == null
                                || (!instant(fresh).isBefore(start)
                                        && instant(fresh).isBefore(end));
                mapped.put(
                        "filterDecision",
                        !valid
                                ? "QUARANTINE"
                                : fresh == null
                                        ? "NOT_APPLICABLE"
                                        : inWindow ? "IN_WINDOW" : "OUTSIDE_WINDOW");
                mapped.put("eligible", valid && inWindow);
                mapped.put("page", pageNumber);
                if (!valid) {
                    quarantined++;
                } else if (!inWindow) {
                    filtered++;
                } else {
                    accepted++;
                }
                records.add(mapped);
            }
        }
        result.put("inputRows", records.size());
        result.put("pages", pageNumber);
        result.put("accepted", accepted);
        result.put("quarantined", quarantined);
        result.put("filtered", filtered);
        result.put("terminal", true);
        result.put("sourceCompletenessProven", false);
        return result;
    }

    private static ObjectNode project(final String name, final int ordinal, final JsonNode row) {
        final ObjectNode result = JSON.createObjectNode();
        result.put("ordinal", ordinal);
        switch (name) {
            case "Users" -> {
                final var mapped = new UsuarioGraphQlNodeMapper().map(ordinal, row);
                common(result, mapped.quarantineReasonCode(), mapped.sourceKey(), null);
                result.put(
                        "presence",
                        mapped.namePresence() == null ? null : mapped.namePresence().name());
                result.put("name", mapped.name());
            }
            case "Manifestos" -> {
                final var mapped = new ManifestoDataExportRecordMapper().map(ordinal, row);
                common(
                        result,
                        mapped.quarantineReasonCode(),
                        mapped.sourceKey(),
                        mapped.freshnessAtUtc());
                result.put(
                        "presence",
                        mapped.competence() == null
                                ? null
                                : mapped.rootFields().get("contract_type").presence().name());
                result.put(
                        "freshnessOrigin",
                        mapped.freshnessOrigin() == null ? null : mapped.freshnessOrigin().name());
                result.put(
                        "relationKey",
                        mapped.pickSourceKey()
                                .map(ScopedSourceIdentity.SourceKey::storageValue)
                                .orElse(null));
            }
            case "Coletas" -> {
                final var mapped = new ColetaDataExportRecordMapper().map(ordinal, row);
                common(
                        result,
                        mapped.quarantineReasonCode(),
                        mapped.sourceKey(),
                        mapped.freshnessAtUtc());
                result.put(
                        "presence",
                        mapped.sequenceCodePresence() == null
                                ? null
                                : mapped.sequenceCodePresence().name());
                result.put(
                        "freshnessOrigin",
                        mapped.freshnessOrigin() == null ? null : mapped.freshnessOrigin().name());
                if (mapped.relationCandidatesJson() != null) {
                    result.set("relations", parse(mapped.relationCandidatesJson()));
                }
            }
            case "Fretes" -> {
                final var mapped = new FreteDataExportRecordMapper().map(ordinal, row);
                common(
                        result,
                        mapped.quarantineReasonCode(),
                        mapped.sourceKey(),
                        mapped.freshnessAtUtc());
                result.put("presence", presence(row, "reference_number"));
                result.put(
                        "freshnessOrigin",
                        mapped.freshnessOrigin() == null ? null : mapped.freshnessOrigin().name());
                result.put("terminalStatus", mapped.terminal());
                if (mapped.financialJson() != null) {
                    result.set("financial", parse(mapped.financialJson()));
                    result.set("fieldEvidence", parse(mapped.fieldPresenceJson()));
                }
            }
            case "Cotacoes" -> {
                final var mapped = new CotacaoDataExportRecordMapper().map(ordinal, row);
                common(
                        result,
                        mapped.quarantineReasonCode(),
                        mapped.sourceKey(),
                        mapped.freshnessAtUtc());
                result.put("presence", presence(row, "qoe_crn_psn_nickname"));
                result.put(
                        "total",
                        mapped.totalAmount() == null ? null : mapped.totalAmount().toPlainString());
                result.put(
                        "freshnessOrigin",
                        mapped.freshnessOrigin() == null ? null : mapped.freshnessOrigin().name());
                if (mapped.fieldPresenceJson() != null) {
                    result.set("fieldEvidence", parse(mapped.fieldPresenceJson()));
                }
            }
            case "Localizacao" -> {
                final var mapped =
                        new LocalizacaoCargaDataExportRecordMapper().map(ordinal, row.toString());
                common(
                        result,
                        mapped.quarantineReasonCode(),
                        mapped.sourceKey(),
                        mapped.serviceAtUtc());
                result.put("presence", presence(row, "fit_dyn_name"));
                result.put("volumes", mapped.invoicesVolumes());
                result.put("total", mapped.total() == null ? null : mapped.total().toPlainString());
                result.put("status", mapped.statusNormalized());
                if (mapped.fieldPresenceJson() != null) {
                    result.set("fieldEvidence", parse(mapped.fieldPresenceJson()));
                }
            }
            case "CAP", "Faturas", "Inventario", "Sinistros" ->
                    expansion(result, name, ordinal, row);
            case "Raster" -> raster(result, ordinal, row);
            default -> throw rejected("DEV_ENTITY_INVENTORY");
        }
        return result;
    }

    private static void expansion(
            final ObjectNode result, final String name, final int ordinal, final JsonNode row) {
        final ObjectNode envelope = JSON.createObjectNode().set("data", row);
        final ExpansionObservation mapped =
                switch (name) {
                    case "CAP" -> new ContaPagarDataExportMapper().map(ordinal, envelope);
                    case "Faturas" -> new FaturaClienteDataExportMapper().map(ordinal, envelope);
                    case "Inventario" -> new InventarioDataExportMapper().map(ordinal, envelope);
                    case "Sinistros" -> new SinistroDataExportMapper().map(ordinal, envelope);
                    default -> throw rejected("DEV_ENTITY_INVENTORY");
                };
        common(result, mapped.valid() ? null : "EXP_FIELD_INVALID", null, null);
        result.put("sourceKey", "SYNTHETIC:" + name + ":" + ordinal);
        result.put("identityMeaning", "LOCAL_OCCURRENCE_NOT_SUPPLIER_ROOT");
        final ObjectNode fields = fields(mapped);
        result.set("values", fields);
        final String field =
                switch (name) {
                    case "CAP", "Faturas" -> "comments";
                    case "Inventario" -> "cnrCSFitDynName";
                    default -> "openingAtDate";
                };
        result.put("presence", fields.path(field).path("presence").asText());
    }

    private static ObjectNode fields(final Object record) {
        final ObjectNode fields = JSON.createObjectNode();
        try {
            for (final var component : record.getClass().getRecordComponents()) {
                final Object value = component.getAccessor().invoke(record);
                if (value instanceof ExpansionValue<?> observed) {
                    final ObjectNode field = fields.putObject(component.getName());
                    field.put("presence", observed.presence().name());
                    field.put("wire", observed.wire().name());
                    field.put("raw", observed.raw());
                    field.put(
                            "typed", observed.value() == null ? null : observed.value().toString());
                    field.put("issue", observed.issue());
                }
            }
        } catch (final ReflectiveOperationException failure) {
            throw rejected("DEV_TYPED_PROJECTION_FAILED");
        }
        return fields;
    }

    private static void raster(final ObjectNode result, final int ordinal, final JsonNode row) {
        final ObjectNode wrapper = JSON.createObjectNode();
        wrapper.putArray("Viagens").add(row);
        final ArrayNode stops = result.putArray("stops");
        try {
            new RasterResponseParser(ZoneId.of("America/Sao_Paulo"))
                    .parse(
                            wrapper.toString().getBytes(StandardCharsets.UTF_8),
                            new RasterResponseParser.Sink() {
                                @Override
                                public void trip(
                                        final int position,
                                        final RasterTripObservation observation) {
                                    common(
                                            result,
                                            observation.valid() ? null : "RAS_FIELD_INVALID",
                                            null,
                                            null);
                                    result.set("values", fields(observation.trip()));
                                    result.put("presence", observation.routePresence().name());
                                    result.put("stopPresence", observation.stopsPresence().name());
                                }

                                @Override
                                public void stop(
                                        final int tripPosition,
                                        final int position,
                                        final RasterStop observation) {
                                    if (stops.size() == MAX_ROWS_PER_ENTITY) {
                                        throw rejected("DEV_RASTER_STOP_BOUND");
                                    }
                                    stops.add(fields(observation));
                                    if (!observation.valid()) {
                                        result.put("valid", false);
                                        result.put("quarantine", "RAS_STOP_INVALID");
                                    }
                                }
                            });
        } catch (final IOException | SQLException failure) {
            throw rejected("DEV_RASTER_PARSE_FAILED");
        } catch (final IllegalArgumentException failure) {
            throw rejected("DEV_RASTER_CONTRACT_FAILED");
        }
        result.put("sourceKey", "SYNTHETIC:Raster:" + ordinal);
        result.put("identityMeaning", "LOCAL_OCCURRENCE_NOT_SUPPLIER_ROOT");
    }

    private static void common(
            final ObjectNode target,
            final String reason,
            final ScopedSourceIdentity.SourceKey key,
            final Instant freshness) {
        target.put("valid", reason == null);
        target.put("quarantine", reason);
        target.put("sourceKey", key == null ? null : key.storageValue());
        target.put("freshness", freshness == null ? null : freshness.toString());
    }

    private static String presence(final JsonNode node, final String field) {
        return !node.has(field) ? "ABSENT" : node.get(field).isNull() ? "NULL" : "VALUE";
    }

    private static ObjectNode freshnessChecks() {
        final Instant at = Instant.parse("2026-10-01T10:00:00Z");
        final String original = "a".repeat(64);
        final String changed = "b".repeat(64);
        final ObjectNode checks = JSON.createObjectNode();
        checks.put(
                "insert", LocalizacaoCargaFreshnessPolicy.decide(null, null, at, original).name());
        checks.put(
                "replay",
                LocalizacaoCargaFreshnessPolicy.decide(at, original, at, original).name());
        checks.put(
                "stale",
                LocalizacaoCargaFreshnessPolicy.decide(at, original, at.minusSeconds(1), changed)
                        .name());
        checks.put(
                "update",
                LocalizacaoCargaFreshnessPolicy.decide(at, original, at.plusSeconds(1), changed)
                        .name());
        checks.put(
                "conflict",
                LocalizacaoCargaFreshnessPolicy.decide(at, original, at, changed).name());
        return checks;
    }

    private static Instant instant(final String value) {
        try {
            return Instant.parse(value);
        } catch (final RuntimeException failure) {
            throw rejected("DEV_FILTER_TIMESTAMP");
        }
    }

    private static void schema(final JsonNode value, final Set<String> allowed) {
        if (value == null || !value.isObject() || value.size() != allowed.size()) {
            throw rejected("DEV_CLOSED_SCHEMA");
        }
        final var names = value.fieldNames();
        while (names.hasNext()) {
            if (!allowed.contains(names.next())) {
                throw rejected("DEV_CLOSED_SCHEMA");
            }
        }
    }

    private static IllegalArgumentException rejected(final String code) {
        return new IllegalArgumentException(code);
    }
}
