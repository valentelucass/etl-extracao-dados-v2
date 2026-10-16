package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.fretes.domain.FreteFreshnessPolicy;
import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaFreshnessPolicy;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionFreshness;
import br.com.esl.etl.v2.plataforma.persistencia.devlocal.DevLocalLedger;
import br.com.esl.etl.v2.plataforma.relacional.RelationalBinding;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Bounded local projections. SQL publication and nominal financial policies remain separate. */
final class DevLocalEngineAnalytics {
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
    private static final LocalDate DATE = LocalDate.of(2026, 10, 1);

    private DevLocalEngineAnalytics() {}

    static ObjectNode state(
            final JsonNode dataset,
            final JsonNode previous,
            final int scale,
            final int batch,
            final CancellationToken cancellation) {
        final ObjectNode state = DevLocalEngine.JSON.createObjectNode();
        state.put("synthetic", true);
        state.put("version", "SYNTHETIC_STATE_V2");
        state.put("scale", scale);
        final ArrayNode output = state.putArray("entities");
        final ObjectNode observations = DevLocalEngine.JSON.createObjectNode();
        if (!previous.isMissingNode()
                && (!previous.isObject()
                        || !previous.path("synthetic").asBoolean()
                        || !"SYNTHETIC_STATE_V2".equals(previous.path("version").asText())
                        || previous.path("scale").asInt() != scale
                        || !previous.path("entities").isArray()
                        || previous.path("entities").size() != 11
                        || !previous.path("observations").isObject()
                        || previous.path("observations").size() > 2816)) {
            throw new IllegalArgumentException("DEV_LOCAL_PREVIOUS_CONTRACT");
        }
        final Set<String> seen = new HashSet<>();
        int processed = 0;
        int maximumBatch = 0;
        for (final JsonNode entity : dataset.path("entities")) {
            cancellation.throwIfCancellationRequested();
            final String name = entity.path("entity").asText();
            if (!ENTITIES.contains(name)
                    || !seen.add(name)
                    || !DevLocalEngine.SCOPE.equals(entity.path("identityScope").asText())
                    || !entity.path("terminal").isBoolean()
                    || !entity.path("terminal").asBoolean()
                    || !entity.path("rows").isArray()
                    || entity.path("rows").size() > 16
                    || entity.path("inputRows").asInt(-1) != entity.path("rows").size()) {
                throw new IllegalArgumentException("DEV_LOCAL_ENTITY_CONTRACT");
            }
            final Map<String, ObjectNode> roots = previous(previous, name, scale, observations);
            int accepted = 0;
            int quarantine = 0;
            int filtered = 0;
            int inserts = 0;
            int updates = 0;
            int noops = 0;
            for (int offset = 0; offset < entity.path("rows").size(); offset += batch) {
                cancellation.throwIfCancellationRequested();
                final int end = Math.min(offset + batch, entity.path("rows").size());
                maximumBatch = Math.max(maximumBatch, end - offset);
                for (int index = offset; index < end; index++) {
                    final JsonNode row = entity.path("rows").path(index);
                    if (!row.isObject()
                            || !row.path("valid").isBoolean()
                            || !row.path("eligible").isBoolean()) {
                        throw new IllegalArgumentException("DEV_LOCAL_ROW_CONTRACT");
                    }
                    if (!row.path("valid").asBoolean()) {
                        quarantine++;
                        continue;
                    }
                    if (!row.path("eligible").asBoolean()) {
                        filtered++;
                        continue;
                    }
                    accepted++;
                    final String key = row.path("sourceKey").asText();
                    if (!key.matches(
                            "(?:INTEGER:[0-9]{1,19}|STRING:.{1,120}|SYNTHETIC:[A-Za-z]+:[0-9]{1,3})")) {
                        throw new IllegalArgumentException("DEV_LOCAL_KEY_CONTRACT");
                    }
                    final ObjectNode observation = row.deepCopy();
                    observation.remove(Set.of("ordinal", "page"));
                    final String hash = DevLocalLedger.sha256(DevLocalEngine.bytes(observation));
                    final JsonNode domain = DevLocalEngineObservations.rules(name, row);
                    observations.put(
                            hash,
                            new String(DevLocalEngine.bytes(observation), StandardCharsets.UTF_8));
                    for (int copy = 0; copy < scale; copy++) {
                        processed++;
                        final String technicalKey = name + "|" + copy + "|" + key;
                        final ObjectNode current = roots.get(technicalKey);
                        cancellation.throwIfCancellationRequested();
                        final String decision = decision(name, current, observation, hash, domain);
                        if ("EQUAL_FRESHNESS_CONFLICT".equals(decision)) {
                            throw new IllegalArgumentException(
                                    "DEV_LOCAL_EQUAL_FRESHNESS_CONFLICT");
                        }
                        if (current != null
                                && ("REPLAY_NO_OP".equals(decision)
                                        || "STALE_NO_OP".equals(decision))) {
                            current.put("observed", true);
                            noops++;
                        } else {
                            final ObjectNode next = DevLocalEngine.JSON.createObjectNode();
                            next.put("technicalKey", technicalKey);
                            next.put("copy", copy);
                            next.put("sourceKey", key);
                            next.put("canonicalHash", hash);
                            next.put(
                                    "active",
                                    current == null || current.path("active").asBoolean());
                            next.put("observed", true);
                            next.set("observation", projection(observation));
                            next.set("domain", domain);
                            roots.put(technicalKey, next);
                            if (current == null) {
                                inserts++;
                            } else {
                                updates++;
                            }
                        }
                    }
                }
            }
            if (accepted != entity.path("accepted").asInt(-1)
                    || quarantine != entity.path("quarantined").asInt(-1)
                    || filtered != entity.path("filtered").asInt(-1)) {
                throw new IllegalArgumentException("DEV_LOCAL_COUNTS_DIVERGENT");
            }
            if (roots.size() > 256) {
                throw new IllegalArgumentException("DEV_LOCAL_STATE_BOUND");
            }
            final ObjectNode result = output.addObject();
            result.put("entity", name);
            result.put("accepted", accepted * scale);
            result.put("quarantined", quarantine * scale);
            result.put("filtered", filtered * scale);
            result.put("inserts", inserts);
            result.put("updates", updates);
            result.put("noops", noops);
            final ArrayNode rows = result.putArray("rows");
            roots.values().forEach(rows::add);
        }
        state.put("processedRows", processed);
        state.put("maximumBatchRows", maximumBatch);
        final ObjectNode retainedObservations = state.putObject("observations");
        for (final JsonNode entity : output) {
            for (final JsonNode row : entity.path("rows")) {
                final String hash = row.path("canonicalHash").asText();
                retainedObservations.set(hash, observations.path(hash));
            }
        }
        return state;
    }

