package com.foundflow.lostitem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bounded executor for off-request-path intake enrichment. A small pool plus a
 * bounded queue keep a submit burst from thread-bombing the pod under the tight
 * AET quota. CallerRunsPolicy applies backpressure (the enrichment falls back to
 * the caller thread) rather than dropping a report's created event when the
 * queue saturates — a slow intake beats a lost match.
 */
@Configuration
@EnableAsync
public class IntakeEnrichmentConfig {

    @Bean(name = "intakeEnrichmentExecutor")
    public ThreadPoolTaskExecutor intakeEnrichmentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("intake-enrich-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
