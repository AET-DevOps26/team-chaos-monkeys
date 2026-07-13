package com.foundflow.matching.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "genai.verify")
public class GenaiVerifyProperties {

    private boolean enabled = true;

    // When verify-match returns a confident no_match verdict, DELETE the candidate
    // (issue #374) so it never clutters the inbox — REJECTED is reserved for a human
    // rejection. This enforces the verify backstop: candidate generation is lenient
    // (category veto + semantic gate) and verify culls the plausible-but-wrong pairs.
    private boolean dropOnNoMatch = true;
    private double dropMinConfidence = 0.7;

    private Executor executor = new Executor();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isDropOnNoMatch() { return dropOnNoMatch; }
    public void setDropOnNoMatch(boolean v) { this.dropOnNoMatch = v; }

    public double getDropMinConfidence() { return dropMinConfidence; }
    public void setDropMinConfidence(double v) { this.dropMinConfidence = v; }

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