    private static ObjectNode projection(final JsonNode observation) {
        final ObjectNode result = DevLocalEngine.JSON.createObjectNode();
        for (final String name :
                List.of(
                        "sourceKey",
                        "freshness",
                        "presence",
                        "terminalStatus",
                        "status",
                        "total",
                        "financial",
                        "relationKey")) {
            if (observation.has(name)) {
                if ("financial".equals(name)) {
                    final JsonNode financial = observation.path(name);
                    final ObjectNode projection = result.putObject(name);
                    for (final String metadata :
                            List.of("evidenceScope", "currency", "unit", "arithmetic")) {
                        if (financial.has(metadata)) {
                            projection.set(metadata, financial.path(metadata));
                        }
                    }
                    final ObjectNode total = projection.putObject("total");
                    if (financial.path("total").has("typedDecimal")) {
                        total.set("typedDecimal", financial.path("total").path("typedDecimal"));
                    }
                } else {
                    result.set(name, observation.path(name));
                }
            }
        }
        return result;
    }

    private static Map<String, ObjectNode> previous(
            final JsonNode previous,
            final String name,
            final int scale,
            final ObjectNode observations) {
        final Map<String, ObjectNode> result = new LinkedHashMap<>();
        if (previous.isMissingNode()) {
            return result;
        }
        JsonNode found = null;
        for (final JsonNode entity : previous.path("entities")) {
            if (name.equals(entity.path("entity").asText())) {
                if (found != null) {
                    throw new IllegalArgumentException("DEV_LOCAL_PREVIOUS_DUPLICATE");
                }
                found = entity;
            }
        }
        if (found == null || !found.path("rows").isArray() || found.path("rows").size() > 256) {
            throw new IllegalArgumentException("DEV_LOCAL_PREVIOUS_ENTITY");
        }
        for (final JsonNode row : found.path("rows")) {
            final String key = row.path("technicalKey").asText();
            final String hash = row.path("canonicalHash").asText();
            final JsonNode canonical = previous.path("observations").path(hash);
            final JsonNode source =
                    canonical.isTextual()
                            ? source(canonical.textValue())
                            : DevLocalEngine.JSON.getNodeFactory().missingNode();
            if (!row.isObject()
                    || !row.path("copy").isIntegralNumber()
                    || row.path("copy").asInt() < 0
                    || row.path("copy").asInt() >= scale
                    || !key.equals(
                            name
                                    + "|"
                                    + row.path("copy").asInt()
                                    + "|"
                                    + row.path("sourceKey").asText())
                    || !row.path("active").isBoolean()
                    || !row.path("observation").isObject()
                    || !source.isObject()
                    || !source.path("sourceKey").equals(row.path("sourceKey"))
                    || !canonical.isTextual()
                    || !DevLocalLedger.sha256(
                                    canonical.textValue().getBytes(StandardCharsets.UTF_8))
                            .equals(hash)
                    || !sameBytes(projection(source), row.path("observation"))
                    || !sameBytes(
                            DevLocalEngineObservations.rules(name, source), row.path("domain"))) {
                throw new IllegalArgumentException("DEV_LOCAL_PREVIOUS_ROW");
            }
            final ObjectNode copy = row.deepCopy();
            copy.put("observed", false);
            observations.set(hash, canonical);
            if (result.put(key, copy) != null) {
                throw new IllegalArgumentException("DEV_LOCAL_PREVIOUS_DUPLICATE");
            }
        }
        return result;
    }

