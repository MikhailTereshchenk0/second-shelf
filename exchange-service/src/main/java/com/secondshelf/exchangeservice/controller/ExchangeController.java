package com.secondshelf.exchangeservice.controller;

import com.secondshelf.exchangeservice.dto.CreateExchangeRequest;
import com.secondshelf.exchangeservice.dto.ExchangeResponse;
import com.secondshelf.exchangeservice.security.UserPrincipal;
import com.secondshelf.exchangeservice.service.ExchangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static org.springframework.data.domain.Sort.Direction.DESC;

@RestController
@RequestMapping("/api/v1/exchanges")
@RequiredArgsConstructor
public class ExchangeController {

    private final ExchangeService exchangeService;

    @PostMapping
    public ExchangeResponse create(@Valid @RequestBody CreateExchangeRequest req,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return exchangeService.create(req, principal);
    }

    @GetMapping("/my/outgoing")
    public Page<ExchangeResponse> outgoing(@AuthenticationPrincipal UserPrincipal principal,
                                           @PageableDefault(size = 20, sort = "createdAt", direction = DESC) Pageable pageable) {
        return exchangeService.myOutgoing(principal, pageable);
    }

    @GetMapping("/my/incoming")
    public Page<ExchangeResponse> incoming(@AuthenticationPrincipal UserPrincipal principal,
                                           @PageableDefault(size = 20, sort = "createdAt", direction = DESC) Pageable pageable) {
        return exchangeService.myIncoming(principal, pageable);
    }

    @PostMapping("/{id}/accept")
    public ExchangeResponse accept(@PathVariable Long id,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return exchangeService.accept(id, principal);
    }

    @PostMapping("/{id}/decline")
    public ExchangeResponse decline(@PathVariable Long id,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        return exchangeService.decline(id, principal);
    }

    @PostMapping("/{id}/cancel")
    public ExchangeResponse cancel(@PathVariable Long id,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return exchangeService.cancel(id, principal);
    }

    @PostMapping("/{id}/complete")
    public ExchangeResponse complete(@PathVariable Long id,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        return exchangeService.complete(id, principal);
    }
}
