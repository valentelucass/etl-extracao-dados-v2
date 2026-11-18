package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Typed consumer projection. Governance facts are explicit inputs; this class never publishes SQL.
 */
public final class IndependentDashboardBridge {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private IndependentDashboardBridge() {}

    public record PreparedRow(
            String target, Map<String, Object> columns, JsonNode governanceEvidence) {
        public PreparedRow {
            columns = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(columns));
            governanceEvidence = governanceEvidence.deepCopy();
        }

        @Override
        public JsonNode governanceEvidence() {
            return governanceEvidence.deepCopy();
        }

        @Override
        public String toString() {
            return "PreparedRow[target=" + target + ", columns=<private>]";
        }
    }

    public static ObjectNode contract() {
        return ExportValidationSupport.JSON
                .createObjectNode()
                .put("coletasTarget", "dbo.vw_coletas_powerbi")
                .put("coletasColumns", 21)
                .put("fretesTarget", "dbo.fato_fretes_faturamento")
                .put("sourceFretesScopeAxis", "freights.service_at")
                .put("consumerFretesPeriodAxis", "data_referencia_faturamento_date")
                .put("status", "PENDING_GOVERNED_FACTS_AND_ROOT_SQL_PUBLICATION")
                .put("distinctPeriodAxesPreserved", true)
                .put("SQLPublicationPerformed", false)
                .put("consumerRoundtripVerified", false)
                .put("mainAdopted", false);
    }

    /** Prepare a real typed staging artifact from explicitly bound source/governance rows. */
    public static ObjectNode prepare(
            final JsonNode pair, final ExportValidationSupport.Budget budget) throws Exception {
        final var planPin = ExportValidationSupport.Pin.from(pair.required("dashboardBridgePin"));
        final var plan = planPin.json(262144, budget);
        require(
                "INDEPENDENT_DASHBOARD_BRIDGE_PLAN_V1".equals(text(plan, "schema"))
                        && plan.required("requestPin").equals(pair.required("requestPin"))
                        && plan.required("apiDataPin").equals(pair.path("api").required("dataPin"))
                        && plan.required("uiDataPin").equals(pair.path("ui").required("dataPin")),
                "DASHBOARD_EXACT_PAIR_BRIDGE_BINDING");
        final var sourcePin = ExportValidationSupport.Pin.from(plan.required("rowsPin"));
        final byte[] input = sourcePin.read(8388608, budget);
        require(input[input.length - 1] == '\n', "DASHBOARD_CLOSED_ROWS_LF");
        final var rows = new java.util.ArrayList<PreparedRow>();
        try (var reader =
                new java.io.BufferedReader(
                        new java.io.InputStreamReader(
                                new java.io.ByteArrayInputStream(input),
                                java.nio.charset.StandardCharsets.UTF_8
                                        .newDecoder()
                                        .onMalformedInput(
                                                java.nio.charset.CodingErrorAction.REPORT)))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                budget.check();
                require(
                        !line.isBlank() && line.length() <= 262144 && rows.size() < 12000,
                        "DASHBOARD_ROWS_BOUND");
                final JsonNode record;
                try (var parser = ExportValidationSupport.JSON.createParser(line)) {
                    record = ExportValidationSupport.JSON.readTree(parser);
                    require(
                            record != null && parser.nextToken() == null,
                            "DASHBOARD_ROWS_TRAILING_JSON");
                }
                final var source = record.required("source");
                final var facts = record.required("governance");
                for (final String field :
                        List.of(
                                "exclusionEvidencePin",
                                "snapshotEvidencePin",
                                "sourceBindingEvidencePin")) {
                    ExportValidationSupport.Pin.from(facts.required(field)).json(262144, budget);
                }
                final boolean coletas =
                        ColetasIndependentRequest.PROFILE.equals(text(plan, "profile"));
                require(
                        coletas || IndependentExportRequest.PROFILE.equals(text(plan, "profile")),
                        "DASHBOARD_ENTITY_PROFILE");
                ExportValidationSupport.Pin.from(
                                facts.path(coletas ? "logisticsRegion" : "billing")
                                        .required("evidencePin"))
                        .json(262144, budget);
                rows.add(coletas ? coleta(source, facts) : frete(source, facts));
            }
        }
        requireUniquePublicationGrain(
                rows,
                ColetasIndependentRequest.PROFILE.equals(text(plan, "profile"))
                        ? "ID"
                        : "frete_id");
        final var destination = java.nio.file.Path.of(text(plan, "outputPath"));
        final var partial = destination.resolveSibling(destination.getFileName() + ".partial");
        require(
                destination.isAbsolute()
                        && !java.nio.file.Files.exists(destination)
                        && !java.nio.file.Files.exists(partial),
                "DASHBOARD_FRESH_PRIVATE_STAGE_PATH");
        for (var part = destination.getParent(); part != null; part = part.getParent()) {
            require(!java.nio.file.Files.isSymbolicLink(part), "DASHBOARD_STAGE_LINK");
        }
        final var digest = java.security.MessageDigest.getInstance("SHA-256");
        long bytes = 0;
        try (var file =
                java.nio.channels.FileChannel.open(
                        partial,
                        java.nio.file.StandardOpenOption.CREATE_NEW,
                        java.nio.file.StandardOpenOption.WRITE)) {
            for (final var row : rows) {
                final byte[] data =
                        (ExportValidationSupport.JSON.writeValueAsString(row) + "\n")
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                bytes += data.length;
                require(data.length <= 262144 && bytes <= 8388608, "DASHBOARD_STAGE_BYTE_BOUND");
                budget.check();
                digest.update(data);
                final var buffer = java.nio.ByteBuffer.wrap(data);
                while (buffer.hasRemaining()) {
                    file.write(buffer);
                }
            }
            file.force(true);
        }
        require(bytes > 0, "DASHBOARD_EMPTY_STAGE_REQUIRES_EXPLICIT_PUBLICATION_POLICY");
        budget.check();
        java.nio.file.Files.move(
                partial, destination, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        final var output =
                new ExportValidationSupport.Pin(
                        destination, java.util.HexFormat.of().formatHex(digest.digest()), bytes);
        output.read(8388608, budget);
        sourcePin.read(8388608, budget);
        planPin.read(262144, budget);
        final var result = contract().put("typedRowsPrepared", rows.size());
        result.set("stagePin", ExportValidationSupport.JSON.valueToTree(output));
        result.set("planPin", ExportValidationSupport.JSON.valueToTree(planPin));
        return result;
    }

    public static PreparedRow coleta(final JsonNode source, final JsonNode governance) {
        final String id = integral(source, "id").toString();
        governedIdentity(governance, id);
        final var requested = day(source, "request_date");
        final var region = governance.required("logisticsRegion");
        require(
                region.isObject()
                        && id.equals(text(region, "sourceKey"))
                        && region.required("postalCode")
                                .equals(source.required("pck_pds_postal_code"))
                        && region.required("city").equals(source.required("pck_pds_cty_name"))
                        && region.required("state").equals(source.required("pck_pds_cty_sae_code"))
                        && !requested.isBefore(LocalDate.parse(text(region, "validFrom")))
                        && !requested.isAfter(LocalDate.parse(text(region, "validThrough")))
                        && region.has("evidencePin"),
                "DASHBOARD_GOVERNED_LOGISTICS_REGION_REQUIRED");
        final var row = new LinkedHashMap<String, Object>();
        row.put("ID", id);
        row.put("Coleta", optionalLong(source, "sequence_code"));
        row.put("Solicitacao", requested);
        row.put("Agendamento", day(source, "service_date"));
        row.put("Finalizacao", day(source, "finish_date"));
        final String status = ColetasCommonAdapter.statusLabel(text(source, "status"));
        require(status != null, "DASHBOARD_STATUS_MAPPING_PENDING");
        row.put(
                "Status",
                governance.has("statusExcluded") && bool(governance, "statusExcluded")
                        ? "Excluída"
                        : status);
        row.put("Excluída na Origem", bool(governance, "excludedAtSource"));
        row.put("Volumes", optionalInteger(source, "invoices_volumes"));
        row.put("Peso Real", decimal(source, "invoices_weight"));
        row.put("Peso Taxado", decimal(source, "taxed_weight"));
        row.put("Valor NF", decimal(source, "invoices_value"));
        row.put(
                "Numero Manifesto",
                governance.has("canonicalManifestSequence")
                        ? optionalLong(governance, "canonicalManifestSequence")
                        : optionalLong(source, "pck_mik_mft_sequence_code"));
        for (final var mapping :
                Map.of(
                                "Cliente",
                                "pck_cor_nickname",
                                "Cidade",
                                "pck_pds_cty_name",
                                "UF",
                                "pck_pds_cty_sae_code",
                                "Região da Coleta",
                                "pck_prn_name",
                                "Filial",
                                "pck_crn_psn_nickname",
                                "Usuario",
                                "pck_uer_name",
                                "Motivo Cancel.",
                                "cancellation_reason")
                        .entrySet()) {
            row.put(mapping.getKey(), optionalText(source, mapping.getValue()));
        }
        row.put("Região Logística", text(region, "name"));
        row.put("Data de extracao", snapshot(governance));
        require(row.size() == 21, "DASHBOARD_ALL21_COLETAS_COLUMNS");
        return new PreparedRow("dbo.vw_coletas_powerbi", row, governance);
    }

    public static PreparedRow frete(final JsonNode source, final JsonNode governance) {
        final long id = integral(source, "id").longValueExact();
        governedIdentity(governance, Long.toString(id));
        require(
                source.path("service_at").isTextual()
                        && text(source, "service_at").equals(text(governance, "sourceServiceAt")),
                "DASHBOARD_SERVICE_AT_SOURCE_BINDING_REQUIRED");
        // These facts depend on billing/documents/materialization decisions. No service_at
        // fallback, zero tax/amount invention, updated_at snapshot, or status inference.
        final var billing = governance.required("billing");
        require(
                billing.isObject() && billing.has("evidencePin") && billing.has("governedRevision"),
                "DASHBOARD_GOVERNED_BILLING_REQUIRED");
        final var reference = OffsetDateTime.parse(text(billing, "referenceAt"));
        require(
                ZONE.getRules().getOffset(reference.toInstant()).equals(reference.getOffset()),
                "DASHBOARD_BILLING_ZONE");
        final var row = new LinkedHashMap<String, Object>();
        row.put("frete_id", id);
        row.put("data_frete", OffsetDateTime.parse(text(governance, "sourceServiceAt")));
        row.put("numero_minuta", optionalLong(source, "corporation_sequence_number"));
        row.put("receita_bruta", decimal(billing, "grossRevenue"));
        row.put("valor_frete", decimal(billing, "freightValue"));
        row.put(
                "volumes",
                governance.has("materializedVolumes")
                        ? optionalInteger(governance, "materializedVolumes")
                        : optionalInteger(source, "invoices_volumes"));
        row.put("peso_taxado", decimal(source, "taxed_weight"));
        final var fields =
                Map.ofEntries(
                        Map.entry("pagador_nome", "fit_pyr_name"),
                                Map.entry("remetente_nome", "fit_sdr_nickname"),
                        Map.entry("destinatario_nome", "fit_rpt_nickname"),
                                Map.entry("origem_uf", "fit_ony_sae_code"),
                        Map.entry("destino_uf", "fit_diy_sae_code"),
                                Map.entry("destino_cidade", "fit_dos_cty_name"),
                        Map.entry("filial_nome", "fit_crn_psn_nickname"),
                                Map.entry("classificacao_nome", "fit_fsn_name"));
        fields.forEach((target, field) -> row.put(target, optionalText(source, field)));
        if (governance.has("materializedBranchName")) {
            row.put("filial_nome", optionalText(governance, "materializedBranchName"));
        }
        for (final String field :
                List.of("responsavel_regiao_destino", "status_frete", "tipo_frete", "modal")) {
            row.put(field, optionalText(governance, field));
        }
        row.put("is_cortesia", bool(governance, "courtesy"));
        if (governance.has("responsavel_regiao_destino_key")) {
            row.put(
                    "responsavel_regiao_destino_key",
                    optionalText(governance, "responsavel_regiao_destino_key"));
        }
        row.put("is_elegivel_faturamento", bool(billing, "eligible"));
        row.put("data_referencia_faturamento", reference);
        row.put("data_referencia_faturamento_date", reference.toLocalDate());
        row.put("numero_cte", optionalLong(billing, "cteNumber"));
        row.put("cte_id", optionalLong(billing, "cteId"));
        row.put("nfse_number", optionalText(billing, "nfseNumber"));
        row.put(
                "data_emissao_cte",
                billing.path("cteIssuedAt").isNull()
                        ? null
                        : OffsetDateTime.parse(text(billing, "cteIssuedAt")));
        row.put("snapshot_em", snapshot(governance));
        row.put("excluido_na_origem", bool(governance, "excludedAtSource"));
        return new PreparedRow("dbo.fato_fretes_faturamento", row, governance);
    }

    /** Grain admission is explicit: expanded occurrences may never be silently collapsed by ID. */
    public static void requireUniquePublicationGrain(
            final List<PreparedRow> rows, final String idColumn) {
        final var ids = new java.util.HashSet<Object>();
        for (final var row : rows) {
            require(
                    ids.add(row.columns().get(idColumn)),
                    "DASHBOARD_EXPANDED_GRAIN_REQUIRES_GOVERNED_MATERIALIZATION");
        }
    }

    private static void governedIdentity(final JsonNode facts, final String id) {
        require(
                "INDEPENDENT_DASHBOARD_GOVERNANCE_V1".equals(text(facts, "schema"))
                        && id.equals(text(facts, "sourceKey"))
                        && facts.has("exclusionEvidencePin")
                        && facts.has("snapshotEvidencePin"),
                "DASHBOARD_INDEPENDENT_GOVERNANCE_REQUIRED");
    }

    private static LocalDateTime snapshot(final JsonNode facts) {
        return LocalDateTime.ofInstant(Instant.parse(text(facts, "publicationSnapshotUtc")), ZONE);
    }

    private static Boolean bool(final JsonNode source, final String field) {
        require(source.required(field).isBoolean(), "DASHBOARD_EXPLICIT_BOOLEAN_REQUIRED");
        return source.get(field).booleanValue();
    }

    private static LocalDate day(final JsonNode source, final String field) {
        final var value = source.required(field);
        return value.isNull()
                ? null
                : LocalDate.parse(QualifiedColetasModel.sourceCivil(text(source, field)));
    }

    private static String optionalText(final JsonNode source, final String field) {
        final var value = source.required(field);
        require(value.isNull() || value.isTextual(), "DASHBOARD_TEXT_NATIVE_TYPE");
        return value.isNull() ? null : value.textValue();
    }

    private static java.math.BigInteger integral(final JsonNode source, final String field) {
        final var value = source.required(field);
        require(value.isIntegralNumber(), "DASHBOARD_INTEGRAL_NATIVE_TYPE");
        return value.bigIntegerValue();
    }

    private static Long optionalLong(final JsonNode source, final String field) {
        return source.required(field).isNull() ? null : integral(source, field).longValueExact();
    }

    private static Integer optionalInteger(final JsonNode source, final String field) {
        return source.required(field).isNull() ? null : integral(source, field).intValueExact();
    }

    private static BigDecimal decimal(final JsonNode source, final String field) {
        final var value = source.required(field);
        if (value.isNull()) {
            return null;
        }
        require(value.isNumber() || value.isTextual(), "DASHBOARD_EXACT_DECIMAL_TYPE");
        ExactBinary64.ratio(value.asText());
        return new BigDecimal(value.asText());
    }
}