    private static JsonNode source(final String canonical) {
        try {
            return DevLocalEngine.JSON.readTree(canonical);
        } catch (final java.io.IOException failure) {
            throw new IllegalArgumentException("DEV_LOCAL_PREVIOUS_CANONICAL_JSON", failure);
        }
    }

    private static boolean sameBytes(final JsonNode first, final JsonNode second) {
        return java.util.Arrays.equals(DevLocalEngine.bytes(first), DevLocalEngine.bytes(second));
    }

    private static String decision(
            final String name,
            final ObjectNode current,
            final ObjectNode observation,
            final String hash,
            final JsonNode domain) {
        if (current == null) {
            return "INSERT";
        }
        final String oldHash = current.path("canonicalHash").asText();
        final String oldAt = current.path("observation").path("freshness").asText(null);
        final String candidateAt = observation.path("freshness").asText(null);
        if (domain.path("freshness").isObject()
                && current.path("domain").path("freshness").isObject()) {
            final int comparison =
                    freshness(domain.path("freshness"))
                            .compareTo(freshness(current.path("domain").path("freshness")));
            if (comparison < 0) {
                return "STALE_NO_OP";
            }
            if (comparison > 0) {
                return "UPDATE";
            }
            return oldHash.equals(hash) ? "REPLAY_NO_OP" : "EQUAL_FRESHNESS_CONFLICT";
        }
        if (candidateAt == null || oldAt == null) {
            return oldHash.equals(hash) ? "REPLAY_NO_OP" : "EQUAL_FRESHNESS_CONFLICT";
        }
        if ("Localizacao".equals(name)) {
            return LocalizacaoCargaFreshnessPolicy.decide(
                            Instant.parse(oldAt), oldHash, Instant.parse(candidateAt), hash)
                    .name();
        }
        return FreteFreshnessPolicy.decide(
                        Instant.parse(oldAt), oldHash, Instant.parse(candidateAt), hash)
                .name();
    }

    private static ExpansionFreshness freshness(final JsonNode value) {
        return new ExpansionFreshness(
                value.path("second").longValue(),
                value.path("nano").intValue(),
                value.path("exclusive").booleanValue());
    }

