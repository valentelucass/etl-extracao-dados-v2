package br.com.esl.etl.v2.plataforma.orquestracao;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/** Explicit versioned input. Constructing a policy never enables a schedule. */
public record RuntimeTemporalPolicy(
        String version,
        ZoneId zone,
        ExecutionMode mode,
        RuntimeWindowStrategy strategy,
        Cadence cadence,
        LocalTime civilBoundary,
        Duration lookback,
        Duration stabilization,
        Duration sla,
        Duration deadline,
        int concurrency,
        int maximumBacklog,
        int maximumReconciliation,
        int maximumDegraded,
        List<Blackout> blackouts) {
    public enum Cadence {
        CIVIL_DAY,
        CIVIL_MONTH
    }

    public record Blackout(LocalDate start, LocalDate endExclusive) {
        public Blackout {
            if (!Objects.requireNonNull(start).isBefore(Objects.requireNonNull(endExclusive))) {
                throw new IllegalArgumentException("BLACKOUT_INTERVAL");
            }
        }

        boolean intersects(final LocalDate startDate, final LocalDate endDate) {
            return start.isBefore(endDate) && startDate.isBefore(endExclusive);
        }
    }

    public RuntimeTemporalPolicy {
        new ImmutableFingerprint(version, "0".repeat(64));
        Objects.requireNonNull(zone);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(strategy);
        Objects.requireNonNull(cadence);
        Objects.requireNonNull(civilBoundary);
        Objects.requireNonNull(lookback);
        Objects.requireNonNull(stabilization);
        Objects.requireNonNull(sla);
        Objects.requireNonNull(deadline);
        if (blackouts.size() > 64) {
            throw new IllegalArgumentException("TEMPORAL_BLACKOUT_LIMIT");
        }
        blackouts =
                blackouts.stream()
                        .sorted(
                                java.util.Comparator.comparing(Blackout::start)
                                        .thenComparing(Blackout::endExclusive))
                        .toList();
        if (!zone.getId().contains("/")
                || lookback.isNegative()
                || stabilization.isNegative()
                || sla.isNegative()
                || sla.isZero()
                || deadline.isNegative()
                || deadline.isZero()
                || deadline.compareTo(sla) > 0
                || concurrency < 1
                || concurrency > 4
                || maximumBacklog < 1
                || maximumBacklog > 64
                || maximumReconciliation < 1
                || maximumReconciliation > 64
                || maximumDegraded < 0
                || maximumDegraded > maximumReconciliation
                || strategy != RuntimeWindowStrategy.INTERVAL) {
            throw new IllegalArgumentException("TEMPORAL_POLICY_INVALID");
        }
    }

    java.time.Instant resolve(final LocalDate date) {
        final var civil = LocalDateTime.of(date, civilBoundary);
        final var offsets = zone.getRules().getValidOffsets(civil);
        // No silent shift or choice of offset during a gap/overlap.
        if (offsets.size() != 1) {
            throw new IllegalArgumentException("CIVIL_BOUNDARY_AMBIGUOUS_OR_MISSING");
        }
        return civil.toInstant(offsets.get(0));
    }

    @Override
    public String toString() {
        return "RuntimeTemporalPolicy[version=" + version + "]";
    }

    public String material() {
        final var node =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
                        .objectNode()
                        .put("version", version)
                        .put("zone", zone.getId())
                        .put("mode", mode.name())
                        .put("strategy", strategy.name())
                        .put("cadence", cadence.name())
                        .put("boundary", civilBoundary.toString())
                        .put("lookback", lookback.toString())
                        .put("stabilization", stabilization.toString())
                        .put("sla", sla.toString())
                        .put("deadline", deadline.toString())
                        .put("concurrency", concurrency)
                        .put("maximumBacklog", maximumBacklog)
                        .put("maximumReconciliation", maximumReconciliation)
                        .put("maximumDegraded", maximumDegraded);
        final var values = node.putArray("blackouts");
        blackouts.forEach(
                value ->
                        values.addObject()
                                .put("start", value.start().toString())
                                .put("endExclusive", value.endExclusive().toString()));
        final String material = node.toString();
        if (material.length() > 4000) {
            throw new IllegalArgumentException("TEMPORAL_POLICY_MATERIAL_LIMIT");
        }
        return material;
    }

    public ImmutableFingerprint fingerprint() {
        try {
            return new ImmutableFingerprint(
                    version,
                    java.util.HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    material()
                                                            .getBytes(
                                                                    java.nio.charset
                                                                            .StandardCharsets
                                                                            .UTF_16LE))));
        } catch (final java.security.NoSuchAlgorithmException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }
}
