package br.com.esl.etl.v2.plataforma.persistencia.controle;

import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding;
import br.com.esl.etl.v2.plataforma.configuracao.C78DevPeerProof;
import br.com.esl.etl.v2.plataforma.configuracao.SecretKey;
import br.com.esl.etl.v2.plataforma.configuracao.SecretProvider;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.function.Supplier;

/** Separate explicit engine dispatch; neither a bundle nor the catalog claims grant admission. */
public final class C78DevEngineBankAdapter implements RuntimeDevEngineBankBinding {
    @FunctionalInterface
    interface Opener {
        Connection open(Scope scope, SecretProvider secrets, C78DevPeerProof proof)
                throws SQLException;
    }

    private final Path dispatch;
    private final String dispatchSHA256;
    private final Supplier<SecretProvider> secrets;
    private final Opener opener;
    private final Admission independentAdmission;

    /** The existing CLI without a Root dispatch has no physical authority. */
    public C78DevEngineBankAdapter() {
        this(null, null);
    }

    /** Runtime must wire its explicit dispatch CLI to this same leased concrete adapter. */
    public C78DevEngineBankAdapter(final Path dispatch, final String dispatchSHA256) {
        this(dispatch, dispatchSHA256, null);
    }

    /**
     * Only a directly bound, reviewed Root/custodian admission can supply independent authority.
     * Serialized descriptors, parent identity, local flags and digests cannot construct it.
     */
    public C78DevEngineBankAdapter(
            final Path dispatch,
            final String dispatchSHA256,
            final Admission independentAdmission) {
        this(
                dispatch,
                dispatchSHA256,
                independentAdmission,
                () ->
                        key -> {
                            if (key != SecretKey.SHADOW_SAMPLE_SA_PASSWORD) {
                                throw new IllegalStateException("DEV_ENGINE_SECRET_KEY_REFUSED");
                            }
                            final String value = System.getenv("C78_DEV_SA_SECRET");
                            if (value == null || value.isBlank()) {
                                throw new IllegalStateException(
                                        "DEV_ENGINE_EXISTING_SECRET_UNAVAILABLE");
                            }
                            return value;
                        },
                (scope, provider, proof) ->
                        C78LocalSaJdbcConnector.openDev(scope.target(), provider, proof));
    }

    // Offline boundary tests only; production always uses the qualified concrete C78 connector.
    C78DevEngineBankAdapter(
            final Path dispatch,
            final String dispatchSHA256,
            final Admission independentAdmission,
            final Supplier<SecretProvider> secrets,
            final Opener opener) {
        this.dispatch = dispatch;
        this.dispatchSHA256 = dispatchSHA256;
        this.independentAdmission = independentAdmission;
        this.secrets = Objects.requireNonNull(secrets);
        this.opener = Objects.requireNonNull(opener);
    }

    @Override
    public Admission prepare(final Scope scope) throws SQLException {
        Objects.requireNonNull(scope);
        return new Prepared(scope);
    }

    private final class Prepared implements Admission {
        private final Scope scope;
        private C78DevEngineDispatch controls;
        private C78DevEngineSession session;
        private boolean opened;
        private boolean closed;

        private Prepared(final Scope scope) {
            this.scope = scope;
        }

        @Override
        public void verify() throws SQLException {
            if (closed) {
                throw new Refusal(MissingGate.ADMISSION);
            }
            if (opened && (session == null || !session.active())) {
                throw new Refusal(MissingGate.WRITER_PROTECTION);
            }
            if (dispatch == null || dispatchSHA256 == null || independentAdmission == null) {
                throw new Refusal(MissingGate.ADMISSION);
            }
            if (independentAdmission instanceof C78DevEngineAdmissionProducer producer) {
                producer.verifyRoot(scope);
                producer.verify();
                return;
            }
            if (independentAdmission instanceof C78Sample12AdmissionProducer producer) {
                producer.verifyRoot(scope);
                producer.verify();
                controls = producer.controls(scope);
                return;
            }
            independentAdmission.verify();
            if (controls == null) {
                controls = C78DevEngineDispatch.read(dispatch, dispatchSHA256, scope);
            }
            try {
                controls.verify();
            } catch (final RuntimeException malformed) {
                final Refusal refusal = new Refusal(MissingGate.ADMISSION);
                refusal.initCause(malformed);
                throw refusal;
            }
        }

        @Override
        public Session open() throws SQLException {
            if (opened) {
                throw new Refusal(MissingGate.ADMISSION);
            }
            if (independentAdmission instanceof C78DevEngineAdmissionProducer producer) {
                producer.verifyRoot(scope);
                return producer.open();
            }
            verify();
            opened = true;
            Connection connection = null;
            try {
                // Exclusive live parent permit and a CREATE_NEW claim precede secret lookup.
                controls.start();
                controls.checkpoint();
                independentAdmission.verify();
                final C78Sample12Selection selected =
                        independentAdmission instanceof C78Sample12AdmissionProducer producer
                                ? producer.selection()
                                : null;
                final SecretProvider provider =
                        selected == null
                                ? Objects.requireNonNull(secrets.get())
                                : key -> {
                                    if (key != SecretKey.SHADOW_SAMPLE_SA_PASSWORD) {
                                        throw new IllegalStateException(
                                                "DEV_ENGINE_SECRET_KEY_REFUSED");
                                    }
                                    return controls.sampleSecret();
                                };
                connection = opener.open(scope, provider, controls);
                session =
                        new C78DevEngineSession(
                                connection, controls, independentAdmission, selected);
                session.establishProtection();
                return session;
            } catch (final SQLException | RuntimeException | Error failure) {
                if (session != null) {
                    try {
                        session.close();
                    } catch (final SQLException | RuntimeException | Error cleanup) {
                        C78OwnedCleanup.combine(failure, cleanup);
                    }
                } else if (connection != null) {
                    try {
                        connection.close();
                    } catch (final SQLException | RuntimeException | Error cleanup) {
                        C78OwnedCleanup.combine(failure, cleanup);
                    }
                }
                if (failure instanceof Error fatal) {
                    throw fatal;
                }
                throw failure instanceof SQLException sql
                        ? sql
                        : new SQLException("DEV_ENGINE_OPEN_FAILED", failure);
            }
        }

        @Override
        public void close() throws SQLException {
            if (!closed || session != null && !session.physicalClosed()) {
                closed = true;
                Throwable failure = null;
                try {
                    if (session != null) {
                        session.close();
                    }
                } catch (final SQLException | RuntimeException | Error cleanup) {
                    failure = C78OwnedCleanup.failure(cleanup, "DEV_ENGINE_SESSION_CLOSE_FAILED");
                }
                try {
                    if (controls != null) {
                        controls.close();
                    }
                } catch (final SQLException | RuntimeException | Error cleanup) {
                    failure =
                            C78OwnedCleanup.combine(
                                    failure,
                                    C78OwnedCleanup.failure(
                                            cleanup, "DEV_ENGINE_CONTROL_CLOSE_FAILED"));
                }
                try {
                    if (independentAdmission != null) {
                        independentAdmission.close();
                    }
                } catch (final SQLException | RuntimeException | Error cleanup) {
                    failure =
                            C78OwnedCleanup.combine(
                                    failure,
                                    C78OwnedCleanup.failure(
                                            cleanup, "DEV_ENGINE_ADMISSION_CLOSE_FAILED"));
                }
                C78OwnedCleanup.rethrow(failure);
            }
        }
    }
}
