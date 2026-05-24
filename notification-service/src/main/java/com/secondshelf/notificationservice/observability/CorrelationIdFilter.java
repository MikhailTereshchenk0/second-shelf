package com.secondshelf.notificationservice.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws java.io.IOException, jakarta.servlet.ServletException {
        String correlationId = CorrelationId.resolve(request.getHeader(CorrelationId.HEADER_NAME));
        response.setHeader(CorrelationId.HEADER_NAME, correlationId);

        try (CorrelationId.Scope ignored = CorrelationId.openScope(correlationId)) {
            filterChain.doFilter(request, response);
        }
    }
}
