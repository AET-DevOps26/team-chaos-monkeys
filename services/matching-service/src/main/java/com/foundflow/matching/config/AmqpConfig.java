package com.foundflow.matching.config;

import com.foundflow.events.DomainEventMessageConverterFactory;
import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.matching.service.EmbeddingDimensionMismatchException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler.DefaultExceptionStrategy;
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
    public Queue lostReportCreatedQueue() {
        return consumerQueue(FoundFlowEventRouting.MATCHING_LOST_REPORTS_QUEUE);
    }

    @Bean
    public Queue lostReportUpdatedQueue() {
        return consumerQueue(FoundFlowEventRouting.MATCHING_LOST_REPORT_UPDATES_QUEUE);
    }

    @Bean
    public Queue foundItemCreatedQueue() {
        return consumerQueue(FoundFlowEventRouting.MATCHING_FOUND_ITEMS_QUEUE);
    }

    @Bean
    public Queue foundItemUpdatedQueue() {
        return consumerQueue(FoundFlowEventRouting.MATCHING_FOUND_ITEM_UPDATES_QUEUE);
    }

    @Bean
    public Queue matchCandidateCreatedQueue() {
        return consumerQueue(FoundFlowEventRouting.MATCHING_MATCH_CANDIDATES_QUEUE);
    }

    @Bean
    public Queue lostReportCreatedDlq() {
        return deadLetterQueue(FoundFlowEventRouting.MATCHING_LOST_REPORTS_QUEUE);
    }

    @Bean
    public Queue lostReportUpdatedDlq() {
        return deadLetterQueue(FoundFlowEventRouting.MATCHING_LOST_REPORT_UPDATES_QUEUE);
    }

    @Bean
    public Queue foundItemCreatedDlq() {
        return deadLetterQueue(FoundFlowEventRouting.MATCHING_FOUND_ITEMS_QUEUE);
    }

    @Bean
    public Queue foundItemUpdatedDlq() {
        return deadLetterQueue(FoundFlowEventRouting.MATCHING_FOUND_ITEM_UPDATES_QUEUE);
    }

    @Bean
    public Queue matchCandidateCreatedDlq() {
        return deadLetterQueue(FoundFlowEventRouting.MATCHING_MATCH_CANDIDATES_QUEUE);
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
    public Binding foundItemCreatedBinding(
            Queue foundItemCreatedQueue,
            TopicExchange domainEventsExchange
    ) {
        return BindingBuilder.bind(foundItemCreatedQueue)
                .to(domainEventsExchange)
                .with(FoundFlowEventRouting.FOUND_ITEM_CREATED);
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
    public Binding matchCandidateCreatedBinding(
            Queue matchCandidateCreatedQueue,
            TopicExchange domainEventsExchange
    ) {
        return BindingBuilder.bind(matchCandidateCreatedQueue)
                .to(domainEventsExchange)
                .with(FoundFlowEventRouting.MATCH_CANDIDATE_CREATED);
    }

    @Bean
    public Binding lostReportCreatedDlqBinding(TopicExchange deadLetterExchange) {
        return deadLetterBinding(lostReportCreatedDlq(), deadLetterExchange,
                FoundFlowEventRouting.MATCHING_LOST_REPORTS_QUEUE);
    }

    @Bean
    public Binding lostReportUpdatedDlqBinding(TopicExchange deadLetterExchange) {
        return deadLetterBinding(lostReportUpdatedDlq(), deadLetterExchange,
                FoundFlowEventRouting.MATCHING_LOST_REPORT_UPDATES_QUEUE);
    }

    @Bean
    public Binding foundItemCreatedDlqBinding(TopicExchange deadLetterExchange) {
        return deadLetterBinding(foundItemCreatedDlq(), deadLetterExchange,
                FoundFlowEventRouting.MATCHING_FOUND_ITEMS_QUEUE);
    }

    @Bean
    public Binding foundItemUpdatedDlqBinding(TopicExchange deadLetterExchange) {
        return deadLetterBinding(foundItemUpdatedDlq(), deadLetterExchange,
                FoundFlowEventRouting.MATCHING_FOUND_ITEM_UPDATES_QUEUE);
    }

    @Bean
    public Binding matchCandidateCreatedDlqBinding(TopicExchange deadLetterExchange) {
        return deadLetterBinding(matchCandidateCreatedDlq(), deadLetterExchange,
                FoundFlowEventRouting.MATCHING_MATCH_CANDIDATES_QUEUE);
    }

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return DomainEventMessageConverterFactory.create(jsonMapper);
    }

    @Bean
    public ConditionalRejectingErrorHandler rabbitListenerErrorHandler() {
        return new ConditionalRejectingErrorHandler(new DefaultExceptionStrategy() {
            @Override
            public boolean isFatal(Throwable t) {
                Throwable cur = t;
                while (cur != null) {
                    if (cur instanceof EmbeddingDimensionMismatchException) {
                        return true;
                    }
                    cur = cur.getCause();
                }
                return super.isFatal(t);
            }
        });
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setErrorHandler(rabbitListenerErrorHandler());
        return factory;
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

    private Binding deadLetterBinding(Queue queue, TopicExchange deadLetterExchange, String sourceQueueName) {
        return BindingBuilder.bind(queue)
                .to(deadLetterExchange)
                .with(FoundFlowEventRouting.deadLetterRoutingKey(sourceQueueName));
    }
}
