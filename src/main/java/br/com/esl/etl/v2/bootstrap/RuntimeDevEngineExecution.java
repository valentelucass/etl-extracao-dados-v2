package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.CurrentSourceInfoEvidence;
import br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevEngineAdmissionProducer;
import br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevEngineBankAdapter;
import br.com.esl.etl.v2.plataforma.persistencia.controle.C78Sample12AdmissionProducer;
import br.com.esl.etl.v2.plataforma.persistencia.controle.C78Sample12Selection;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.LinkedHashMap;

/** Separate DEV admission followed by the existing real dispatcher and application handlers. */
final class RuntimeDevEngineExecution {
    private RuntimeDevEngineExecution() {}

    record Result(RuntimeExitCategory category, String receipt) {}

    static Result execute(
            final RuntimeDevPreparedInvocation prepared,
            final RuntimeDevWorkspaceBundle bundle,
            final PrintStream diagnostic,
            final java.nio.file.Path dispatch,
            final String dispatchSHA256)
            throws Exception {
        final var request = prepared.request;
        final boolean sampleOptIn =
                prepared.sampleOptIn || Boolean.getBoolean("runtime.dev.engine.sample.enabled");
        if (sampleOptIn) {
            RuntimeDevSampleExecution.validateScope(prepared.configuration, request);
        }
        final var scope =
                new RuntimeDevEngineBankBinding.Scope(
                        prepared.configuration,
                        prepared.scope,
                        prepared.target,
                        bundle.manifestSha256(),
                        bundle.entrySha256(RuntimeDevWorkspaceBundle.CONFIGURATION),
                        bundle.entrySha256(RuntimeDevWorkspaceBundle.REQUEST),
                        request.plan.fingerprint().sha256(),
                        request.template,
                        request.pageSize,
                        request.limits);
        final var unit = bundle.configuration().getParent().getParent().getParent();
        final boolean currentSourceUnit =
                usesCurrentSource(unit, sampleOptIn, prepared.curatedSource);
        final RuntimeDevEngineBankBinding.Admission producer;
        final RuntimeDevSourceEvidenceInspection reader;
        if (currentSourceUnit) {
            final var composition =
                    currentSourceProducer(
                            scope, request, unit, dispatch, dispatchSHA256, diagnostic);
            producer = composition.producer();
            reader = composition.reader();
        } else {
            reader = new RuntimeDevSourceEvidenceInspection(unit, request.template, diagnostic);
            producer =
                    admissionProducer(
                            scope, request, dispatch, dispatchSHA256, reader, sampleOptIn);
        }
        try (var sourceInspection = reader;
                var provisioning =
                        new RuntimeDevSourceProvisioning(
                                sourceInspection,
                                prepared.curatedSource
                                        || Boolean.getBoolean(
                                                "runtime.dev.source.provisioning.enabled"))) {
            final var adapter = new C78DevEngineBankAdapter(dispatch, dispatchSHA256, producer);
            return execute(
                    scope,
                    request,
                    adapter,
                    diagnostic,
                    sourceInspection,
                    provisioning,
                    sampleOptIn);
        }
    }

    record CurrentSourceComposition(
            C78Sample12AdmissionProducer producer, RuntimeDevSourceEvidenceInspection reader) {
        @Override
        public String toString() {
            return "CurrentSourceComposition[redacted; one-producer-private-pipes]";
        }
    }

    static CurrentSourceComposition currentSourceProducer(
            final RuntimeDevEngineBankBinding.Scope scope,
            final RuntimeOperationalRequest request,
            final java.nio.file.Path unit,
            final java.nio.file.Path dispatch,
            final String dispatchSHA256,
            final PrintStream diagnostic) {
        if (!usesCurrentSource(unit, true, true)) {
            throw new IllegalArgumentException("DEV_CURRENT_SOURCE_NAMESPACE_REQUIRED");
        }
        final var actualSource =
                new java.util.concurrent.atomic.AtomicReference<
                        RuntimeDevSourceEvidenceInspection>();
        final var selection = C78Sample12Selection.from(scope, request.plan);
        final var producer =
                C78Sample12AdmissionProducer.sample12Current(
                        selection,
                        dispatch,
                        dispatchSHA256,
                        evidence -> {
                            final var source =
                                    new RuntimeDevSourceEvidenceInspection(
                                            unit, request.template, diagnostic, evidence);
                            if (!actualSource.compareAndSet(null, source)) {
                                source.close();
                                throw new IllegalStateException(
                                        "DEV_SOURCE_READER_FACTORY_NOT_SINGLE");
                            }
                            return source;
                        });
        return new CurrentSourceComposition(
                producer, java.util.Objects.requireNonNull(actualSource.get()));
    }

