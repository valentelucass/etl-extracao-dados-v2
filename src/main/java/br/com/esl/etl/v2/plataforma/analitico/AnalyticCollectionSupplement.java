package br.com.esl.etl.v2.plataforma.analitico;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/** Lateral values, separately attributed to a synthetic GraphQL observation and a captured root. */
public record AnalyticCollectionSupplement(
        ExpansionValue<LocalTime> requestHour,
        ExpansionValue<String> vehicleTypeId,
        ExpansionValue<String> customerName,
        ExpansionValue<String> customerDocument,
        ExpansionValue<String> addressLine,
        ExpansionValue<String> addressNumber,
        ExpansionValue<String> addressComplement,
        ExpansionValue<String> branchSourceId,
        ExpansionValue<String> cancellationUserId,
        ExpansionValue<String> destroyReason,
        ExpansionValue<String> destroyUserId,
        ExpansionValue<Instant> statusUpdatedAt) {
    public AnalyticCollectionSupplement {
        Objects.requireNonNull(requestHour);
        Objects.requireNonNull(vehicleTypeId);
        Objects.requireNonNull(customerName);
        Objects.requireNonNull(customerDocument);
        Objects.requireNonNull(addressLine);
        Objects.requireNonNull(addressNumber);
        Objects.requireNonNull(addressComplement);
        Objects.requireNonNull(branchSourceId);
        Objects.requireNonNull(cancellationUserId);
        Objects.requireNonNull(destroyReason);
        Objects.requireNonNull(destroyUserId);
        Objects.requireNonNull(statusUpdatedAt);
    }

    public List<ExpansionValue<?>> fields() {
        return List.of(
                requestHour,
                vehicleTypeId,
                customerName,
                customerDocument,
                addressLine,
                addressNumber,
                addressComplement,
                branchSourceId,
                cancellationUserId,
                destroyReason,
                destroyUserId,
                statusUpdatedAt);
    }

    public boolean valid() {
        return fields().stream().allMatch(ExpansionValue::valid);
    }
}
