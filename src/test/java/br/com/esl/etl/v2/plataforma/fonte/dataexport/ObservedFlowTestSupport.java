package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportProperties;
import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

/** Synthetic in-memory transport. Does not construct a network HttpClient or open a socket. */
final class ObservedFlowTestSupport {
    static final ImmutableFingerprint BASE =
            new ImmutableFingerprint("synthetic-runtime-v1", "e".repeat(64));
    static final BusinessDateRange DATES =
            new BusinessDateRange(LocalDate.of(2036, 3, 19), LocalDate.of(2036, 3, 19));
    static final ObjectMapper JSON = new ObjectMapper();
    final SourceContractRelease release;
    final ContractRunGuard guard;
    final br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart start;
    final DataExportPageRequest request;
    final DataExportContractObservationConfiguration observation;
    final FakeClient client;
    final DataExportHttpGatewayBundle bundle;

    ObservedFlowTestSupport(
            final DataExportTemplate template, final int per, final String... pages) {
        release = DataExportObservedEntityContractCatalog.release(template);
        request = DataExportPageRequest.observed(template, release, BASE, DATES, 1, per);
        final var policy =
                ContractCompatibilityPolicy.create(
                        "observed-flow-test-v1", release.contractFingerprint(), List.of());
        final var binding =
                ContractExecutionBinding.create(
                        UUID.randomUUID(),
                        release,
                        policy,
                        request.profile().runtimeFingerprint(BASE));
        start = ContractTestSupport.controlPlaneStart(binding);
        guard = new ContractRunGuard(binding, release, policy, start, ignored -> {});
        observation =
                DataExportContractObservationConfiguration.forRelease(
                        template,
                        release,
                        ContractObservationLimits.runtimeDefaults(),
                        ContractResponsePathBoundary.forRuntime(release, policy),
                        BASE);
        client = new FakeClient(List.of(pages));
        bundle =
                DataExportHttpGatewayBundle.materialized(
                        gateway(
                                properties(DataExportTransport.GET_WITH_BODY, 1),
                                DataExportTransportFallbackPolicy.PREFERRED_TRANSPORT_ONLY),
                        new HttpDataExportTemplateInfoGateway(
                                client,
                                properties(DataExportTransport.GET_WITH_BODY, 1),
                                JSON,
                                ignored -> {
                                    throw new AssertionError("No retry sleep expected");
                                }),
                        new DataExportCircuitBreakerRegistry(
                                new DataExportCircuitBreakerPolicy(3, Duration.ofSeconds(1)),
                                Clock.systemUTC()),
                        observation);
    }

    HttpDataExportGateway gateway(
            final DataExportProperties properties,
            final DataExportTransportFallbackPolicy fallback) {
        return new HttpDataExportGateway(
                client,
                properties,
                JSON,
                DataExportHttpAttemptObserver.noop(),
                DataExportHttpAttemptGovernor.ungoverned(
                        Duration.ofSeconds(2),
                        Duration.ZERO,
                        ignored -> {
                            throw new AssertionError("No retry sleep expected");
                        }),
                fallback,
                Optional.of(observation));
    }

    static DataExportProperties properties(
            final DataExportTransport transport, final int attempts) {
        return new DataExportProperties(
                URI.create("https://synthetic.invalid"),
                "synthetic-token",
                ZoneId.of("America/Sao_Paulo"),
                Duration.ofSeconds(2),
                transport,
                new DataExportRetryPolicy(attempts, Duration.ZERO, Duration.ZERO),
                2 * 1024 * 1024);
    }

    DataExportPageStreamer streamer() {
        final var gateways = DataExportContractGate.enforce(bundle, request, guard);
        guard.validateMetadata(release.metadata());
        return new DataExportPageStreamer(
                gateways.dataGateway(), DataExportExtractionAudit.noop(), Clock.systemUTC());
    }

    static String localRow(final int id) throws Exception {
        final var release =
                DataExportObservedEntityContractCatalog.release(
                        DataExportTemplate.LOCALIZACAO_CARGAS);
        final ObjectNode node = JSON.createObjectNode();
        for (final var field : release.response().fields()) {
            if (field.path().indexOf('/', 1) < 0) {
                final String name = field.path().substring(1);
                if (name.equals("invoices_mapping")) {
                    node.putArray(name).add("synthetic-invoice");
                } else if (name.equals("corporation_sequence_number")) {
                    node.put(name, id);
                } else if (name.equals("invoices_volumes")) {
                    node.put(name, 1);
                } else if (name.equals("service_at")) {
                    node.put(name, "2036-03-19T12:00:00Z");
                } else if (List.of("taxed_weight", "invoices_value", "total").contains(name)) {
                    node.put(name, "10.250000000");
                } else {
                    node.put(name, "synthetic");
                }
            }
        }
        return JSON.writeValueAsString(node);
    }