    static boolean usesCurrentSource(
            final java.nio.file.Path unit, final boolean sampleOptIn, final boolean curatedSource) {
        return sampleOptIn
                && curatedSource
                && unit != null
                && unit.getFileName() != null
                && CurrentSourceInfoEvidence.supportsUnit(unit.getFileName().toString());
    }

    static RuntimeDevEngineBankBinding.Admission admissionProducer(
            final RuntimeDevEngineBankBinding.Scope scope,
            final RuntimeOperationalRequest request,
            final java.nio.file.Path dispatch,
            final String dispatchSHA256,
            final RuntimeDevEngineBankBinding.Admission sourceInspection,
            final boolean sampleOptIn) {
        if (sampleOptIn) {
            final var selection = C78Sample12Selection.from(scope, request.plan);
            return C78Sample12AdmissionProducer.sample12(
                    selection, dispatch, dispatchSHA256, sourceInspection);
        }
        return C78DevEngineAdmissionProducer.inspect(
                scope, dispatch, dispatchSHA256, sourceInspection);
    }

    // Negative admission controls use this seam, never a fake DataSource or source for delivery.
    static Result execute(
            final RuntimeDevEngineBankBinding.Scope scope,
            final RuntimeOperationalRequest request,
            final RuntimeDevEngineBankBinding bank,
            final PrintStream diagnostic)
            throws Exception {
        return execute(scope, request, bank, diagnostic, null, null, false);
    }

    // Selected SAMPLE preparation failures are qualified without a source or physical adapter.
    static Result execute(
            final RuntimeDevEngineBankBinding.Scope scope,
            final RuntimeOperationalRequest request,
            final RuntimeDevEngineBankBinding bank,
            final PrintStream diagnostic,
            final boolean sampleOptIn)
            throws Exception {
        return execute(scope, request, bank, diagnostic, null, null, sampleOptIn);
    }

