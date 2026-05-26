package com.secondshelf.notificationservice.service;

import com.secondshelf.notificationservice.dto.NotificationResponse;
import com.secondshelf.notificationservice.entity.Notification;
import com.secondshelf.notificationservice.entity.NotificationStatus;
import com.secondshelf.notificationservice.exception.NotificationForbiddenException;
import com.secondshelf.notificationservice.exception.NotificationException;
import com.secondshelf.notificationservice.exception.NotificationNotFoundException;
import com.secondshelf.notificationservice.repository.NotificationRepository;
import com.secondshelf.notificationservice.security.UserPrincipal;
import com.secondshelf.observability.AuditEvent;
import com.secondshelf.observability.AuditLogger;
import com.secondshelf.observability.AuditOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final AuditLogger AUDIT_LOGGER = AuditLogger.forClass(NotificationService.class);

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getMyNotifications(UserPrincipal principal, Pageable pageable) {
        Long userId = requireUserId(principal);
        return notificationRepository.findAllByUserId(userId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UserPrincipal principal) {
        Long userId = requireUserId(principal);
        return notificationRepository.countByUserIdAndStatus(userId, NotificationStatus.UNREAD);
    }

    @Transactional
    public NotificationResponse markAsRead(Long notificationId, UserPrincipal principal) {
        Long userId = requireUserId(principal);

        try {
            Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                    .orElseThrow(() -> new NotificationNotFoundException(
                            "NOTIFICATION_NOT_FOUND",
                            "Notification not found."
                    ));

            if (notification.getStatus() == NotificationStatus.UNREAD) {
                notification.markAsRead();
                notificationRepository.save(notification);
            }

            NotificationResponse response = toResponse(notification);
            AUDIT_LOGGER.log(AuditEvent.builder("NOTIFICATION_MARK_READ", AuditOutcome.SUCCESS)
                    .actorUserId(userId)
                    .targetUserId(userId)
                    .entityId(notificationId)
                    .build());
            return response;
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("NOTIFICATION_MARK_READ", AuditOutcome.FAILURE)
                    .actorUserId(userId)
                    .targetUserId(userId)
                    .entityId(notificationId)
                    .reason(ex.getMessage())
                    .errorCode(resolveErrorCode(ex))
                    .build());
            throw ex;
        }
    }

    @Transactional
    public void markAllAsRead(UserPrincipal principal) {
        Long userId = requireUserId(principal);
        try {
            notificationRepository.markAllAsReadByUserId(
                    userId,
                    NotificationStatus.UNREAD,
                    NotificationStatus.READ,
                    LocalDateTime.now()
            );
            AUDIT_LOGGER.log(AuditEvent.builder("NOTIFICATION_MARK_READ_ALL", AuditOutcome.SUCCESS)
                    .actorUserId(userId)
                    .targetUserId(userId)
                    .build());
        } catch (RuntimeException ex) {
            AUDIT_LOGGER.log(AuditEvent.builder("NOTIFICATION_MARK_READ_ALL", AuditOutcome.FAILURE)
                    .actorUserId(userId)
                    .targetUserId(userId)
                    .reason(ex.getMessage())
                    .errorCode(resolveErrorCode(ex))
                    .build());
            throw ex;
        }
    }

    private Long requireUserId(UserPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw new NotificationForbiddenException(
                    "USER_CONTEXT_REQUIRED",
                    "Authenticated user context is required."
            );
        }
        return principal.userId();
    }

    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .status(notification.getStatus())
                .relatedEntityType(notification.getRelatedEntityType())
                .relatedEntityId(notification.getRelatedEntityId())
                .createdAt(notification.getCreatedAt())
                .readAt(notification.getReadAt())
                .build();
    }

    private String resolveErrorCode(RuntimeException ex) {
        if (ex instanceof NotificationException notificationException) {
            return notificationException.getCode();
        }
        return null;
    }
}
