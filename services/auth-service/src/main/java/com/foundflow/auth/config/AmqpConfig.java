package com.foundflow.auth.config;

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
    public Queue venueDeletedQueue() {
        return new Queue(FoundFlowEventRouting.AUTH_VENUE_DELETED_QUEUE, true);
    }

    @Bean
    public Binding venueDeletedBinding(
            Queue venueDeletedQueue,
            TopicExchange domainEventsExchange
    ) {
        return BindingBuilder.bind(venueDeletedQueue)
                .to(domainEventsExchange)
                .with(FoundFlowEventRouting.VENUE_DELETED);
    }

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return DomainEventMessageConverterFactory.create(jsonMapper);
    }
}
