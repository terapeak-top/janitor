package io.terapeak.janitor.spring.auto;

import io.terapeak.janitor.executor.CleanupExecutor;
import io.terapeak.janitor.registry.CleanupRegistry;
import io.terapeak.janitor.spring.properties.CleanupProperties;
import io.terapeak.janitor.spring.scheduler.CleanupScheduler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;

import jakarta.persistence.EntityManagerFactory;

/**
 * Spring Boot auto-configuration for entity cleanup.
 *
 * <p>Activated automatically via {@code META-INF/spring/org.springframework.boot
 * .autoconfigure.AutoConfiguration.imports}.
 *
 * <p>All beans are annotated with {@link ConditionalOnMissingBean} so host
 * applications can override any part of the infrastructure.
 *
 * <p>The entire auto-configuration can be disabled with:
 * <pre>{@code cleanup.enabled=false}</pre>
 */
@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(CleanupProperties.class)
@ConditionalOnProperty(prefix = "cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CleanupAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CleanupRegistry cleanupRegistry() {
        return new CleanupRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public CleanupExecutor cleanupExecutor() {
        return new CleanupExecutor();
    }

    @Bean
    @ConditionalOnMissingBean(name = "cleanupTaskScheduler")
    public TaskScheduler cleanupTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("janitor-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    @ConditionalOnMissingBean
    public CleanupScheduler cleanupScheduler(CleanupRegistry registry,
                                             CleanupExecutor executor,
                                             CleanupProperties properties,
                                             TaskScheduler cleanupTaskScheduler,
                                             EntityManagerFactory entityManagerFactory,
                                             PlatformTransactionManager transactionManager) {
        return new CleanupScheduler(
                registry, executor, properties,
                cleanupTaskScheduler, entityManagerFactory, transactionManager);
    }
}
