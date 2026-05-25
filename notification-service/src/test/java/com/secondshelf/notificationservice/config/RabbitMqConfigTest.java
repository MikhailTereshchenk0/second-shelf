package com.secondshelf.notificationservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
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
        DirectExchange deadLetterExchange = rabbitMqConfig.exchangeEventsDeadLetterExchange(properties);
        Queue deadLetterQueue = rabbitMqConfig.exchangeEventNotificationsDeadLetterQueue(properties);
        Binding binding = rabbitMqConfig.exchangeEventNotificationsBinding(queue, exchange, properties);
        Binding deadLetterBinding = rabbitMqConfig.exchangeEventNotificationsDeadLetterBinding(
                deadLetterQueue,
                deadLetterExchange,
                properties
        );
        Declarables declarables = rabbitMqConfig.exchangeEventTopology(
                exchange,
                queue,
                binding,
                deadLetterExchange,
                deadLetterQueue,
                deadLetterBinding
        );

        assertEquals("exchange.events", exchange.getName());
        assertTrue(exchange.isDurable());
        assertEquals("notification.exchange-events", queue.getName());
        assertTrue(queue.isDurable());
        assertEquals("notification.exchange-events.dlx", queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals("notification.exchange-events.dlq", queue.getArguments().get("x-dead-letter-routing-key"));
        assertEquals("notification.exchange-events", binding.getDestination());
        assertEquals("exchange.events", binding.getExchange());
        assertEquals("exchange.request.*", binding.getRoutingKey());
        assertEquals("notification.exchange-events.dlx", deadLetterExchange.getName());
        assertEquals("notification.exchange-events.dlq", deadLetterQueue.getName());
        assertEquals("notification.exchange-events.dlq", deadLetterBinding.getDestination());
        assertEquals("notification.exchange-events.dlx", deadLetterBinding.getExchange());
        assertEquals("notification.exchange-events.dlq", deadLetterBinding.getRoutingKey());
        assertEquals(6, declarables.getDeclarables().size());
    }

    @Test
    void rabbitAdminShouldAutoDeclareTopologyOnStartup() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory("localhost");

        RabbitAdmin rabbitAdmin = rabbitMqConfig.rabbitAdmin(connectionFactory);

        assertTrue(rabbitAdmin.isAutoStartup());
        connectionFactory.destroy();
    }
}
