package com.secondshelf.notificationservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitMqConfigTest {

    private final RabbitMqConfig rabbitMqConfig = new RabbitMqConfig();

    @Test
    void exchangeTopologyShouldDeclareExchangeQueueAndBinding() {
        NotificationRabbitProperties properties = new NotificationRabbitProperties();
        properties.setExchange("exchange.events");
        properties.setQueue("notification.exchange-events");
        properties.setRoutingKeyPattern("exchange.request.*");

        TopicExchange exchange = rabbitMqConfig.exchangeEventsExchange(properties);
        Queue queue = rabbitMqConfig.exchangeEventNotificationsQueue(properties);
        Binding binding = rabbitMqConfig.exchangeEventNotificationsBinding(queue, exchange, properties);
        Declarables declarables = rabbitMqConfig.exchangeEventTopology(exchange, queue, binding);

        assertEquals("exchange.events", exchange.getName());
        assertTrue(exchange.isDurable());
        assertEquals("notification.exchange-events", queue.getName());
        assertTrue(queue.isDurable());
        assertEquals("notification.exchange-events", binding.getDestination());
        assertEquals("exchange.events", binding.getExchange());
        assertEquals("exchange.request.*", binding.getRoutingKey());
        assertEquals(3, declarables.getDeclarables().size());
    }

    @Test
    void rabbitAdminShouldAutoDeclareTopologyOnStartup() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory("localhost");

        RabbitAdmin rabbitAdmin = rabbitMqConfig.rabbitAdmin(connectionFactory);

        assertTrue(rabbitAdmin.isAutoStartup());
        connectionFactory.destroy();
    }
}
