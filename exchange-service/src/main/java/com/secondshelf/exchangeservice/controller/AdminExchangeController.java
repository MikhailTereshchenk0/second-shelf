package com.secondshelf.exchangeservice.controller;

import com.secondshelf.exchangeservice.dto.ExchangeResponse;
import com.secondshelf.exchangeservice.security.UserPrincipal;
import com.secondshelf.exchangeservice.service.ExchangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/exchanges")
@RequiredArgsConstructor
@Tag(name = "Admin Exchange API", description = "Operational exchange repair endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AdminExchangeController {

    private final ExchangeService exchangeService;

    @Operation(
            summary = "Repair exchange request",
            description = "Retries the remote book transitions needed to repair a REPAIR_REQUIRED exchange and move it to the intended terminal state."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exchange repair completed or already terminal"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin role required"),
            @ApiResponse(responseCode = "404", description = "Exchange request not found"),
            @ApiResponse(responseCode = "409", description = "Exchange cannot be repaired in its current state")
    })
    @PostMapping("/{id}/repair")
    public ExchangeResponse repair(@PathVariable Long id,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return exchangeService.repair(id, principal);
    }
}
