package com.secondshelf.notificationservice.messaging;

import com.secondshelf.notificationservice.entity.Notification;
import com.secondshelf.notificationservice.entity.NotificationChannel;
import com.secondshelf.notificationservice.entity.NotificationDeliveryStatus;
import com.secondshelf.notificationservice.entity.NotificationStatus;
import com.secondshelf.notificationservice.entity.NotificationType;
import com.secondshelf.notificationservice.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InAppNotificationChannelHandlerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Test
    void deliverShouldPersistInAppNotification() {
        InAppNotificationChannelHandler handler = new InAppNotificationChannelHandler(notificationRepository);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 5, 26, 14, 30);

        handler.deliver(NotificationDeliveryCommand.builder()
                .channel(NotificationChannel.IN_APP)
                .userId(42L)
                .type(NotificationType.EXCHANGE_REQUEST_CREATED)
                .title("New exchange request")
                .message("Someone wants to exchange a book with you.")
                .relatedEntityType("EXCHANGE_REQUEST")
                .relatedEntityId("101")
                .occurredAt(occurredAt)
                .build());

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification notification = captor.getValue();
        assertEquals(NotificationChannel.IN_APP, notification.getChannel());
        assertEquals(NotificationDeliveryStatus.DELIVERED, notification.getDeliveryStatus());
        assertEquals(NotificationStatus.UNREAD, notification.getStatus());
        assertEquals(42L, notification.getUserId());
        assertEquals(occurredAt, notification.getCreatedAt());
        assertTrue(handler.supports(NotificationChannel.IN_APP));
    }
}
