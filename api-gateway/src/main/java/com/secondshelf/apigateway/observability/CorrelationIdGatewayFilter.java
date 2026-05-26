package com.secondshelf.apigateway.observability;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class CorrelationIdGatewayFilter implements GlobalFilter, Ordered {

    public static final String HEADER_NAME = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = resolve(exchange.getRequest().getHeaders().getFirst(HEADER_NAME));
        ServerHttpRequest request = exchange.getRequest()
                .mutate()
                .headers(headers -> headers.set(HEADER_NAME, correlationId))
                .build();
        ServerWebExchange exchangeWithCorrelationId = exchange.mutate().request(request).build();

        exchangeWithCorrelationId.getResponse().beforeCommit(() -> {
            exchangeWithCorrelationId.getResponse().getHeaders().set(HEADER_NAME, correlationId);
            return Mono.empty();
        });

        return chain.filter(exchangeWithCorrelationId);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private String resolve(String candidate) {
        if (StringUtils.hasText(candidate)) {
            return candidate.trim();
        }
        return UUID.randomUUID().toString();
    }
}
