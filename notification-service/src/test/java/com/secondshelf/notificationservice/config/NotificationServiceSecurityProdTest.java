package com.secondshelf.notificationservice.config;

import com.secondshelf.notificationservice.observability.CorrelationIdFilter;
import com.secondshelf.notificationservice.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NotificationServiceSecurityTestController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, JwtAuthenticationFilter.class})
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-12345678"
})
class NotificationServiceSecurityProdTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiShouldBeDeniedInProdProfile() throws Exception {
        mockMvc.perform(get("/v3/api-docs")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithAnonymousUser
    void metricsShouldNotBePublicInProdProfile() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void metricsShouldAllowAdminInProdProfile() throws Exception {
        mockMvc.perform(get("/actuator/metrics")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("requests.total"));
    }

    @Test
    @WithAnonymousUser
    void asyncHealthGroupShouldNotBePublicInProdProfile() throws Exception {
        mockMvc.perform(get("/actuator/health/asyncFlow"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void asyncHealthGroupShouldAllowAdminInProdProfile() throws Exception {
        mockMvc.perform(get("/actuator/health/asyncFlow")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @WithAnonymousUser
    void readinessShouldRemainPublicInProdProfile() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
