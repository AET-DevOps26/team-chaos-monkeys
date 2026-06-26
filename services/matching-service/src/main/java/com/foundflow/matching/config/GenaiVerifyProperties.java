package com.foundflow.matching.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "genai.verify")
public class GenaiVerifyProperties {

    private boolean enabled = true;

    // When verify-match returns a confident no_match verdict, transition the
    // candidate from PENDING to REJECTED so it stops surfacing as a match.
    // This is the enforcement of the verify-match backstop: candidate
    // generation is deliberately lenient (semantic score gate only), and
    // verify is what culls the plausible-but-wrong pairs.
    private boolean autoRejectOnNoMatch = true;
    private double autoRejectMinConfidence = 0.7;

    private Executor executor = new Executor();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isAutoRejectOnNoMatch() { return autoRejectOnNoMatch; }
    public void setAutoRejectOnNoMatch(boolean v) { this.autoRejectOnNoMatch = v; }

    public double getAutoRejectMinConfidence() { return autoRejectMinConfidence; }
    public void setAutoRejectMinConfidence(double v) { this.autoRejectMinConfidence = v; }

    public Executor getExecutor() { return executor; }
    public void setExecutor(Executor executor) { this.executor = executor; }

    public static class Executor {
        private int corePoolSize = 2;
        private int maxPoolSize = 8;
        private int queueCapacity = 200;

        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int v) { this.corePoolSize = v; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int v) { this.maxPoolSize = v; }
        public int getQueueCapacity() { return queueCapacity; }
        public void setQueueCapacity(int v) { this.queueCapacity = v; }
    }
}
