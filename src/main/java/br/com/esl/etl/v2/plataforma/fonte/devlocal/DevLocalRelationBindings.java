package br.com.esl.etl.v2.plataforma.fonte.devlocal;

import br.com.esl.etl.v2.plataforma.relacional.RelationalBinding;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

/** DEV-SOURCE-002: declared synthetic assertions and observed roots confer no nominal identity. */
final class DevLocalRelationBindings {
    static final int MAX_BINDINGS = 32;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> FIELDS =
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

    private DevLocalRelationBindings() {}

    static ObjectNode characterize(
            final JsonNode declarations,
            final JsonNode entities,
            final Instant start,
            final Instant end) {
        final ObjectNode result = JSON.createObjectNode();
        final ArrayNode bindings = result.putArray("relationBindings");
        final ArrayNode observations = result.putArray("relationBindingObservations");
        if (declarations.isMissingNode()) {
            return result;
        }
        if (!declarations.isArray() || declarations.size() > MAX_BINDINGS) {
            throw rejected("DEV_RELATION_BOUND");
        }
        final Set<String> evidence = new HashSet<>();
        final Set<String> origins = new HashSet<>();
        for (final JsonNode declaration : declarations) {
            schema(declaration);
            final RelationalBinding binding = binding(declaration);
            if (binding.targetDate().isBefore(start.atOffset(ZoneOffset.UTC).toLocalDate())
                    || binding.targetDate()
                            .isAfter(end.minusNanos(1).atOffset(ZoneOffset.UTC).toLocalDate())) {
                throw rejected("DEV_RELATION_DATE_BOUND");
            }
            final String origin =
                    binding.relation()
                            + "|"
                            + binding.origin().storage()
                            + "|"
                            + binding.originComponent().storage();
            if (!evidence.add(binding.evidenceId()) || !origins.add(origin)) {
                throw rejected("DEV_RELATION_CONFLICT");
            }
            final ObjectNode validated = bindings.addObject();
            validated.put("relation", binding.relation().name());
            validated.put("originKey", binding.origin().storage());
            validated.put("originComponent", binding.originComponent().storage());
            validated.put("targetKey", binding.target().storage());
            validated.put("targetComponent", binding.targetComponent().storage());
            validated.put("evidence", binding.evidenceId());
            validated.put("revision", binding.revision());
            validated.put("cardinality", binding.cardinality().name());
            validated.put("date", binding.targetDate().toString());
            observations.add(observe(entities, binding));
        }
        return result;
    }

    private static void schema(final JsonNode declaration) {
        if (!declaration.isObject() || declaration.size() != FIELDS.size()) {
            throw rejected("DEV_RELATION_SCHEMA");
        }
        final var names = declaration.fieldNames();
        while (names.hasNext()) {
            final String name = names.next();
            final JsonNode value = declaration.get(name);
            if (!FIELDS.contains(name)) {
                throw rejected("DEV_RELATION_SCHEMA");
            }
            if ("revision".equals(name)) {
                if (!value.isIntegralNumber()
                        || !value.canConvertToInt()
                        || value.intValue() != 1) {
                    throw rejected("DEV_RELATION_SCHEMA");
                }
            } else if (!value.isTextual() || value.textValue().length() > 120) {
                throw rejected("DEV_RELATION_SCHEMA");
            }
        }
    }

    private static RelationalBinding binding(final JsonNode declaration) {
        try {
            final String date = declaration.get("date").textValue();
            if (!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                throw rejected("DEV_RELATION_DECLARATION");
            }
            return new RelationalBinding(
                    declaration.get("evidence").textValue(),
                    RelationalBinding.Relation.valueOf(declaration.get("relation").textValue()),
                    key(declaration.get("originKey").textValue()),
                    key(declaration.get("originComponent").textValue()),
                    key(declaration.get("targetKey").textValue()),
                    key(declaration.get("targetComponent").textValue()),
                    LocalDate.parse(date),
                    declaration.get("revision").intValue(),
                    RelationalBinding.Cardinality.valueOf(
                            declaration.get("cardinality").textValue()));
        } catch (final IllegalArgumentException | DateTimeException failure) {
            throw rejected("DEV_RELATION_DECLARATION");
        }
    }

