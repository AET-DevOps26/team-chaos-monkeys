package com.foundflow.matching.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(GenaiVerifyProperties.class)
public class AsyncConfig {

    @Bean(name = "genaiVerifyExecutor")
    public Executor genaiVerifyExecutor(GenaiVerifyProperties props) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(props.getExecutor().getCorePoolSize());
        ex.setMaxPoolSize(props.getExecutor().getMaxPoolSize());
        ex.setQueueCapacity(props.getExecutor().getQueueCapacity());
        ex.setThreadNamePrefix("genai-verify-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        ex.initialize();
        return ex;
    }
}
