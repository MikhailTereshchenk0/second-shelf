package com.secondshelf.userservice.config;

import com.secondshelf.userservice.security.JwtAuthenticationFilter;
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

@WebMvcTest(controllers = UserServiceSecurityTestController.class)
@Import({SecurityConfig.class, InternalTokenFilter.class, JwtAuthenticationFilter.class})
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "internal.token=test-internal-token",
        "jwt.secret=test-secret-test-secret-test-secret-12345678"
})
class UserServiceSecurityProdTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownRoutesShouldNotBePermitAll() throws Exception {
        mockMvc.perform(get("/definitely-unknown-route")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

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
    void readinessShouldRemainPublicInProdProfile() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
