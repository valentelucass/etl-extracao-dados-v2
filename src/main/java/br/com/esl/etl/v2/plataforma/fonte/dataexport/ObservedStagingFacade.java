package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportRuntimeConfigurationFingerprint;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Product-side metadata preparation; does not construct a client, governor, provider or connection.
 */
public final class ObservedStagingFacade {
    private ObservedStagingFacade() {}

    /** Private construction conveys metadata provenance only, never operational authority. */
    public static final class Prepared {
        private final ObservedExpansionCapturePlan plan;
        private final String configurationSha256;
        private final ObservedExpansionSchemaCatalog.Selection schema;

        private Prepared(
                final ObservedExpansionCapturePlan plan,
                final String configurationSha256,
                final ObservedExpansionSchemaCatalog.Selection schema) {
            this.plan = plan;
            this.configurationSha256 = configurationSha256;
            this.schema = schema;
        }

        public ObservedExpansionCapturePlan plan() {
            return plan;
        }

        public String configurationSha256() {
            return configurationSha256;
        }

        public String schemaRelease() {
            return schema.schemaRelease();
        }

        public String schemaSha256() {
            return schema.schemaResourceSha256();
        }

        public String technicalJson() {
            final var json =
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            json.put("version", "observed-staging-plan-v1");
            json.put("mode", "PLAN_ONLY");
            json.put("templateId", plan.template().templateId());
            json.put("per", plan.per());
            json.put("maximumInfoCalls", 1);
            json.put("maximumDataCalls", plan.limits().maximumPages());
            json.put("schemaRelease", schemaRelease());
            json.put("schemaSha256", schemaSha256());
            json.put("configurationSha256", configurationSha256);
            json.put("resourcePinsChecked", true);
            json.put("infoSchemaObserved", false);
            json.put("sourceHttp", 0);
            json.put("sqlConnections", 0);
            json.put("physicalAuthorityGranted", false);
            json.put("globalCompletenessProven", false);
            json.put("domainIdentityProven", false);
            json.put("retainedData", false);
            return json.toString();
        }

        @Override
        public String toString() {
            return "ObservedStagingPrepared[metadataOnly,authority=false]";
        }
    }

    public static Prepared prepare(
            final RuntimeConfiguration configuration,
            final UUID execution,
            final ObservedExpansionCaptureTemplate template,
            final BusinessDateRange window) {
        final var runtime = Objects.requireNonNull(configuration);
        final var source =
                runtime.dataExport()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "OBSERVED_STAGING_DATAEXPORT_REQUIRED"));
        if (source.settings().preferredTransport() != DataExportTransport.GET_WITH_BODY
                || source.settings().retryPolicy().maxAttempts() != 1
                || source.settings().maxResponseBytes() > 8L * 1024 * 1024
                || source.settings().requestTimeout().compareTo(Duration.ofSeconds(30)) > 0
                || source.resiliencePolicy()
                                .minimumRequestInterval()
                                .compareTo(Duration.ofSeconds(3))
                        < 0
                || source.resiliencePolicy().maxInFlight() != 1
                || source.resiliencePolicy().maxRequestsPerWorkload() > 5
                || source.resiliencePolicy().maxRequestsPerCycle() > 20
                || source.resiliencePolicy().maxRepartitions() != 0
                || source.resiliencePolicy().stepTimeout().compareTo(Duration.ofSeconds(180)) > 0
                || source.resiliencePolicy().cycleTimeout().compareTo(Duration.ofSeconds(1080)) > 0
                || !runtime.businessZone().equals(source.settings().sourceZone())) {
            throw new IllegalArgumentException("OBSERVED_STAGING_FINITE_SOURCE_REQUIRED");
        }
        final var plan =
                new ObservedExpansionCapturePlan(
                        execution,
                        template,
                        source.sourceInstance(),
                        source.tenantScope(),
                        window,
                        source.settings().sourceZone(),
                        DataExportTransport.GET_WITH_BODY,
                        ObservedExpansionCapturePlan.OrderBy.absent(),
                        ObservedExpansionCapturePlan.Envelope.ROOT_ARRAY,
                        50,
                        ObservedExpansionCaptureLimits.proposedProofBounds());
        final var schema = ObservedExpansionSchemaCatalog.select(template);
        ObservedExpansionSchemaCatalog.schema(
                schema); // Real resource parser + code-owned byte pins.
        return new Prepared(
                plan, DataExportRuntimeConfigurationFingerprint.from(source).sha256(), schema);
    }
}
