package io.terapeak.janitor.spring.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Global overrides for all cleanup jobs, configurable via {@code application.properties}
 * or {@code application.yml}.
 *
 * <pre>{@code
 * # application.properties
 * janitor.enabled=true
 * janitor.default-batch-size=500
 * }</pre>
 */
@ConfigurationProperties(prefix = "janitor")
public class JanitorProperties {

    /**
     * Master switch. When {@code false}, no cleanup jobs are registered,
     * regardless of individual job configuration.
     */
    private boolean enabled = true;

    /**
     * Override the default batch size for all jobs that have {@code batchSize=0}.
     * A value of {@code 0} means no override is applied.
     */
    private int defaultBatchSize = 0;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getDefaultBatchSize() { return defaultBatchSize; }
    public void setDefaultBatchSize(int defaultBatchSize) { this.defaultBatchSize = defaultBatchSize; }
}
