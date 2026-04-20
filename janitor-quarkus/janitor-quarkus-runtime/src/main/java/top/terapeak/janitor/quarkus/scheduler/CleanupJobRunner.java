package top.terapeak.janitor.quarkus.scheduler;

import top.terapeak.janitor.config.CleanupConfig;
import top.terapeak.janitor.executor.CleanupExecutor;
import top.terapeak.janitor.registry.CleanupRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Quarkus runtime CDI bean responsible for discovering cleanup configurations
 * and providing the execution target called by the Quarkus Scheduler.
 *
 * <p>Because Quarkus {@code @Scheduled} requires compile-time cron expressions,
 * we use the programmatic {@link io.quarkus.scheduler.Scheduler} API to register
 * jobs dynamically at startup — one per discovered {@link CleanupConfig}.
 *
 * <p>See {@link CleanupBootstrap} for the programmatic registration logic.
 */
@ApplicationScoped
public class CleanupJobRunner {

    private static final Logger log = LoggerFactory.getLogger(CleanupJobRunner.class);

    @Inject
    EntityManager entityManager;

    private final CleanupExecutor  executor = CleanupExecutor.getInstance();
    private       List<CleanupConfig> configs  = Collections.emptyList();

    @PostConstruct
    void init() {
        CleanupRegistry registry = new CleanupRegistry(
                Thread.currentThread().getContextClassLoader());
        configs = registry.discoverAll();
        log.info("[Cleanup/Quarkus] Discovered {} cleanup configuration(s).", configs.size());
    }

    /**
     * Returns all discovered and enabled configurations — used by
     * {@link CleanupBootstrap} to register scheduled jobs.
     */
    public List<CleanupConfig> getEnabledConfigs() {
        return configs.stream()
                .filter(CleanupConfig::isEnabled)
                .toList();
    }

    /**
     * Execute a single cleanup job by its job-id.
     * Called from the Quarkus Scheduler trigger registered in {@link CleanupBootstrap}.
     *
     * @param jobId the value of {@link CleanupConfig#getJobId()}
     */
    @Transactional
    public void runById(String jobId) {
        configs.stream()
                .filter(c -> c.getJobId().equals(jobId))
                .findFirst()
                .ifPresentOrElse(
                        config -> executor.execute(config, entityManager),
                        () -> log.warn("[Cleanup/Quarkus] No config found for jobId '{}'", jobId));
    }
}
