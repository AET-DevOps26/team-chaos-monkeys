package com.foundflow.notification.config;

import com.foundflow.events.FoundFlowEventRouting;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mail.MailSendException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationSendRetryInterceptorTest {

    @Test
    void retryInterceptor_retriesUpTo3Times_thenInvokesRecovererAndIncrementsCounterTaggedMatchInvite() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AmqpConfig config = new AmqpConfig();
        MessageRecoverer recoverer = config.notificationSendFailureRecoverer(meterRegistry);
        MethodInterceptor interceptor = config.notificationSendRetryInterceptor(recoverer);

        AtomicInteger calls = new AtomicInteger();
        AlwaysFailingTarget target = new AlwaysFailingTarget(calls);
        FailingMessageHandler proxy = proxyOf(target, interceptor);

        Message message = messageWithRoutingKey(FoundFlowEventRouting.MATCH_INVITE_REQUESTED);

        // The container-facing exception type is "AmqpRejectAndDontRequeueException"
        // or "ListenerExecutionFailedException(\"Retry Policy Exhausted\")"
        // depending on Spring AMQP version. We just assert the retry path ran
        // by verifying the side-effects: attempts + counter increment.
        assertThatThrownBy(() -> proxy.handleMessage(null, message)).isInstanceOf(Throwable.class);

        assertThat(calls.get())
                .as("retry interceptor should invoke the target maxRetries times before recovering")
                .isEqualTo(3);
        assertThat(counterCount(meterRegistry, "match-invite-requested"))
                .as("counter increments once per terminal drop, tagged by event_type")
                .isEqualTo(1.0);
    }

    @Test
    void retryInterceptor_dropsForPickupRoutingKey_incrementsPickupTaggedCounter() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AmqpConfig config = new AmqpConfig();
        MessageRecoverer recoverer = config.notificationSendFailureRecoverer(meterRegistry);
        MethodInterceptor interceptor = config.notificationSendRetryInterceptor(recoverer);

        FailingMessageHandler proxy = proxyOf(new AlwaysFailingTarget(new AtomicInteger()), interceptor);
        Message message = messageWithRoutingKey(FoundFlowEventRouting.PICKUP_CONFIRMATION_REQUESTED);

        assertThatThrownBy(() -> proxy.handleMessage(null, message)).isInstanceOf(Throwable.class);

        assertThat(counterCount(meterRegistry, "pickup-confirmation-requested")).isEqualTo(1.0);
        assertThatNoCounter(meterRegistry, "match-invite-requested");
    }

    @Test
    void retryInterceptor_unknownRoutingKey_incrementsUnknownTaggedCounter() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AmqpConfig config = new AmqpConfig();
        MessageRecoverer recoverer = config.notificationSendFailureRecoverer(meterRegistry);
        MethodInterceptor interceptor = config.notificationSendRetryInterceptor(recoverer);

        FailingMessageHandler proxy = proxyOf(new AlwaysFailingTarget(new AtomicInteger()), interceptor);
        Message message = messageWithRoutingKey("something.unexpected.v1");

        assertThatThrownBy(() -> proxy.handleMessage(null, message)).isInstanceOf(Throwable.class);

        assertThat(counterCount(meterRegistry, "unknown")).isEqualTo(1.0);
    }

    @Test
    void retryInterceptor_doesNotInvokeRecovererWhenTargetSucceedsOnRetry() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AmqpConfig config = new AmqpConfig();
        MessageRecoverer recoverer = config.notificationSendFailureRecoverer(meterRegistry);
        MethodInterceptor interceptor = config.notificationSendRetryInterceptor(recoverer);

        AtomicInteger calls = new AtomicInteger();
        FailsOnceThenSucceedsTarget target = new FailsOnceThenSucceedsTarget(calls);
        FailingMessageHandler proxy = proxyOf(target, interceptor);

        Message message = messageWithRoutingKey(FoundFlowEventRouting.MATCH_INVITE_REQUESTED);

        proxy.handleMessage(null, message);

        assertThat(calls.get()).isEqualTo(2);
        assertThatNoCounter(meterRegistry, "match-invite-requested");
    }

    private static FailingMessageHandler proxyOf(FailingMessageHandler target, MethodInterceptor interceptor) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(interceptor);
        return (FailingMessageHandler) factory.getProxy();
    }

    private static Message messageWithRoutingKey(String routingKey) {
        Message message = new Message("{}".getBytes(), new MessageProperties());
        message.getMessageProperties().setReceivedRoutingKey(routingKey);
        return message;
    }

    private static double counterCount(SimpleMeterRegistry meterRegistry, String eventType) {
        Counter counter = meterRegistry.get(AmqpConfig.SEND_FAILURES_COUNTER)
                .tag("event_type", eventType)
                .counter();
        return counter.count();
    }

    private static void assertThatNoCounter(SimpleMeterRegistry meterRegistry, String eventType) {
        assertThatThrownBy(() -> meterRegistry.get(AmqpConfig.SEND_FAILURES_COUNTER)
                .tag("event_type", eventType)
                .counter()).isInstanceOf(MeterNotFoundException.class);
    }

    // Mirrors the Spring AMQP listener container's call shape: the retry
    // interceptor's recoverer reads args[1] as the Message, so the proxied
    // method needs (channel, message) — Channel is unused by us, hence Object.
    interface FailingMessageHandler {
        void handleMessage(Object channel, Message message);
    }

    private static final class AlwaysFailingTarget implements FailingMessageHandler {
        private final AtomicInteger calls;

        AlwaysFailingTarget(AtomicInteger calls) {
            this.calls = calls;
        }

        @Override
        public void handleMessage(Object channel, Message message) {
            calls.incrementAndGet();
            throw new MailSendException("smtp unavailable");
        }
    }

    private static final class FailsOnceThenSucceedsTarget implements FailingMessageHandler {
        private final AtomicInteger calls;

        FailsOnceThenSucceedsTarget(AtomicInteger calls) {
            this.calls = calls;
        }

        @Override
        public void handleMessage(Object channel, Message message) {
            int attempt = calls.incrementAndGet();
            if (attempt < 2) {
                throw new MailSendException("transient");
            }
        }
    }
}
