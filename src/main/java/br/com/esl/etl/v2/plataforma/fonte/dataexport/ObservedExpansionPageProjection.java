package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Immutable core result. MAPPED means mapper execution, never observed-schema approval. */
public final class ObservedExpansionPageProjection {
    public enum Status {
        MAPPED,
        STRUCTURE_ISSUE
    }

    public enum Issue {
        INVALID_UTF8,
        INVALID_JSON,
        ENVELOPE_SHAPE,
        RECORD_SHAPE,
        PHYSICAL_ROW_BOUND,
        MAPPER_REJECTED,
        PROJECTION_BYTES_EXCEEDED
    }

    private final ObservedExpansionRawPage rawPage;
    private final Status status;
    private final Issue issue;
    private final List<ObservedExpansionProjectedRecord> records;
    private final String projectionJson;

    ObservedExpansionPageProjection(
            final ObservedExpansionRawPage rawPage,
            final Status status,
            final Issue issue,
            final List<ObservedExpansionProjectedRecord> records,
            final String projectionJson) {
        this.rawPage = Objects.requireNonNull(rawPage);
        this.status = Objects.requireNonNull(status);
        this.projectionJson = Objects.requireNonNull(projectionJson);
        this.issue = issue;
        Objects.requireNonNull(records);
        final var plan = rawPage.request().plan();
        final int recordCount = records.size();
        final int maximumPageRows = Math.min(1000, plan.limits().maximumPhysicalRowsPerPage());
        final long maximumOccurrence = Math.min(2000L, plan.limits().maximumRowsPerExecution());
        // Refuse size before touching any element, copy, iterator, or stream.
        if (recordCount < 0 || recordCount > maximumPageRows) {
            throw new IllegalArgumentException("OBS_CAPTURE_PAGE_RECORD_BOUND");
        }
        if ((status == Status.MAPPED && issue != null)
                || (status == Status.STRUCTURE_ISSUE && (issue == null || recordCount != 0))
                || 2L * projectionJson.length() > plan.limits().maximumProjectionBytes()) {
            throw new IllegalArgumentException("OBS_CAPTURE_PROJECTION");
        }
        final var boundedCopy = new ArrayList<ObservedExpansionProjectedRecord>(recordCount);
        long firstOccurrence = 0;
        for (int index = 0; index < recordCount; index++) {
            final var record = Objects.requireNonNull(records.get(index));
            if (record.pageOrdinal() != index + 1
                    || record.occurrence() < 1
                    || record.occurrence() > maximumOccurrence
                    || record.captured().binding() != null
                    || !plan.template()
                            .vertical()
                            .equals(record.captured().observation().vertical())) {
                throw new IllegalArgumentException("OBS_CAPTURE_PAGE_RECORD_CONTEXT");
            }
            if (index == 0) {
                firstOccurrence = record.occurrence();
                final long maximumStartByEarlierPages =
                        (long) (rawPage.request().page() - 1) * maximumPageRows + 1;
                if (firstOccurrence < rawPage.request().page()
                        || firstOccurrence > maximumStartByEarlierPages
                        || firstOccurrence + recordCount - 1 > maximumOccurrence) {
                    throw new IllegalArgumentException("OBS_CAPTURE_PAGE_OCCURRENCE_CONTEXT");
                }
            }
            if (record.occurrence() != firstOccurrence + index) {
                throw new IllegalArgumentException("OBS_CAPTURE_PAGE_OCCURRENCE_CONTINUITY");
            }
            boundedCopy.add(record);
        }
        this.records = List.copyOf(boundedCopy);
    }

    public ObservedExpansionRawPage rawPage() {
        return rawPage;
    }

    public Status status() {
        return status;
    }

    public Issue pageIssue() {
        return issue;
    }

    public List<ObservedExpansionProjectedRecord> records() {
        return records;
    }

    public String sourceSchemaValidation() {
        return "NOT_PERFORMED_BY_CORE";
    }

    public OptionalInt physicalRows() {
        return status == Status.MAPPED ? OptionalInt.of(records.size()) : OptionalInt.empty();
    }

    public OptionalLong firstOccurrence() {
        return records.isEmpty()
                ? OptionalLong.empty()
                : OptionalLong.of(records.get(0).occurrence());
    }

    public OptionalLong lastOccurrence() {
        return records.isEmpty()
                ? OptionalLong.empty()
                : OptionalLong.of(records.get(records.size() - 1).occurrence());
    }

    public int fieldIssueRows() {
        return (int) records.stream().filter(row -> !row.mapperValid()).count();
    }

    public String projectionJson() {
        return projectionJson;
    }

    public long projectionByteLength() {
        return 2L * projectionJson.length();
    }

    public byte[] projectionUtf16Le() {
        return projectionJson.getBytes(StandardCharsets.UTF_16LE);
    }

    @Override
    public String toString() {
        return "ObservedExpansionPageProjection[page="
                + rawPage.request().page()
                + ",status="
                + status
                + ",knownRows="
                + (physicalRows().isPresent() ? physicalRows().getAsInt() : "UNKNOWN")
                + "]";
    }
}
