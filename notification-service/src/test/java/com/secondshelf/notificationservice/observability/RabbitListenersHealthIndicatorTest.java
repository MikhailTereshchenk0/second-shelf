package com.secondshelf.notificationservice.observability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RabbitListenersHealthIndicatorTest {

    @Mock
    private RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry;

    @Mock
    private MessageListenerContainer listenerContainer;

    @Test
    void healthShouldBeUpWhenAllRabbitListenersAreRunning() {
        when(rabbitListenerEndpointRegistry.getListenerContainerIds()).thenReturn(Set.of("exchangeEventConsumer"));
        when(rabbitListenerEndpointRegistry.getListenerContainer("exchangeEventConsumer")).thenReturn(listenerContainer);
        when(listenerContainer.isRunning()).thenReturn(true);

        Health health = new RabbitListenersHealthIndicator(rabbitListenerEndpointRegistry).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(1, health.getDetails().get("containerCount"));
    }

    @Test
    void healthShouldBeDownWhenListenerContainerIsNotRunning() {
        when(rabbitListenerEndpointRegistry.getListenerContainerIds()).thenReturn(Set.of("exchangeEventConsumer"));
        when(rabbitListenerEndpointRegistry.getListenerContainer("exchangeEventConsumer")).thenReturn(listenerContainer);
        when(listenerContainer.isRunning()).thenReturn(false);

        Health health = new RabbitListenersHealthIndicator(rabbitListenerEndpointRegistry).health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(1, health.getDetails().get("containerCount"));
    }

    @Test
    void healthShouldBeDownWhenNoRabbitListenersAreRegistered() {
        when(rabbitListenerEndpointRegistry.getListenerContainerIds()).thenReturn(Set.of());

        Health health = new RabbitListenersHealthIndicator(rabbitListenerEndpointRegistry).health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals(0, health.getDetails().get("containerCount"));
    }
}
