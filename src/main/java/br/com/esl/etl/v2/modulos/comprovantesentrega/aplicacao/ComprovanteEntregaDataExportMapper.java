package br.com.esl.etl.v2.modulos.comprovantesentrega.aplicacao;

import br.com.esl.etl.v2.modulos.comprovantesentrega.domain.ComprovanteEntregaObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionFieldParser;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.MappedReportFields;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** The root is a freight archive. A row preserves one expanded freight occurrence. */
public final class ComprovanteEntregaDataExportMapper {
    private static final String FREIGHT = "fag_fhs_fit_";
    private static final String INVOICES = FREIGHT + "invoices_mapping";
    private static final String HAS_RECEIPT = "fag_fhs_fte_has_delivery_receipt";

    private static final List<String> TEXT =
            List.of(
                    "fag_crn_psn_nickname",
                    "name",
                    "status",
                    FREIGHT + "crn_psn_nickname",
                    FREIGHT + "pyr_nickname",
                    FREIGHT + "dyn_drt_nickname",
                    FREIGHT + "fln_cln_nickname",
                    FREIGHT + "status",
                    FREIGHT + "emission_type",
                    FREIGHT + "service_type",
                    FREIGHT + "type",
                    FREIGHT + "fte_flt_ron_document_number",
                    FREIGHT + "fte_llt_ron_document_number");
    private static final List<String> INTEGER =
            List.of(
                    "sequence_code",
                    FREIGHT + "corporation_sequence_number",
                    FREIGHT + "fhe_cte_number",
                    FREIGHT + "operation_type",
                    FREIGHT + "fte_flt_ron_sequence_code",
                    FREIGHT + "fte_llt_ron_sequence_code");
    private static final List<String> INSTANT =
            List.of(FREIGHT + "d_t_created_at", FREIGHT + "fln_updated_at");

    public ComprovanteEntregaObservation map(final JsonNode row) {
        final var fields = new LinkedHashMap<String, ExpansionValue<?>>();
        TEXT.forEach(field -> fields.put(field, MappedReportFields.text(row, field)));
        INTEGER.forEach(field -> fields.put(field, ExpansionFieldParser.integer(row, field)));
        INSTANT.forEach(field -> fields.put(field, ExpansionFieldParser.instant(row, field)));
        fields.put(HAS_RECEIPT, ExpansionFieldParser.bool(row, HAS_RECEIPT));
        fields.put(
                INVOICES,
                MappedReportFields.parse(
                        row, INVOICES, ComprovanteEntregaDataExportMapper::invoiceNumbers));
        MappedReportFields.requireExact(row, fields.keySet());
        return new ComprovanteEntregaObservation(fields);
    }

    private static List<String> invoiceNumbers(final JsonNode node) {
        if (!node.isArray()) {
            throw new IllegalArgumentException("DELIVERY_ARCHIVE_INVOICES_ARRAY");
        }
        final var numbers = new ArrayList<String>(node.size());
        for (final var entry : node) {
            if (!entry.isTextual()) {
                throw new IllegalArgumentException("DELIVERY_ARCHIVE_INVOICE_TEXT");
            }
            numbers.add(entry.textValue());
        }
        return List.copyOf(numbers);
    }
}
