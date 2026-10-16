package br.com.esl.etl.v2.bootstrap;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** Exact bounded local protocol: decimal values and scale survive persisted Engine readback. */
final class DevLocalMainJson {
    private DevLocalMainJson() {}

    static ObjectMapper mapper() {
        return JsonMapper.builder(
                        com.fasterxml.jackson.core.JsonFactory.builder()
                                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                .streamReadConstraints(
                                        StreamReadConstraints.builder()
                                                .maxNestingDepth(64)
                                                .maxStringLength(262144)
                                                .maxNumberLength(80)
                                                .build())
                                .build())
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
                .build();
    }
}
