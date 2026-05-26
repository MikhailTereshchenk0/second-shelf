package com.secondshelf.notificationservice.messaging;

import com.secondshelf.notificationservice.entity.NotificationChannel;
import com.secondshelf.notificationservice.exception.NotificationBadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationChannelDispatcherTest {

    @Test
    void deliverShouldFailSafelyWhenChannelIsUnsupported() {
        NotificationChannelDispatcher dispatcher = new NotificationChannelDispatcher(List.of());
        NotificationDeliveryCommand command = NotificationDeliveryCommand.builder()
                .channel(NotificationChannel.IN_APP)
                .userId(42L)
                .build();

        NotificationBadRequestException exception = assertThrows(
                NotificationBadRequestException.class,
                () -> dispatcher.deliver(command)
        );

        assertEquals("UNSUPPORTED_NOTIFICATION_CHANNEL", exception.getCode());
    }
}
