package com.secondshelf.notificationservice.dto;

import com.secondshelf.notificationservice.entity.NotificationStatus;
import com.secondshelf.notificationservice.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private Long userId;
    private NotificationType type;
    private String title;
    private String message;
    private NotificationStatus status;
    private String relatedEntityType;
    private String relatedEntityId;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
}
