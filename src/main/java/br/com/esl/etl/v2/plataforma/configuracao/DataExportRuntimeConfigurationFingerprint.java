package br.com.esl.etl.v2.plataforma.configuracao;

import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRetryPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;

/** Fingerprint determinístico da configuração Data Export efetiva, sem incluir o segredo. */
public final class DataExportRuntimeConfigurationFingerprint {

    public static final String VERSION = "dataexport-runtime-configuration-v2";
    private static final int MAXIMUM_BYTES = 65_536;

    private DataExportRuntimeConfigurationFingerprint() {}

    public static ImmutableFingerprint from(final DataExportSourceConfiguration configuration) {
        final DataExportSourceConfiguration required =
                Objects.requireNonNull(configuration, "A configuração Data Export é obrigatória.");
        final DataExportClientSettings settings = required.settings();
        final DataExportRetryPolicy retry = settings.retryPolicy();
        final EslResiliencePolicy resilience = required.resiliencePolicy();
        final Encoder encoder = new Encoder();
        encoder.write(VERSION);
        encoder.write(required.sourceInstance());
        encoder.write(required.tenantScope());
        encoder.write(settings.baseUri().toASCIIString());
        encoder.write(settings.sourceZone().getId());
        encoder.write(settings.requestTimeout());
        encoder.write(settings.preferredTransport().name());
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
        // This v2 wire is frozen for durable B53/B54 occurrences. New template semantics are
        // bound by their contract and operational request, without rewriting this history.
        for (final DataExportTemplate template :
                java.util.List.of(DataExportTemplate.COLETAS, DataExportTemplate.FRETES)) {
            encoder.write(template.name());
            encoder.write(template.contractSemanticsFingerprint().version());
            encoder.write(template.contractSemanticsFingerprint().sha256());
        }
        return encoder.finish();
    }

    private static final class Encoder {

        private final MessageDigest digest;
        private int encodedBytes;

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
            Objects.requireNonNull(value, "O valor canônico é obrigatório.");
            final ByteBuffer encoded;
            try {
                encoded =
                        StandardCharsets.UTF_8
                                .newEncoder()
                                .onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT)
                                .encode(CharBuffer.wrap(value));
            } catch (final CharacterCodingException exception) {
                throw new IllegalArgumentException(
                        "A configuração contém Unicode inválido.", exception);
            }
            final int length = encoded.remaining();
            final long prospective = (long) encodedBytes + Integer.BYTES + length;
            if (prospective > MAXIMUM_BYTES) {
                throw new IllegalArgumentException("A configuração excede o limite canônico.");
            }
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(length).array());
            digest.update(encoded);
            encodedBytes = (int) prospective;
        }

        private ImmutableFingerprint finish() {
            return new ImmutableFingerprint(VERSION, HexFormat.of().formatHex(digest.digest()));
        }
    }
}
