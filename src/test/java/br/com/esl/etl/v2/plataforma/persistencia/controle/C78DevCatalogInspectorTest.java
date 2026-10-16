package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.configuracao.SecretProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** JDBC doubles qualify the new adapter boundary only; these are not physical Main/SQL proofs. */
final class C78DevCatalogInspectorTest {
    private String enabledBefore;
    private String pinBefore;
    private final SecretProvider unavailable =
            key -> {
                throw new AssertionError("PROVIDER_MUST_NOT_RUN");
            };

    @BeforeEach
    void enableOwnFlags() {
        enabledBefore = System.getProperty("c78.local.shadow.enabled");
        pinBefore = System.getProperty("c78.local.shadow.sql-pin.enabled");
        System.setProperty("c78.local.shadow.enabled", "true");
        System.setProperty("c78.local.shadow.sql-pin.enabled", "true");
    }

    @AfterEach
    void restoreOwnFlags() {
        restore("c78.local.shadow.enabled", enabledBefore);
        restore("c78.local.shadow.sql-pin.enabled", pinBefore);
    }

    private static void restore(final String key, final String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static C78DevCatalogPlan plan() {
        return C78DevCatalogPlan.prepare(
                UUID.randomUUID(),
                C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE));
    }

    @Test
    void preparationIsPureAndPreservesExactProfile() {
        System.clearProperty("c78.local.shadow.enabled");
        System.clearProperty("c78.local.shadow.sql-pin.enabled");
        assertEquals(C78LocalSqlTarget.URL, plan().target().jdbcUrl());
        assertEquals(12, plan().queries().size());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new C78LocalSqlTarget(
                                C78LocalSqlTarget.URL.replace("SHADOW", "PROD"),
                                C78LocalSqlTarget.APPROVAL_REFERENCE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"c78.local.shadow.enabled", "c78.local.shadow.sql-pin.enabled"})
    void eitherMissingFlagRefusesBeforeAdmissionOrOpening(final String key) {
        System.clearProperty(key);
        final var fixture = new Fixture();
        assertThrows(
                RuntimeException.class,
                () -> fixture.inspector.inspect(plan(), unavailable, fixture));
        assertEquals(0, fixture.admissions);
        assertEquals(0, fixture.opens);
    }

    @Test
    void missingAdmissionHasNoOpening() {
        final var fixture = new Fixture();
        assertThrows(
                NullPointerException.class,
                () -> fixture.inspector.inspect(plan(), unavailable, null));
        assertEquals(0, fixture.opens);
    }

    @Test
    void rootAdmissionRefusalIsSanitizedAndConsumed() {
        final var fixture = new Fixture();
        fixture.refuseAdmission = true;
        final var error =
                assertThrows(
                        SQLException.class,
                        () -> fixture.inspector.inspect(plan(), unavailable, fixture));
        assertEquals("C78_CATALOG_ADMISSION_REFUSED", error.getMessage());
        assertNull(error.getCause());
        fixture.refuseAdmission = false;
        assertThrows(
                SQLException.class, () -> fixture.inspector.inspect(plan(), unavailable, fixture));
        assertEquals(0, fixture.opens);
        assertEquals(1, fixture.admissions);
    }

    @Test
    void fixedTwelveSelectsFinishAfterStatementResultAndConnectionClosure() throws SQLException {
        final var fixture = new Fixture();
        final var report = fixture.inspector.inspect(plan(), unavailable, fixture);
        assertEquals(12, report.selectsAttempted());
        assertEquals(12, report.evidence().size());
        assertEquals(1, fixture.opens);
        assertEquals(12, fixture.statementsClosed);
        assertEquals(12, fixture.resultsClosed);
        assertTrue(fixture.closed);
        assertTrue(fixture.receiptClosed);
        assertEquals(12, fixture.receiptAttempts);
        assertEquals(12, fixture.timeouts.size());
        assertTrue(fixture.timeouts.stream().allMatch(n -> n == 10));
        assertEquals(plan().queries(), fixture.executed);
        assertThrows(UnsupportedOperationException.class, () -> report.evidence().clear());
    }

