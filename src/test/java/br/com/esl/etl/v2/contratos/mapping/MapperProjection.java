package br.com.esl.etl.v2.contratos.mapping;

import br.com.esl.etl.v2.modulos.cotacoes.aplicacao.CotacaoDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.fretes.aplicacao.FreteDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.LocalizacaoCargaDataExportRecordMapper;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.CharacterizationParserAccess;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Values live only during one row comparison; reports never serialize this projection. */
public final class MapperProjection {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> FRETE_FIELDS =
            List.of(
                    "id",
                    "updated_at",
                    "reference_number",
                    "fit_p_m_pck_sequence_code",
                    "corporation_sequence_number",
                    "finished_at",
                    "fit_dpn_performance_finished_at",
                    "status",
                    "cte_created_at",
                    "cte_issued_at",
                    "criado_em",
                    "servico_em",
                    "cte",
                    "ctes",
                    "cte_key",
                    "finalizations",
                    "finalizacoes",
                    "total");
    private static final List<String> COTACAO_FIELDS =
            List.of(
                    "sequence_code",
                    "requested_at",
                    "qoe_qes_fit_nse_issued_at",
                    "qoe_qes_fit_fhe_cte_issued_at",
                    "qoe_qes_total",
                    "qoe_crn_psn_nickname",
                    "qoe_uer_name",
                    "qoe_qes_ony_sae_code",
                    "qoe_qes_diy_sae_code");

    private MapperProjection() {}

    public enum Entity {
        COTACOES("COT-01/COT-02"),
        LOCALIZACAO_CARGAS("LOC-01/LOC-02/LOC-03/LOC-04/LOC-05/LOC-06/LOC-07"),
        LOCALIZACAO_CARGAS_PIPELINE("LOC-01/LOC-02/LOC-03/LOC-04/LOC-05/LOC-06/LOC-07"),
        FRETES("FRE-01/FRE-02/FRE-03/FRE-04/FRE-05/FRE-06/FRE-07");

        private final String rule;

        Entity(final String rule) {
            this.rule = rule;
        }

        String rule() {
            return rule;
        }
    }

    static Map<String, String> observe(
            final Entity entity, final int ordinal, final JsonNode row, final String rawRow) {
        return switch (entity) {
            case COTACOES -> cotacao(ordinal, row);
            case LOCALIZACAO_CARGAS -> localizacao(ordinal, row, rawRow);
            case LOCALIZACAO_CARGAS_PIPELINE -> localizacao(ordinal, row, null);
            case FRETES -> frete(ordinal, row);
        };
    }

    private static Map<String, String> frete(final int ordinal, final JsonNode row) {
        final Map<String, String> values = new TreeMap<>();
        fields(values, row, FRETE_FIELDS);
        // The two-argument mapper supplies an absent sidecar; no GraphQL input or adapter here.
        final var mapped = new FreteDataExportRecordMapper().map(ordinal, row);
        put(values, "/quarantine", mapped.quarantined() ? mapped.quarantineReasonCode() : "NONE");
        put(
                values,
                "/id/typed",
                mapped.sourceKey() == null ? null : mapped.sourceKey().storageValue());
        put(values, "/freshness/typed", mapped.freshnessAtUtc());
        put(values, "/freshness/origin", mapped.freshnessOrigin());
        put(values, "/servico_em/typed", mapped.serviceAtUtc());
        put(values, "/performance/typed", mapped.performanceAtUtc());
        put(values, "/performance/origin", mapped.performanceOrigin());
        put(values, "/status/typed", mapped.statusCode());
        put(values, "/status/raw", mapped.statusRaw());
        put(values, "/status/label", mapped.statusLabel());
        put(values, "/status/terminal", mapped.terminal());
        final JsonNode presence = parse(mapped.fieldPresenceJson());
        for (final String field : FRETE_FIELDS) {
            put(
                    values,
                    "/" + field + "/mappedPresence",
                    presence.path(field).asText("NOT_AVAILABLE"));
        }
        final JsonNode freshness = parse(mapped.freshnessEvidenceJson());
        for (final String field :
                List.of("cte_created_at", "cte_issued_at", "criado_em", "servico_em")) {
            put(
                    values,
                    "/" + field + "/parseState",
                    freshness.path(field).path("parseState").asText("NOT_AVAILABLE"));
        }
        put(values, "/freshness/proof", freshness.path("evidenceScope").asText("NOT_AVAILABLE"));
        put(values, "/updated_at/policy", freshness.path("updated_at").asText("NOT_AVAILABLE"));
        final JsonNode performance = parse(mapped.performanceEvidenceJson());
        put(
                values,
                "/performance/parseState",
                performance.path("parseState").asText("NOT_AVAILABLE"));
        put(
                values,
                "/performance/officialProvenance",
                performance.path("official").path("provenance").asText("NOT_AVAILABLE"));
        put(
                values,
                "/performance/fallbackProvenance",
                performance.path("fallback").path("provenance").asText("NOT_AVAILABLE"));
        final JsonNode financial = parse(mapped.financialJson());
        put(values, "/total/preserved", financial.path("total").path("raw").toString());
        put(
                values,
                "/total/typed",
                financial.path("total").path("typedDecimal").asText("NOT_AVAILABLE"));
        put(
                values,
                "/financial/currencyPolicy",
                financial.path("currency").asText("NOT_AVAILABLE"));
        put(
                values,
                "/financial/arithmeticPolicy",
                financial.path("arithmetic").asText("NOT_AVAILABLE"));
        final JsonNode candidates = parse(mapped.relationCandidatesJson());
        put(values, "/relation/policy", candidates.path("policy").asText("NOT_AVAILABLE"));
        put(
                values,
                "/sidecar/presence",
                parse(mapped.sidecarJson()).path("edgesPresence").asText("NOT_AVAILABLE"));
        return values;
    }

