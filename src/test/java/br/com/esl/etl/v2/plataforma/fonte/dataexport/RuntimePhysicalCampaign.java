package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;

/** Reservations survive reruns, failures and the isolated Maven clean. Never refunded. */
final class RuntimePhysicalCampaign {
    private final Path directory;
    private final Instant deadline;

    RuntimePhysicalCampaign(final Path manifest) throws Exception {
        directory = Path.of("target/bloco53").toAbsolutePath().normalize();
        if (!manifest.toAbsolutePath().normalize().equals(directory.resolve("campaign.properties"))
                || !Files.isRegularFile(manifest)) {
            throw new IllegalArgumentException("CAMPAIGN_MANIFEST_REQUIRED");
        }
        if (Files.size(manifest) > 4096) {
            throw new IllegalArgumentException("CAMPAIGN_MANIFEST_LIMIT");
        }
        final var values =
                new Properties() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public synchronized Object put(final Object key, final Object value) {
                        if (containsKey(key)) {
                            throw new IllegalArgumentException("CAMPAIGN_DUPLICATE_KEY");
                        }
                        return super.put(key, value);
                    }
                };
        try (var reader = Files.newBufferedReader(manifest)) {
            values.load(reader);
        }
        if (!values.stringPropertyNames()
                        .equals(java.util.Set.of("campaign", "target", "started", "deadline"))
                || !values.getProperty("target").equals("localhost/ETL_SISTEMA_V2_SHADOW")) {
            throw new IllegalArgumentException("CAMPAIGN_TARGET_REQUIRED");
        }
        UUID.fromString(values.getProperty("campaign"));
        final Instant started = Instant.parse(values.getProperty("started"));
        deadline = Instant.parse(values.getProperty("deadline"));
        if (deadline.isAfter(started.plusSeconds(900))
                || !started.isBefore(deadline)
                || Instant.now().isBefore(started)) {
            throw new IllegalArgumentException("CAMPAIGN_DEADLINE_INVALID");
        }
        checkDeadline();
    }

    void checkDeadline() {
        if (!Instant.now().isBefore(deadline)) {
            throw new IllegalStateException("CAMPAIGN_DEADLINE_EXCEEDED");
        }
    }

    UUID reserve() throws Exception {
        checkDeadline();
        try (var channel =
                        FileChannel.open(
                                directory.resolve("cumulative-reservations.txt"),
                                StandardOpenOption.CREATE,
                                StandardOpenOption.READ,
                                StandardOpenOption.WRITE);
                var lock = channel.tryLock()) {
            if (lock == null || !lock.isValid() || channel.size() > 16384) {
                throw new IllegalStateException("CAMPAIGN_LEDGER_INVALID");
            }
            final ByteBuffer bytes = ByteBuffer.allocate((int) channel.size());
            channel.read(bytes);
            bytes.flip();
            final String previous =
                    java.nio.charset.StandardCharsets.UTF_8.decode(bytes).toString();
            final var reservations = previous.lines().toList();
            for (final String value : reservations) {
                UUID.fromString(value);
            }
            // Each admitted occurrence reserves 16 input rows, 4 pages, 512 derived rows.
            if (reservations.size() >= 128) {
                throw new IllegalStateException("CUMULATIVE_BLOCK53_BUDGET_EXHAUSTED");
            }
            final UUID id = UUID.randomUUID();
            channel.position(channel.size());
            channel.write(java.nio.charset.StandardCharsets.UTF_8.encode(id + "\n"));
            channel.force(true);
            return id;
        }
    }

    void requireReservation(final UUID id) throws Exception {
        checkDeadline();
        if (!Files.readAllLines(directory.resolve("cumulative-reservations.txt"))
                .contains(id.toString())) {
            throw new IllegalArgumentException("PHYSICAL_ADMISSION_REQUIRED");
        }
    }
}
