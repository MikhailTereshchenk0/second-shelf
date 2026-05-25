package com.secondshelf.exchangeservice.config;

import com.secondshelf.exchangeservice.observability.CorrelationIdFilter;
import com.secondshelf.exchangeservice.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ExchangeServiceSecurityTestController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, JwtAuthenticationFilter.class})
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-12345678"
})
class ExchangeServiceSecurityLocalDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithAnonymousUser
    void openApiShouldBeAccessibleInLocalProfile() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.0.1"));
    }

    @Test
    @WithAnonymousUser
    void readinessShouldRemainPublicInLocalProfile() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
