package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionBinding;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionCaptured;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionKey;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionObservation;
import com.fasterxml.jackson.databind.JsonNode;

/** Parses the lateral binding; it never inserts or changes a field inside data. */
public final class ExpansionEnvelope {
    private ExpansionEnvelope() {}

    public static <T extends ExpansionObservation> ExpansionCaptured<T> capture(
            final JsonNode envelope, final T observation) {
        if (!envelope.path("capture_occurrence").canConvertToLong()
                || !"FIXTURE_SINTETICA_EXPLICITA".equals(envelope.path("provenance").asText())) {
            throw new IllegalArgumentException("EXP_CAPTURE_ENVELOPE");
        }
        final JsonNode binding = envelope.get("binding");
        final ExpansionBinding declared;
        if (binding == null || binding.isNull()) {
            declared = null;
        } else {
            if (!binding.isObject()
                    || !binding.path("revision").isInt()
                    || !binding.path("active").isBoolean()
                    || !binding.path("reactivation").isBoolean()
                    || !binding.path("additive_allocation").isBoolean()) {
                throw new IllegalArgumentException("EXP_BINDING_SHAPE");
            }
            declared =
                    new ExpansionBinding(
                            key(binding, "root"),
                            key(binding, "part"),
                            key(binding, "component"),
                            binding.path("revision").intValue(),
                            binding.path("evidence").asText(),
                            binding.path("currency").asText(),
                            binding.path("unit").asText(),
                            binding.path("additive_allocation").booleanValue(),
                            binding.path("active").booleanValue(),
                            binding.path("reactivation").booleanValue());
        }
        return new ExpansionCaptured<>(
                envelope.path("capture_occurrence").longValue(), observation, declared);
    }

    private static ExpansionKey key(final JsonNode binding, final String name) {
        final var node = binding.path(name);
        if (!node.isIntegralNumber() && !node.isTextual()) {
            throw new IllegalArgumentException("EXP_BINDING_KEY");
        }
        return new ExpansionKey(
                node.isIntegralNumber() ? ExpansionKey.Kind.INTEGER : ExpansionKey.Kind.STRING,
                node.asText());
    }
}
