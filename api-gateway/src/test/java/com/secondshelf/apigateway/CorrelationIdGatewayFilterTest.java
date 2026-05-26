package com.secondshelf.apigateway;

import com.secondshelf.apigateway.observability.CorrelationIdGatewayFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdGatewayFilterTest {

    private static final String UUID_PATTERN = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private final CorrelationIdGatewayFilter filter = new CorrelationIdGatewayFilter();

    @Test
    void shouldPreserveAuthorizationAndCorrelationHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/books")
                .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                .header(CorrelationIdGatewayFilter.HEADER_NAME, "corr-client-123")
                .build());
        AtomicReference<HttpHeaders> downstreamHeaders = new AtomicReference<>();

        filter.filter(exchange, filteredExchange -> {
            downstreamHeaders.set(filteredExchange.getRequest().getHeaders());
            return filteredExchange.getResponse().setComplete();
        }).block();

        assertThat(downstreamHeaders.get().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-token");
        assertThat(downstreamHeaders.get().getFirst(CorrelationIdGatewayFilter.HEADER_NAME)).isEqualTo("corr-client-123");
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdGatewayFilter.HEADER_NAME))
                .isEqualTo("corr-client-123");
    }

    @Test
    void shouldGenerateCorrelationHeaderWhenMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/books").build());
        AtomicReference<HttpHeaders> downstreamHeaders = new AtomicReference<>();

        filter.filter(exchange, filteredExchange -> {
            downstreamHeaders.set(filteredExchange.getRequest().getHeaders());
            return filteredExchange.getResponse().setComplete();
        }).block();

        String generatedCorrelationId = downstreamHeaders.get().getFirst(CorrelationIdGatewayFilter.HEADER_NAME);

        assertThat(generatedCorrelationId).matches(UUID_PATTERN);
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdGatewayFilter.HEADER_NAME))
                .isEqualTo(generatedCorrelationId);
    }
}
