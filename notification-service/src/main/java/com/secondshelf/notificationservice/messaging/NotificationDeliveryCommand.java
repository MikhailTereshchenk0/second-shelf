package com.secondshelf.notificationservice.messaging;

import com.secondshelf.notificationservice.entity.NotificationChannel;
import com.secondshelf.notificationservice.entity.NotificationType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class NotificationDeliveryCommand {

    NotificationChannel channel;
    Long userId;
    NotificationType type;
    String title;
    String message;
    String relatedEntityType;
    String relatedEntityId;
    LocalDateTime occurredAt;
}
