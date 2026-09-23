package br.com.esl.etl.v2.contratos.bloco58;

import static br.com.esl.etl.v2.contratos.bloco58.LocalCharacterization.put;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageRecord;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.CharacterizationParserAccess;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Projection of the actual typed record, also used by the synchronous staging sink. */
public final class ColetasProjection {
    public static final List<String> FIELDS =
            List.of(
                    "id",
                    "sequence_code",
                    "status",
                    "status_updated_at",
                    "finish_date",
                    "service_date",
                    "request_date",
                    "updated_at",
                    "cancellation_reason",
                    "manifesto",
                    "pick_item_id",
                    "fit_p_m_pck_sequence_code",
                    "frete",
                    "pck_mik_mft_sequence_code");

    private ColetasProjection() {}

    public static Map<String, String> observe(final ColetaStageRecord record) {
        final Map<String, String> result = new TreeMap<>();
        put(
                result,
                "/quarantine",
                record.quarantineReasonCode() == null ? "NONE" : record.quarantineReasonCode());
        put(
                result,
                "/id/typed",
                record.sourceKey() == null ? null : record.sourceKey().storageValue());
        put(result, "/id/type", record.sourceKey() == null ? null : record.sourceKey().wireType());
        put(result, "/sequence_code/typed", record.sequenceCodeJson());
        put(result, "/sequence_code/mappedPresence", record.sequenceCodePresence());
        put(result, "/freshness/raw", record.freshnessRaw());
        put(result, "/freshness/typed", record.freshnessAtUtc());
        put(result, "/freshness/origin", record.freshnessOrigin());
        final var status = record.status();
        put(result, "/status/raw", status == null ? null : status.raw());
        put(result, "/status/code", status == null ? null : status.code());
        put(result, "/status/label", status == null ? null : status.label());
        put(result, "/status/terminal", status == null ? null : status.terminal());
        put(result, "/status/action", status == null ? null : status.occurrenceAction());
        put(result, "/status/attempts", status == null ? null : status.attempts());
        final JsonNode payload = parse(record.payloadJson());
        final JsonNode presence = parse(record.fieldPresenceJson());
        final JsonNode candidates = parse(record.relationCandidatesJson());
        for (final String field : FIELDS) {
            LocalCharacterization.field(result, "/" + field, payload.path(field));
            put(
                    result,
                    "/" + field + "/mappedPresence",
                    presence.path(field).asText("NOT_AVAILABLE"));
            put(result, "/" + field + "/preserved", payload.path(field).toString());
        }
        for (final String field :
                List.of(
                        "manifesto",
                        "pick_item_id",
                        "fit_p_m_pck_sequence_code",
                        "frete",
                        "pck_mik_mft_sequence_code")) {
            put(
                    result,
                    "/" + field + "/candidatePresence",
                    candidates.path(field).path("presence").asText("NOT_AVAILABLE"));
            put(
                    result,
                    "/" + field + "/candidateValue",
                    candidates.path(field).path("value").toString());
        }
        return result;
    }

    private static JsonNode parse(final String value) {
        try {
            return CharacterizationParserAccess.parse(value == null ? "{}" : value);
        } catch (final java.io.IOException error) {
            throw new IllegalStateException("MAPPER_EVIDENCE_INVALID");
        }
    }
}
