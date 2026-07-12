package com.foundflow.lostitem.config;

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
    public Queue statusChangeQueue() {
        return consumerQueue(FoundFlowEventRouting.LOST_ITEM_STATUS_CHANGE_QUEUE);
    }

    @Bean
    public Queue statusChangeDlq() {
        return deadLetterQueue(FoundFlowEventRouting.LOST_ITEM_STATUS_CHANGE_QUEUE);
    }

    @Bean
    public Binding statusChangeBinding(
            Queue statusChangeQueue,
            TopicExchange domainEventsExchange
    ) {
        return BindingBuilder.bind(statusChangeQueue)
                .to(domainEventsExchange)
                .with(FoundFlowEventRouting.LOST_REPORT_STATUS_CHANGE_REQUESTED);
    }

    @Bean
    public Binding statusChangeDlqBinding(TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(statusChangeDlq())
                .to(deadLetterExchange)
                .with(FoundFlowEventRouting.deadLetterRoutingKey(
                        FoundFlowEventRouting.LOST_ITEM_STATUS_CHANGE_QUEUE));
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
