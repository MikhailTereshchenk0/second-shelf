package com.secondshelf.authservice.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondshelf.authservice.client.dto.UserClaimsResponse;
import com.secondshelf.authservice.observability.CorrelationId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserServiceClientTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void getClaimsShouldPropagateCorrelationIdHeader() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://user-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        UserServiceClient client = new UserServiceClient(restClient, new ObjectMapper());
        ReflectionTestUtils.setField(client, "internalToken", "internal-token-123");

        server.expect(requestTo("http://user-service/internal/users/42/claims"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(CorrelationId.HEADER_NAME, "corr-auth-rest-123"))
                .andExpect(header("X-Internal-Token", "internal-token-123"))
                .andRespond(withSuccess(
                        """
                        {"userId":42,"username":"alice","roles":["ROLE_USER"],"enabled":true}
                        """,
                        MediaType.APPLICATION_JSON
                ));

        UserClaimsResponse response;
        try (CorrelationId.Scope ignored = CorrelationId.openScope("corr-auth-rest-123")) {
            response = client.getClaims(42L);
        }

        assertEquals(42L, response.getUserId());
        assertEquals("alice", response.getUsername());
        assertEquals(List.of("ROLE_USER"), response.getRoles());
        server.verify();
    }
}
