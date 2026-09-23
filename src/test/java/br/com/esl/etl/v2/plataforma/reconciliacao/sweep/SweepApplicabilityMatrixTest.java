package br.com.esl.etl.v2.plataforma.reconciliacao.sweep;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SweepApplicabilityMatrixTest {
    private static final Path MATRIX =
            Path.of("docs/catalogos/sweep-v2-013/matriz-aplicabilidade-v01.csv");
    private static final String MATRIX_SHA256 =
            "69ef33c8c095c200f4025264e38c0dd248fa4f12bde8722aaa00101c570d3bd9";
    private static final String GUARDRAILS =
            "PREVIEW_ONLY+NO_APPLY+NO_DELETE+NO_DEACTIVATE+NO_PRUNE+NO_PERSIST+"
                    + "NO_ENTITY_ENABLEMENT+BLOCK_TIMEOUT+BLOCK_MISSING_PAGE+BLOCK_CAP+"
                    + "BLOCK_ANOMALOUS_EMPTY+BLOCK_INVALIDS+BLOCK_QUARANTINE+"
                    + "BLOCK_STRONG_VOLUME_DROP+BLOCK_HISTORY_GAP";

    @Test
    void matrixIsHashPinnedClosedWorldAndContainsEveryDeclaredResponsibility() throws Exception {
        final byte[] bytes = Files.readAllBytes(MATRIX);
        assertEquals(
                MATRIX_SHA256,
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        final String csv =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
        assertFalse(csv.startsWith("\uFEFF"));
        assertFalse(csv.contains("\r"));
        assertTrue(csv.endsWith("\n"));
        assertFalse(csv.contains("\""), "A matriz gerada não deve precisar de CSV quoting.");

        final List<String> lines = csv.lines().toList();
        final List<String> header = List.of(lines.get(0).split(",", -1));
        assertEquals(
                List.of(
                        "row_id",
                        "family",
                        "responsibility",
                        "row_kind",
                        "parent_row_id",
                        "applicability",
                        "completeness_status",
                        "owner_status",
                        "hierarchy_policy",
                        "presence_key_path",
                        "presence_key_value",
                        "presence_key_status",
                        "confirmation_policy",
                        "guardrail_policy",
                        "reason_code",
                        "evidence_anchor"),
                header);

        final List<Map<String, String>> rows =
                lines.stream().skip(1).map(line -> row(header, line)).toList();
        assertEquals(33, rows.size());
        assertEquals(EXPECTED_IDS, values(rows, "row_id"));
        assertEquals(EXPECTED_FAMILIES, values(rows, "family"));
        assertEquals(
                Arrays.stream(SweepScope.ResponsibilityKind.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet()),
                values(rows, "row_kind"));
        assertTrue(
                Set.of("ENABLED", "DISABLED", "BLOCKED", "NOT_APPLICABLE")
                        .containsAll(values(rows, "applicability")));
        assertFalse(values(rows, "applicability").contains("ENABLED"));
        assertEquals(Set.of("BLOCKED_NO_COMPLETENESS_PROOF"), values(rows, "completeness_status"));

        final Set<String> ids = values(rows, "row_id");
        for (final Map<String, String> item : rows) {
            assertEquals(GUARDRAILS, item.get("guardrail_policy"));
            assertFalse(item.get("evidence_anchor").isBlank());
            if (!item.get("parent_row_id").isBlank()) {
                assertTrue(ids.contains(item.get("parent_row_id")));
                assertFalse(item.get("row_id").equals(item.get("parent_row_id")));
            }
            if (item.get("applicability").equals("DISABLED")) {
                assertFalse(item.get("reason_code").equals("ENTITY_SWEEP_GATE_OPEN"));
            }
        }
    }

    @Test
    void presenceLocatorsAndDeferredCandidatesRemainExplicitAndNonAuthoritative() throws Exception {
        final List<String> lines = Files.readAllLines(MATRIX, StandardCharsets.UTF_8);
        final List<String> header = List.of(lines.get(0).split(",", -1));
        final Map<String, Map<String, String>> rows =
                lines.stream()
                        .skip(1)
                        .map(line -> row(header, line))
                        .collect(Collectors.toMap(item -> item.get("row_id"), item -> item));
        for (final Map.Entry<String, String> expected : EXPECTED_LOCATORS.entrySet()) {
            assertEquals(expected.getValue(), rows.get(expected.getKey()).get("presence_key_path"));
        }
        assertEquals("DISABLED", rows.get("SWP-FRETES-ROOT").get("applicability"));
        assertEquals("DISABLED", rows.get("SWP-LOCALIZACAO-ROOT").get("applicability"));
        assertEquals("DISABLED", rows.get("SWP-USUARIOS-CURRENT").get("applicability"));
        assertEquals(
                "UNOBSERVED_LEGACY_HEURISTIC:billingId",
                rows.get("SWP-FATURAS-BILLING-CAND").get("presence_key_path"));
        assertEquals(
                "LEGACY_COLUMN_CANDIDATE:cod_solicitacao",
                rows.get("SWP-RASTER-VIAGENS").get("presence_key_path"));
        assertEquals(
                "LEGACY_COMPOSITE_CANDIDATE:cod_solicitacao+ordem",
                rows.get("SWP-RASTER-PARADAS").get("presence_key_path"));
    }

    private static Map<String, String> row(final List<String> header, final String line) {
        final String[] values = line.split(",", -1);
        assertEquals(header.size(), values.length);
        final Map<String, String> row = new HashMap<>();
        for (int index = 0; index < header.size(); index++) {
            row.put(header.get(index), values[index]);
        }
        return Map.copyOf(row);
    }

    private static Set<String> values(final List<Map<String, String>> rows, final String column) {
        final Set<String> result = new HashSet<>();
        for (final Map<String, String> row : rows) {
            assertTrue(result.add(row.get(column)) || !column.equals("row_id"));
        }
        return Set.copyOf(result);
    }

    private static final Set<String> EXPECTED_FAMILIES =
            Set.of(
                    "COLETAS",
                    "FRETES",
                    "MANIFESTOS",
                    "COTACOES",
                    "LOCALIZACAO_CARGAS",
                    "CONTAS_A_PAGAR",
                    "FATURAS_POR_CLIENTE",
                    "INVENTARIO",
                    "SINISTROS",
                    "USUARIOS",
                    "RASTER");

    private static final Set<String> EXPECTED_IDS =
            Set.of(
                    "SWP-COLETAS-ROOT",
                    "SWP-COLETAS-FRETE-CAND",
                    "SWP-FRETES-ROOT",
                    "SWP-FRETES-PERFORMANCE",
                    "SWP-FRETES-GRAPHQL",
                    "SWP-FRETES-COLETA-CAND",
                    "SWP-MANIFESTOS-ROOT",
                    "SWP-MANIFESTOS-PICK",
                    "SWP-MANIFESTOS-MDFE",
                    "SWP-MANIFESTOS-COLETA-CAND",
                    "SWP-COTACOES-ROOT",
                    "SWP-COTACOES-TARIFA-REF",
                    "SWP-LOCALIZACAO-ROOT",
                    "SWP-LOCALIZACAO-FRETE-CAND",
                    "SWP-CAP-ROOT-CAND",
                    "SWP-CAP-PARCELA-CAND",
                    "SWP-FATURAS-ROOT-CAND",
                    "SWP-FATURAS-TITULO-CAND",
                    "SWP-FATURAS-NFSE-CAND",
                    "SWP-FATURAS-CTE-CAND",
                    "SWP-FATURAS-BILLING-CAND",
                    "SWP-FATURAS-INVOICE-CAND",
                    "SWP-FATURAS-ORDER-CAND",
                    "SWP-INVENTARIO-ROOT-CAND",
                    "SWP-INVENTARIO-FREIGHT-CAND",
                    "SWP-INVENTARIO-INVOICE-CAND",
                    "SWP-SINISTROS-ROOT-CAND",
                    "SWP-SINISTROS-MINUTA-CAND",
                    "SWP-SINISTROS-INVOICE-CAND",
                    "SWP-USUARIOS-CURRENT",
                    "SWP-USUARIOS-HISTORY",
                    "SWP-RASTER-VIAGENS",
                    "SWP-RASTER-PARADAS");

    private static final Map<String, String> EXPECTED_LOCATORS =
            Map.ofEntries(
                    Map.entry("SWP-COLETAS-ROOT", "/id"),
                    Map.entry("SWP-FRETES-ROOT", "/id"),
                    Map.entry("SWP-MANIFESTOS-ROOT", "/sequence_code"),
                    Map.entry("SWP-MANIFESTOS-PICK", "/mft_pfs_pck_sequence_code"),
                    Map.entry("SWP-MANIFESTOS-MDFE", "/mft_mfs_key"),
                    Map.entry("SWP-COTACOES-ROOT", "/sequence_code"),
                    Map.entry("SWP-LOCALIZACAO-ROOT", "/corporation_sequence_number"),
                    Map.entry("SWP-CAP-PARCELA-CAND", "/ant_ils_sequence_code"),
                    Map.entry("SWP-FATURAS-ROOT-CAND", "/id"),
                    Map.entry("SWP-FATURAS-TITULO-CAND", "/fit_ant_document"),
                    Map.entry("SWP-FATURAS-NFSE-CAND", "/fit_nse_number|/nfse_number"),
                    Map.entry("SWP-FATURAS-CTE-CAND", "/fit_fhe_cte_number|/fit_fhe_cte_key"),
                    Map.entry("SWP-FATURAS-INVOICE-CAND", "/invoices_mapping/*"),
                    Map.entry("SWP-FATURAS-ORDER-CAND", "/fit_fte_invoices_order_number/*"),
                    Map.entry("SWP-INVENTARIO-ROOT-CAND", "/sequence_code"),
                    Map.entry(
                            "SWP-INVENTARIO-FREIGHT-CAND",
                            "/cnr_c_s_fit_corporation_sequence_number"),
                    Map.entry("SWP-INVENTARIO-INVOICE-CAND", "/cnr_c_s_fit_invoices_mapping/*"),
                    Map.entry("SWP-SINISTROS-ROOT-CAND", "/sequence_code"),
                    Map.entry(
                            "SWP-SINISTROS-MINUTA-CAND",
                            "/icm_fis_fit_corporation_sequence_number"),
                    Map.entry("SWP-SINISTROS-INVOICE-CAND", "/icm_fis_ioe_number"),
                    Map.entry("SWP-USUARIOS-CURRENT", "/node/id"));
}
