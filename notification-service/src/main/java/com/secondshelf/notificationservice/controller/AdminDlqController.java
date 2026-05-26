package com.secondshelf.notificationservice.controller;

import com.secondshelf.notificationservice.dto.DlqRedriveResponse;
import com.secondshelf.notificationservice.service.NotificationDlqRedriveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/admin/notifications/dlq")
@RequiredArgsConstructor
@Tag(name = "Admin Notification DLQ API", description = "Operational notification DLQ redrive endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AdminDlqController {

    private final NotificationDlqRedriveService notificationDlqRedriveService;

    @Operation(
            summary = "Redrive notification DLQ messages",
            description = "Reads messages from the configured notification DLQ and republishes them to the original exchange and routing key."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "DLQ redrive completed"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "403", description = "Admin role required")
    })
    @PostMapping("/redrive")
    public DlqRedriveResponse redrive(@RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return notificationDlqRedriveService.redrive(limit);
    }
}
