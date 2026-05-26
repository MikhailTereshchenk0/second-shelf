package com.secondshelf.notificationservice.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DlqRedriveMessageError {

    int messageIndex;
    String eventId;
    String eventType;
    String reason;
}