    @Test
    void catalogRouteRefusesServerSettingAndDriverMetadataApis() throws SQLException {
        final var fixture = new Fixture();
        final var report = fixture.inspector.inspect(plan(), unavailable, fixture);
        assertEquals(12, report.selectsAttempted());
        assertEquals(plan().queries(), fixture.executed);
        assertEquals(12, fixture.statementsClosed);
        assertEquals(12, fixture.resultsClosed);
        assertTrue(fixture.receiptClosed);
        try (final var guarded = fixture.statement()) {
            assertEquals(
                    "SERVER_SQL_SETTING_API_REFUSED",
                    assertThrows(AssertionError.class, () -> guarded.setMaxFieldSize(1024))
                            .getMessage());
            assertEquals(
                    "SERVER_SQL_SETTING_API_REFUSED",
                    assertThrows(AssertionError.class, () -> guarded.setMaxRows(512)).getMessage());
        }
        final Connection guarded = Fixture.proxy(Connection.class, fixture);
        assertEquals(
                "UNEXPECTED_JDBC_CONNECTION_METHOD_getMetaData",
                assertThrows(AssertionError.class, guarded::getMetaData).getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {256, 257})
    void cellBoundStopsWithoutDriverSideTruncation(final int chars) throws SQLException {
        final var fixture = new Fixture();
        fixture.rows.put(
                C78DevCatalogPlan.Query.TABLE_PRESENCE,
                List.of(List.of("ctl", "x".repeat(chars), "1")));
        if (chars == 256) {
            final var report = fixture.inspector.inspect(plan(), unavailable, fixture);
            assertEquals("ctl|" + "x".repeat(chars) + "|1", report.evidence().get(1).rows().get(0));
            assertEquals(12, fixture.executed.size());
        } else {
            assertEquals(
                    "METADATA_SHAPE",
                    assertThrows(
                                    C78DevCatalogInspector.CatalogFailure.class,
                                    () -> fixture.inspector.inspect(plan(), unavailable, fixture))
                            .reasonCode());
            assertEquals(2, fixture.executed.size());
        }
        assertEquals(1, fixture.opens);
        assertTrue(fixture.closed);
        assertTrue(fixture.receiptClosed);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "0|16.0.1000.6|1|SQL|48480|80|0",
                "1|16.0.1000.7|1|SQL|48480|80|0",
                "1|16.0.1000.6|0|SQL|48480|80|0",
                "1|16.0.1000.6|1|NTLM|48480|80|0",
                "1|16.0.1000.6|1|SQL|0|80|0",
                "1|16.0.1000.6|1|SQL|48480|80|1"
            })
    void wrongTargetVersionAuthenticationOrTransactionStopsOnFirstSelect(final String context) {
        final var fixture = new Fixture();
        fixture.rows.put(
                C78DevCatalogPlan.Query.CONTEXT_PRE, List.of(List.of(context.split("\\|"))));
        final var error =
                assertThrows(
                        SQLException.class,
                        () -> fixture.inspector.inspect(plan(), unavailable, fixture));
        assertEquals("C78_CATALOG_TARGET_VERSION_AUTH_CONTEXT", error.getMessage());
        assertEquals(1, fixture.executed.size());
        assertTrue(fixture.closed);
    }

    @Test
    void prepostPidOrSessionDriftRefusesWithoutRetry() {
        final var fixture = new Fixture();
        fixture.rows.put(
                C78DevCatalogPlan.Query.CONTEXT_POST,
                List.of(List.of("1", "16.0.1000.6", "1", "SQL", "48481", "80", "0")));
        final var error =
                assertThrows(
                        SQLException.class,
                        () -> fixture.inspector.inspect(plan(), unavailable, fixture));
        assertEquals("C78_CATALOG_PREPOST_MISMATCH", error.getMessage());
        assertEquals(1, fixture.opens);
        assertTrue(fixture.closed);
    }

