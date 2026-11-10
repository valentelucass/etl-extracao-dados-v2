package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.consolidacoes.aplicacao.ConsolidacaoDataExportMapper;
import br.com.esl.etl.v2.modulos.contasapagar.aplicacao.ContaPagarDataExportMapper;
import br.com.esl.etl.v2.modulos.cotacoes.aplicacao.CotacaoDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.faturasporcliente.aplicacao.FaturaClienteDataExportMapper;
import br.com.esl.etl.v2.modulos.fretes.aplicacao.FreteDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.inventario.aplicacao.InventarioDataExportMapper;
import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.LocalizacaoCargaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.manifestos.aplicacao.ManifestoDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.ocorrencias.aplicacao.OcorrenciaDataExportMapper;
import br.com.esl.etl.v2.modulos.sinistros.aplicacao.SinistroDataExportMapper;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionObservation;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.CurlSnapshotWire;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRecordInput;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotTemplate;
import com.fasterxml.jackson.databind.JsonNode;

/** Reuses the existing nine application mappers without giving them a synthetic identity. */
public final class SnapshotMapperCatalog {
    private SnapshotMapperCatalog() {}

    public static ExpansionObservation map(
            final ReportSnapshotTemplate template, final DataExportRecordInput input) {
        final JsonNode row = input.value();
        final var envelope = CurlSnapshotWire.JSON.createObjectNode().set("data", row);
        return switch (template) {
            case CONSOLIDACAO_DETALHADA -> new ConsolidacaoDataExportMapper().map(row);
            case OCORRENCIAS -> new OcorrenciaDataExportMapper().map(row);
            case COLETAS ->
                    new Disposition(
                            template.name(),
                            new ColetaDataExportRecordMapper().map(1, row).quarantineReasonCode()
                                    == null);
            case FRETES ->
                    new Disposition(
                            template.name(),
                            !new FreteDataExportRecordMapper().map(1, row).quarantined());
            case MANIFESTOS ->
                    new Disposition(
                            template.name(),
                            new ManifestoDataExportRecordMapper().map(1, row).quarantineReasonCode()
                                    == null);
            case COTACOES ->
                    new Disposition(
                            template.name(),
                            !new CotacaoDataExportRecordMapper().map(1, row).quarantined());
            case LOCALIZACAO_CARGAS ->
                    new Disposition(
                            template.name(),
                            !LocalizacaoCargaDataExportRecordMapper.observed20261024()
                                    .map(
                                            1,
                                            input.rawJson()
                                                    .orElseThrow(
                                                            () ->
                                                                    new IllegalArgumentException(
                                                                            "SNAPSHOT_LITERAL_RECORD_REQUIRED")))
                                    .quarantined());
            case CONTAS_A_PAGAR -> new ContaPagarDataExportMapper().map(1, envelope);
            case FATURAS_POR_CLIENTE -> new FaturaClienteDataExportMapper().map(1, envelope);
            case INVENTARIO -> new InventarioDataExportMapper().map(1, envelope);
            case SINISTROS -> new SinistroDataExportMapper().map(1, envelope);
            default -> throw new IllegalArgumentException("SNAPSHOT_USE_ENTITY_EXTRACTOR");
        };
    }

    private record Disposition(String vertical, boolean valid) implements ExpansionObservation {}
}
