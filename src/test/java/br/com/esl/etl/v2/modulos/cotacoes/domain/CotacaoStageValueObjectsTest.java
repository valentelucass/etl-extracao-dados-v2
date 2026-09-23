package br.com.esl.etl.v2.modulos.cotacoes.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CotacaoStageValueObjectsTest {
    private static final Instant FRESHNESS = Instant.parse("2026-09-04T20:00:00Z");

    @Test
    void keepsTypedIntegerIdentityAndRedactsPayloadFromToString() {
        final CotacaoStageRecord record = valid(1);

        assertEquals("INTEGER:6906", record.sourceKey().storageValue());
        assertEquals(LocalDate.of(2026, 9, 4), record.freshnessBusinessDate());
        assertTrue(record.toString().contains("sensitive=<redacted>"));
        assertFalse(record.toString().contains("private-payload"));
        assertFalse(record.quarantined());
    }

    @Test
    void rejectsNonIntegerKeysMissingBusinessDateAndUnapprovedScale() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CotacaoStageRecord(
                                1,
                                new ScopedSourceIdentity.SourceKey(
                                        ScopedSourceIdentity.WireType.STRING, "STRING:6906"),
                                "{}",
                                "{}",
                                null,
                                null,
                                null,
                                FRESHNESS,
                                FRESHNESS,
                                CotacaoFreshnessOrigin.REQUESTED_AT,
                                LocalDate.of(2026, 9, 4),
                                null,
                                null,
                                null,
                                null,
                                null));
        assertThrows(
                NullPointerException.class,
                () ->
                        new CotacaoStageRecord(
                                1,
                                integerKey(),
                                "{}",
                                "{}",
                                null,
                                null,
                                null,
                                FRESHNESS,
                                FRESHNESS,
                                CotacaoFreshnessOrigin.REQUESTED_AT,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null));
        assertThrows(IllegalArgumentException.class, () -> withAmount(new BigDecimal("1.00001")));
    }

    @Test
    void rejectsNonPositiveSequenceCodesAtTheDirectConstructionBoundary() {
        assertAll(
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> withSourceKey(integerKey("0"))),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> withSourceKey(integerKey("-1"))));
    }

    @Test
    void boundsSequenceCodeToThePositiveSignedBigintDomain() {
        assertAll(
                () ->
                        assertEquals(
                                "INTEGER:2147483648",
                                withSourceKey(integerKey("2147483648")).sourceKey().storageValue()),
                () ->
                        assertEquals(
                                "INTEGER:9223372036854775807",
                                withSourceKey(integerKey("9223372036854775807"))
                                        .sourceKey()
                                        .storageValue()),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> withSourceKey(integerKey("9223372036854775808"))),
                () -> assertThrows(IllegalArgumentException.class, () -> integerKey("+1")),
                () -> assertThrows(IllegalArgumentException.class, () -> integerKey("01")),
                () -> assertThrows(IllegalArgumentException.class, () -> integerKey(" 1")),
                () -> assertThrows(IllegalArgumentException.class, () -> integerKey("1 ")),
                () -> assertThrows(IllegalArgumentException.class, () -> integerKey("1.0")),
                () -> assertThrows(IllegalArgumentException.class, () -> integerKey("１")));
    }

    @Test
    void rejectsAmountsOutsideTheIntegerRangeOfDecimal19Scale4() {
        assertAll(
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> withAmount(new BigDecimal("1000000000000000"))),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> withAmount(new BigDecimal("1E+15"))));
        assertEquals(
                new BigDecimal("999999999999999.9999"),
                withAmount(new BigDecimal("999999999999999.9999")).totalAmount());
        assertEquals(0, withAmount(BigDecimal.ZERO).totalAmount().signum());
    }

    @Test
    void rejectsFreshnessThatDoesNotFollowPrecedenceOrBusinessTimezone() {
        final CotacaoStageRecord base = valid(1);
        assertAll(
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new CotacaoStageRecord(
                                                base.inputOrdinal(),
                                                base.sourceKey(),
                                                base.payloadJson(),
                                                base.fieldPresenceJson(),
                                                base.userNameNormalized(),
                                                null,
                                                FRESHNESS.minusSeconds(1),
                                                FRESHNESS,
                                                FRESHNESS,
                                                CotacaoFreshnessOrigin.REQUESTED_AT,
                                                base.freshnessBusinessDate(),
                                                base.totalAmount(),
                                                null,
                                                base.originUf(),
                                                base.destinationUf(),
                                                null)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new CotacaoStageRecord(
                                                base.inputOrdinal(),
                                                base.sourceKey(),
                                                base.payloadJson(),
                                                base.fieldPresenceJson(),
                                                base.userNameNormalized(),
                                                base.nfseIssuedAtUtc(),
                                                base.cteIssuedAtUtc(),
                                                base.requestedAtUtc(),
                                                base.freshnessAtUtc(),
                                                base.freshnessOrigin(),
                                                base.freshnessBusinessDate().plusDays(1),
                                                base.totalAmount(),
                                                null,
                                                base.originUf(),
                                                base.destinationUf(),
                                                null)));
    }

    @Test
    void doesNotAllowSourceCurrencyOrMixedQuarantineRecords() {
        final CotacaoStageRecord base = valid(1);
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> withCurrency("BRL")),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        new CotacaoStageRecord(
                                                base.inputOrdinal(),
                                                base.sourceKey(),
                                                base.payloadJson(),
                                                base.fieldPresenceJson(),
                                                base.userNameNormalized(),
                                                base.nfseIssuedAtUtc(),
                                                base.cteIssuedAtUtc(),
                                                base.requestedAtUtc(),
                                                base.freshnessAtUtc(),
                                                base.freshnessOrigin(),
                                                base.freshnessBusinessDate(),
                                                base.totalAmount(),
                                                null,
                                                base.originUf(),
                                                base.destinationUf(),
                                                "EQUAL_FRESHNESS_CONFLICT")));
    }

    @Test
    void normalizesTheDisplayUserEvenForDirectConstruction() {
        assertEquals("José", withUser("  José  ").userNameNormalized());
    }

    @Test
    void boundsOnePageAndRejectsRepeatedOrdinals() {
        final UUID executionId = UUID.fromString("00000000-0000-0000-0000-000000000027");
        final CotacaoStageBatch batch =
                new CotacaoStageBatch(executionId, 1, List.of(valid(1)), FRESHNESS);
        assertEquals(1, batch.size());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CotacaoStageBatch(
                                executionId, 1, List.of(valid(1), valid(1)), FRESHNESS));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CotacaoStageBatch(executionId, 1, List.of(), FRESHNESS));

        final List<CotacaoStageRecord> maximum = new ArrayList<>();
        for (int ordinal = 1; ordinal <= CotacaoStageRecord.MAXIMUM_PAGE_SIZE; ordinal++) {
            maximum.add(valid(ordinal));
        }
        assertEquals(
                CotacaoStageRecord.MAXIMUM_PAGE_SIZE,
                new CotacaoStageBatch(executionId, 1, maximum, FRESHNESS).size());
    }

    @Test
    void keepsStableSanitizedQuarantineReasons() {
        final CotacaoStageRecord record =
                CotacaoStageRecord.quarantine(1, "EQUAL_FRESHNESS_CONFLICT");
        assertTrue(record.quarantined());
        assertEquals("EQUAL_FRESHNESS_CONFLICT", record.quarantineReasonCode());
        assertThrows(
                IllegalArgumentException.class,
                () -> CotacaoStageRecord.quarantine(1, "arrival-order"));
    }

    private static CotacaoStageRecord valid(final int ordinal) {
        return new CotacaoStageRecord(
                ordinal,
                integerKey(),
                "{\"payload\":\"private-payload\"}",
                "{\"qoe_uer_name\":\"ABSENT\"}",
                "  José  ",
                null,
                null,
                FRESHNESS,
                FRESHNESS,
                CotacaoFreshnessOrigin.REQUESTED_AT,
                LocalDate.of(2026, 9, 4),
                new BigDecimal("10.2500"),
                null,
                "SP",
                "RJ",
                null);
    }

    private static CotacaoStageRecord withAmount(final BigDecimal amount) {
        final CotacaoStageRecord base = valid(1);
        return new CotacaoStageRecord(
                base.inputOrdinal(),
                base.sourceKey(),
                base.payloadJson(),
                base.fieldPresenceJson(),
                base.userNameNormalized(),
                base.nfseIssuedAtUtc(),
                base.cteIssuedAtUtc(),
                base.requestedAtUtc(),
                base.freshnessAtUtc(),
                base.freshnessOrigin(),
                base.freshnessBusinessDate(),
                amount,
                base.currencyCode(),
                base.originUf(),
                base.destinationUf(),
                null);
    }

    private static CotacaoStageRecord withSourceKey(
            final ScopedSourceIdentity.SourceKey sourceKey) {
        final CotacaoStageRecord base = valid(1);
        return new CotacaoStageRecord(
                base.inputOrdinal(),
                sourceKey,
                base.payloadJson(),
                base.fieldPresenceJson(),
                base.userNameNormalized(),
                base.nfseIssuedAtUtc(),
                base.cteIssuedAtUtc(),
                base.requestedAtUtc(),
                base.freshnessAtUtc(),
                base.freshnessOrigin(),
                base.freshnessBusinessDate(),
                base.totalAmount(),
                base.currencyCode(),
                base.originUf(),
                base.destinationUf(),
                null);
    }

    private static CotacaoStageRecord withCurrency(final String currency) {
        final CotacaoStageRecord base = valid(1);
        return new CotacaoStageRecord(
                base.inputOrdinal(),
                base.sourceKey(),
                base.payloadJson(),
                base.fieldPresenceJson(),
                base.userNameNormalized(),
                base.nfseIssuedAtUtc(),
                base.cteIssuedAtUtc(),
                base.requestedAtUtc(),
                base.freshnessAtUtc(),
                base.freshnessOrigin(),
                base.freshnessBusinessDate(),
                base.totalAmount(),
                currency,
                base.originUf(),
                base.destinationUf(),
                null);
    }

    private static CotacaoStageRecord withUser(final String user) {
        final CotacaoStageRecord base = valid(1);
        return new CotacaoStageRecord(
                base.inputOrdinal(),
                base.sourceKey(),
                base.payloadJson(),
                base.fieldPresenceJson(),
                user,
                base.nfseIssuedAtUtc(),
                base.cteIssuedAtUtc(),
                base.requestedAtUtc(),
                base.freshnessAtUtc(),
                base.freshnessOrigin(),
                base.freshnessBusinessDate(),
                base.totalAmount(),
                base.currencyCode(),
                base.originUf(),
                base.destinationUf(),
                null);
    }

    private static ScopedSourceIdentity.SourceKey integerKey() {
        return integerKey("6906");
    }

    private static ScopedSourceIdentity.SourceKey integerKey(final String value) {
        return new ScopedSourceIdentity.SourceKey(
                ScopedSourceIdentity.WireType.INTEGER, "INTEGER:" + value);
    }
}
