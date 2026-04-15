package io.terapeak.janitor.quarkus.scheduler;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduler;
import io.terapeak.janitor.config.CleanupConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Registers Quarkus {@link Scheduler} jobs programmatically at application startup,
 * one per enabled {@link CleanupConfig}.
 *
 * <p>Quarkus's programmatic scheduling API (introduced in Quarkus 3.x) allows us to
 * supply cron expressions resolved at runtime from the annotation index, which means
 * we don't need compile-time {@code @Scheduled} annotations with hardcoded crons.
 *
 * <p>Each job is identified by its {@link CleanupConfig#getJobId()} and triggers
 * {@link CleanupJobRunner#runById(String)}.
 */
@ApplicationScoped
public class CleanupQuartzRegistrar {

    private static final Logger log = LoggerFactory.getLogger(CleanupQuartzRegistrar.class);

    @Inject
    Scheduler scheduler;

    @Inject
    CleanupJobRunner runner;

    void onStart(@Observes StartupEvent event) {
        List<CleanupConfig> configs = runner.getEnabledConfigs();

        if (configs.isEmpty()) {
            log.info("[Cleanup/Quarkus] No enabled cleanup jobs to register.");
            return;
        }

        for (CleanupConfig config : configs) {
            String jobId = config.getJobId();
            String cron  = config.getCron();

            scheduler.newJob(jobId)
                    .setCron(cron)
                    .setTask(ex -> runner.runById(jobId))
                    .schedule();

            log.info("[Cleanup/Quarkus] Registered job '{}' with cron '{}'.", jobId, cron);
        }
    }
}