    @Test
    void missingObjectsAreReportedForReviewWithoutCaptureClaim() throws SQLException {
        final var fixture = new Fixture();
        fixture.rows.put(
                C78DevCatalogPlan.Query.TABLE_PRESENCE,
                List.of(List.of("ctl", "execution_attempt", "0")));
        final var report = fixture.inspector.inspect(plan(), unavailable, fixture);
        assertEquals("ctl|execution_attempt|0", report.evidence().get(1).rows().get(0));
        assertEquals("0", report.evidence().get(10).rows().get(0));
    }

    @Test
    void rowLimitIsDetectedRatherThanSilentlyTruncated() {
        final var fixture = new Fixture();
        final var excess = new ArrayList<List<String>>();
        for (int n = 0; n < 513; n++) {
            excess.add(List.of("ctl", "execution_attempt", "1"));
        }
        fixture.rows.put(C78DevCatalogPlan.Query.TABLE_PRESENCE, excess);
        final var error =
                assertThrows(
                        SQLException.class,
                        () -> fixture.inspector.inspect(plan(), unavailable, fixture));
        assertEquals("C78_CATALOG_ROW_LIMIT", error.getMessage());
        assertEquals(2, fixture.executed.size());
        assertTrue(fixture.closed);
    }

    @Test
    void perQueryMaterialCapStopsFiniteMetadataRead() {
        final var fixture = new Fixture();
        final var excess = new ArrayList<List<String>>();
        for (int n = 0; n < 100; n++) {
            excess.add(List.of("x".repeat(256), "y".repeat(256), "z".repeat(256)));
        }
        fixture.rows.put(C78DevCatalogPlan.Query.TABLE_PRESENCE, excess);
        assertEquals(
                "C78_CATALOG_MATERIAL_LIMIT",
                assertThrows(
                                SQLException.class,
                                () -> fixture.inspector.inspect(plan(), unavailable, fixture))
                        .getMessage());
        assertTrue(fixture.closed);
    }

    @Test
    void unexpectedMetadataCannotLeakThroughReport() {
        final var fixture = new Fixture();
        fixture.rows.put(
                C78DevCatalogPlan.Query.TABLE_PRESENCE, List.of(List.of("ctl", "bad\nvalue", "1")));
        assertEquals(
                "C78_CATALOG_METADATA_SHAPE",
                assertThrows(
                                SQLException.class,
                                () -> fixture.inspector.inspect(plan(), unavailable, fixture))
                        .getMessage());
        assertTrue(fixture.closed);
    }

    @Test
    void elapsedAdmissionCannotStartConnection() {
        final var fixture = new Fixture();
        fixture.admissionNanos = TimeUnit.SECONDS.toNanos(60);
        assertEquals(
                "C78_CATALOG_DEADLINE",
                assertThrows(
                                SQLException.class,
                                () -> fixture.inspector.inspect(plan(), unavailable, fixture))
                        .getMessage());
        assertEquals(0, fixture.opens);
    }

    @Test
    void elapsedQueryStopsAndCloses() {
        final var fixture = new Fixture();
        fixture.queryNanos = TimeUnit.SECONDS.toNanos(60);
        assertEquals(
                "C78_CATALOG_DEADLINE",
                assertThrows(
                                SQLException.class,
                                () -> fixture.inspector.inspect(plan(), unavailable, fixture))
                        .getMessage());
        assertEquals(1, fixture.executed.size());
        assertTrue(fixture.closed);
    }

    @Test
    void driverFailureStopsFirstFailedQueryAndDropsNestedPrivateCause() {
        final var fixture = new Fixture();
        fixture.failQuery = C78DevCatalogPlan.Query.COLUMNS;
        final var error =
                assertThrows(
                        SQLException.class,
                        () -> fixture.inspector.inspect(plan(), unavailable, fixture));
        assertEquals("C78_CATALOG_EXECUTION_FAILED", error.getMessage());
        assertNull(error.getCause());
        assertEquals(0, error.getSuppressed().length);
        assertEquals(3, fixture.executed.size());
        assertEquals(3, fixture.statementsClosed);
        assertEquals(2, fixture.resultsClosed);
        assertTrue(fixture.closed);
    }

