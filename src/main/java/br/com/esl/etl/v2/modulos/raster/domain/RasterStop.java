package br.com.esl.etl.v2.modulos.raster.domain;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;

/** Typed source attributes; candidate codes and wire representations are not canonical identity. */
public record RasterStop(
        ExpansionValue<String> ordem,
        ExpansionValue<String> tipo,
        ExpansionValue<String> codIbgeCidade,
        ExpansionValue<String> cnpjCliente,
        ExpansionValue<String> codigoCliente,
        ExpansionValue<RasterTime> dataHoraPrevChegada,
        ExpansionValue<RasterTime> dataHoraPrevSaida,
        ExpansionValue<RasterTime> dataHoraRealChegada,
        ExpansionValue<RasterTime> dataHoraRealSaida,
        ExpansionValue<java.math.BigDecimal> latitude,
        ExpansionValue<java.math.BigDecimal> longitude,
        ExpansionValue<String> dentroPrazo,
        ExpansionValue<String> diferencaTempo,
        ExpansionValue<java.math.BigDecimal> kmPercorridoEntrega,
        ExpansionValue<java.math.BigDecimal> kmRestanteEntrega,
        ExpansionValue<String> chegouNaEntrega,
        ExpansionValue<RasterTime> dataHoraUltimaPosicao,
        ExpansionValue<java.math.BigDecimal> latitudeUltimaPosicao,
        ExpansionValue<java.math.BigDecimal> longitudeUltimaPosicao,
        ExpansionValue<String> referenciaUltimaPosicao) {
    public RasterStop {
        java.util.Objects.requireNonNull(ordem);
        java.util.Objects.requireNonNull(tipo);
        java.util.Objects.requireNonNull(codIbgeCidade);
        java.util.Objects.requireNonNull(cnpjCliente);
        java.util.Objects.requireNonNull(codigoCliente);
        java.util.Objects.requireNonNull(dataHoraPrevChegada);
        java.util.Objects.requireNonNull(dataHoraPrevSaida);
        java.util.Objects.requireNonNull(dataHoraRealChegada);
        java.util.Objects.requireNonNull(dataHoraRealSaida);
        java.util.Objects.requireNonNull(latitude);
        java.util.Objects.requireNonNull(longitude);
        java.util.Objects.requireNonNull(dentroPrazo);
        java.util.Objects.requireNonNull(diferencaTempo);
        java.util.Objects.requireNonNull(kmPercorridoEntrega);
        java.util.Objects.requireNonNull(kmRestanteEntrega);
        java.util.Objects.requireNonNull(chegouNaEntrega);
        java.util.Objects.requireNonNull(dataHoraUltimaPosicao);
        java.util.Objects.requireNonNull(latitudeUltimaPosicao);
        java.util.Objects.requireNonNull(longitudeUltimaPosicao);
        java.util.Objects.requireNonNull(referenciaUltimaPosicao);
    }

    public boolean valid() {
        return ordem.valid()
                && tipo.valid()
                && codIbgeCidade.valid()
                && cnpjCliente.valid()
                && codigoCliente.valid()
                && dataHoraPrevChegada.valid()
                && dataHoraPrevSaida.valid()
                && dataHoraRealChegada.valid()
                && dataHoraRealSaida.valid()
                && latitude.valid()
                && longitude.valid()
                && dentroPrazo.valid()
                && diferencaTempo.valid()
                && kmPercorridoEntrega.valid()
                && kmRestanteEntrega.valid()
                && chegouNaEntrega.valid()
                && dataHoraUltimaPosicao.valid()
                && latitudeUltimaPosicao.valid()
                && longitudeUltimaPosicao.valid()
                && referenciaUltimaPosicao.valid();
    }
}
