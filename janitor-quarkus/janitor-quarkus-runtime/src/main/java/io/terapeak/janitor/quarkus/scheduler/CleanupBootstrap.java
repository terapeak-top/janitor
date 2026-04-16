package io.terapeak.janitor.quarkus.scheduler;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduler;
import io.terapeak.janitor.config.CleanupConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers Quarkus {@link Scheduler} jobs programmatically at application startup, one per enabled {@link CleanupConfig}.
 *
 * <p>Quarkus's programmatic scheduling API (introduced in Quarkus 3.x) allows us to
 * supply cron expressions resolved at runtime from the annotation index, which means we don't need compile-time {@code @Scheduled}
 * annotations with hardcoded crons.
 *
 * <p>Each job is identified by its {@link CleanupConfig#getJobId()} and triggers
 * {@link CleanupJobRunner#runById(String)}.
 */
@ApplicationScoped
public class CleanupBootstrap {

    private static final Logger log = LoggerFactory.getLogger(CleanupBootstrap.class);

    @Inject
    ScheduledExecutorService executor;

    CronParser parser = new CronParser(CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ));

    @Inject
    CleanupJobRunner runner;

    void onStart(@Observes StartupEvent event) {
        CronParser cronParser = new CronParser(
            CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)
        );
        List<CleanupConfig> configs = runner.getEnabledConfigs();
        //log.info("[Cleanup/Quarkus] Is scheduler running " + scheduler.isStarted());

        if (configs.isEmpty()) {
            log.info("[Cleanup/Quarkus] No enabled cleanup jobs to register.");
            return;
        }

        for (CleanupConfig config : configs) {
            String jobId = config.getJobId();
            String cron = config.getCron();
            schedule(cron,() -> runner.runById(jobId));
            log.info("[Cleanup/Quarkus] Registered job '{}' with cron '{}'.", jobId, cron);
        }
    }
    public void schedule(String cronExpr, Runnable task) {
        Cron cron = parser.parse(cronExpr);
        cron.validate();
        scheduleNext(cron, task);
    }

    private void scheduleNext(Cron cron, Runnable task) {
        ExecutionTime executionTime = ExecutionTime.forCron(cron);

        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime next = executionTime.nextExecution(now).orElseThrow();

        long delay = java.time.Duration.between(now, next).toMillis();

        executor.schedule(() -> {
            try {
                task.run();
            } finally {
                // reschedule again (true cron behavior)
                scheduleNext(cron, task);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }


}
