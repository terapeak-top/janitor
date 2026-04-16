package io.terapeak.janitor.kumuluzee.scheduler;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import io.terapeak.janitor.config.CleanupConfig;
import io.terapeak.janitor.registry.CleanupRegistry;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.context.Initialized;
import javax.enterprise.event.Observes;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KumuluzEE application-scoped bean that discovers all {@link CleanupConfig} instances and registers a {@link CleanupCronJob} for each one
 * using the {@code kumuluzee-cron} extension's {@link com.kumuluz.ee.cron.utils.CronUtils}.
 *
 * <p>Initialization is triggered when the {@code ApplicationScoped} context
 * starts, via {@code @Observes @Initialized(ApplicationScoped.class)}.
 */
@ApplicationScoped
public class CleanupJobFactory {

    private static final Logger log = LoggerFactory.getLogger(CleanupJobFactory.class);

    private final KumuluzeeCleanupExecutor executor = KumuluzeeCleanupExecutor.getInstance();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * Use PersistenceUnit (EMF) rather than @PersistenceContext (EM) so we can create short-lived EntityManagers per job execution, keeping
     * transaction boundaries clean across cron invocations.
     */
    @PersistenceUnit
    private EntityManagerFactory emf;

    /**
     * Called when the CDI ApplicationScoped context is initialized — the earliest point at which all CDI beans and resources (JPA, JTA) are
     * ready.
     */
    void onApplicationStart(@Observes @Initialized(ApplicationScoped.class) Object event) {
        CleanupRegistry registry = new CleanupRegistry(
            Thread.currentThread().getContextClassLoader());

        List<CleanupConfig> configs = registry.discoverAll();
        if (configs == null || configs.isEmpty()) {
            log.info("[Cleanup/KumuluzEE] No cleanup jobs discovered.");
            return;
        }

        for (CleanupConfig config : configs) {
            if (!config.isEnabled()) {
                log.info("[Cleanup/KumuluzEE] Job '{}' is disabled, skipping.", config.getJobId());
                continue;
            }
            CleanupCronJob job = new CleanupCronJob(config, executor, emf);
            schedule(config.getCron(), job);
            log.info("[Cleanup/KumuluzEE] Registered job '{}' with cron '{}'.",
                config.getJobId(), config.getCron());
        }
    }

    private void schedule(String cronExpr, Runnable task) {
        CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ));
        Cron cron = parser.parse(cronExpr);
        cron.validate();
        scheduleNext(cron, task);
    }

    private void scheduleNext(Cron cron, Runnable task) {
        ExecutionTime executionTime = ExecutionTime.forCron(cron);

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime next = executionTime.nextExecution(now).orElseThrow();

        long delay = java.time.Duration.between(now, next).toMillis();

        scheduler.schedule(() -> {
            try {
                task.run();
            } finally {
                // reschedule again (true cron behavior)
                scheduleNext(cron, task);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }
}
