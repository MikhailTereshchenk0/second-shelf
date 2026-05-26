package com.secondshelf.notificationservice.messaging;

import com.secondshelf.notificationservice.entity.NotificationChannel;

public interface NotificationChannelHandler {

    boolean supports(NotificationChannel channel);

    void deliver(NotificationDeliveryCommand command);
}
