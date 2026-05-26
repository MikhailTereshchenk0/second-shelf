package com.secondshelf.notificationservice.messaging;

import com.secondshelf.notificationservice.entity.NotificationChannel;
import com.secondshelf.notificationservice.exception.NotificationBadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class NotificationChannelDispatcher {

    private final List<NotificationChannelHandler> handlers;

    public void deliver(NotificationDeliveryCommand command) {
        NotificationChannel channel = command.getChannel();
        handlers.stream()
                .filter(handler -> handler.supports(channel))
                .findFirst()
                .orElseThrow(() -> new NotificationBadRequestException(
                        "UNSUPPORTED_NOTIFICATION_CHANNEL",
                        "Notification channel is not supported: " + channel
                ))
                .deliver(command);
    }
}
