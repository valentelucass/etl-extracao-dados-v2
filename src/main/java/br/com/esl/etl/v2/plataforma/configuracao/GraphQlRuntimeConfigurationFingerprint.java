package br.com.esl.etl.v2.plataforma.configuracao;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlReadOperation;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlRetryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;

/** Fingerprint da configuração GraphQL efetiva, deliberadamente sem token. */
public final class GraphQlRuntimeConfigurationFingerprint {

    public static final String VERSION = "graphql-runtime-configuration-v2";

    private GraphQlRuntimeConfigurationFingerprint() {}

    public static ImmutableFingerprint from(final GraphQlSourceConfiguration configuration) {
        final GraphQlSourceConfiguration required =
                Objects.requireNonNull(configuration, "A configuração GraphQL é obrigatória.");
        final GraphQlClientSettings settings = required.settings();
        final GraphQlRetryPolicy retry = settings.retryPolicy();
        final EslResiliencePolicy resilience = required.resiliencePolicy();
        final Encoder encoder = new Encoder();
        encoder.write(VERSION);
        encoder.write(required.sourceInstance());
        encoder.write(required.tenantScope());
        encoder.write(settings.endpoint().toASCIIString());
        encoder.write(settings.requestTimeout());
        encoder.write(retry.maxAttempts());
        encoder.write(retry.initialDelay());
        encoder.write(retry.maxDelay());
        encoder.write(settings.maxResponseBytes());
        encoder.write(resilience.minimumRequestInterval());
        encoder.write(resilience.maxInFlight());
        encoder.write(resilience.maxRequestsPerCycle());
        encoder.write(resilience.maxRequestsPerWorkload());
        encoder.write(resilience.requestTimeout());
        encoder.write(resilience.stepTimeout());
        encoder.write(resilience.cycleTimeout());
        encoder.write(resilience.maxRetryAfter());
        encoder.write(resilience.maxRepartitions());
        encoder.write(resilience.circuitFailureThreshold());
        encoder.write(resilience.circuitCooldown());
        for (final GraphQlReadOperation operation : GraphQlReadOperation.values()) {
            encoder.write(operation.name());
            encoder.write(operation.contractSemanticsFingerprint().version());
            encoder.write(operation.contractSemanticsFingerprint().sha256());
        }
        return encoder.finish();
    }

    private static final class Encoder {

        private final MessageDigest digest;

        private Encoder() {
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (final NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 não está disponível.", exception);
            }
        }

        private void write(final int value) {
            write(Integer.toString(value));
        }

        private void write(final long value) {
            write(Long.toString(value));
        }

        private void write(final Duration value) {
            write(value.getSeconds());
            write(value.getNano());
        }

        private void write(final String value) {
            final byte[] bytes =
                    Objects.requireNonNull(value, "O valor canônico é obrigatório.")
                            .getBytes(StandardCharsets.UTF_8);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            digest.update(bytes);
        }

        private ImmutableFingerprint finish() {
            return new ImmutableFingerprint(VERSION, HexFormat.of().formatHex(digest.digest()));
        }
    }
}
