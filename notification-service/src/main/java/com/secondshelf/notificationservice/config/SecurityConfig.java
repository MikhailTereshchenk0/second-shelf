package com.secondshelf.notificationservice.config;

import com.secondshelf.notificationservice.observability.CorrelationIdFilter;
import com.secondshelf.notificationservice.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_ACTUATOR_ENDPOINTS = {
            "/error",
            "/actuator/health",
            "/actuator/health/liveness",
            "/actuator/health/readiness",
            "/actuator/info"
    };
    private static final String[] DOCS_ENDPOINTS = {
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };
    private static final String[] ADMIN_MONITORING_ENDPOINTS = {
            "/actuator/metrics",
            "/actuator/metrics/**",
            "/actuator/health/**"
    };
    private static final String[] ADMIN_API_ENDPOINTS = {
            "/api/v1/admin/**"
    };

    private final CorrelationIdFilter correlationIdFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${app.security.public-docs-enabled:false}")
    private boolean publicDocsEnabled;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                response.sendError(HttpServletResponse.SC_FORBIDDEN))
                )
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(PUBLIC_ACTUATOR_ENDPOINTS).permitAll();

                    if (publicDocsEnabled) {
                        auth.requestMatchers(DOCS_ENDPOINTS).permitAll();
                    } else {
                        auth.requestMatchers(DOCS_ENDPOINTS).denyAll();
                    }

                    auth.requestMatchers(ADMIN_MONITORING_ENDPOINTS).hasRole("ADMIN");
                    auth.requestMatchers(ADMIN_API_ENDPOINTS).hasRole("ADMIN");
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthenticationFilter, CorrelationIdFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }
}