    private static Result execute(
            final RuntimeDevEngineBankBinding.Scope scope,
            final RuntimeOperationalRequest request,
            final RuntimeDevEngineBankBinding bank,
            final PrintStream diagnostic,
            final RuntimeDevEngineBankBinding.Admission sourceInspection,
            final RuntimeDevSourceProvisioning provisioning,
            final boolean sampleOptIn)
            throws Exception {
        boolean executorInvoked = false;
        boolean openAttempted = false;
        RuntimeHttpAttempts attempts = null;
        try (var admission = bank.prepare(scope)) {
            // All dependent gates precede session/native/TCP/secret work, including recovery SQL.
            admission.verify();
            if (sampleOptIn) {
                try {
                    RuntimeDevSampleExecution.validateReadbackScope(scope.configuration(), request);
                } catch (final IllegalArgumentException invalid) {
                    final var refusal =
                            new RuntimeDevEngineBankBinding.Refusal(
                                    RuntimeDevEngineBankBinding.MissingGate.BUDGET);
                    refusal.initCause(invalid);
                    throw refusal;
                }
            }
            openAttempted = true;
            try (var session = admission.open();
                    var sampleFinish = sampleOptIn ? new SampleFinishGuard(session) : null) {
                session.checkpoint();
                final var dataSource = java.util.Objects.requireNonNull(session.dataSource());
                attempts =
                        new RuntimeHttpAttempts(
                                scope.configuration()
                                        .dataExport()
                                        .orElseThrow()
                                        .resiliencePolicy()
                                        .maxRequestsPerWorkload());
                final var actualAttempts = attempts;
                final br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpAttemptObserver
                        beforeAttempt =
                                attempt -> {
                                    try {
                                        if (sourceInspection != null) {
                                            sourceInspection.verify();
                                        }
                                        session.checkpoint();
                                    } catch (final SQLException refused) {
                                        throw new IllegalStateException(
                                                "DEV_ENGINE_HTTP_GATE_REFUSED", refused);
                                    }
                                    actualAttempts.beforeAttempt(attempt);
                                };
                final var realSource =
                        provisioning != null && provisioning.enabled()
                                ? provisioning.gateways(beforeAttempt)
                                : RuntimeOperationalExecution.observedDataExportSourceGateways(
                                        beforeAttempt);
                final RuntimeOperationalExecution.SourceGateways guardedSource =
                        (configuration, frozen, cancellation, observation) -> {
                            try {
                                if (sourceInspection != null) {
                                    sourceInspection.verify();
                                }
                                session.checkpoint();
                            } catch (final SQLException refused) {
                                throw new IllegalStateException(
                                        "DEV_ENGINE_SOURCE_GATE_REFUSED", refused);
                            }
                            return realSource.create(
                                    configuration, frozen, cancellation, observation);
                        };
                final RuntimeExitCategory outcome;
                boolean partialPageBoundaryObserved = false;
                RuntimeDevEngineBankBinding.SampleReadback sampleReadback = null;
                Coletas6908SampleExtraction.StatusParity statusParity = null;
                if (sampleOptIn) {
                    // A real transaction-aware gateway is supplied by Banco, never the ordinary
                    // page gateway that commits. Missing capability refuses before the executor.
                    final var staging = session.sampleStagingGateway();
                    final Runnable checkpoint =
                            () -> {
                                try {
                                    session.checkpoint();
                                } catch (final SQLException failed) {
                                    throw new IllegalStateException(
                                            "DEV_SAMPLE_BANK_GATE_REFUSED", failed);
                                }
                            };
                    executorInvoked = true;
                    final var finished =
                            executeSampleAndFinish(
                                    sampleFinish,
                                    () ->
                                            RuntimeDevSampleExecution.execute(
                                                    true,
                                                    scope.configuration(),
                                                    request,
                                                    dataSource,
                                                    guardedSource,
                                                    staging,
                                                    checkpoint));
                    outcome = finished.sample().category();
                    partialPageBoundaryObserved = finished.sample().partialPageBoundaryObserved();
                    sampleReadback = finished.readback();
                    statusParity = finished.sample().statusParity().orElse(null);
                } else {
                    executorInvoked = true;
                    outcome =
                            RuntimeOperationalExecution.execute(
                                    scope.configuration(),
                                    request,
                                    br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction.RUN,
                                    dataSource,
                                    guardedSource,
                                    diagnostic::println,
                                    null);
                }
                if (!sampleOptIn) {
                    session.checkpoint();
                }
                final var result =
                        receipt(
                                sampleOptIn
                                        ? partialPageBoundaryObserved
                                                ? "SAMPLE_STAGED_READBACK_ROLLED_BACK"
                                                : "DEV_SAMPLE_WORKLOAD_UNVERIFIED_ROLLBACK_VERIFIED"
                                        : "DEV_ENGINE_EXECUTOR_RETURNED_READBACK_REQUIRED",
                                true);
                result.put("executorExit", outcome.code());
                result.put("executionSemantics", sampleOptIn ? "SAMPLE_ROLLBACK_ONLY" : "ORDINARY");
                if (sampleOptIn) {
                    result.put("samplePartialPageBoundaryObserved", partialPageBoundaryObserved);
                    result.put(
                            "sampleReadbackVerification",
                            sampleReadback.expectationVerified() ? "VERIFIED" : "NOT_VERIFIED");
                    result.put("sampleCleanupVerified", sampleReadback.cleanupVerified());
                    result.put("sampleVerifiedPhysicalRows", sampleReadback.physicalRowsVerified());
                    result.put("sampleVerifiedRoots", sampleReadback.rootsVerified());
                    result.put("sampleVerifiedBatches", sampleReadback.batchesVerified());
                    result.put("sampleSqlStatementsUsed", sampleReadback.sqlStatementsUsed());
                    result.put("sampleRetained", false);
                    result.put("sampleTraversalComplete", false);
                    if (statusParity != null) {
                        publishStatusParity(result, statusParity, sampleReadback);
                    }
                }
                // A return code alone does not prove business completion or independent readback.
                return new Result(
                        outcome.combine(RuntimeExitCategory.DEGRADED),
                        new ObjectMapper().writeValueAsString(result));
            }
        } catch (final SQLException | RuntimeException failure) {
            if (failure instanceof RuntimeDevEngineBankBinding.Refusal refusal
                    && !openAttempted
                    && !executorInvoked
                    && refusal.getSuppressed().length == 0) {
                final var result = receipt("DEV_ENGINE_GATE_REFUSED_ZERO_EFFECTS", false);
                result.put("reasonCode", "DEV_ENGINE_GATE_" + refusal.gate().name());
                if (refusal.gate() == RuntimeDevEngineBankBinding.MissingGate.SOURCE_QUOTA
                        && sourceInspection instanceof RuntimeDevSourceEvidenceInspection facts
                        && facts.gateDetailCode() != null) {
                    result.put("gateDetailCode", facts.gateDetailCode());
                }
                return new Result(
                        RuntimeExitCategory.CONFIG_AUTH,
                        new ObjectMapper().writeValueAsString(result));
            }
            final var result = new LinkedHashMap<String, Object>();
            result.put("status", "DEV_ENGINE_OUTCOME_NOT_VERIFIED");
            result.put("reasonCode", "DEV_ENGINE_BOUNDARY_OR_CLEANUP_FAILED");
            result.put("openAttempted", openAttempted);
            result.put("executorInvoked", executorInvoked);
            result.put("businessOutcomeVerification", "REQUIRED");
            result.put("goalComplete", false);
            result.put("cleanupVerified", false);
            result.putAll(failureDiagnostic(failure));
            return new Result(
                    RuntimeExitCategory.CONFIG_AUTH, new ObjectMapper().writeValueAsString(result));
        } finally {
            if (attempts != null) {
                diagnostic.println(attempts.summary());
            }
        }
    }

