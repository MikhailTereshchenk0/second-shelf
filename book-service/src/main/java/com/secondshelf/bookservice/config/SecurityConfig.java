package com.secondshelf.bookservice.config;

import com.secondshelf.bookservice.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
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

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final InternalTokenFilter internalTokenFilter;

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
                    auth.requestMatchers("/api/v1/books/public").permitAll();
                    auth.requestMatchers("/internal/**").hasAuthority(InternalTokenFilter.AUTHORITY);
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(internalTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }
}
