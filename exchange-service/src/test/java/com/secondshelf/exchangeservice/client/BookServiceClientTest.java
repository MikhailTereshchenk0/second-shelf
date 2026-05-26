package com.secondshelf.exchangeservice.client;

import com.secondshelf.exchangeservice.client.dto.BookDto;
import com.secondshelf.exchangeservice.observability.CorrelationId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BookServiceClientTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void getBookShouldPropagateCorrelationIdHeader() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://book-service");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();

        BookServiceClient client = new BookServiceClient(restClient, "internal-token-456");

        server.expect(requestTo("http://book-service/internal/books/5"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(CorrelationId.HEADER_NAME, "corr-exchange-rest-123"))
                .andExpect(header("X-Internal-Token", "internal-token-456"))
                .andRespond(withSuccess(
                        """
                        {"id":5,"ownerId":42,"title":"Dune","author":"Frank Herbert","visibility":"PUBLIC","status":"AVAILABLE"}
                        """,
                        MediaType.APPLICATION_JSON
                ));

        BookDto response;
        try (CorrelationId.Scope ignored = CorrelationId.openScope("corr-exchange-rest-123")) {
            response = client.getBook(5L);
        }

        assertEquals(5L, response.getId());
        assertEquals(42L, response.getOwnerId());
        assertEquals("Dune", response.getTitle());
        server.verify();
    }
}
