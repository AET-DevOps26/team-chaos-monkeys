package com.foundflow.pickup.config;

import com.foundflow.events.DomainEventMessageConverterFactory;
import com.foundflow.events.FoundFlowEventRouting;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class AmqpConfig {

    @Bean
    public TopicExchange domainEventsExchange() {
        return new TopicExchange(FoundFlowEventRouting.EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(FoundFlowEventRouting.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue matchDeletedQueue() {
        return QueueBuilder.durable(FoundFlowEventRouting.PICKUP_MATCH_DELETED_QUEUE)
                .deadLetterExchange(FoundFlowEventRouting.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(
                        FoundFlowEventRouting.deadLetterRoutingKey(FoundFlowEventRouting.PICKUP_MATCH_DELETED_QUEUE))
                .build();
    }

    @Bean
    public Queue matchDeletedDlq() {
        return QueueBuilder.durable(
                FoundFlowEventRouting.deadLetterQueue(FoundFlowEventRouting.PICKUP_MATCH_DELETED_QUEUE)).build();
    }

    @Bean
    public Binding matchDeletedBinding(Queue matchDeletedQueue, TopicExchange domainEventsExchange) {
        return BindingBuilder.bind(matchDeletedQueue)
                .to(domainEventsExchange)
                .with(FoundFlowEventRouting.MATCH_DELETED);
    }

    @Bean
    public Binding matchDeletedDlqBinding(Queue matchDeletedDlq, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(matchDeletedDlq)
                .to(deadLetterExchange)
                .with(FoundFlowEventRouting.deadLetterRoutingKey(FoundFlowEventRouting.PICKUP_MATCH_DELETED_QUEUE));
    }

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return DomainEventMessageConverterFactory.create(jsonMapper);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        return factory;
    }
}
