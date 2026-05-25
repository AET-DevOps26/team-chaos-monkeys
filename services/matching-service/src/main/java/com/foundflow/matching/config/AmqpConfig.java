package com.foundflow.matching.config;

import com.foundflow.events.DomainEventMessageConverterFactory;
import com.foundflow.events.FoundFlowEventRouting;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
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
    public Queue lostReportCreatedQueue() {
        return new Queue(FoundFlowEventRouting.MATCHING_LOST_REPORTS_QUEUE, true);
    }

    @Bean
    public Queue lostReportUpdatedQueue() {
        return new Queue(FoundFlowEventRouting.MATCHING_LOST_REPORT_UPDATES_QUEUE, true);
    }

    @Bean
    public Queue foundItemLoggedQueue() {
        return new Queue(FoundFlowEventRouting.MATCHING_FOUND_ITEMS_QUEUE, true);
    }

    @Bean
    public Queue foundItemUpdatedQueue() {
        return new Queue(FoundFlowEventRouting.MATCHING_FOUND_ITEM_UPDATES_QUEUE, true);
    }

    @Bean
    public Binding lostReportCreatedBinding(
            Queue lostReportCreatedQueue,
            TopicExchange domainEventsExchange
    ) {
        return BindingBuilder.bind(lostReportCreatedQueue)
                .to(domainEventsExchange)
                .with(FoundFlowEventRouting.LOST_REPORT_CREATED);
    }

    @Bean
    public Binding lostReportUpdatedBinding(
            Queue lostReportUpdatedQueue,
            TopicExchange domainEventsExchange
    ) {
        return BindingBuilder.bind(lostReportUpdatedQueue)
                .to(domainEventsExchange)
                .with(FoundFlowEventRouting.LOST_REPORT_UPDATED);
    }

    @Bean
    public Binding foundItemLoggedBinding(
            Queue foundItemLoggedQueue,
            TopicExchange domainEventsExchange
    ) {
        return BindingBuilder.bind(foundItemLoggedQueue)
                .to(domainEventsExchange)
                .with(FoundFlowEventRouting.FOUND_ITEM_LOGGED);
    }

    @Bean
    public Binding foundItemUpdatedBinding(
            Queue foundItemUpdatedQueue,
            TopicExchange domainEventsExchange
    ) {
        return BindingBuilder.bind(foundItemUpdatedQueue)
                .to(domainEventsExchange)
                .with(FoundFlowEventRouting.FOUND_ITEM_UPDATED);
    }

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return DomainEventMessageConverterFactory.create(jsonMapper);
    }
}
