package br.com.esl.etl.v2.modulos.raster.aplicacao;

import br.com.esl.etl.v2.modulos.raster.domain.RasterRoute;
import br.com.esl.etl.v2.modulos.raster.domain.RasterStop;
import br.com.esl.etl.v2.modulos.raster.domain.RasterTrip;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterFieldParser;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.ZoneId;

/** Source DTO conversion; no synthetic key is injected in the provider payload. */
public final class RasterMapper {
    private RasterMapper() {}

    public static RasterTrip trip(final JsonNode node, final ZoneId zone) {
        return new RasterTrip(
                RasterFieldParser.identifier(node, "CodSolicitacao", "codSolicitacao"),
                RasterFieldParser.identifier(node, "Sequencial", "sequencial"),
                RasterFieldParser.identifier(node, "CodFilial", "codFilial"),
                RasterFieldParser.text(node, "StatusViagem", "statusViagem"),
                RasterFieldParser.text(node, "PlacaVeiculo", "placaVeiculo"),
                RasterFieldParser.text(node, "PlacaCarreta1", "placaCarreta1"),
                RasterFieldParser.text(node, "PlacaCarreta02", "PlacaCarreta2", "placaCarreta2"),
                RasterFieldParser.text(node, "PlacaCarreta3", "placaCarreta3"),
                RasterFieldParser.text(node, "CPFMotorista1", "CpfMotorista1", "cpfMotorista1"),
                RasterFieldParser.text(node, "CPFMotorista2", "CpfMotorista2", "cpfMotorista2"),
                RasterFieldParser.text(
                        node, "CNPJClienteOrig", "CnpjClienteOrig", "cnpjClienteOrig"),
                RasterFieldParser.text(
                        node, "CNPJClienteDest", "CnpjClienteDest", "cnpjClienteDest"),
                RasterFieldParser.identifier(
                        node, "CodIBGECidadeOrig", "CodIbgeCidadeOrig", "codIbgeCidadeOrig"),
                RasterFieldParser.identifier(
                        node, "CodIBGECidadeDest", "CodIbgeCidadeDest", "codIbgeCidadeDest"),
                RasterFieldParser.time(node, zone, "DataHoraPrevIni", "dataHoraPrevIni"),
                RasterFieldParser.time(node, zone, "DataHoraPrevFim", "dataHoraPrevFim"),
                RasterFieldParser.time(node, zone, "DataHoraRealIni", "dataHoraRealIni"),
                RasterFieldParser.time(node, zone, "DataHoraRealFim", "dataHoraRealFim"),
                RasterFieldParser.time(
                        node, zone, "DataHoraIdentificouFimViagem", "dataHoraIdentificouFimViagem"),
                RasterFieldParser.count(node, "TempoTotalViagem", "tempoTotalViagem"),
                RasterFieldParser.text(node, "DentroPrazo", "dentroPrazo"),
                RasterFieldParser.decimal(node, "PercentualAtraso", "percentualAtraso"),
                RasterFieldParser.text(node, "RodouForaHorario", "rodouForaHorario"),
                RasterFieldParser.decimal(node, "VelocidadeMedia", "velocidadeMedia"),
                RasterFieldParser.count(node, "EventosVelocidade", "eventosVelocidade"),
                RasterFieldParser.count(node, "DesviosDeRota", "desviosDeRota"),
                RasterFieldParser.text(
                        node, "LinkTimeLine", "LinkTimeline", "linkTimeLine", "linkTimeline"));
    }

    public static RasterStop stop(final JsonNode node, final ZoneId zone) {
        return new RasterStop(
                RasterFieldParser.identifier(node, "Ordem", "ordem"),
                RasterFieldParser.text(node, "Tipo", "tipo"),
                RasterFieldParser.identifier(
                        node, "CodIBGECidade", "codIBGECidade", "CodIbgeCidade"),
                RasterFieldParser.text(node, "CNPJCliente", "cnpjCliente", "CnpjCliente"),
                RasterFieldParser.text(node, "CodigoCliente", "codigoCliente"),
                RasterFieldParser.time(node, zone, "DataHoraPrevChegada", "dataHoraPrevChegada"),
                RasterFieldParser.time(node, zone, "DataHoraPrevSaida", "dataHoraPrevSaida"),
                RasterFieldParser.time(node, zone, "DataHoraRealChegada", "dataHoraRealChegada"),
                RasterFieldParser.time(node, zone, "DataHoraRealSaida", "dataHoraRealSaida"),
                RasterFieldParser.decimal(node, "Latitude", "latitude"),
                RasterFieldParser.decimal(node, "Longitude", "longitude"),
                RasterFieldParser.text(node, "DentroPrazo", "dentroPrazo"),
                RasterFieldParser.text(node, "DiferencaTempo", "diferencaTempo"),
                RasterFieldParser.decimal(node, "KmPercorridoEntrega", "kmPercorridoEntrega"),
                RasterFieldParser.decimal(node, "KmRestanteEntrega", "kmRestanteEntrega"),
                RasterFieldParser.text(node, "ChegouNaEntrega", "chegouNaEntrega"),
                RasterFieldParser.time(
                        node, zone, "DataHoraUltimaPosicao", "dataHoraUltimaPosicao"),
                RasterFieldParser.decimal(node, "LatitudeUltimaPosicao", "latitudeUltimaPosicao"),
                RasterFieldParser.decimal(node, "LongitudeUltimaPosicao", "longitudeUltimaPosicao"),
                RasterFieldParser.text(node, "ReferenciaUltimaPosicao", "referenciaUltimaPosicao"));
    }

    public static RasterRoute route(final JsonNode node, final ZoneId zone) {
        return new RasterRoute(
                RasterFieldParser.identifier(node, "CodRota", "codRota"),
                RasterFieldParser.text(
                        node, "Descricao", "descricao", "RotaDescricao", "DescRota"));
    }
}
