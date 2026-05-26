package com.secondshelf.exchangeservice.controller;

import com.secondshelf.exchangeservice.dto.OutboxEventSummaryResponse;
import com.secondshelf.exchangeservice.dto.OutboxRetryResponse;
import com.secondshelf.exchangeservice.service.ExchangeOutboxAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/outbox")
@RequiredArgsConstructor
@Tag(name = "Admin Outbox API", description = "Operational outbox recovery endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AdminOutboxController {

    private final ExchangeOutboxAdminService exchangeOutboxAdminService;

    @Operation(
            summary = "List terminally failed outbox events",
            description = "Returns a bounded summary list of terminally failed exchange outbox events without payload bodies."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Terminally failed outbox events returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    @GetMapping("/terminal-failed")
    public List<OutboxEventSummaryResponse> terminalFailedEvents() {
        return exchangeOutboxAdminService.findTerminalFailedEvents();
    }

    @Operation(
            summary = "Retry terminally failed outbox event",
            description = "Re-queues a terminally failed outbox event for the scheduled publisher. The event is not published synchronously."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Outbox event re-queued"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin role required"),
            @ApiResponse(responseCode = "404", description = "Outbox event not found"),
            @ApiResponse(responseCode = "409", description = "Outbox event is not terminally failed")
    })
    @PostMapping("/{eventId}/retry")
    public OutboxRetryResponse retry(@PathVariable UUID eventId) {
        return exchangeOutboxAdminService.retryTerminalFailedEvent(eventId);
    }
}
