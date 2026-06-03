package com.foundflow.notification.config;

import io.micrometer.core.instrument.Counter;
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
    void retryInterceptor_retriesUpTo3Times_thenInvokesRecovererAndIncrementsCounter() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AmqpConfig config = new AmqpConfig();
        Counter counter = config.notificationsSendFailuresCounter(meterRegistry);
        MessageRecoverer recoverer = config.notificationSendFailureRecoverer(counter);
        MethodInterceptor interceptor = config.notificationSendRetryInterceptor(recoverer);

        AtomicInteger calls = new AtomicInteger();
        AlwaysFailingTarget target = new AlwaysFailingTarget(calls);
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(interceptor);
        FailingMessageHandler proxy = (FailingMessageHandler) factory.getProxy();

        Message message = new Message(
                "{}".getBytes(),
                new MessageProperties()
        );
        message.getMessageProperties().setReceivedRoutingKey("notification.match-invite-requested.v1");

        // The container-facing exception type is "AmqpRejectAndDontRequeueException"
        // or "ListenerExecutionFailedException(\"Retry Policy Exhausted\")"
        // depending on Spring AMQP version. We just assert the retry path ran
        // by verifying the side-effects: attempts + counter increment.
        assertThatThrownBy(() -> proxy.handleMessage(null, message)).isInstanceOf(Throwable.class);

        assertThat(calls.get())
                .as("retry interceptor should invoke the target maxRetries times before recovering")
                .isEqualTo(3);
        assertThat(counter.count())
                .as("notifications_send_failures_total increments once per terminal drop")
                .isEqualTo(1.0);
    }

    @Test
    void retryInterceptor_doesNotInvokeRecovererWhenTargetSucceedsOnRetry() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AmqpConfig config = new AmqpConfig();
        Counter counter = config.notificationsSendFailuresCounter(meterRegistry);
        MessageRecoverer recoverer = config.notificationSendFailureRecoverer(counter);
        MethodInterceptor interceptor = config.notificationSendRetryInterceptor(recoverer);

        AtomicInteger calls = new AtomicInteger();
        FailsOnceThenSucceedsTarget target = new FailsOnceThenSucceedsTarget(calls);
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(interceptor);
        FailingMessageHandler proxy = (FailingMessageHandler) factory.getProxy();

        Message message = new Message("{}".getBytes(), new MessageProperties());
        message.getMessageProperties().setReceivedRoutingKey("notification.match-invite-requested.v1");

        proxy.handleMessage(null, message);

        assertThat(calls.get()).isEqualTo(2);
        assertThat(counter.count())
                .as("recoverer should not run when retry succeeds")
                .isEqualTo(0.0);
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
