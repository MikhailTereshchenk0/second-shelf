package com.secondshelf.exchangeservice.controller;

import com.secondshelf.exchangeservice.dto.CreateExchangeRequest;
import com.secondshelf.exchangeservice.dto.ExchangeResponse;
import com.secondshelf.exchangeservice.dto.OwnerOfferRequest;
import com.secondshelf.exchangeservice.security.UserPrincipal;
import com.secondshelf.exchangeservice.service.ExchangeService;
import com.secondshelf.exchangeservice.web.PageableSanitizer;
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

import java.util.Set;

import static org.springframework.data.domain.Sort.Direction.DESC;

@RestController
@RequestMapping("/api/v1/exchanges")
@RequiredArgsConstructor
@Tag(name = "Exchange API", description = "Exchange request workflow endpoints")
@SecurityRequirement(name = "bearerAuth")
public class ExchangeController {

    private static final Set<String> EXCHANGE_SORT_FIELDS = Set.of("createdAt", "updatedAt", "status");

    private final ExchangeService exchangeService;

    @Operation(
            summary = "Create exchange request",
            description = "Creates a new exchange request for a requested public book. The requester does not choose their own book at creation time; the owner may later choose one requester book as a counter offer. Responses include book snapshots, participant username snapshots, and contact fields only when the workflow allows them."
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
                                   @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return exchangeService.create(req, principal, idempotencyKey);
    }

    @Operation(
            summary = "Get my outgoing exchange requests",
            description = "Returns paginated list of exchange requests created by the authenticated user. Owner phone is included only after the requester accepts the owner offer and the exchange becomes ACCEPTED."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Outgoing exchange requests returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @GetMapping("/my/outgoing")
    public Page<ExchangeResponse> outgoing(@AuthenticationPrincipal UserPrincipal principal,
                                           @ParameterObject
                                           @PageableDefault(size = 20, sort = "createdAt", direction = DESC) Pageable pageable) {
        return exchangeService.myOutgoing(principal, PageableSanitizer.sanitize(pageable, EXCHANGE_SORT_FIELDS));
    }

    @Operation(
            summary = "Get my incoming exchange requests",
            description = "Returns paginated list of exchange requests received for books owned by the authenticated user. Owner view includes requester phone and the requester's currently available public books."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Incoming exchange requests returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @GetMapping("/my/incoming")
    public Page<ExchangeResponse> incoming(@AuthenticationPrincipal UserPrincipal principal,
                                           @ParameterObject
                                           @PageableDefault(size = 20, sort = "createdAt", direction = DESC) Pageable pageable) {
        return exchangeService.myIncoming(principal, PageableSanitizer.sanitize(pageable, EXCHANGE_SORT_FIELDS));
    }

    @Operation(
            summary = "Create owner counter offer",
            description = "Allows the owner of the requested book to select one available public book from the requester and move the exchange from PENDING to OWNER_OFFERED."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Owner counter offer created"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Operation is forbidden"),
            @ApiResponse(responseCode = "404", description = "Exchange request or offered book not found"),
            @ApiResponse(responseCode = "409", description = "Exchange state conflict")
    })
    @PostMapping("/{id}/offer")
    public ExchangeResponse offer(@PathVariable Long id,
                                  @Valid @RequestBody OwnerOfferRequest request,
                                  @AuthenticationPrincipal UserPrincipal principal) {
        return exchangeService.offer(id, request, principal);
    }

    @Operation(
            summary = "Accept owner offer",
            description = "Allows the requester to accept the owner's selected book offer. This is the final agreement step: both books are reserved and the requester can see owner phone after the request becomes ACCEPTED."
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
            description = "Allows the owner of the requested book to decline a PENDING exchange request. No books are reserved and owner phone remains hidden from the requester."
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
            summary = "Decline owner offer",
            description = "Allows the requester to decline the owner counter offer. The exchange is cancelled and owner phone remains hidden from the requester."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Owner offer declined"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Operation is forbidden"),
            @ApiResponse(responseCode = "404", description = "Exchange request not found"),
            @ApiResponse(responseCode = "409", description = "Exchange state conflict")
    })
    @PostMapping("/{id}/decline-offer")
    public ExchangeResponse declineOffer(@PathVariable Long id,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        return exchangeService.declineOffer(id, principal);
    }

    @Operation(
            summary = "Cancel exchange request",
            description = "Cancels an exchange request created by the authenticated requester. PENDING and OWNER_OFFERED requests cancel without book state changes; ACCEPTED requests without completion confirmation release both reserved books."
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
            description = "Confirms exchange completion for the authenticated participant. The exchange becomes completed only after both participants confirm it."
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
