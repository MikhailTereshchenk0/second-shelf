package com.secondshelf.exchangeservice.controller;

import com.secondshelf.exchangeservice.dto.CreateExchangeRequest;
import com.secondshelf.exchangeservice.dto.ExchangeResponse;
import com.secondshelf.exchangeservice.security.UserPrincipal;
import com.secondshelf.exchangeservice.service.ExchangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static org.springframework.data.domain.Sort.Direction.DESC;

@RestController
@RequestMapping("/api/v1/exchanges")
@RequiredArgsConstructor
@Tag(name = "Exchange API", description = "Exchange request workflow endpoints")
@SecurityRequirement(name = "bearerAuth")
public class ExchangeController {

    private final ExchangeService exchangeService;

    @Operation(
            summary = "Create exchange request",
            description = "Creates a new exchange request where the authenticated user offers one of their books in exchange for a requested public book"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exchange request created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Operation is forbidden"),
            @ApiResponse(responseCode = "404", description = "Requested book not found"),
            @ApiResponse(responseCode = "409", description = "Exchange request conflict")
    })
    @PostMapping
    public ExchangeResponse create(@Valid @RequestBody CreateExchangeRequest req,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return exchangeService.create(req, principal);
    }

    @Operation(
            summary = "Get my outgoing exchange requests",
            description = "Returns paginated list of exchange requests created by the authenticated user"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Outgoing exchange requests returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @GetMapping("/my/outgoing")
    public Page<ExchangeResponse> outgoing(@AuthenticationPrincipal UserPrincipal principal,
                                           @ParameterObject
                                           @PageableDefault(size = 20, sort = "createdAt", direction = DESC) Pageable pageable) {
        return exchangeService.myOutgoing(principal, pageable);
    }

    @Operation(
            summary = "Get my incoming exchange requests",
            description = "Returns paginated list of exchange requests received for books owned by the authenticated user"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Incoming exchange requests returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @GetMapping("/my/incoming")
    public Page<ExchangeResponse> incoming(@AuthenticationPrincipal UserPrincipal principal,
                                           @ParameterObject
                                           @PageableDefault(size = 20, sort = "createdAt", direction = DESC) Pageable pageable) {
        return exchangeService.myIncoming(principal, pageable);
    }

    @Operation(
            summary = "Accept exchange request",
            description = "Accepts an incoming exchange request"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exchange request accepted"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Operation is forbidden"),
            @ApiResponse(responseCode = "404", description = "Exchange request not found"),
            @ApiResponse(responseCode = "409", description = "Exchange state conflict")
    })
    @PostMapping("/{id}/accept")
    public ExchangeResponse accept(@PathVariable Long id,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return exchangeService.accept(id, principal);
    }

    @Operation(
            summary = "Decline exchange request",
            description = "Declines an incoming exchange request"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exchange request declined"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Operation is forbidden"),
            @ApiResponse(responseCode = "404", description = "Exchange request not found"),
            @ApiResponse(responseCode = "409", description = "Exchange state conflict")
    })
    @PostMapping("/{id}/decline")
    public ExchangeResponse decline(@PathVariable Long id,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        return exchangeService.decline(id, principal);
    }

    @Operation(
            summary = "Cancel exchange request",
            description = "Cancels an exchange request created by the authenticated user"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exchange request cancelled"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Operation is forbidden"),
            @ApiResponse(responseCode = "404", description = "Exchange request not found"),
            @ApiResponse(responseCode = "409", description = "Exchange state conflict")
    })
    @PostMapping("/{id}/cancel")
    public ExchangeResponse cancel(@PathVariable Long id,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return exchangeService.cancel(id, principal);
    }

    @Operation(
            summary = "Complete exchange request",
            description = "Marks an accepted exchange request as completed"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exchange request completed"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Operation is forbidden"),
            @ApiResponse(responseCode = "404", description = "Exchange request not found"),
            @ApiResponse(responseCode = "409", description = "Exchange state conflict")
    })
    @PostMapping("/{id}/complete")
    public ExchangeResponse complete(@PathVariable Long id,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        return exchangeService.complete(id, principal);
    }
}