package com.secondshelf.notificationservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NotificationRabbitProperties.class)
public class RabbitMqConfig {

    @Bean
    public TopicExchange exchangeEventsTopicExchange(NotificationRabbitProperties properties) {
        return new TopicExchange(properties.getExchange(), true, false);
    }

    @Bean
    public Queue notificationExchangeEventsQueue(NotificationRabbitProperties properties) {
        return QueueBuilder.durable(properties.getQueue()).build();
    }

    @Bean
    public Binding notificationExchangeEventsBinding(Queue notificationExchangeEventsQueue,
                                                     TopicExchange exchangeEventsTopicExchange,
                                                     NotificationRabbitProperties properties) {
        return BindingBuilder.bind(notificationExchangeEventsQueue)
                .to(exchangeEventsTopicExchange)
                .with(properties.getRoutingKeyPattern());
    }

    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
