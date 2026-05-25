package com.secondshelf.notificationservice.observability;

import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component("rabbitListeners")
public class RabbitListenersHealthIndicator implements HealthIndicator {

    private final RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry;

    public RabbitListenersHealthIndicator(RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry) {
        this.rabbitListenerEndpointRegistry = rabbitListenerEndpointRegistry;
    }

    @Override
    public Health health() {
        Set<String> containerIds = rabbitListenerEndpointRegistry.getListenerContainerIds();
        if (containerIds.isEmpty()) {
            return Health.down()
                    .withDetail("containerCount", 0)
                    .withDetail("reason", "No Rabbit listener containers registered.")
                    .build();
        }

        List<MessageListenerContainer> containers = containerIds.stream()
                .map(rabbitListenerEndpointRegistry::getListenerContainer)
                .filter(Objects::nonNull)
                .toList();

        List<Map<String, Object>> containerStates = containerIds.stream()
                .map(this::describeContainer)
                .toList();

        boolean allReady = containers.size() == containerIds.size() && containers.stream().allMatch(this::isContainerReady);
        Health.Builder builder = allReady ? Health.up() : Health.down();
        return builder
                .withDetail("containerCount", containerIds.size())
                .withDetail("containers", containerStates)
                .build();
    }

    private boolean isContainerReady(MessageListenerContainer container) {
        return container.isRunning();
    }

    private Map<String, Object> describeContainer(String containerId) {
        Map<String, Object> details = new LinkedHashMap<>();
        MessageListenerContainer container = rabbitListenerEndpointRegistry.getListenerContainer(containerId);
        details.put("listenerId", containerId);
        if (container == null) {
            details.put("registered", false);
            return details;
        }

        details.put("registered", true);
        details.put("queueNames", resolveQueueNames(container));
        details.put("running", container.isRunning());
        if (container instanceof AbstractMessageListenerContainer listenerContainer) {
            details.put("active", listenerContainer.isActive());
        }
        return details;
    }

    private List<String> resolveQueueNames(MessageListenerContainer container) {
        if (!(container instanceof AbstractMessageListenerContainer listenerContainer)) {
            return List.of();
        }

        String[] queueNames = listenerContainer.getQueueNames();
        return queueNames == null ? List.of() : Arrays.asList(queueNames);
    }
}
