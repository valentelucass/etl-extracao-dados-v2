package br.com.esl.etl.v2.plataforma.expansao;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Typed laboratory detail at current component grain; root and part amounts are non-additive. */
public sealed interface ExpansionProjection {
    Lineage lineage();

    record Lineage(
            long cursor,
            long rootId,
            UUID execution,
            long occurrence,
            ExpansionKey root,
            ExpansionKey part,
            ExpansionKey component,
            String currency,
            String unit,
            boolean unresolvedConflict) {}

    record Payable(
            Lineage lineage,
            BigDecimal rootAmount,
            BigDecimal partAmount,
            BigDecimal allocationAmount,
            boolean additiveAllocation,
            String paymentState,
            String paidLabel,
            String reconciliationLabel,
            String typeRaw,
            String typeLabel,
            String classificationRaw,
            String classificationLabel,
            Long labelRelease,
            LocalDate issueDate)
            implements ExpansionProjection {
        @Override
        public String toString() {
            return "ExpansionPayable[synthetic-detail]";
        }
    }

    record InvoiceCustomer(
            Lineage lineage,
            String lineCandidate,
            String lineWire,
            String documentRaw,
            boolean hasInvoice,
            String cteNumber,
            String nfseNumber,
            String nfseAlias,
            String officialNumber,
            String fiscalState,
            String nfseSeriesState,
            String statusRaw,
            String statusLabel,
            Long labelRelease,
            LocalDate issueDate,
            LocalDate dueDate,
            LocalDate paidDate,
            BigDecimal titleValue,
            BigDecimal freightValue,
            String clientKey,
            String clientProvenance,
            String freightType)
            implements ExpansionProjection {
        @Override
        public String toString() {
            return "ExpansionInvoiceCustomer[synthetic-detail]";
        }
    }

    record Inventory(
            Lineage lineage,
            String sequenceCandidate,
            String sequenceWire,
            String freightCandidate,
            boolean proofAttached,
            BigDecimal invoiceValue,
            BigInteger volumes,
            BigDecimal realWeight,
            BigDecimal taxedWeight,
            BigDecimal cubicVolume,
            Integer mappingCount,
            String mappingPresence,
            String statusRaw)
            implements ExpansionProjection {
        @Override
        public String toString() {
            return "ExpansionInventory[synthetic-detail]";
        }
    }

    record InsuranceClaim(
            Lineage lineage,
            String sequenceCandidate,
            String sequenceWire,
            String freightCandidate,
            String occurrenceCandidate,
            BigDecimal claimTotal,
            BigDecimal invoiceValue,
            BigInteger volumes,
            BigDecimal weight,
            LocalDate openingDate,
            LocalDate occurrenceDate,
            LocalTime occurrenceTime,
            LocalDate finishedDate,
            LocalTime finishedTime,
            String dealingType,
            String solutionType)
            implements ExpansionProjection {
        @Override
        public String toString() {
            return "ExpansionInsuranceClaim[synthetic-detail]";
        }
    }
}
