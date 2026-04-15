package io.terapeak.janitor.spring.scheduler;

import io.terapeak.janitor.config.CleanupConfig;
import io.terapeak.janitor.executor.CleanupExecutor;
import io.terapeak.janitor.registry.CleanupRegistry;
import io.terapeak.janitor.spring.properties.CleanupProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityManager;
import java.util.List;

/**
 * Registers one dynamic cron task per discovered {@link CleanupConfig} using
 * Spring's {@link TaskScheduler}.
 *
 * <p>By using {@code TaskScheduler} and {@link CronTrigger} rather than
 * {@code @Scheduled}, we support fully dynamic cron expressions resolved at
 * runtime from annotation values — no hard-coding required.
 *
 * <p>Each job runs inside a dedicated {@link TransactionTemplate} so that batches
 * are committed independently and a failure in one batch does not roll back
 * previously committed work.
 */
public class CleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);

    private final CleanupRegistry            registry;
    private final CleanupExecutor            executor;
    private final CleanupProperties          properties;
    private final TaskScheduler              taskScheduler;
    private final EntityManagerFactory       emf;
    private final PlatformTransactionManager txManager;

    public CleanupScheduler(CleanupRegistry registry,
                            CleanupExecutor executor,
                            CleanupProperties properties,
                            TaskScheduler taskScheduler,
                            EntityManagerFactory emf,
                            PlatformTransactionManager txManager) {
        this.registry      = registry;
        this.executor      = executor;
        this.properties    = properties;
        this.taskScheduler = taskScheduler;
        this.emf           = emf;
        this.txManager     = txManager;
    }

    @PostConstruct
    public void registerJobs() {
        if (!properties.isEnabled()) {
            log.info("[Cleanup] Master switch is OFF — no cleanup jobs will be registered.");
            return;
        }

        List<CleanupConfig> configs = registry.discoverAll();
        if (configs.isEmpty()) {
            log.info("[Cleanup] No cleanup jobs discovered.");
            return;
        }

        for (CleanupConfig config : configs) {
            if (!config.isEnabled()) {
                log.info("[Cleanup] Job '{}' is disabled, skipping registration.", config.getJobId());
                continue;
            }

            CleanupConfig resolved = applyGlobalDefaults(config);
            CronTrigger   trigger  = new CronTrigger(resolved.getCron());

            taskScheduler.schedule(() -> runJob(resolved), trigger);

            log.info("[Cleanup] Registered job '{}' with cron '{}'.",
                    resolved.getJobId(), resolved.getCron());
        }
    }

    private void runJob(CleanupConfig config) {
        log.debug("[Cleanup] Triggering job '{}'.", config.getJobId());

        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        tx.execute(status -> {
            EntityManager em = emf.createEntityManager();
            try {
                executor.execute(config, em);
                return null;
            } finally {
                if (em.isOpen()) em.close();
            }
        });
    }

    private CleanupConfig applyGlobalDefaults(CleanupConfig config) {
        if (properties.getDefaultBatchSize() > 0 && config.getBatchSize() == 0) {
            return CleanupConfig.builder()
                    .entity(config.getEntity())
                    .entityName(config.getEntityName())
                    .field(config.getField())
                    .retentionDays(config.getRetentionDays())
                    .cron(config.getCron())
                    .enabled(config.isEnabled())
                    .batchSize(properties.getDefaultBatchSize())
                    .softDelete(config.isSoftDelete())
                    .skipSoftDeleted(config.isSkipSoftDeleted())
                    .build();
        }
        return config;
    }
}
