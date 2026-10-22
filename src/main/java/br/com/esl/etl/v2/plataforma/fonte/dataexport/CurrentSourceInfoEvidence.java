package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;

/**
 * Pure verification of an already completed, separately admitted INFO call. The implementation must
 * verify the actual compound holder and unchanged leased selection before returning. Reading a
 * positive JSON flag alone is not an implementation of this contract; verification never sends HTTP
 * or materializes a supplier credential.
 */
@FunctionalInterface
public interface CurrentSourceInfoEvidence {
    AcceptedInfo verifyCurrentScope() throws SQLException;

    /** Closed namespace selection; recognition conveys no physical authority. */
    static boolean supportsUnit(String unit) {
        return "hermesc54d6a25880d".equals(unit) || "user20261016-continuar-v2".equals(unit);
    }

    /** Request bound to a declared unit; unknown units cannot inherit another request. */
    static String requestForUnit(String unit) {
        if ("hermesc54d6a25880d".equals(unit)) {
            return "HERMES_c54d6a25880d";
        }
        if ("user20261016-continuar-v2".equals(unit)) {
            return "USER_20261016_CONTINUAR_V2";
        }
        throw new IllegalArgumentException("CURRENT_SOURCE_UNIT_NOT_SUPPORTED");
    }

    /** Sanitized observation, not a physical permit, supplier quota or future DATA guarantee. */
    record AcceptedInfo(
            String requestId,
            String scopeBinding,
            int templateId,
            int httpStatus,
            long responseBytes,
            int metadataFields,
            int attempts,
            Instant observedAt) {
        public AcceptedInfo {
            Objects.requireNonNull(requestId, "CURRENT_SOURCE_REQUEST_REQUIRED");
            Objects.requireNonNull(scopeBinding, "CURRENT_SOURCE_BINDING_REQUIRED");
            Objects.requireNonNull(observedAt, "CURRENT_SOURCE_OBSERVATION_REQUIRED");
            if (!(requestId.equals("HERMES_c54d6a25880d")
                            || requestId.equals("USER_20261016_CONTINUAR_V2"))
                    || !scopeBinding.matches("[a-f0-9]{32}")
                    || templateId != 6908
                    || httpStatus < 200
                    || httpStatus > 299
                    || responseBytes < 1
                    || responseBytes > 10_485_760
                    || metadataFields < 0
                    || metadataFields > 4096
                    || attempts != 1) {
                throw new IllegalArgumentException("CURRENT_SOURCE_INFO_OBSERVATION_INVALID");
            }
        }
    }
}