    static void publishStatusParity(
            final java.util.Map<String, Object> receipt,
            final Coletas6908SampleExtraction.StatusParity parity,
            final RuntimeDevEngineBankBinding.SampleReadback readback) {
        if (!readback.expectationVerified()
                || !readback.cleanupVerified()
                || readback.physicalRowsVerified() != parity.observedRows()) {
            return;
        }
        receipt.put("sampleV1ParityScope", "STATUS_CODE_LABEL_TERMINAL_ONLY");
        receipt.put("sampleV1StatusParity", parity.outcome());
        receipt.put("sampleV1StatusObservedRows", parity.observedRows());
        receipt.put("sampleV1StatusComparedRows", parity.comparedRows());
        receipt.put("sampleV1StatusDivergentRows", parity.divergentRows());
        receipt.put("sampleV1StatusUnknownRows", parity.unknownRows());
        receipt.put("v1FullFunctionalParityVerified", false);
    }

    record SampleOwnerResult(
            RuntimeDevSampleExecution.Result sample,
            RuntimeDevEngineBankBinding.SampleReadback readback) {}

    /** Forward real extraction memory even on failure; Bank alone reconciles/rolls back/closes. */
    static SampleOwnerResult executeSampleAndFinish(
            final RuntimeDevEngineBankBinding.Session session,
            final java.util.function.Supplier<RuntimeDevSampleExecution.Result> workload)
            throws SQLException {
        try (var finish = new SampleFinishGuard(session)) {
            return executeSampleAndFinish(finish, workload);
        }
    }

    private static SampleOwnerResult executeSampleAndFinish(
            final SampleFinishGuard owner,
            final java.util.function.Supplier<RuntimeDevSampleExecution.Result> workload)
            throws SQLException {
        RuntimeDevSampleExecution.Result sample = null;
        RuntimeDevEngineBankBinding.SampleReadback readback = null;
        Throwable primary = null;
        try {
            sample = java.util.Objects.requireNonNull(workload.get());
            // Preserve the existing post-executor protection check and its finite SQL charge.
            owner.session.checkpoint();
        } catch (final SQLException | RuntimeException | Error failed) {
            primary = failed;
            throw failed;
        } finally {
            final var finish =
                    sample != null
                            ? sample.finish()
                            : new RuntimeDevEngineBankBinding.SampleFinish(
                                    java.util.Optional.empty(),
                                    RuntimeExitCategory.CONFIG_AUTH,
                                    RuntimeDevEngineBankBinding.SampleOutcome.FAILED);
            try {
                readback = owner.finish(finish);
            } catch (final SQLException | RuntimeException | Error cleanup) {
                if (primary != null) {
                    if (cleanup != primary) {
                        primary.addSuppressed(cleanup);
                    }
                } else {
                    throw cleanup;
                }
            }
        }
        return new SampleOwnerResult(sample, readback);
    }

