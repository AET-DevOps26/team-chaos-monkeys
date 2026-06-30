package com.foundflow.notification.config;

import com.foundflow.events.DomainEventMessageConverterFactory;
import com.foundflow.events.FoundFlowEventRouting;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class AmqpConfig {

    public static final String SEND_FAILURES_COUNTER = "notifications_send_failures_total";

    private static final Logger log = LoggerFactory.getLogger(AmqpConfig.class);

    @Bean
    public TopicExchange domainEventsExchange() {
        return new TopicExchange(FoundFlowEventRouting.EXCHANGE, true, false);
    }

    @Bean
    public Queue matchInviteRequestedQueue() {
        return new Queue(FoundFlowEventRouting.NOTIFICATION_MATCH_INVITES_QUEUE, true);
    }

    @Bean
    public Queue pickupConfirmationRequestedQueue() {
        return new Queue(FoundFlowEventRouting.NOTIFICATION_PICKUP_CONFIRMATIONS_QUEUE, true);
    }

    @Bean
    public Queue passwordResetRequestedQueue() {
        return new Queue(FoundFlowEventRouting.NOTIFICATION_PASSWORD_RESETS_QUEUE, true);
    }

    @Bean
    public Queue lostReportConfirmationQueue() {
        return new Queue(FoundFlowEventRouting.NOTIFICATION_LOST_REPORT_CONFIRMATIONS_QUEUE, true);
    }

    @Bean
    public Binding matchInviteRequestedBinding(
            Queue matchInviteRequestedQueue,
            TopicExchange domainEventsExchange
    ) {
        return BindingBuilder.bind(matchInviteRequestedQueue)
                .to(domainEventsExchange)
                .with(FoundFlowEventRouting.MATCH_INVITE_REQUESTED);
    }

    @Bean
    public Binding pickupConfirmationRequestedBinding(
            Queue pickupConfirmationRequestedQueue,
            TopicExchange domainEventsExchange
    ) {
        return BindingBuilder.bind(pickupConfirmationRequestedQueue)
                .to(domainEventsExchange)
                .with(FoundFlowEventRouting.PICKUP_CONFIRMATION_REQUESTED);
    }

    @Bean
    public Binding passwordResetRequestedBinding(
            Queue passwordResetRequestedQueue,
            TopicExchange domainEventsExchange
    ) {
        return BindingBuilder.bind(passwordResetRequestedQueue)
                .to(domainEventsExchange)
                .with(FoundFlowEventRouting.PASSWORD_RESET_REQUESTED);
    }

    // Second subscriber on lost-report.created.v1 (matching-service binds its own
    // queue to the same key for intake). The guest gets a receipt for the report.
    @Bean
    public Binding lostReportConfirmationBinding(
            Queue lostReportConfirmationQueue,
            TopicExchange domainEventsExchange
    ) {
        return BindingBuilder.bind(lostReportConfirmationQueue)
                .to(domainEventsExchange)
                .with(FoundFlowEventRouting.LOST_REPORT_CREATED);
    }

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        return DomainEventMessageConverterFactory.create(jsonMapper);
    }

    @Bean
    public MessageRecoverer notificationSendFailureRecoverer(MeterRegistry meterRegistry) {
        RejectAndDontRequeueRecoverer reject = new RejectAndDontRequeueRecoverer();
        return (message, cause) -> {
            String routingKey = message.getMessageProperties().getReceivedRoutingKey();
            Counter.builder(SEND_FAILURES_COUNTER)
                    .description("Notification email sends that exhausted all retries and were dropped.")
                    .tag("event_type", eventTypeFor(routingKey))
                    .register(meterRegistry)
                    .increment();
            log.warn(
                    "Dropping notification message after exhausting retries: routingKey={}",
                    routingKey,
                    cause
            );
            reject.recover(message, cause);
        };
    }

    static String eventTypeFor(String routingKey) {
        if (routingKey == null) {
            return "unknown";
        }
        return switch (routingKey) {
            case FoundFlowEventRouting.MATCH_INVITE_REQUESTED -> "match-invite-requested";
            case FoundFlowEventRouting.PICKUP_CONFIRMATION_REQUESTED -> "pickup-confirmation-requested";
            case FoundFlowEventRouting.LOST_REPORT_CREATED -> "lost-report-confirmation";
            default -> "unknown";
        };
    }

    @Bean
    public MethodInterceptor notificationSendRetryInterceptor(MessageRecoverer notificationSendFailureRecoverer) {
        // maxRetries(2) = 1 initial attempt + 2 retries = 3 total attempts.
        // Verified by NotificationSendRetryInterceptorTest.
        return RetryInterceptorBuilder.stateless()
                .maxRetries(2)
                .backOffOptions(1000L, 2.0, 10_000L)
                .recoverer(notificationSendFailureRecoverer)
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            MethodInterceptor notificationSendRetryInterceptor
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        // Apply Boot's spring.rabbitmq.listener.* properties (incl. auto-startup,
        // which tests flip to false so the listener container doesn't try to
        // connect during context load).
        configurer.configure(factory, connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(notificationSendRetryInterceptor);
        return factory;
    }
}
