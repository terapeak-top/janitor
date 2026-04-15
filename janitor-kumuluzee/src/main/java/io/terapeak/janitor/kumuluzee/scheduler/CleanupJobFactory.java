package io.terapeak.janitor.kumuluzee.scheduler;

import io.terapeak.janitor.config.CleanupConfig;
import io.terapeak.janitor.executor.CleanupExecutor;
import io.terapeak.janitor.registry.CleanupRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import java.util.List;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import javax.transaction.UserTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KumuluzEE application-scoped bean that discovers all {@link CleanupConfig}
 * instances and registers a {@link CleanupCronJob} for each one using the
 * {@code kumuluzee-cron} extension's {@link com.kumuluz.ee.cron.utils.CronUtils}.
 *
 * <p>Initialization is triggered when the {@code ApplicationScoped} context
 * starts, via {@code @Observes @Initialized(ApplicationScoped.class)}.
 */
@ApplicationScoped
public class CleanupJobFactory {

    private static final Logger log = LoggerFactory.getLogger(CleanupJobFactory.class);

    /**
     * Use PersistenceUnit (EMF) rather than @PersistenceContext (EM) so we can
     * create short-lived EntityManagers per job execution, keeping transaction
     * boundaries clean across cron invocations.
     */
    @PersistenceUnit
    private EntityManagerFactory emf;

    @Inject
    private UserTransaction userTransaction;

    private final CleanupExecutor  executor = CleanupExecutor.getInstance();
    private       List<CleanupConfig> configs;

    @PostConstruct
    void init() {
        CleanupRegistry registry = new CleanupRegistry(
                Thread.currentThread().getContextClassLoader());
        configs = registry.discoverAll();
    }

    /**
     * Called when the CDI ApplicationScoped context is initialized — the earliest
     * point at which all CDI beans and resources (JPA, JTA) are ready.
     */
    void onApplicationStart(
            @Observes @Initialized(ApplicationScoped.class) Object event) {

        if (configs == null || configs.isEmpty()) {
            log.info("[Cleanup/KumuluzEE] No cleanup jobs discovered.");
            return;
        }

        for (CleanupConfig config : configs) {
            if (!config.isEnabled()) {
                log.info("[Cleanup/KumuluzEE] Job '{}' is disabled, skipping.", config.getJobId());
                continue;
            }

            CleanupCronJob job = new CleanupCronJob(config, executor, emf, userTransaction);
            //com.kumuluz.ee..utils.CronUtils.schedule(config.getCron(), job);

            log.info("[Cleanup/KumuluzEE] Registered job '{}' with cron '{}'.",
                    config.getJobId(), config.getCron());
        }
    }
}
