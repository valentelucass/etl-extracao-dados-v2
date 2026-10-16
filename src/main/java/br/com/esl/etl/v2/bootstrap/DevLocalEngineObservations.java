package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.contasapagar.domain.ContaPagarObservation;
import br.com.esl.etl.v2.modulos.contasapagar.domain.ContaPagarRules;
import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteObservation;
import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules;
import br.com.esl.etl.v2.modulos.inventario.domain.InventarioObservation;
import br.com.esl.etl.v2.modulos.inventario.domain.InventarioRules;
import br.com.esl.etl.v2.modulos.sinistros.domain.SinistroObservation;
import br.com.esl.etl.v2.modulos.sinistros.domain.SinistroRules;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionFreshness;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionStrings;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;

/** Rehydrates the closed typed projection, then calls existing pure expansion rules. */
final class DevLocalEngineObservations {
    private DevLocalEngineObservations() {}

    static JsonNode rules(final String entity, final JsonNode row) {
        final ObjectNode result = DevLocalEngine.JSON.createObjectNode();
        ExpansionFreshness freshness = null;
        switch (entity) {
            case "CAP" -> {
                final var observation = decode(ContaPagarObservation.class, row.path("values"));
                freshness = ContaPagarRules.freshness(observation);
                result.put("payment", ContaPagarRules.payment(observation));
                result.put("typeToken", ContaPagarRules.typeToken(observation.type().value()));
            }
            case "Faturas" -> {
                final var observation = decode(FaturaClienteObservation.class, row.path("values"));
                freshness = FaturaClienteRules.freshness(observation);
                result.put(
                        "realDocument",
                        FaturaClienteRules.realDocument(observation.fitAntDocument().value()));
                result.put("cnpj", FaturaClienteRules.cnpj(observation.fitPyrDocument().value()));
                result.put(
                        "freightType", FaturaClienteRules.freightType(observation.type().value()));
            }
            case "Inventario" -> {
                final var observation = decode(InventarioObservation.class, row.path("values"));
                freshness = InventarioRules.freshness(observation);
                result.put(
                        "proofAttached",
                        InventarioRules.proofAttached(
                                observation.cnrCSFitFteLceOreDescription().value()));
            }
            case "Sinistros" ->
                    freshness =
                            SinistroRules.freshness(
                                    decode(SinistroObservation.class, row.path("values")));
            default -> result.put("ruleAuthority", "SOURCE_CHARACTERIZER");
        }
        if (freshness != null) {
            final ObjectNode value = result.putObject("freshness");
            value.put("second", freshness.second());
            value.put("nano", freshness.nano());
            value.put("exclusive", freshness.exclusive());
        }
        return result;
    }

    private static <T> T decode(final Class<T> type, final JsonNode fields) {
        final var components = type.getRecordComponents();
        if (!fields.isObject() || fields.size() != components.length) {
            throw new IllegalArgumentException("DEV_LOCAL_TYPED_FIELDS");
        }
        final Object[] values = new Object[components.length];
        final Class<?>[] parameters = new Class<?>[components.length];
        try {
            for (int index = 0; index < components.length; index++) {
                final var component = components[index];
                final JsonNode field = fields.path(component.getName());
                final Class<?> valueType =
                        (Class<?>)
                                ((ParameterizedType) component.getGenericType())
                                        .getActualTypeArguments()[0];
                final var presence =
                        ExpansionValue.Presence.valueOf(field.path("presence").asText());
                final var wire = ExpansionValue.Wire.valueOf(field.path("wire").asText());
                final String raw = field.path("raw").asText(null);
                final String issue = field.path("issue").asText(null);
                final Object value =
                        presence != ExpansionValue.Presence.VALUE || issue != null
                                ? null
                                : typed(valueType, field.path("typed").asText(), raw);
                values[index] = new ExpansionValue<>(presence, wire, raw, value, issue);
                parameters[index] = ExpansionValue.class;
            }
            return type.getConstructor(parameters).newInstance(values);
        } catch (final ReflectiveOperationException | IOException failure) {
            throw new IllegalArgumentException("DEV_LOCAL_TYPED_PROJECTION", failure);
        }
    }

    private static Object typed(final Class<?> type, final String value, final String raw)
            throws IOException {
        if (type == String.class) {
            return value;
        }
        if (type == BigDecimal.class) {
            return new BigDecimal(value);
        }
        if (type == Instant.class) {
            return Instant.parse(value);
        }
        if (type == LocalDate.class) {
            return LocalDate.parse(value);
        }
        if (type == Long.class) {
            return Long.valueOf(value);
        }
        if (type == Integer.class) {
            return Integer.valueOf(value);
        }
        if (type == Boolean.class && (value.equals("true") || value.equals("false"))) {
            return Boolean.valueOf(value);
        }
        if (type == ExpansionStrings.class) {
            final JsonNode array = DevLocalEngine.JSON.readTree(raw);
            if (!array.isArray() || array.size() > 32) {
                throw new IllegalArgumentException("DEV_LOCAL_TYPED_ARRAY");
            }
            final var strings = new ArrayList<String>();
            for (final JsonNode item : array) {
                if (!item.isTextual()) {
                    throw new IllegalArgumentException("DEV_LOCAL_TYPED_ARRAY");
                }
                strings.add(item.textValue());
            }
            return new ExpansionStrings(strings);
        }
        throw new IllegalArgumentException("DEV_LOCAL_TYPED_TYPE");
    }
}
