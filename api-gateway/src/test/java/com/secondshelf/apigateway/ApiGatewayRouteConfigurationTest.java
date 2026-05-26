package com.secondshelf.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ApiGatewayRouteConfigurationTest {

    @Test
    void shouldConfigureExpectedRoutes() throws Exception {
        Map<String, RouteDefinition> routes = gatewayProperties().getRoutes()
                .stream()
                .collect(Collectors.toMap(RouteDefinition::getId, Function.identity()));

        assertThat(routes).hasSize(9);
        assertRoute(routes, "auth-service", "http://auth-test:8080", "/api/auth/**");
        assertRoute(routes, "user-service-users", "http://user-test:8081", "/api/v1/users/**");
        assertRoute(routes, "user-service-admin-users", "http://user-test:8081", "/api/v1/admin/users/**");
        assertRoute(routes, "book-service", "http://book-test:8082", "/api/v1/books/**");
        assertRoute(routes, "exchange-service-exchanges", "http://exchange-test:8083", "/api/v1/exchanges/**");
        assertRoute(routes, "exchange-service-admin-exchanges", "http://exchange-test:8083", "/api/v1/admin/exchanges/**");
        assertRoute(routes, "exchange-service-admin-outbox", "http://exchange-test:8083", "/api/v1/admin/outbox/**");
        assertRoute(routes, "notification-service-notifications", "http://notification-test:8084", "/api/v1/notifications/**");
        assertRoute(routes, "notification-service-admin-notifications", "http://notification-test:8084", "/api/v1/admin/notifications/**");
    }

    private void assertRoute(Map<String, RouteDefinition> routes, String id, String uri, String path) {
        RouteDefinition route = routes.get(id);

        assertThat(route).isNotNull();
        assertThat(route.getUri()).isEqualTo(URI.create(uri));
        assertThat(route.getPredicates())
                .anySatisfy(predicate -> {
                    assertThat(predicate.getName()).isEqualTo("Path");
                    assertThat(predicate.getArgs()).containsValue(path);
                });
    }

    private GatewayProperties gatewayProperties() throws Exception {
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test-gateway-uris", Map.of(
                "AUTH_SERVICE_URI", "http://auth-test:8080",
                "USER_SERVICE_URI", "http://user-test:8081",
                "BOOK_SERVICE_URI", "http://book-test:8082",
                "EXCHANGE_SERVICE_URI", "http://exchange-test:8083",
                "NOTIFICATION_SERVICE_URI", "http://notification-test:8084"
        )));

        List<PropertySource<?>> yamlSources = new YamlPropertySourceLoader()
                .load("gateway-config", new ClassPathResource("application.yaml"));
        for (PropertySource<?> yamlSource : yamlSources) {
            environment.getPropertySources().addLast(yamlSource);
        }

        return Binder.get(environment)
                .bind("spring.cloud.gateway.server.webflux", GatewayProperties.class)
                .orElseThrow(() -> new IllegalStateException("Gateway routes are not configured"));
    }
}
