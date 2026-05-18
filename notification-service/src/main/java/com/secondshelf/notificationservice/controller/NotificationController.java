package com.secondshelf.notificationservice.controller;

import com.secondshelf.notificationservice.dto.NotificationResponse;
import com.secondshelf.notificationservice.dto.UnreadCountResponse;
import com.secondshelf.notificationservice.security.UserPrincipal;
import com.secondshelf.notificationservice.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.data.domain.Sort.Direction.DESC;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification API", description = "Notification endpoints for authenticated users")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(
            summary = "Get my notifications",
            description = "Returns paginated notifications for the authenticated user"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notifications returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @GetMapping
    public Page<NotificationResponse> getMyNotifications(@AuthenticationPrincipal UserPrincipal principal,
                                                         @ParameterObject
                                                         @PageableDefault(size = 20, sort = "createdAt", direction = DESC) Pageable pageable) {
        return notificationService.getMyNotifications(principal, pageable);
    }

    @Operation(
            summary = "Get unread notifications count",
            description = "Returns unread notifications count for the authenticated user"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unread count returned"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @GetMapping("/unread-count")
    public UnreadCountResponse getUnreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        return new UnreadCountResponse(notificationService.getUnreadCount(principal));
    }

    @Operation(
            summary = "Mark notification as read",
            description = "Marks a single notification as read for the authenticated user"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification marked as read"),
            @ApiResponse(responseCode = "401", description = "Authentication required"),
            @ApiResponse(responseCode = "404", description = "Notification not found")
    })
    @PostMapping("/{id}/read")
    public NotificationResponse markAsRead(@PathVariable Long id,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        return notificationService.markAsRead(id, principal);
    }

    @Operation(
            summary = "Mark all notifications as read",
            description = "Marks all notifications as read for the authenticated user"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Notifications marked as read"),
            @ApiResponse(responseCode = "401", description = "Authentication required")
    })
    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllAsRead(@AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAllAsRead(principal);
    }
}
