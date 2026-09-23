package br.com.esl.etl.v2.modulos.fretes.domain;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Typed synthetic attribute snapshot. It carries no source identity or final materialized
 * indicators.
 */
public record FreightAnalyticAttributes(
        ExpansionValue<String> modal,
        ExpansionValue<String> tipoFrete,
        ExpansionValue<Long> accountingCreditId,
        ExpansionValue<Long> accountingCreditInstallmentId,
        ExpansionValue<BigDecimal> valorNotas,
        ExpansionValue<BigDecimal> pesoNotas,
        ExpansionValue<Long> idCorporacao,
        ExpansionValue<Long> idCidadeDestino,
        ExpansionValue<LocalDate> dataPrevisaoEntrega,
        ExpansionValue<LocalDate> serviceDate,
        ExpansionValue<Long> pickItemId,
        ExpansionValue<Long> pagadorId,
        ExpansionValue<String> pagadorNome,
        ExpansionValue<Long> remetenteId,
        ExpansionValue<String> remetenteNome,
        ExpansionValue<String> origemCidade,
        ExpansionValue<String> origemUf,
        ExpansionValue<Long> destinatarioId,
        ExpansionValue<String> destinatarioNome,
        ExpansionValue<String> destinoCidade,
        ExpansionValue<String> destinoUf,
        ExpansionValue<String> filialNome,
        ExpansionValue<String> filialApelido,
        ExpansionValue<String> numeroNotaFiscal,
        ExpansionValue<String> tabelaPrecoNome,
        ExpansionValue<String> classificacaoNome,
        ExpansionValue<String> centroCustoNome,
        ExpansionValue<String> usuarioNome,
        ExpansionValue<Integer> invoicesTotalVolumes,
        ExpansionValue<BigDecimal> taxedWeight,
        ExpansionValue<BigDecimal> realWeight,
        ExpansionValue<BigDecimal> totalCubicVolume,
        ExpansionValue<BigDecimal> subtotal,
        ExpansionValue<String> chaveCte,
        ExpansionValue<Integer> numeroCte,
        ExpansionValue<Integer> serieCte,
        ExpansionValue<Long> cteId,
        ExpansionValue<String> cteEmissionType,
        ExpansionValue<Integer> serviceType,
        ExpansionValue<Boolean> insuranceEnabled,
        ExpansionValue<BigDecimal> grisSubtotal,
        ExpansionValue<BigDecimal> tdeSubtotal,
        ExpansionValue<String> modalCte,
        ExpansionValue<BigDecimal> redispatchSubtotal,
        ExpansionValue<BigDecimal> suframaSubtotal,
        ExpansionValue<String> paymentType,
        ExpansionValue<String> previousDocumentType,
        ExpansionValue<BigDecimal> productsValue,
        ExpansionValue<BigDecimal> trtSubtotal,
        ExpansionValue<String> nfseSeries,
        ExpansionValue<Integer> nfseNumber,
        ExpansionValue<Long> insuranceId,
        ExpansionValue<BigDecimal> otherFees,
        ExpansionValue<BigDecimal> km,
        ExpansionValue<Integer> paymentAccountableType,
        ExpansionValue<BigDecimal> insuredValue,
        ExpansionValue<Boolean> globalized,
        ExpansionValue<BigDecimal> secCatSubtotal,
        ExpansionValue<String> globalizedType,
        ExpansionValue<Integer> priceTableAccountableType,
        ExpansionValue<Integer> insuranceAccountableType,
        ExpansionValue<BigDecimal> fiscalCalculationBasis,
        ExpansionValue<BigDecimal> fiscalTaxRate,
        ExpansionValue<BigDecimal> fiscalPisRate,
        ExpansionValue<BigDecimal> fiscalCofinsRate,
        ExpansionValue<Boolean> fiscalHasDifal,
        ExpansionValue<BigDecimal> fiscalDifalOrigin,
        ExpansionValue<BigDecimal> fiscalDifalDestination,
        ExpansionValue<String> fiscalCstType,
        ExpansionValue<String> fiscalCfopCode,
        ExpansionValue<BigDecimal> fiscalTaxValue,
        ExpansionValue<BigDecimal> fiscalPisValue,
        ExpansionValue<BigDecimal> fiscalCofinsValue,
        ExpansionValue<BigDecimal> cubagesCubedWeight,
        ExpansionValue<BigDecimal> freightWeightSubtotal,
        ExpansionValue<BigDecimal> adValoremSubtotal,
        ExpansionValue<BigDecimal> tollSubtotal,
        ExpansionValue<BigDecimal> itrSubtotal,
        ExpansionValue<String> pagadorDocumento,
        ExpansionValue<String> remetenteDocumento,
        ExpansionValue<String> destinatarioDocumento,
        ExpansionValue<String> filialCnpj,
        ExpansionValue<String> nfseIntegrationId,
        ExpansionValue<String> nfseStatus,
        ExpansionValue<LocalDate> nfseIssuedAt,
        ExpansionValue<String> nfseCancelationReason,
        ExpansionValue<String> nfsePdfServiceUrl,
        ExpansionValue<Long> nfseCorporationId,
        ExpansionValue<String> nfseServiceDescription,
        ExpansionValue<String> nfseXmlDocument) {
    public FreightAnalyticAttributes {
        if (modal == null
                || tipoFrete == null
                || accountingCreditId == null
                || accountingCreditInstallmentId == null
                || valorNotas == null
                || pesoNotas == null
                || idCorporacao == null
                || idCidadeDestino == null
                || dataPrevisaoEntrega == null
                || serviceDate == null
                || pickItemId == null
                || pagadorId == null
                || pagadorNome == null
                || remetenteId == null
                || remetenteNome == null
                || origemCidade == null
                || origemUf == null
                || destinatarioId == null
                || destinatarioNome == null
                || destinoCidade == null
                || destinoUf == null
                || filialNome == null
                || filialApelido == null
                || numeroNotaFiscal == null
                || tabelaPrecoNome == null
                || classificacaoNome == null
                || centroCustoNome == null
                || usuarioNome == null
                || invoicesTotalVolumes == null
                || taxedWeight == null
                || realWeight == null
                || totalCubicVolume == null
                || subtotal == null
                || chaveCte == null
                || numeroCte == null
                || serieCte == null
                || cteId == null
                || cteEmissionType == null
                || serviceType == null
                || insuranceEnabled == null
                || grisSubtotal == null
                || tdeSubtotal == null
                || modalCte == null
                || redispatchSubtotal == null
                || suframaSubtotal == null
                || paymentType == null
                || previousDocumentType == null
                || productsValue == null
                || trtSubtotal == null
                || nfseSeries == null
                || nfseNumber == null
                || insuranceId == null
                || otherFees == null
                || km == null
                || paymentAccountableType == null
                || insuredValue == null
                || globalized == null
                || secCatSubtotal == null
                || globalizedType == null
                || priceTableAccountableType == null
                || insuranceAccountableType == null
                || fiscalCalculationBasis == null
                || fiscalTaxRate == null
                || fiscalPisRate == null
                || fiscalCofinsRate == null
                || fiscalHasDifal == null
                || fiscalDifalOrigin == null
                || fiscalDifalDestination == null
                || fiscalCstType == null
                || fiscalCfopCode == null
                || fiscalTaxValue == null
                || fiscalPisValue == null
                || fiscalCofinsValue == null
                || cubagesCubedWeight == null
                || freightWeightSubtotal == null
                || adValoremSubtotal == null
                || tollSubtotal == null
                || itrSubtotal == null
                || pagadorDocumento == null
                || remetenteDocumento == null
                || destinatarioDocumento == null
                || filialCnpj == null
                || nfseIntegrationId == null
                || nfseStatus == null
                || nfseIssuedAt == null
                || nfseCancelationReason == null
                || nfsePdfServiceUrl == null
                || nfseCorporationId == null
                || nfseServiceDescription == null
                || nfseXmlDocument == null) {
            throw new IllegalArgumentException("ANA_FREIGHT_ATTRIBUTE_PRESENCE_REQUIRED");
        }
    }

    public boolean valid() {
        return modal.valid()
                && tipoFrete.valid()
                && accountingCreditId.valid()
                && accountingCreditInstallmentId.valid()
                && valorNotas.valid()
                && pesoNotas.valid()
                && idCorporacao.valid()
                && idCidadeDestino.valid()
                && dataPrevisaoEntrega.valid()
                && serviceDate.valid()
                && pickItemId.valid()
                && pagadorId.valid()
                && pagadorNome.valid()
                && remetenteId.valid()
                && remetenteNome.valid()
                && origemCidade.valid()
                && origemUf.valid()
                && destinatarioId.valid()
                && destinatarioNome.valid()
                && destinoCidade.valid()
                && destinoUf.valid()
                && filialNome.valid()
                && filialApelido.valid()
                && numeroNotaFiscal.valid()
                && tabelaPrecoNome.valid()
                && classificacaoNome.valid()
                && centroCustoNome.valid()
                && usuarioNome.valid()
                && invoicesTotalVolumes.valid()
                && taxedWeight.valid()
                && realWeight.valid()
                && totalCubicVolume.valid()
                && subtotal.valid()
                && chaveCte.valid()
                && numeroCte.valid()
                && serieCte.valid()
                && cteId.valid()
                && cteEmissionType.valid()
                && serviceType.valid()
                && insuranceEnabled.valid()
                && grisSubtotal.valid()
                && tdeSubtotal.valid()
                && modalCte.valid()
                && redispatchSubtotal.valid()
                && suframaSubtotal.valid()
                && paymentType.valid()
                && previousDocumentType.valid()
                && productsValue.valid()
                && trtSubtotal.valid()
                && nfseSeries.valid()
                && nfseNumber.valid()
                && insuranceId.valid()
                && otherFees.valid()
                && km.valid()
                && paymentAccountableType.valid()
                && insuredValue.valid()
                && globalized.valid()
                && secCatSubtotal.valid()
                && globalizedType.valid()
                && priceTableAccountableType.valid()
                && insuranceAccountableType.valid()
                && fiscalCalculationBasis.valid()
                && fiscalTaxRate.valid()
                && fiscalPisRate.valid()
                && fiscalCofinsRate.valid()
                && fiscalHasDifal.valid()
                && fiscalDifalOrigin.valid()
                && fiscalDifalDestination.valid()
                && fiscalCstType.valid()
                && fiscalCfopCode.valid()
                && fiscalTaxValue.valid()
                && fiscalPisValue.valid()
                && fiscalCofinsValue.valid()
                && cubagesCubedWeight.valid()
                && freightWeightSubtotal.valid()
                && adValoremSubtotal.valid()
                && tollSubtotal.valid()
                && itrSubtotal.valid()
                && pagadorDocumento.valid()
                && remetenteDocumento.valid()
                && destinatarioDocumento.valid()
                && filialCnpj.valid()
                && nfseIntegrationId.valid()
                && nfseStatus.valid()
                && nfseIssuedAt.valid()
                && nfseCancelationReason.valid()
                && nfsePdfServiceUrl.valid()
                && nfseCorporationId.valid()
                && nfseServiceDescription.valid()
                && nfseXmlDocument.valid();
    }
}