    private static RelationalBinding.Key key(final String storage) {
        if ("ROOT".equals(storage)) {
            return RelationalBinding.Key.root();
        }
        if (!storage.matches("INTEGER:[1-9][0-9]{0,18}")) {
            throw rejected("DEV_RELATION_DECLARATION");
        }
        return RelationalBinding.Key.integer(
                Long.parseLong(storage.substring("INTEGER:".length())));
    }

    private static ObjectNode observe(final JsonNode entities, final RelationalBinding binding) {
        final String from =
                binding.relation() == RelationalBinding.Relation.MC ? "Manifestos" : "Coletas";
        final String to =
                binding.relation() == RelationalBinding.Relation.MC ? "Coletas" : "Fretes";
        final RootObservation origin = root(entities, from, binding.origin().storage());
        final RootObservation target = root(entities, to, binding.target().storage());
        final ObjectNode observed = JSON.createObjectNode();
        observed.put("relation", binding.relation().name());
        observed.put("evidence", binding.evidenceId());
        observed.put("identityScope", "SYNTHETIC_LOCAL_ONLY");
        observed.put("originEntity", from);
        observed.put("originKey", binding.origin().storage());
        observed.put("originObservedRows", origin.observed());
        observed.put("originEligibleRows", origin.eligible());
        observed.put("targetEntity", to);
        observed.put("targetKey", binding.target().storage());
        observed.put("targetObservedRows", target.observed());
        observed.put("targetEligibleRows", target.eligible());
        observed.put("componentAuthority", "DECLARED_SYNTHETIC_BINDING");
        observed.put("canonicalIdentityProven", false);
        observed.put("status", status(origin, target, binding.targetDate()));
        return observed;
    }

    private static RootObservation root(
            final JsonNode entities, final String name, final String key) {
        int observed = 0;
        int eligible = 0;
        int quarantined = 0;
        JsonNode unique = null;
        for (final JsonNode entity : entities) {
            if (!name.equals(entity.path("entity").asText())) {
                continue;
            }
            for (final JsonNode row : entity.path("rows")) {
                if (key.equals(row.path("sourceKey").asText())) {
                    observed++;
                    eligible += row.path("eligible").asBoolean() ? 1 : 0;
                    quarantined += row.path("valid").asBoolean() ? 0 : 1;
                    unique = row;
                }
            }
        }
        return new RootObservation(observed, eligible, quarantined, unique);
    }

    private static String status(
            final RootObservation origin, final RootObservation target, final LocalDate date) {
        if (origin.observed() == 0 || target.observed() == 0) {
            return "ORPHAN_BLOCKED";
        }
        if (origin.observed() != 1 || target.observed() != 1) {
            return "AMBIGUOUS_BLOCKED";
        }
        if (origin.quarantined() != 0 || target.quarantined() != 0) {
            return "QUARANTINED_BLOCKED";
        }
        if (origin.eligible() == 0 || target.eligible() == 0) {
            return "OUTSIDE_WINDOW_BLOCKED";
        }
        final String freshness = target.unique().path("freshness").asText(null);
        if (freshness == null
                || !Instant.parse(freshness).atOffset(ZoneOffset.UTC).toLocalDate().equals(date)) {
            return "DATE_MISMATCH_BLOCKED";
        }
        return "OBSERVED_SYNTHETIC_ROOTS";
    }

    private record RootObservation(int observed, int eligible, int quarantined, JsonNode unique) {}

    private static IllegalArgumentException rejected(final String code) {
        return new IllegalArgumentException(code);
    }
}