    static JsonNode relations(
            final JsonNode state, final JsonNode declarations, final JsonNode observations) {
        final ArrayNode result = DevLocalEngine.JSON.createArrayNode();
        if (declarations.isMissingNode()) {
            return result;
        }
        if (!declarations.isArray() || declarations.size() > 32) {
            throw new IllegalArgumentException("DEV_LOCAL_RELATION_BOUND");
        }
        if (!observations.isMissingNode()
                && (!observations.isArray() || observations.size() != declarations.size())) {
            throw new IllegalArgumentException("DEV_LOCAL_RELATION_OBSERVATIONS_BOUND");
        }
        final Set<String> identities = new HashSet<>();
        for (final JsonNode declaration : declarations) {
            final Set<String> fields =
                    Set.of(
                            "relation",
                            "originKey",
                            "originComponent",
                            "targetKey",
                            "targetComponent",
                            "evidence",
                            "revision",
                            "cardinality",
                            "date");
            if (!declaration.isObject() || declaration.size() != fields.size()) {
                throw new IllegalArgumentException("DEV_LOCAL_RELATION_SHAPE");
            }
            declaration
                    .fieldNames()
                    .forEachRemaining(
                            name -> {
                                if (!fields.contains(name)) {
                                    throw new IllegalArgumentException("DEV_LOCAL_RELATION_FIELD");
                                }
                            });
            if (!declaration.path("revision").isIntegralNumber()) {
                throw new IllegalArgumentException("DEV_LOCAL_RELATION_REVISION");
            }
            final var binding =
                    new RelationalBinding(
                            declaration.path("evidence").asText(),
                            RelationalBinding.Relation.valueOf(
                                    declaration.path("relation").asText()),
                            key(declaration.path("originKey").asText()),
                            key(declaration.path("originComponent").asText()),
                            key(declaration.path("targetKey").asText()),
                            key(declaration.path("targetComponent").asText()),
                            LocalDate.parse(declaration.path("date").asText()),
                            declaration.path("revision").intValue(),
                            RelationalBinding.Cardinality.valueOf(
                                    declaration.path("cardinality").asText()));
            final String identity =
                    binding.relation()
                            + "|"
                            + binding.origin().storage()
                            + "|"
                            + binding.originComponent().storage();
            if (!identities.add(identity)) {
                throw new IllegalArgumentException("DEV_LOCAL_RELATION_CONFLICT");
            }
            final String from =
                    binding.relation() == RelationalBinding.Relation.MC ? "Manifestos" : "Coletas";
            final String to =
                    binding.relation() == RelationalBinding.Relation.MC ? "Coletas" : "Fretes";
            final String observed = observation(observations, binding, from, to);
            for (int copy = 0; copy < state.path("scale").asInt(); copy++) {
                relation(result, state, copy, binding, from, to, observed);
            }
        }
        return result;
    }

    private static String observation(
            final JsonNode observations,
            final RelationalBinding binding,
            final String from,
            final String to) {
        if (observations.isMissingNode()) {
            return "EXPLICIT_ENGINE_ASSERTION_ONLY";
        }
        JsonNode found = null;
        for (final JsonNode observation : observations) {
            if (binding.evidenceId().equals(observation.path("evidence").asText())) {
                if (found != null) {
                    throw new IllegalArgumentException("DEV_LOCAL_RELATION_OBSERVATION_DUPLICATE");
                }
                found = observation;
            }
        }
        if (found == null
                || !found.isObject()
                || !DevLocalEngine.SCOPE.equals(found.path("identityScope").asText())
                || !binding.relation().name().equals(found.path("relation").asText())
                || !from.equals(found.path("originEntity").asText())
                || !to.equals(found.path("targetEntity").asText())
                || !binding.origin().storage().equals(found.path("originKey").asText())
                || !binding.target().storage().equals(found.path("targetKey").asText())
                || !Set.of(
                                "OBSERVED_SYNTHETIC_ROOTS",
                                "ORPHAN_BLOCKED",
                                "AMBIGUOUS_BLOCKED",
                                "OUTSIDE_WINDOW_BLOCKED",
                                "QUARANTINED_BLOCKED",
                                "DATE_MISMATCH_BLOCKED")
                        .contains(found.path("status").asText())) {
            throw new IllegalArgumentException("DEV_LOCAL_RELATION_OBSERVATION_BINDING");
        }
        for (final String count :
                List.of(
                        "originObservedRows",
                        "originEligibleRows",
                        "targetObservedRows",
                        "targetEligibleRows")) {
            if (!found.path(count).isIntegralNumber()
                    || !found.path(count).canConvertToInt()
                    || found.path(count).asInt() < 0
                    || found.path(count).asInt() > 16
                    || ("OBSERVED_SYNTHETIC_ROOTS".equals(found.path("status").asText())
                            && found.path(count).asInt() != 1)) {
                throw new IllegalArgumentException("DEV_LOCAL_RELATION_OBSERVATION_COUNTS");
            }
        }
        return found.path("status").asText();
    }