    private static Map<String, String> localizacao(
            final int ordinal, final JsonNode row, final String rawRow) {
        final Map<String, String> values = new TreeMap<>();
        fields(values, row, LocalizacaoCargaDataExportRecordMapper.ACCEPTED_FIELDS);
        final var mapper = new LocalizacaoCargaDataExportRecordMapper();
        final var mapped = rawRow == null ? mapper.map(ordinal, row) : mapper.map(ordinal, rawRow);
        put(values, "/quarantine", mapped.quarantined() ? mapped.quarantineReasonCode() : "NONE");
        put(
                values,
                "/corporation_sequence_number/typed",
                mapped.sourceKey() == null ? null : mapped.sourceKey().storageValue());
        put(values, "/service_at/typed", mapped.serviceAtUtc());
        put(values, "/service_at/parseState", mapped.serviceAtParseState());
        put(values, "/invoices_volumes/typed", mapped.invoicesVolumes());
        put(values, "/invoices_volumes/parseState", mapped.invoicesVolumesParseState());
        put(values, "/taxed_weight/typed", mapped.taxedWeight());
        put(values, "/invoices_value/typed", mapped.invoicesValue());
        put(values, "/total/typed", mapped.total());
        put(values, "/fit_fln_status/typed", mapped.statusNormalized());
        put(values, "/fit_fln_status/terminal", mapped.statusTerminal());
        put(values, "/fit_fln_status/raw", mapped.statusRaw());
        put(values, "/status_branch_nickname/provenance", mapped.statusBranchNicknameProvenance());
        final JsonNode evidence = parse(mapped.fieldPresenceJson());
        put(
                values,
                "/status_branch_nickname/presence",
                evidence.path("policy")
                        .path("statusBranchNickname")
                        .path("presence")
                        .asText("NOT_AVAILABLE"));
        put(
                values,
                "/invoices_volumes/fallback",
                evidence.path("policy").path("freightVolumeFallback").asText("NOT_AVAILABLE"));
        for (final String field : LocalizacaoCargaDataExportRecordMapper.ACCEPTED_FIELDS) {
            final JsonNode details = evidence.path("fields").path(field);
            for (final String attribute :
                    List.of("presence", "path", "provenance", "rawWireLexeme")) {
                put(
                        values,
                        "/"
                                + field
                                + "/"
                                + (attribute.equals("presence") ? "mappedPresence" : attribute),
                        details.path(attribute).asText("NOT_AVAILABLE"));
            }
        }
        return values;
    }

    private static Map<String, String> cotacao(final int ordinal, final JsonNode row) {
        final Map<String, String> values = new TreeMap<>();
        fields(values, row, COTACAO_FIELDS);
        final var mapped = new CotacaoDataExportRecordMapper().map(ordinal, row);
        put(values, "/quarantine", mapped.quarantined() ? mapped.quarantineReasonCode() : "NONE");
        put(
                values,
                "/sequence_code/typed",
                mapped.sourceKey() == null ? null : mapped.sourceKey().storageValue());
        put(values, "/requested_at/typed", mapped.requestedAtUtc());
        put(values, "/qoe_qes_fit_nse_issued_at/typed", mapped.nfseIssuedAtUtc());
        put(values, "/qoe_qes_fit_fhe_cte_issued_at/typed", mapped.cteIssuedAtUtc());
        put(values, "/qoe_qes_total/typed", mapped.totalAmount());
        put(values, "/qoe_uer_name/typed", mapped.userNameNormalized());
        put(values, "/qoe_qes_ony_sae_code/typed", mapped.originUf());
        put(values, "/qoe_qes_diy_sae_code/typed", mapped.destinationUf());
        put(values, "/freshness/typed", mapped.freshnessAtUtc());
        put(values, "/freshness/origin", mapped.freshnessOrigin());
        put(values, "/freshness/businessDate", mapped.freshnessBusinessDate());
        final JsonNode payload = parse(mapped.payloadJson());
        put(
                values,
                "/qoe_crn_psn_nickname/preserved",
                payload.path("qoe_crn_psn_nickname").toString());
        final JsonNode presence = parse(mapped.fieldPresenceJson());
        for (final String field : COTACAO_FIELDS) {
            put(
                    values,
                    "/" + field + "/mappedPresence",
                    presence.path(field).asText("NOT_AVAILABLE"));
        }
        return values;
    }

    private static void fields(
            final Map<String, String> values, final JsonNode row, final List<String> fields) {
        for (final String field : fields) {
            final JsonNode value = row.path(field);
            put(
                    values,
                    "/" + field + "/presence",
                    value.isMissingNode() ? "ABSENT" : value.isNull() ? "NULL" : "VALUE");
            put(
                    values,
                    "/" + field + "/wireType",
                    value.isMissingNode()
                            ? "ABSENT"
                            : value.isIntegralNumber()
                                    ? "INTEGER"
                                    : value.isFloatingPointNumber()
                                            ? "DECIMAL"
                                            : value.getNodeType().name());
        }
    }

    private static void put(
            final Map<String, String> values, final String path, final Object value) {
        values.put(path, value == null ? "NULL" : value.toString());
    }

    private static JsonNode parse(final String document) {
        try {
            return document == null
                    ? JSON.createObjectNode()
                    : CharacterizationParserAccess.parse(document);
        } catch (final java.io.IOException error) {
            throw new IllegalStateException("Invalid mapper evidence");
        }
    }
}
