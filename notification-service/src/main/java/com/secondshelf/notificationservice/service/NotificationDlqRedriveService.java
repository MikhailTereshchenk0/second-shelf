package com.secondshelf.notificationservice.service;

import com.secondshelf.notificationservice.config.NotificationRabbitProperties;
import com.secondshelf.notificationservice.dto.DlqRedriveMessageError;
import com.secondshelf.notificationservice.dto.DlqRedriveResponse;
import com.secondshelf.notificationservice.observability.NotificationAsyncMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationDlqRedriveService {

    private static final String ORIGINAL_ROUTING_KEY_HEADER = "x-original-routing-key";
    private static final String FIRST_DEATH_ROUTING_KEY_HEADER = "x-first-death-routing-key";
    private static final String X_DEATH_HEADER = "x-death";
    private static final String X_DEATH_ROUTING_KEYS = "routing-keys";

    private final RabbitTemplate rabbitTemplate;
    private final NotificationRabbitProperties notificationRabbitProperties;
    private final NotificationAsyncMetrics notificationAsyncMetrics;

    public DlqRedriveResponse redrive(int limit) {
        int requestedLimit = Math.max(1, limit);
        int redrivenCount = 0;
        int skippedCount = 0;
        int failedCount = 0;
        List<DlqRedriveMessageError> errors = new ArrayList<>();

        for (int index = 1; index <= requestedLimit; index++) {
            Message message = rabbitTemplate.receive(notificationRabbitProperties.resolveDeadLetterQueue());
            if (message == null) {
                break;
            }

            notificationAsyncMetrics.incrementDlqRedriveAttempted();
            String routingKey = resolveOriginalRoutingKey(message);
            if (!StringUtils.hasText(routingKey)) {
                skippedCount++;
                notificationAsyncMetrics.incrementDlqRedriveFailed();
                String restoreError = restoreToDlq(message);
                errors.add(error(
                        index,
                        message,
                        appendRestoreError(
                                "Original routing key is missing and configured fallback is not a concrete routing key.",
                                restoreError
                        )
                ));
                continue;
            }

            try {
                rabbitTemplate.send(notificationRabbitProperties.getExchange(), routingKey, message);
                redrivenCount++;
                notificationAsyncMetrics.incrementDlqRedriveSucceeded();
            } catch (RuntimeException ex) {
                failedCount++;
                notificationAsyncMetrics.incrementDlqRedriveFailed();
                String restoreError = restoreToDlq(message);
                errors.add(error(
                        index,
                        message,
                        appendRestoreError("Failed to redrive message: " + ex.getMessage(), restoreError)
                ));
            }
        }

        return DlqRedriveResponse.builder()
                .requestedLimit(requestedLimit)
                .redrivenCount(redrivenCount)
                .skippedCount(skippedCount)
                .failedCount(failedCount)
                .errors(errors)
                .build();
    }

    private String resolveOriginalRoutingKey(Message message) {
        MessageProperties properties = message.getMessageProperties();
        String explicitRoutingKey = headerAsString(properties, ORIGINAL_ROUTING_KEY_HEADER);
        if (StringUtils.hasText(explicitRoutingKey)) {
            return explicitRoutingKey;
        }

        String firstDeathRoutingKey = headerAsString(properties, FIRST_DEATH_ROUTING_KEY_HEADER);
        if (StringUtils.hasText(firstDeathRoutingKey)) {
            return firstDeathRoutingKey;
        }

        String xDeathRoutingKey = routingKeyFromXDeath(properties.getHeaders().get(X_DEATH_HEADER));
        if (StringUtils.hasText(xDeathRoutingKey)) {
            return xDeathRoutingKey;
        }

        String fallback = notificationRabbitProperties.getRoutingKeyPattern();
        if (StringUtils.hasText(fallback) && !fallback.contains("*") && !fallback.contains("#")) {
            return fallback;
        }

        return null;
    }

    private String routingKeyFromXDeath(Object xDeathHeader) {
        if (!(xDeathHeader instanceof Collection<?> deaths)) {
            return null;
        }

        for (Object death : deaths) {
            if (!(death instanceof Map<?, ?> deathMap)) {
                continue;
            }

            Object routingKeys = deathMap.get(X_DEATH_ROUTING_KEYS);
            if (routingKeys instanceof Collection<?> keys) {
                for (Object key : keys) {
                    if (key instanceof String routingKey && StringUtils.hasText(routingKey)) {
                        return routingKey;
                    }
                }
            } else if (routingKeys instanceof String routingKey && StringUtils.hasText(routingKey)) {
                return routingKey;
            }
        }

        return null;
    }

    private String headerAsString(MessageProperties properties, String headerName) {
        Object value = properties.getHeaders().get(headerName);
        return value instanceof String text ? text : null;
    }

    private String restoreToDlq(Message message) {
        try {
            rabbitTemplate.send(
                    notificationRabbitProperties.resolveDeadLetterExchange(),
                    notificationRabbitProperties.resolveDeadLetterRoutingKey(),
                    message
            );
            return null;
        } catch (RuntimeException ex) {
            return " Failed to return message to DLQ: " + ex.getMessage();
        }
    }

    private String appendRestoreError(String reason, String restoreError) {
        if (!StringUtils.hasText(restoreError)) {
            return reason;
        }
        return reason + restoreError;
    }

    private DlqRedriveMessageError error(int messageIndex, Message message, String reason) {
        MessageProperties properties = message.getMessageProperties();
        return DlqRedriveMessageError.builder()
                .messageIndex(messageIndex)
                .eventId(String.valueOf(properties.getHeaders().getOrDefault("eventId", "unknown")))
                .eventType(String.valueOf(properties.getHeaders().getOrDefault("eventType", "unknown")))
                .reason(reason)
                .build();
    }
}