    private static RelationalBinding.Key key(final String value) {
        if ("ROOT".equals(value)) {
            return RelationalBinding.Key.root();
        }
        if (!value.matches("INTEGER:[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("DEV_LOCAL_RELATION_KEY");
        }
        return RelationalBinding.Key.integer(Long.parseLong(value.substring(8)));
    }

    private static void relation(
            final ArrayNode output,
            final JsonNode state,
            final int copy,
            final RelationalBinding binding,
            final String from,
            final String to,
            final String observed) {
        final JsonNode origin = root(state, from, copy, binding.origin().storage());
        final JsonNode target = root(state, to, copy, binding.target().storage());
        final ObjectNode row = output.addObject();
        row.put("relation", binding.relation().name());
        row.put("copy", copy);
        row.put("origin", binding.origin().storage());
        row.put("target", binding.target().storage());
        row.put("evidence", binding.evidenceId());
        row.put("cardinality", binding.cardinality().name());
        row.put("identityAuthority", "DECLARED_SYNTHETIC_BINDING");
        row.put("sourceObservation", observed);
        row.put(
                "status",
                origin == null || target == null
                        ? "ORPHAN_BLOCKED"
                        : "EXPLICIT_ENGINE_ASSERTION_ONLY".equals(observed)
                                        || "OBSERVED_SYNTHETIC_ROOTS".equals(observed)
                                ? "RESOLVED_LOCAL"
                                : observed);
    }

    static JsonNode dimensions(final JsonNode state, final long release) {
        final ArrayNode output = DevLocalEngine.JSON.createArrayNode();
        for (final JsonNode entity : state.path("entities")) {
            final String name = entity.path("entity").asText();
            for (final JsonNode row : entity.path("rows")) {
                if (!row.path("active").asBoolean()) {
                    continue;
                }
                final var identity =
                        UUID.nameUUIDFromBytes(
                                row.path("technicalKey").asText().getBytes(StandardCharsets.UTF_8));
                final var type =
                        switch (name) {
                            case "Users" -> AnalyticDimensionBinding.Entity.USUARIO;
                            case "CAP" -> AnalyticDimensionBinding.Entity.CAP;
                            case "Manifestos" -> AnalyticDimensionBinding.Entity.MAN;
                            case "Coletas" -> AnalyticDimensionBinding.Entity.COL;
                            case "Faturas" -> AnalyticDimensionBinding.Entity.FAT;
                            case "Fretes" -> AnalyticDimensionBinding.Entity.FRETE;
                            case "Localizacao" -> AnalyticDimensionBinding.Entity.LOC;
                            case "Cotacoes" -> AnalyticDimensionBinding.Entity.COT;
                            case "Inventario" -> AnalyticDimensionBinding.Entity.INV;
                            case "Sinistros" -> AnalyticDimensionBinding.Entity.SIN;
                            case "Raster" -> AnalyticDimensionBinding.Entity.RAS;
                            default ->
                                    throw new IllegalArgumentException(
                                            "DEV_LOCAL_DIMENSION_ENTITY");
                        };
                final AnalyticDimensionBinding.Role[] roles =
                        switch (name) {
                            case "Users" ->
                                    new AnalyticDimensionBinding.Role[] {
                                        AnalyticDimensionBinding.Role.USER
                                    };
                            case "CAP" ->
                                    new AnalyticDimensionBinding.Role[] {
                                        AnalyticDimensionBinding.Role.ACCOUNT,
                                        AnalyticDimensionBinding.Role.BRANCH
                                    };
                            case "Manifestos" ->
                                    new AnalyticDimensionBinding.Role[] {
                                        AnalyticDimensionBinding.Role.BRANCH,
                                        AnalyticDimensionBinding.Role.TRACTOR,
                                        AnalyticDimensionBinding.Role.DRIVER
                                    };
                            case "Coletas", "Faturas" ->
                                    new AnalyticDimensionBinding.Role[] {
                                        AnalyticDimensionBinding.Role.CLIENT,
                                        AnalyticDimensionBinding.Role.BRANCH
                                    };
                            case "Fretes" ->
                                    new AnalyticDimensionBinding.Role[] {
                                        AnalyticDimensionBinding.Role.PAYER,
                                        AnalyticDimensionBinding.Role.SENDER,
                                        AnalyticDimensionBinding.Role.RECIPIENT,
                                        AnalyticDimensionBinding.Role.BRANCH
                                    };
                            default -> new AnalyticDimensionBinding.Role[0];
                        };
                for (final var role : roles) {
                    final String dimension =
                            "synthetic-"
                                    + role.dimension().toLowerCase(java.util.Locale.ROOT)
                                    + "-"
                                    + row.path("copy").asInt();
                    final var binding =
                            new AnalyticDimensionBinding(
                                    type,
                                    row.path("sourceKey").asText(),
                                    identity,
                                    role,
                                    dimension,
                                    (int) release,
                                    DATE,
                                    DATE.plusDays(31),
                                    true,
                                    null,
                                    "synthetic-dev-declared-reference");
                    final ObjectNode projection = output.addObject();
                    projection.put("source", row.path("technicalKey").asText());
                    projection.put("dimension", role.dimension());
                    projection.put("entityKey", binding.entityKey());
                    projection.put("role", role.name());
                    projection.put("release", release);
                    projection.put("validFrom", binding.from().toString());
                    projection.put("validToExclusive", binding.toExclusive().toString());
                    projection.put("authority", "SYNTHETIC_DECLARED_REFERENCE");
                }
            }
        }
        return output;
    }

    static JsonNode facts(final JsonNode state, final long release) {
        final ArrayNode output = DevLocalEngine.JSON.createArrayNode();
        final String[] grains = {
            "sourceKey,indicator", "date,branch", "sourceKey", "sourceKey", "sourceKey"
        };
        final String[] dependencies = {
            "Fretes,Localizacao,referencias",
            "Fretes,Manifestos,Inventario,referencias",
            "Fretes,Localizacao,Faturas,referencias",
            "Faturas,politicaFiscal",
            "Manifestos,Coletas,Fretes,frota,MC,CF"
        };
        for (int index = 0; index < 5; index++) {
            final ObjectNode fact = output.addObject();
            fact.put("id", "MAT-0" + (index + 1));
            fact.put("referenceReleaseId", release);
            fact.put("status", "EXCLUIDO_DEV_LOCAL");
            fact.put("implementation", "SQL_ONLY");
            fact.put("reasonCode", "SQL_MATERIALIZER_REQUIRES_UNAUTHORIZED_SQL_ENGINE");
            fact.put("grain", grains[index]);
            fact.put("dependencies", dependencies[index]);
            fact.put("oracleSchema", "local-fact-oracles-v1");
            fact.put("oracleImplementation", "LocalFactOracle");
            fact.put("nominalSqlRulesProven", false);
            fact.put("computed", false);
            fact.put("applyCapability", false);
        }
        return output;
    }

    private static JsonNode root(
            final JsonNode state, final String entity, final int copy, final String sourceKey) {
        for (final JsonNode group : state.path("entities")) {
            if (!entity.equals(group.path("entity").asText())) {
                continue;
            }
            for (final JsonNode row : group.path("rows")) {
                if (row.path("active").asBoolean()
                        && row.path("copy").asInt() == copy
                        && sourceKey.equals(row.path("sourceKey").asText())) {
                    return row;
                }
            }
        }
        return null;
    }

    static JsonNode contracts() {
        final ArrayNode output = DevLocalEngine.JSON.createArrayNode();
        for (final AnalyticSqlContract contract : AnalyticSqlContract.values()) {
            final ObjectNode row = output.addObject();
            row.put("id", contract.id());
            row.put("name", contract.localName());
            row.put("columns", AnalyticSqlCatalog.columns(contract).size());
            row.put("order", contract.technicalOrder());
            row.put("revisionSelected", contract.revisionSelected());
            row.put("visibility", contract == AnalyticSqlContract.SQL_10 ? "INTERNAL" : "EXTERNAL");
            row.put(
                    "catalogSha256",
                    DevLocalLedger.sha256(
                            DevLocalEngine.bytes(
                                    DevLocalEngine.JSON.valueToTree(
                                            AnalyticSqlCatalog.columns(contract)))));
            row.put("evidence", "EXISTING_CLOSED_CATALOG_LOADED_NOT_SQL_EXECUTION");
        }
        return output;
    }
}
