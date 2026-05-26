package com.secondshelf.notificationservice.messaging;

import com.secondshelf.notificationservice.entity.Notification;
import com.secondshelf.notificationservice.entity.NotificationChannel;
import com.secondshelf.notificationservice.entity.NotificationDeliveryStatus;
import com.secondshelf.notificationservice.entity.NotificationStatus;
import com.secondshelf.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InAppNotificationChannelHandler implements NotificationChannelHandler {

    private final NotificationRepository notificationRepository;

    @Override
    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.IN_APP;
    }

    @Override
    public void deliver(NotificationDeliveryCommand command) {
        notificationRepository.save(Notification.builder()
                .userId(command.getUserId())
                .type(command.getType())
                .title(command.getTitle())
                .message(command.getMessage())
                .status(NotificationStatus.UNREAD)
                .channel(NotificationChannel.IN_APP)
                .deliveryStatus(NotificationDeliveryStatus.DELIVERED)
                .relatedEntityType(command.getRelatedEntityType())
                .relatedEntityId(command.getRelatedEntityId())
                .createdAt(command.getOccurredAt())
                .build());
    }
}
