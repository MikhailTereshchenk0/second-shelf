package com.secondshelf.exchangeservice.outbox;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExchangeEventContext {
    Long initiatorUserId;
    String initiatorUsername;
    Long completedByUserId;
}
