package com.foundflow.founditem.config;

import com.foundflow.events.DomainEventMessageConverterFactory;
import com.foundflow.events.FoundFlowEventRouting;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.MessageConverter;
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
    public Queue reservationRequestsQueue() {
        return consumerQueue(FoundFlowEventRouting.FOUND_ITEM_RESERVATION_REQUESTS_QUEUE);
    }

    @Bean
    public Queue reservationRequestsDlq() {
        return deadLetterQueue(FoundFlowEventRouting.FOUND_ITEM_RESERVATION_REQUESTS_QUEUE);
    }

    @Bean
    public Binding reservationRequestsBinding(
            Queue reservationRequestsQueue,
            TopicExchange domainEventsExchange
    ) {
        return BindingBuilder.bind(reservationRequestsQueue)
                .to(domainEventsExchange)
                .with(FoundFlowEventRouting.FOUND_ITEM_RESERVATION_REQUESTED);
    }

    @Bean
    public Binding reservationRequestsDlqBinding(TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(reservationRequestsDlq())
                .to(deadLetterExchange)
                .with(FoundFlowEventRouting.deadLetterRoutingKey(
                        FoundFlowEventRouting.FOUND_ITEM_RESERVATION_REQUESTS_QUEUE));
    }

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return DomainEventMessageConverterFactory.create(jsonMapper);
    }

    private Queue consumerQueue(String queueName) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(FoundFlowEventRouting.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(FoundFlowEventRouting.deadLetterRoutingKey(queueName))
                .build();
    }

    private Queue deadLetterQueue(String queueName) {
        return QueueBuilder.durable(FoundFlowEventRouting.deadLetterQueue(queueName)).build();
    }
}
