package com.secondshelf.notificationservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
@EnableConfigurationProperties(NotificationRabbitProperties.class)
public class RabbitMqConfig {

    @Bean
    public TopicExchange exchangeEventsExchange(NotificationRabbitProperties properties) {
        return new TopicExchange(properties.getExchange(), true, false);
    }

    @Bean
    public Queue exchangeEventNotificationsQueue(NotificationRabbitProperties properties) {
        return QueueBuilder.durable(properties.getQueue())
                .deadLetterExchange(properties.resolveDeadLetterExchange())
                .deadLetterRoutingKey(properties.resolveDeadLetterRoutingKey())
                .build();
    }

    @Bean
    public DirectExchange exchangeEventsDeadLetterExchange(NotificationRabbitProperties properties) {
        return new DirectExchange(properties.resolveDeadLetterExchange(), true, false);
    }

    @Bean
    public Queue exchangeEventNotificationsDeadLetterQueue(NotificationRabbitProperties properties) {
        return QueueBuilder.durable(properties.resolveDeadLetterQueue()).build();
    }

    @Bean
    public Binding exchangeEventNotificationsBinding(
                                                     @Qualifier("exchangeEventNotificationsQueue")
                                                     Queue exchangeEventNotificationsQueue,
                                                     TopicExchange exchangeEventsExchange,
                                                     NotificationRabbitProperties properties) {
        return BindingBuilder.bind(exchangeEventNotificationsQueue)
                .to(exchangeEventsExchange)
                .with(properties.getRoutingKeyPattern());
    }

    @Bean
    public Binding exchangeEventNotificationsDeadLetterBinding(
                                                               @Qualifier("exchangeEventNotificationsDeadLetterQueue")
                                                               Queue exchangeEventNotificationsDeadLetterQueue,
                                                               @Qualifier("exchangeEventsDeadLetterExchange")
                                                               DirectExchange exchangeEventsDeadLetterExchange,
                                                               NotificationRabbitProperties properties) {
        return BindingBuilder.bind(exchangeEventNotificationsDeadLetterQueue)
                .to(exchangeEventsDeadLetterExchange)
                .with(properties.resolveDeadLetterRoutingKey());
    }

    @Bean
    public Declarables exchangeEventTopology(
                                             TopicExchange exchangeEventsExchange,
                                             @Qualifier("exchangeEventNotificationsQueue")
                                             Queue exchangeEventNotificationsQueue,
                                             @Qualifier("exchangeEventNotificationsBinding")
                                             Binding exchangeEventNotificationsBinding,
                                             @Qualifier("exchangeEventsDeadLetterExchange")
                                             DirectExchange exchangeEventsDeadLetterExchange,
                                             @Qualifier("exchangeEventNotificationsDeadLetterQueue")
                                             Queue exchangeEventNotificationsDeadLetterQueue,
                                             @Qualifier("exchangeEventNotificationsDeadLetterBinding")
                                             Binding exchangeEventNotificationsDeadLetterBinding) {
        return new Declarables(
                exchangeEventsExchange,
                exchangeEventNotificationsQueue,
                exchangeEventNotificationsBinding,
                exchangeEventsDeadLetterExchange,
                exchangeEventNotificationsDeadLetterQueue,
                exchangeEventNotificationsDeadLetterBinding
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

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter rabbitMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(rabbitMessageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
