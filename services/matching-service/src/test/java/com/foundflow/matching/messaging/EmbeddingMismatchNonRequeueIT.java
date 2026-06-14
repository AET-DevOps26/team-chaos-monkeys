package com.foundflow.matching.messaging;

import com.foundflow.matching.service.EmbeddingDimensionMismatchException;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingMismatchNonRequeueIT {

    @Test
    void embeddingMismatch_isClassifiedAsFatal_byErrorHandler() {
        ConditionalRejectingErrorHandler handler = newHandlerWithCustomFatalStrategy();

        Throwable wrapped = new ListenerExecutionFailedException(
                "wrapper",
                new EmbeddingDimensionMismatchException("got 1024 dims, expected 768"),
                new org.springframework.amqp.core.Message[0]);

        // ConditionalRejectingErrorHandler throws AmqpRejectAndDontRequeueException for fatal causes
        assertThatThrownBy(() -> handler.handleError(wrapped))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private ConditionalRejectingErrorHandler newHandlerWithCustomFatalStrategy() {
        // This creates the bean directly from AmqpConfig (no Spring context needed)
        return new com.foundflow.matching.config.AmqpConfig().rabbitListenerErrorHandler();
    }
}
