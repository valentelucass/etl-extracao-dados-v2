package br.com.esl.etl.v2.modulos.raster.domain;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;

/** Typed source attributes; candidate codes and wire representations are not canonical identity. */
public record RasterTrip(
        ExpansionValue<String> codSolicitacao,
        ExpansionValue<String> sequencial,
        ExpansionValue<String> codFilial,
        ExpansionValue<String> statusViagem,
        ExpansionValue<String> placaVeiculo,
        ExpansionValue<String> placaCarreta1,
        ExpansionValue<String> placaCarreta2,
        ExpansionValue<String> placaCarreta3,
        ExpansionValue<String> cpfMotorista1,
        ExpansionValue<String> cpfMotorista2,
        ExpansionValue<String> cnpjClienteOrig,
        ExpansionValue<String> cnpjClienteDest,
        ExpansionValue<String> codIbgeCidadeOrig,
        ExpansionValue<String> codIbgeCidadeDest,
        ExpansionValue<RasterTime> dataHoraPrevIni,
        ExpansionValue<RasterTime> dataHoraPrevFim,
        ExpansionValue<RasterTime> dataHoraRealIni,
        ExpansionValue<RasterTime> dataHoraRealFim,
        ExpansionValue<RasterTime> dataHoraIdentificouFimViagem,
        ExpansionValue<Integer> tempoTotalViagem,
        ExpansionValue<String> dentroPrazo,
        ExpansionValue<java.math.BigDecimal> percentualAtraso,
        ExpansionValue<String> rodouForaHorario,
        ExpansionValue<java.math.BigDecimal> velocidadeMedia,
        ExpansionValue<Integer> eventosVelocidade,
        ExpansionValue<Integer> desviosDeRota,
        ExpansionValue<String> linkTimeline) {
    public RasterTrip {
        java.util.Objects.requireNonNull(codSolicitacao);
        java.util.Objects.requireNonNull(sequencial);
        java.util.Objects.requireNonNull(codFilial);
        java.util.Objects.requireNonNull(statusViagem);
        java.util.Objects.requireNonNull(placaVeiculo);
        java.util.Objects.requireNonNull(placaCarreta1);
        java.util.Objects.requireNonNull(placaCarreta2);
        java.util.Objects.requireNonNull(placaCarreta3);
        java.util.Objects.requireNonNull(cpfMotorista1);
        java.util.Objects.requireNonNull(cpfMotorista2);
        java.util.Objects.requireNonNull(cnpjClienteOrig);
        java.util.Objects.requireNonNull(cnpjClienteDest);
        java.util.Objects.requireNonNull(codIbgeCidadeOrig);
        java.util.Objects.requireNonNull(codIbgeCidadeDest);
        java.util.Objects.requireNonNull(dataHoraPrevIni);
        java.util.Objects.requireNonNull(dataHoraPrevFim);
        java.util.Objects.requireNonNull(dataHoraRealIni);
        java.util.Objects.requireNonNull(dataHoraRealFim);
        java.util.Objects.requireNonNull(dataHoraIdentificouFimViagem);
        java.util.Objects.requireNonNull(tempoTotalViagem);
        java.util.Objects.requireNonNull(dentroPrazo);
        java.util.Objects.requireNonNull(percentualAtraso);
        java.util.Objects.requireNonNull(rodouForaHorario);
        java.util.Objects.requireNonNull(velocidadeMedia);
        java.util.Objects.requireNonNull(eventosVelocidade);
        java.util.Objects.requireNonNull(desviosDeRota);
        java.util.Objects.requireNonNull(linkTimeline);
    }

    public boolean valid() {
        return codSolicitacao.valid()
                && sequencial.valid()
                && codFilial.valid()
                && statusViagem.valid()
                && placaVeiculo.valid()
                && placaCarreta1.valid()
                && placaCarreta2.valid()
                && placaCarreta3.valid()
                && cpfMotorista1.valid()
                && cpfMotorista2.valid()
                && cnpjClienteOrig.valid()
                && cnpjClienteDest.valid()
                && codIbgeCidadeOrig.valid()
                && codIbgeCidadeDest.valid()
                && dataHoraPrevIni.valid()
                && dataHoraPrevFim.valid()
                && dataHoraRealIni.valid()
                && dataHoraRealFim.valid()
                && dataHoraIdentificouFimViagem.valid()
                && tempoTotalViagem.valid()
                && dentroPrazo.valid()
                && percentualAtraso.valid()
                && rodouForaHorario.valid()
                && velocidadeMedia.valid()
                && eventosVelocidade.valid()
                && desviosDeRota.valid()
                && linkTimeline.valid();
    }
}
