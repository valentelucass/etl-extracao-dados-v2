package br.com.esl.etl.v2.plataforma.producao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.CurlSnapshotWire;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportUpdateContract;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

/** Durable scheduling state. Only SQL-confirmed snapshots may remove pending replacement work. */
final class RefreshCheckpoint {
    private final Path path;
    private final ObjectNode payload;

    RefreshCheckpoint(final Path path, final String scope, final Instant began) throws Exception {
        this.path = path;
        if (Files.exists(path)) {
            if (Files.size(path) > 1048576) {
                throw new IllegalStateException("REFRESH_CHECKPOINT_BOUND");
            }
            final var saved = CurlSnapshotWire.JSON.readTree(Files.readAllBytes(path));
            payload = (ObjectNode) saved.required("payload");
            if (!saved.path("schema").asText().equals("REFRESH_CHECKPOINT0766")
                    || !saved.path("sha256")
                            .asText()
                            .equals(hash(CurlSnapshotWire.JSON.writeValueAsBytes(payload)))
                    || !payload.path("sourceScope").asText().equals(scope)
                    || !payload.path("entities").isObject()) {
                throw new IllegalStateException("REFRESH_CHECKPOINT_INTEGRITY");
            }
        } else {
            payload =
                    CurlSnapshotWire.JSON
                            .createObjectNode()
                            .put("sourceScope", scope)
                            .put("createdAt", began.toString());
            payload.putObject("entities");
        }
    }

    LocalDate createdOn() {
        return Instant.parse(payload.required("createdAt").asText())
                .atZone(ReportUpdateContract.SOURCE_ZONE)
                .toLocalDate();
    }

    ObjectNode entity(
            final ReportSnapshotTemplate template,
            final LocalDate desiredStart,
            final LocalDate coveredStart,
            final LocalDate today,
            final Instant began)
            throws Exception {
        final var entities = (ObjectNode) payload.required("entities");
        final var floor =
                coveredStart != null && coveredStart.isBefore(desiredStart)
                        ? coveredStart
                        : desiredStart;
        if (!entities.has(template.name())) {
            final var entry = entities.putObject(template.name());
            entry.put("floor", floor.toString())
                    .put("initialNext", today.toString())
                    .put("initialThrough", today.toString())
                    .put("bootstrapCutoff", began.toString())
                    .put("watermark", began.toString())
                    .put("initialComplete", false)
                    .put("lastCalendarDay", today.toString())
                    .put("sweepNext", floor.toString())
                    .put("sweepsCompleted", 0);
            save();
        }
        final var entry = (ObjectNode) entities.required(template.name());
        final var previousFloor = LocalDate.parse(entry.required("floor").asText());
        if (floor.isBefore(previousFloor)) {
            if (!entry.path("initialComplete").asBoolean()) {
                entry.put("floor", floor.toString());
            } else {
                // Expand backwards without resetting the existing update watermark or discarding
                // rows.
                entry.put("floor", floor.toString())
                        .put("initialNext", previousFloor.minusDays(1).toString())
                        .put("initialComplete", false)
                        .put("bootstrapCutoff", entry.required("watermark").asText());
            }
            entry.put("sweepNext", floor.toString());
            save();
        }
        return entry;
    }

    void save() throws Exception {
        final var wrapper =
                CurlSnapshotWire.JSON
                        .createObjectNode()
                        .put("schema", "REFRESH_CHECKPOINT0766")
                        .put("sha256", hash(CurlSnapshotWire.JSON.writeValueAsBytes(payload)));
        wrapper.set("payload", payload);
        atomic(path, CurlSnapshotWire.JSON.writeValueAsBytes(wrapper));
    }

    void requireEligible(final Instant now) {
        if (payload.hasNonNull("lastCompletedAt")
                && now.isBefore(
                        ProductionRefreshPolicy.nextInvocation(
                                Instant.parse(payload.required("lastCompletedAt").asText())))) {
            throw new IllegalStateException("REFRESH_SIXTY_MINUTE_WAIT_NOT_FINISHED");
        }
    }

    boolean allInitialized() {
        for (final var template : ReportSnapshotTemplate.values()) {
            if (!payload.required("entities")
                    .path(template.name())
                    .path("initialComplete")
                    .asBoolean()) {
                return false;
            }
        }
        return true;
    }

    void completed(final Instant instant) throws Exception {
        payload.put("lastCompletedAt", instant.toString());
        save();
    }

    static void atomic(final Path target, final byte[] bytes) throws Exception {
        Files.createDirectories(target.getParent());
        final var temporary =
                target.resolveSibling(target.getFileName() + "." + UUID.randomUUID() + ".partial");
        try (var channel =
                FileChannel.open(
                        temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            final var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        Files.move(
                temporary,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    static String hash(final byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
