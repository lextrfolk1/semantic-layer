package com.lextr.semanticlayer.service.opa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lextr.semanticlayer.config.OpaProperties;
import com.lextr.semanticlayer.dto.FilterLookupPolicyDecisionDto;
import com.lextr.semanticlayer.dto.FilterLookupPolicyRequestDto;
import com.lextr.semanticlayer.exception.OpaPolicyClientException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpaDecisionGatewayTest {

    @Test
    void evaluatesDecisionThroughOpaHttpApi() {
        AtomicReference<HttpRequest> requestRef = new AtomicReference<>();
        OpaDecisionGateway gateway = new OpaDecisionGateway(
                opaProperties(),
                new ObjectMapper().findAndRegisterModules(),
                request -> {
                    requestRef.set(request);
                    return response(200, """
                            {"result":{"allowed":true,"code":"GOV-FL-001","message":"GOV-FL-001: allow"}}
                            """, request);
                }
        );

        FilterLookupPolicyDecisionDto decision = gateway.evaluate(
                "lextr.semantic.filter_lookup",
                new FilterLookupPolicyRequestDto("client-a", "LOOKUP-1", "GOV-FL-001", 30, 30),
                FilterLookupPolicyDecisionDto.class
        );

        assertTrue(decision.allowed());
        assertEquals("GOV-FL-001", decision.code());
        assertEquals("http://localhost:8181/v1/data/lextr/semantic/filter_lookup/evaluate", requestRef.get().uri().toString());
        assertTrue(readBody(requestRef.get()).contains("\"input\""));
        assertTrue(readBody(requestRef.get()).contains("\"client_id\":\"client-a\""));
    }

    @Test
    void surfacesNonSuccessResponsesAsOpaquePolicyFailures() {
        OpaDecisionGateway gateway = new OpaDecisionGateway(
                opaProperties(),
                new ObjectMapper().findAndRegisterModules(),
                request -> response(500, """
                        {"error":"boom"}
                        """, request)
        );

        assertThrows(OpaPolicyClientException.class, () -> gateway.evaluate(
                "lextr.semantic.filter_lookup",
                new FilterLookupPolicyRequestDto("client-a", "LOOKUP-1", "GOV-FL-001", 30, 30),
                FilterLookupPolicyDecisionDto.class
        ));
    }

    private static OpaProperties opaProperties() {
        OpaProperties properties = new OpaProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://localhost:8181");
        properties.setDecisionPathPrefix("/v1/data");
        return properties;
    }

    private static HttpResponse<String> response(int statusCode, String body, HttpRequest request) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return statusCode;
            }

            @Override
            public HttpRequest request() {
                return request;
            }

            @Override
            public Optional<HttpResponse<String>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(Map.of("Content-Type", List.of("application/json")), (k, v) -> true);
            }

            @Override
            public String body() {
                return body.strip();
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
        };
    }

    private static String readBody(HttpRequest request) {
        try {
            BodyPublisher publisher = request.bodyPublisher().orElseThrow();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            CountDownLatch latch = new CountDownLatch(1);
            publisher.subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ByteBuffer item) {
                    byte[] bytes = new byte[item.remaining()];
                    item.get(bytes);
                    outputStream.writeBytes(bytes);
                }

                @Override
                public void onError(Throwable throwable) {
                    latch.countDown();
                    throw new RuntimeException(throwable);
                }

                @Override
                public void onComplete() {
                    latch.countDown();
                }
            });
            latch.await(1, TimeUnit.SECONDS);
            return outputStream.toString(StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read request body", exception);
        }
    }
}
