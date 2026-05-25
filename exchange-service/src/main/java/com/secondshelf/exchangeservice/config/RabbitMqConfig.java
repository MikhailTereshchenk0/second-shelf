package com.secondshelf.exchangeservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ExchangeRabbitProperties.class)
public class RabbitMqConfig {

    @Bean
    public TopicExchange exchangeEventsExchange(ExchangeRabbitProperties properties) {
        return new TopicExchange(properties.getExchange(), true, false);
    }

    @Bean
    public Queue exchangeEventNotificationsQueue(ExchangeRabbitProperties properties) {
        return QueueBuilder.durable(properties.getQueue()).build();
    }

    @Bean
    public Binding exchangeEventNotificationsBinding(Queue exchangeEventNotificationsQueue,
                                                     TopicExchange exchangeEventsExchange,
                                                     ExchangeRabbitProperties properties) {
        return BindingBuilder.bind(exchangeEventNotificationsQueue)
                .to(exchangeEventsExchange)
                .with(properties.getRoutingKeyPattern());
    }

    @Bean
    public Declarables exchangeEventTopology(TopicExchange exchangeEventsExchange,
                                             Queue exchangeEventNotificationsQueue,
                                             Binding exchangeEventNotificationsBinding) {
        return new Declarables(
                exchangeEventsExchange,
                exchangeEventNotificationsQueue,
                exchangeEventNotificationsBinding
        );
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitAdmin.setAutoStartup(true);
        return rabbitAdmin;
    }

    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
