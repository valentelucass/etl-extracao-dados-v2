package br.com.esl.etl.v2.modulos.contasapagar.domain;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionObservation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;

/** Typed physical observation; none of these candidates supplies a synthetic root identity. */
public record ContaPagarObservation(
        ExpansionValue<String> antAlnName,
        ExpansionValue<String> antCesAcrName,
        ExpansionValue<java.math.BigDecimal> antCesValue,
        ExpansionValue<String> antCrnPsnNickname,
        ExpansionValue<java.time.LocalDate> antIlsAtnLiquidationDate,
        ExpansionValue<Boolean> antIlsAtnReconciled,
        ExpansionValue<java.time.LocalDate> antIlsAtnTransactionDate,
        ExpansionValue<String> antIlsComments,
        ExpansionValue<String> antIlsExpenseDescription,
        ExpansionValue<String> antIlsPasAntClassification,
        ExpansionValue<String> antIlsPasAntName,
        ExpansionValue<java.math.BigDecimal> antIlsPasValue,
        ExpansionValue<String> antIlsSequenceCode,
        ExpansionValue<String> antRirName,
        ExpansionValue<String> antUerName,
        ExpansionValue<String> comments,
        ExpansionValue<String> competenceMonth,
        ExpansionValue<String> competenceYear,
        ExpansionValue<java.time.Instant> createdAt,
        ExpansionValue<java.math.BigDecimal> discountValue,
        ExpansionValue<String> document,
        ExpansionValue<java.math.BigDecimal> interestValue,
        ExpansionValue<java.time.LocalDate> issueDate,
        ExpansionValue<Boolean> paid,
        ExpansionValue<java.math.BigDecimal> paidValue,
        ExpansionValue<String> type,
        ExpansionValue<java.math.BigDecimal> value,
        ExpansionValue<java.math.BigDecimal> valueToPay)
        implements ExpansionObservation {
    public ContaPagarObservation {
        java.util.Objects.requireNonNull(antAlnName);
        java.util.Objects.requireNonNull(antCesAcrName);
        java.util.Objects.requireNonNull(antCesValue);
        java.util.Objects.requireNonNull(antCrnPsnNickname);
        java.util.Objects.requireNonNull(antIlsAtnLiquidationDate);
        java.util.Objects.requireNonNull(antIlsAtnReconciled);
        java.util.Objects.requireNonNull(antIlsAtnTransactionDate);
        java.util.Objects.requireNonNull(antIlsComments);
        java.util.Objects.requireNonNull(antIlsExpenseDescription);
        java.util.Objects.requireNonNull(antIlsPasAntClassification);
        java.util.Objects.requireNonNull(antIlsPasAntName);
        java.util.Objects.requireNonNull(antIlsPasValue);
        java.util.Objects.requireNonNull(antIlsSequenceCode);
        java.util.Objects.requireNonNull(antRirName);
        java.util.Objects.requireNonNull(antUerName);
        java.util.Objects.requireNonNull(comments);
        java.util.Objects.requireNonNull(competenceMonth);
        java.util.Objects.requireNonNull(competenceYear);
        java.util.Objects.requireNonNull(createdAt);
        java.util.Objects.requireNonNull(discountValue);
        java.util.Objects.requireNonNull(document);
        java.util.Objects.requireNonNull(interestValue);
        java.util.Objects.requireNonNull(issueDate);
        java.util.Objects.requireNonNull(paid);
        java.util.Objects.requireNonNull(paidValue);
        java.util.Objects.requireNonNull(type);
        java.util.Objects.requireNonNull(value);
        java.util.Objects.requireNonNull(valueToPay);
    }

    @Override
    public String vertical() {
        return "CAP";
    }

    @Override
    public boolean valid() {
        return antAlnName.valid()
                && antCesAcrName.valid()
                && antCesValue.valid()
                && antCrnPsnNickname.valid()
                && antIlsAtnLiquidationDate.valid()
                && antIlsAtnReconciled.valid()
                && antIlsAtnTransactionDate.valid()
                && antIlsComments.valid()
                && antIlsExpenseDescription.valid()
                && antIlsPasAntClassification.valid()
                && antIlsPasAntName.valid()
                && antIlsPasValue.valid()
                && antIlsSequenceCode.valid()
                && antRirName.valid()
                && antUerName.valid()
                && comments.valid()
                && competenceMonth.valid()
                && competenceYear.valid()
                && createdAt.valid()
                && discountValue.valid()
                && document.valid()
                && interestValue.valid()
                && issueDate.valid()
                && paid.valid()
                && paidValue.valid()
                && type.valid()
                && value.valid()
                && valueToPay.valid();
    }
}
