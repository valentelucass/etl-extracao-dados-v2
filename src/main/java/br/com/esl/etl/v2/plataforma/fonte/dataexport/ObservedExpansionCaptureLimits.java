package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;

/** Pure memory bounds. Constructing this value does not reserve source or executor capacity. */
public record ObservedExpansionCaptureLimits(
        int maximumPageBytes,
        int maximumProjectionBytes,
        long maximumRawBytes,
        long maximumProjectionTotalBytes,
        int maximumPhysicalRowsPerPage,
        int maximumRowsPerExecution,
        int maximumPages,
        int maximumJsonNodes) {
    public ObservedExpansionCaptureLimits {
        if (maximumPageBytes < 1
                || maximumPageBytes > 8 * 1024 * 1024
                || maximumProjectionBytes < 1024
                || maximumProjectionBytes > 16 * 1024 * 1024
                || maximumRawBytes < maximumPageBytes
                || maximumRawBytes > 32L * 1024 * 1024
                || maximumProjectionTotalBytes < maximumProjectionBytes
                || maximumProjectionTotalBytes > 64L * 1024 * 1024
                || maximumPhysicalRowsPerPage < 1
                || maximumPhysicalRowsPerPage > 1000
                || maximumRowsPerExecution < maximumPhysicalRowsPerPage
                || maximumRowsPerExecution > 2000
                || maximumPages < 1
                || maximumPages > 4
                || maximumJsonNodes < 1
                || maximumJsonNodes > ContractObservationLimits.ABSOLUTE_MAXIMUM_NODES) {
            throw new IllegalArgumentException("OBS_CAPTURE_LIMITS");
        }
    }

    public static ObservedExpansionCaptureLimits proposedProofBounds() {
        return new ObservedExpansionCaptureLimits(
                8 * 1024 * 1024,
                16 * 1024 * 1024,
                32L * 1024 * 1024,
                64L * 1024 * 1024,
                1000,
                2000,
                4,
                ContractObservationLimits.ABSOLUTE_MAXIMUM_NODES);
    }
}