    static String structuralRow(final DataExportTemplate template) throws Exception {
        final var release = DataExportObservedEntityContractCatalog.release(template);
        final var node = JSON.createObjectNode();
        for (final var field : release.response().fields()) {
            if (field.path().indexOf('/', 1) < 0) {
                final String name = field.path().substring(1);
                if (field.jsonTypes().isEmpty()) {
                    node.putNull(name);
                } else if (field.cardinality()
                        == br.com.esl.etl.v2.plataforma.contrato.ContractResponse.Cardinality
                                .ARRAY) {
                    final var array = node.putArray(name);
                    final var child = release.response().find(field.path() + "/*").orElseThrow();
                    if (child.cardinality()
                            == br.com.esl.etl.v2.plataforma.contrato.ContractResponse.Cardinality
                                    .ARRAY) {
                        array.addArray().add("synthetic");
                    } else {
                        array.add("synthetic");
                    }
                } else if (field.jsonTypes()
                        .contains(
                                br.com.esl.etl.v2.plataforma.contrato.ContractResponse.JsonType
                                        .STRING)) {
                    node.put(name, "synthetic");
                } else if (field.jsonTypes()
                        .contains(
                                br.com.esl.etl.v2.plataforma.contrato.ContractResponse.JsonType
                                        .BOOLEAN)) {
                    node.put(name, false);
                } else {
                    node.put(name, 1);
                }
            }
        }
        return JSON.writeValueAsString(node);
    }

    static final class FakeClient extends HttpClient {
        final List<HttpRequest> requests = new ArrayList<>();
        final List<String> requestBodies = new ArrayList<>();
        final List<String> pages;
        int status = 200;
        List<Integer> statuses = List.of();

        FakeClient(final List<String> pages) {
            this.pages = pages;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                final HttpRequest request, final HttpResponse.BodyHandler<T> handler) {
            final int actualStatus = statuses.isEmpty() ? status : statuses.get(requests.size());
            requests.add(request);
            requestBodies.add(body(request));
            final String document = pages.get(requests.size() - 1);
            final var headers =
                    HttpHeaders.of(
                            Map.of("Content-Type", List.of("application/json")), (a, b) -> true);
            final var info =
                    new HttpResponse.ResponseInfo() {
                        @Override
                        public int statusCode() {
                            return actualStatus;
                        }

                        @Override
                        public HttpHeaders headers() {
                            return headers;
                        }

                        @Override
                        public Version version() {
                            return Version.HTTP_1_1;
                        }
                    };
            final var subscriber = handler.apply(info);
            subscriber.onSubscribe(
                    new Flow.Subscription() {
                        @Override
                        public void request(final long n) {}

                        @Override
                        public void cancel() {}
                    });
            subscriber.onNext(List.of(ByteBuffer.wrap(document.getBytes(StandardCharsets.UTF_8))));
            subscriber.onComplete();
            return subscriber
                    .getBody()
                    .toCompletableFuture()
                    .thenApply(value -> new Response<>(request, actualStatus, headers, value));
        }

        private static String body(final HttpRequest request) {
            if (request.bodyPublisher().isEmpty()) {
                return "";
            }
            final var result = new CompletableFuture<String>();
            final var bytes = new ByteArrayOutputStream();
            request.bodyPublisher()
                    .orElseThrow()
                    .subscribe(
                            new Flow.Subscriber<ByteBuffer>() {
                                @Override
                                public void onSubscribe(final Flow.Subscription subscription) {
                                    subscription.request(Long.MAX_VALUE);
                                }

                                @Override
                                public void onNext(final ByteBuffer buffer) {
                                    final byte[] next = new byte[buffer.remaining()];
                                    buffer.get(next);
                                    bytes.writeBytes(next);
                                }

                                @Override
                                public void onError(final Throwable failure) {
                                    result.completeExceptionally(failure);
                                }

                                @Override
                                public void onComplete() {
                                    result.complete(bytes.toString(StandardCharsets.UTF_8));
                                }
                            });
            return result.join();
        }

        @Override
        public <T> HttpResponse<T> send(
                final HttpRequest request, final HttpResponse.BodyHandler<T> handler) {
            throw new AssertionError("SYNC_NOT_USED");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                final HttpRequest request,
                final HttpResponse.BodyHandler<T> handler,
                final HttpResponse.PushPromiseHandler<T> push) {
            throw new AssertionError("PUSH_NOT_USED");
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }
    }

    private record Response<T>(HttpRequest request, int statusCode, HttpHeaders headers, T body)
            implements HttpResponse<T> {
        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
