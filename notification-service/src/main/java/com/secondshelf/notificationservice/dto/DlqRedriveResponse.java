package com.secondshelf.notificationservice.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class DlqRedriveResponse {

    int requestedLimit;
    int redrivenCount;
    int skippedCount;
    int failedCount;
    List<DlqRedriveMessageError> errors;
}