    /** Installed immediately after open; even a failed finish attempt must never be retried. */
    private static final class SampleFinishGuard implements AutoCloseable {
        private final RuntimeDevEngineBankBinding.Session session;
        private boolean attempted;

        private SampleFinishGuard(final RuntimeDevEngineBankBinding.Session session) {
            this.session = java.util.Objects.requireNonNull(session);
        }

        private RuntimeDevEngineBankBinding.SampleReadback finish(
                final RuntimeDevEngineBankBinding.SampleFinish finish) throws SQLException {
            if (attempted) {
                throw new IllegalStateException("DEV_SAMPLE_OWNER_FINISH_ALREADY_ATTEMPTED");
            }
            attempted = true;
            final var evidence = session.finishSample(finish);
            if (evidence == null || !evidence.matches(finish)) {
                throw new SQLException("DEV_SAMPLE_OWNER_READBACK_OR_CLEANUP_NOT_VERIFIED");
            }
            return evidence;
        }

        @Override
        public void close() throws SQLException {
            if (!attempted) {
                finish(
                        new RuntimeDevEngineBankBinding.SampleFinish(
                                java.util.Optional.empty(),
                                RuntimeExitCategory.CONFIG_AUTH,
                                RuntimeDevEngineBankBinding.SampleOutcome.FAILED));
            }
        }
    }

    private static java.util.Map<String, Object> failureDiagnostic(final Throwable failure) {
        final var result = new LinkedHashMap<String, Object>();
        final var types = new java.util.ArrayList<String>();
        final var seen =
                java.util.Collections.newSetFromMap(
                        new java.util.IdentityHashMap<Throwable, Boolean>());
        Throwable current = failure;
        String frame = "V2_FRAME_NOT_OBSERVED";
        while (current != null && types.size() < 6 && seen.add(current)) {
            types.add(current.getClass().getName());
            final var stack = current.getStackTrace();
            for (int index = 0; index < Math.min(32, stack.length); index++) {
                final var candidate = stack[index];
                if (frame.equals("V2_FRAME_NOT_OBSERVED")
                        && candidate
                                .getClassName()
                                .matches("br\\.com\\.esl\\.etl\\.v2\\.[A-Za-z0-9_.$]+")
                        && candidate.getMethodName().matches("[A-Za-z0-9_$<>]+")) {
                    frame =
                            candidate.getClassName()
                                    + "#"
                                    + candidate.getMethodName()
                                    + ":"
                                    + candidate.getLineNumber();
                }
            }
            current = current.getCause();
        }
        result.put("failureType", failure.getClass().getName());
        result.put("failureCauseTypes", types);
        result.put("failureV2Frame", frame);
        final var suppressed = failure.getSuppressed();
        result.put("cleanupSuppressedCount", suppressed.length);
        result.put(
                "cleanupSuppressedTypes",
                java.util.Arrays.stream(suppressed)
                        .limit(6)
                        .map(value -> value.getClass().getName())
                        .toList());
        return result;
    }

    private static LinkedHashMap<String, Object> receipt(
            final String status, final boolean executorInvoked) {
        final var result = new LinkedHashMap<String, Object>();
        result.put("status", status);
        result.put("executorBinding", "RuntimeOperationalExecution/RuntimeDispatcher");
        result.put("executorInvoked", executorInvoked);
        if (!executorInvoked) {
            result.put("engineExecuted", false);
            result.put("sourceExecuted", false);
            result.put("captureExecuted", false);
        } else {
            result.put("businessOutcomeVerification", "REQUIRED");
        }
        result.put("goalComplete", false);
        result.put("physicalEvidence", false);
        return result;
    }
}
