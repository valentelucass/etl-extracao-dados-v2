package br.com.esl.etl.v2.plataforma.analitico;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Thirty-six captured quote attributes; raw/presence/wire are separate from typed values. */
public record AnalyticQuoteAttributes(
        ExpansionValue<Instant> requestedAt,
        ExpansionValue<String> sequenceCode,
        ExpansionValue<String> operationType,
        ExpansionValue<String> customerDocument,
        ExpansionValue<String> customerName,
        ExpansionValue<String> originCity,
        ExpansionValue<String> originState,
        ExpansionValue<String> destinationCity,
        ExpansionValue<String> destinationState,
        ExpansionValue<String> priceTable,
        ExpansionValue<String> volumes,
        ExpansionValue<BigDecimal> taxedWeight,
        ExpansionValue<BigDecimal> invoicesValue,
        ExpansionValue<BigDecimal> totalValue,
        ExpansionValue<Instant> cteIssuedAt,
        ExpansionValue<Instant> nfseIssuedAt,
        ExpansionValue<String> userName,
        ExpansionValue<String> branchNickname,
        ExpansionValue<String> senderDocument,
        ExpansionValue<String> senderNickname,
        ExpansionValue<String> receiverDocument,
        ExpansionValue<String> receiverNickname,
        ExpansionValue<String> originPostalCode,
        ExpansionValue<String> destinationPostalCode,
        ExpansionValue<BigDecimal> realWeight,
        ExpansionValue<String> disapproveComments,
        ExpansionValue<String> freightComments,
        ExpansionValue<BigDecimal> discountSubtotal,
        ExpansionValue<String> requesterName,
        ExpansionValue<BigDecimal> itrSubtotal,
        ExpansionValue<BigDecimal> tdeSubtotal,
        ExpansionValue<BigDecimal> collectSubtotal,
        ExpansionValue<BigDecimal> deliverySubtotal,
        ExpansionValue<BigDecimal> otherFees,
        ExpansionValue<String> companyName,
        ExpansionValue<String> customerNickname) {
    public AnalyticQuoteAttributes {
        Objects.requireNonNull(requestedAt);
        Objects.requireNonNull(sequenceCode);
        Objects.requireNonNull(operationType);
        Objects.requireNonNull(customerDocument);
        Objects.requireNonNull(customerName);
        Objects.requireNonNull(originCity);
        Objects.requireNonNull(originState);
        Objects.requireNonNull(destinationCity);
        Objects.requireNonNull(destinationState);
        Objects.requireNonNull(priceTable);
        Objects.requireNonNull(volumes);
        Objects.requireNonNull(taxedWeight);
        Objects.requireNonNull(invoicesValue);
        Objects.requireNonNull(totalValue);
        Objects.requireNonNull(cteIssuedAt);
        Objects.requireNonNull(nfseIssuedAt);
        Objects.requireNonNull(userName);
        Objects.requireNonNull(branchNickname);
        Objects.requireNonNull(senderDocument);
        Objects.requireNonNull(senderNickname);
        Objects.requireNonNull(receiverDocument);
        Objects.requireNonNull(receiverNickname);
        Objects.requireNonNull(originPostalCode);
        Objects.requireNonNull(destinationPostalCode);
        Objects.requireNonNull(realWeight);
        Objects.requireNonNull(disapproveComments);
        Objects.requireNonNull(freightComments);
        Objects.requireNonNull(discountSubtotal);
        Objects.requireNonNull(requesterName);
        Objects.requireNonNull(itrSubtotal);
        Objects.requireNonNull(tdeSubtotal);
        Objects.requireNonNull(collectSubtotal);
        Objects.requireNonNull(deliverySubtotal);
        Objects.requireNonNull(otherFees);
        Objects.requireNonNull(companyName);
        Objects.requireNonNull(customerNickname);
    }

    public List<ExpansionValue<?>> fields() {
        return List.of(
                requestedAt,
                sequenceCode,
                operationType,
                customerDocument,
                customerName,
                originCity,
                originState,
                destinationCity,
                destinationState,
                priceTable,
                volumes,
                taxedWeight,
                invoicesValue,
                totalValue,
                cteIssuedAt,
                nfseIssuedAt,
                userName,
                branchNickname,
                senderDocument,
                senderNickname,
                receiverDocument,
                receiverNickname,
                originPostalCode,
                destinationPostalCode,
                realWeight,
                disapproveComments,
                freightComments,
                discountSubtotal,
                requesterName,
                itrSubtotal,
                tdeSubtotal,
                collectSubtotal,
                deliverySubtotal,
                otherFees,
                companyName,
                customerNickname);
    }

    public boolean valid() {
        return fields().stream().allMatch(ExpansionValue::valid);
    }
}