    @Test
    void connectionClosureUncertaintyRefusesReportAndReplay() {
        final var fixture = new Fixture();
        fixture.failClose = true;
        assertEquals(
                "C78_CATALOG_CLOSE_UNCERTAIN",
                assertThrows(
                                SQLException.class,
                                () -> fixture.inspector.inspect(plan(), unavailable, fixture))
                        .getMessage());
        assertFalse(fixture.receiptClosed);
        assertThrows(
                SQLException.class, () -> fixture.inspector.inspect(plan(), unavailable, fixture));
        assertEquals(1, fixture.opens);
    }

    @Test
    void receiptFailureCannotReturnSuccessfulReport() {
        final var fixture = new Fixture();
        fixture.failReceipt = true;
        assertEquals(
                "C78_CATALOG_RECEIPT_UNCERTAIN",
                assertThrows(
                                SQLException.class,
                                () -> fixture.inspector.inspect(plan(), unavailable, fixture))
                        .getMessage());
        assertTrue(fixture.closed);
    }

    @Test
    void realSqlProcedureParameterNamesRemainInCatalogEvidence() throws SQLException {
        final var fixture = new Fixture();
        // V015 signature, lines118-125: metadata names/types only, not parameter values/schema
        // proof.
        fixture.rows.put(
                C78DevCatalogPlan.Query.PARAMETERS,
                List.of(
                        List.of(
                                "ctl",
                                "usp_runtime_recovery",
                                "1",
                                "@execution_id",
                                "uniqueidentifier",
                                "16",
                                "0",
                                "0",
                                "0"),
                        List.of(
                                "ctl",
                                "usp_runtime_recovery",
                                "2",
                                "@operation",
                                "nvarchar",
                                "-1",
                                "0",
                                "0",
                                "0"),
                        List.of(
                                "ctl",
                                "usp_runtime_recovery",
                                "3",
                                "@expected_identity",
                                "nvarchar",
                                "-1",
                                "0",
                                "0",
                                "0"),
                        List.of(
                                "ctl",
                                "usp_runtime_recovery",
                                "4",
                                "@contract_material",
                                "nvarchar",
                                "-1",
                                "0",
                                "0",
                                "0"),
                        List.of(
                                "ctl",
                                "usp_runtime_recovery",
                                "5",
                                "@policy_version",
                                "nvarchar",
                                "-1",
                                "0",
                                "0",
                                "0"),
                        List.of(
                                "ctl",
                                "usp_runtime_recovery",
                                "6",
                                "@policy_fingerprint",
                                "nvarchar",
                                "-1",
                                "0",
                                "0",
                                "0"),
                        List.of(
                                "ctl",
                                "usp_runtime_recovery",
                                "7",
                                "@expected_revision",
                                "nvarchar",
                                "-1",
                                "0",
                                "0",
                                "0"),
                        List.of(
                                "ctl",
                                "usp_runtime_recovery",
                                "8",
                                "@entity",
                                "nvarchar",
                                "-1",
                                "0",
                                "0",
                                "0")));
        final var report = fixture.inspector.inspect(plan(), unavailable, fixture);
        assertEquals(8, report.evidence().get(4).rows().size());
        assertEquals(
                "ctl|usp_runtime_recovery|1|@execution_id|uniqueidentifier|16|0|0|0",
                report.evidence().get(4).rows().get(0));
        assertEquals(
                "ctl|usp_runtime_recovery|8|@entity|nvarchar|-1|0|0|0",
                report.evidence().get(4).rows().get(7));
        assertTrue(fixture.closed);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "@execution|id",
                "@execution;id",
                "@execution\"id",
                "@execution\\id",
                "@execution\nid",
                "@execução"
            })
    void parameterMetadataStillRejectsDelimiterAndArbitraryCharacters(final String name) {
        final var fixture = new Fixture();
        fixture.rows.put(
                C78DevCatalogPlan.Query.PARAMETERS,
                List.of(
                        List.of(
                                "ctl",
                                "usp_runtime_recovery",
                                "1",
                                name,
                                "uniqueidentifier",
                                "16",
                                "0",
                                "0",
                                "0")));
        assertEquals(
                "C78_CATALOG_METADATA_SHAPE",
                assertThrows(
                                SQLException.class,
                                () -> fixture.inspector.inspect(plan(), unavailable, fixture))
                        .getMessage());
        assertEquals(5, fixture.executed.size());
        assertTrue(fixture.closed);
    }

    @Test
    void nullParameterMetadataStillRefusesAndCloses() {
        final var fixture = new Fixture();
        final var row =
                new ArrayList<>(
                        List.of(
                                "ctl",
                                "usp_runtime_recovery",
                                "1",
                                "@execution_id",
                                "uniqueidentifier",
                                "16",
                                "0",
                                "0",
                                "0"));
        row.set(3, null);
        fixture.rows.put(C78DevCatalogPlan.Query.PARAMETERS, List.of(row));
        assertEquals(
                "C78_CATALOG_METADATA_SHAPE",
                assertThrows(
                                SQLException.class,
                                () -> fixture.inspector.inspect(plan(), unavailable, fixture))
                        .getMessage());
        assertTrue(fixture.closed);
    }

    @Test
    void knownClosedConnectorFailureKeepsConstantCategoryAndCallback() {
        final var fixture = new Fixture();
        fixture.openFailure = actualConnectorFailure(false);
        final var error =
                assertThrows(
                        SQLException.class,
                        () -> fixture.inspector.inspect(plan(), unavailable, fixture));
        assertEquals("C78_CATALOG_CONNECTOR_NOT_OBSERVED", error.getMessage());
        assertTrue(fixture.receiptClosed);
        assertEquals(1, fixture.receipts);
        assertEquals(0, fixture.receiptAttempts);
        assertEquals(0, fixture.executed.size());
        assertNull(error.getCause());
        assertEquals(0, error.getSuppressed().length);
        assertThrows(
                SQLException.class, () -> fixture.inspector.inspect(plan(), unavailable, fixture));
        assertEquals(1, fixture.opens);
    }

    @Test
    void uncertainConnectorFailureRetainsUncertaintyAndCallback() {
        final var fixture = new Fixture();
        fixture.openFailure = actualConnectorFailure(true);
        final var error =
                assertThrows(
                        SQLException.class,
                        () -> fixture.inspector.inspect(plan(), unavailable, fixture));
        assertEquals("C78_CATALOG_CONNECTOR_NOT_OBSERVED_CLOSE_UNCERTAIN", error.getMessage());
        assertFalse(fixture.receiptClosed);
        assertEquals(1, fixture.receipts);
        assertEquals(0, fixture.receiptAttempts);
        assertEquals(0, fixture.executed.size());
        assertNull(error.getCause());
        assertEquals(0, error.getSuppressed().length);
    }

    private static C78LocalSaJdbcConnector.ConnectionFailure actualConnectorFailure(
            final boolean uncertain) {
        final var target = plan().target();
        final var closes = new java.util.concurrent.atomic.AtomicInteger();
        final Connection unaccepted =
                Fixture.proxy(
                        Connection.class,
                        (object, method, args) -> {
                            if (!method.getName().equals("close")) {
                                throw new AssertionError("NO_NETWORK_OR_SQL_METHOD_ALLOWED");
                            }
                            closes.incrementAndGet();
                            if (uncertain) {
                                throw new SQLException("UNTRUSTED_CLOSE_DETAIL");
                            }
                            return null;
                        });
        final var error =
                assertThrows(
                        C78LocalSaJdbcConnector.ConnectionFailure.class,
                        () ->
                                C78LocalSaJdbcConnector.open(
                                        target,
                                        key -> UUID.randomUUID().toString(),
                                        (url, properties) -> unaccepted,
                                        () ->
                                                br.com.esl.etl.v2.plataforma.configuracao
                                                        .C78LocalTlsScope.enter(target)));
        assertEquals(uncertain, error.cleanupUncertain());
        assertEquals(1, closes.get());
        error.initCause(new IllegalStateException("UNTRUSTED_NESTED_DETAIL"));
        return error;
    }

    @Test
    void connectorReasonVocabularyCannotEchoUnknownOrNullMetadata() {
        assertEquals("CONNECTOR_REFUSED", C78DevCatalogInspector.connectorReason(null));
        assertEquals(
                "CONNECTOR_REFUSED",
                C78DevCatalogInspector.connectorReason("UNTRUSTED_RAW_DETAIL"));
        assertEquals(
                "CONNECTOR_REFUSED",
                C78DevCatalogInspector.connectorReason("LEAF_CHANGED\nUNTRUSTED_DETAIL"));
        for (final String reason :
                List.of(
                        "SCOPE_CREATION_REFUSED",
                        "NOT_OBSERVED",
                        "PIN_HANDSHAKE_PROOF_MISSING",
                        "PIN_VERIFIED_DRIVER_OR_AUTH_FAILED",
                        "CERTIFICATE_OR_PEER_REFUSED",
                        "TLS_SOCKET_REQUIRED",
                        "TLS_ALGORITHMS_REFUSED",
                        "SINGLE_LEAF_REQUIRED",
                        "DER_BOUND",
                        "LEAF_CHANGED",
                        "LEAF_EXPIRED",
                        "LEAF_NOT_YET_VALID",
                        "SELF_SIGNATURE_REFUSED",
                        "SOCKET_PROOF_REQUIRED")) {
            assertEquals("CONNECTOR_" + reason, C78DevCatalogInspector.connectorReason(reason));
        }
    }

    @Test
    void actualConnectorScopeCreationRefusalHasKnownClosedDiagnostic() {
        final var fixture = new Fixture();
        final var target = plan().target();
        fixture.openFailure =
                assertThrows(
                        C78LocalSaJdbcConnector.ConnectionFailure.class,
                        () ->
                                C78LocalSaJdbcConnector.open(
                                        target,
                                        unavailable,
                                        (url, properties) -> {
                                            throw new AssertionError("DRIVER_MUST_NOT_RUN");
                                        },
                                        () -> {
                                            throw new IllegalStateException(
                                                    "UNTRUSTED_SCOPE_DETAIL");
                                        }));
        assertEquals(
                "C78_CATALOG_CONNECTOR_SCOPE_CREATION_REFUSED",
                assertThrows(
                                SQLException.class,
                                () -> fixture.inspector.inspect(plan(), unavailable, fixture))
                        .getMessage());
        assertTrue(fixture.receiptClosed);
        assertEquals(1, fixture.receipts);
        assertEquals(0, fixture.receiptAttempts);
    }

    @Test
    void evidenceOversizeRefusesBeforeCopyOrIteration() {
        final List<String> source =
                new java.util.AbstractList<>() {
                    @Override
                    public int size() {
                        return 513;
                    }

                    @Override
                    public String get(final int index) {
                        throw new AssertionError("OVERSIZE_ROWS_MUST_NOT_MATERIALIZE");
                    }

                    @Override
                    public java.util.Iterator<String> iterator() {
                        throw new AssertionError("OVERSIZE_ROWS_MUST_NOT_ITERATE");
                    }
                };
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new C78DevCatalogInspector.Evidence(
                                C78DevCatalogPlan.Query.PARAMETERS, source));
    }

    @Test
    void reportOversizeRefusesBeforeCopyOrIteration() {
        final List<C78DevCatalogInspector.Evidence> source =
                new java.util.AbstractList<>() {
                    @Override
                    public int size() {
                        return 13;
                    }

                    @Override
                    public C78DevCatalogInspector.Evidence get(final int index) {
                        throw new AssertionError("OVERSIZE_EVIDENCE_MUST_NOT_MATERIALIZE");
                    }

                    @Override
                    public java.util.Iterator<C78DevCatalogInspector.Evidence> iterator() {
                        throw new AssertionError("OVERSIZE_EVIDENCE_MUST_NOT_ITERATE");
                    }
                };
        assertThrows(
                IllegalArgumentException.class,
                () -> new C78DevCatalogInspector.Report(plan(), source, 12));
    }

    @Test
    void devConnectorCategoriesRemainClosedAndUnknownIsGeneric() {
        for (final String reason :
                List.of(
                        "DEV_PEER_BINDING_CHANGED",
                        "DEV_PEER_PROOF_REENTRANT",
                        "DEV_PEER_PROOF_TIMEOUT",
                        "DEV_PEER_PROOF_REFUSED")) {
            assertEquals("CONNECTOR_" + reason, C78DevCatalogInspector.connectorReason(reason));
        }
        assertEquals(
                "CONNECTOR_REFUSED",
                C78DevCatalogInspector.connectorReason("DEV_PEER_PROOF_UNKNOWN"));
        assertEquals(
                "CONNECTOR_REFUSED",
                C78DevCatalogInspector.connectorReason("DEV_PEER_PROOF_REFUSED\nUNTRUSTED_DETAIL"));
    }

    @Test
    void reasonCodeGetterExposesOnlyReviewedUnprefixedConstant() {
        final var fixture = new Fixture();
        fixture.refuseAdmission = true;
        final var error =
                assertThrows(
                        C78DevCatalogInspector.CatalogFailure.class,
                        () -> fixture.inspector.inspect(plan(), unavailable, fixture));
        assertEquals("ADMISSION_REFUSED", error.reasonCode());
        assertEquals("C78_CATALOG_ADMISSION_REFUSED", error.getMessage());
        error.initCause(new SQLException(UUID.randomUUID().toString()));
        error.setNextException(new SQLException(UUID.randomUUID().toString()));
        assertEquals("ADMISSION_REFUSED", error.reasonCode());
        assertEquals(0, fixture.opens);
    }

    @Test
    void privateFinalFailureRejectsUnknownReasonWithoutEcho() throws Exception {
        final var type = C78DevCatalogInspector.CatalogFailure.class;
        assertTrue(java.lang.reflect.Modifier.isFinal(type.getModifiers()));
        final var constructor = type.getDeclaredConstructor(String.class);
        assertTrue(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()));
        final var field = type.getDeclaredField("reasonCode");
        assertTrue(java.lang.reflect.Modifier.isFinal(field.getModifiers()));
        constructor.setAccessible(true);
        for (final String reason :
                new String[] {
                    null,
                    UUID.randomUUID().toString(),
                    "ADMISSION_REFUSED\nUNTRUSTED_DETAIL",
                    "CONNECTOR_DEV_PEER_PROOF_UNKNOWN"
                }) {
            final var failure = constructor.newInstance(reason);
            assertEquals("EXECUTION_FAILED", failure.reasonCode());
            assertEquals("C78_CATALOG_EXECUTION_FAILED", failure.getMessage());
            assertNull(failure.getCause());
        }
    }

    private static final class Fixture implements C78DevCatalogAdmission, InvocationHandler {
        private final EnumMap<C78DevCatalogPlan.Query, List<List<String>>> rows =
                new EnumMap<>(C78DevCatalogPlan.Query.class);
        private final List<C78DevCatalogPlan.Query> executed = new ArrayList<>();
        private final List<Integer> timeouts = new ArrayList<>();
        private long now;
        private long admissionNanos;
        private long queryNanos;
        private int admissions;
        private int opens;
        private int statementsClosed;
        private int resultsClosed;
        private int receiptAttempts;
        private int receipts;
        private boolean closed;
        private boolean receiptClosed;
        private boolean refuseAdmission;
        private boolean failClose;
        private boolean failReceipt;
        private C78DevCatalogPlan.Query failQuery;
        private SQLException openFailure;
        private final C78DevCatalogInspector inspector =
                new C78DevCatalogInspector(
                        (target, provider) -> {
                            opens++;
                            if (openFailure != null) {
                                throw openFailure;
                            }
                            return proxy(Connection.class, this);
                        },
                        () -> now);

        Fixture() {
            for (final var query : C78DevCatalogPlan.Query.values()) {
                rows.put(query, List.of());
            }
            final var context =
                    List.of(List.of("1", "16.0.1000.6", "1", "SQL", "48480", "80", "0"));
            rows.put(C78DevCatalogPlan.Query.CONTEXT_PRE, context);
            rows.put(C78DevCatalogPlan.Query.CONTEXT_POST, context);
            rows.put(C78DevCatalogPlan.Query.WRITER_SNAPSHOT, List.of(List.of("0")));
        }

        @Override
        public void beforeOpen(final C78DevCatalogPlan value) throws SQLException {
            admissions++;
            now += admissionNanos;
            if (refuseAdmission) {
                throw new SQLException("UNREVIEWED_PRIVATE_ADMISSION_DETAIL");
            }
            assertNotNull(value);
        }

        @Override
        public void afterClose(
                final C78DevCatalogPlan value,
                final boolean connectionClosed,
                final int selectsAttempted)
                throws SQLException {
            receipts++;
            receiptClosed = connectionClosed;
            receiptAttempts = selectsAttempted;
            if (failReceipt) {
                throw new SQLException("OWN_RECEIPT_FAILURE");
            }
        }

        @Override
        public Object invoke(final Object object, final Method method, final Object[] arguments)
                throws SQLException {
            return switch (method.getName()) {
                case "createStatement" -> statement();
                case "close" -> {
                    if (failClose) {
                        throw new SQLException("OWN_CLOSE_FAILURE");
                    }
                    closed = true;
                    yield null;
                }
                case "isClosed" -> closed;
                default ->
                        throw new AssertionError(
                                "UNEXPECTED_JDBC_CONNECTION_METHOD_" + method.getName());
            };
        }

        private Statement statement() {
            return proxy(
                    Statement.class,
                    (object, method, arguments) ->
                            switch (method.getName()) {
                                case "setQueryTimeout" -> {
                                    timeouts.add((Integer) arguments[0]);
                                    yield null;
                                }
                                case "setFetchSize" -> null;
                                case "setMaxFieldSize", "setMaxRows" ->
                                        throw new AssertionError("SERVER_SQL_SETTING_API_REFUSED");
                                case "close" -> {
                                    statementsClosed++;
                                    yield null;
                                }
                                case "executeQuery" -> {
                                    final var query =
                                            C78DevCatalogPlan.Query.values()[executed.size()];
                                    assertEquals(query.sql(), arguments[0]);
                                    executed.add(query);
                                    now += queryNanos;
                                    if (query == failQuery) {
                                        throw new SQLException(
                                                "UNTRUSTED_DRIVER_DETAIL",
                                                new RuntimeException("UNTRUSTED_CAUSE"));
                                    }
                                    yield results(rows.get(query));
                                }
                                default ->
                                        throw new AssertionError(
                                                "UNEXPECTED_JDBC_STATEMENT_METHOD_"
                                                        + method.getName());
                            });
        }

        private ResultSet results(final List<List<String>> values) {
            final int[] index = {-1};
            return proxy(
                    ResultSet.class,
                    (object, method, arguments) ->
                            switch (method.getName()) {
                                case "next" -> ++index[0] < values.size();
                                case "getString" ->
                                        values.get(index[0]).get((Integer) arguments[0] - 1);
                                case "close" -> {
                                    resultsClosed++;
                                    yield null;
                                }
                                default ->
                                        throw new AssertionError(
                                                "UNEXPECTED_JDBC_RESULT_METHOD_"
                                                        + method.getName());
                            });
        }

        private static <T> T proxy(final Class<T> type, final InvocationHandler handler) {
            return type.cast(
                    Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
        }
    }
}
