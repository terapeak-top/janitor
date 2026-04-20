package top.terapeak.janitor.kumuluzee.scheduler;

import top.terapeak.janitor.config.CleanupConfig;
import top.terapeak.janitor.executor.CleanupExecutor;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.transaction.UserTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Runnable} submitted to the {@code kumuluzee-cron} scheduler for a single {@link CleanupConfig}.
 *
 * <p>Each invocation opens a fresh {@link EntityManager}, begins a JTA
 * transaction via {@link UserTransaction}, delegates to {@link CleanupExecutor}, and commits (or rolls back on error). This keeps
 * transactions scoped to a single cron fire and avoids any connection leaks.
 *
 * <p>When {@code batchSize > 0} the executor handles internal batching; each
 * batch flushes and clears the EntityManager but the outer transaction wraps all batches. If you need per-batch commits (e.g. tables with
 * tens of millions of rows), extend this class and override {@link #run()} to split the work across multiple {@link UserTransaction}
 * boundaries.
 */
public class CleanupCronJob implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CleanupCronJob.class);

    private final CleanupConfig config;
    private final KumuluzeeCleanupExecutor executor;
    private final EntityManagerFactory emf;

    public CleanupCronJob(CleanupConfig config, KumuluzeeCleanupExecutor executor, EntityManagerFactory emf) {
        this.config = config;
        this.executor = executor;
        this.emf = emf;
    }

    @Override
    public void run() {
        log.debug("[Cleanup/KumuluzEE] Firing job '{}'.", config.getJobId());
        EntityManager em = emf.createEntityManager();
        try {
            executor.execute(config, em);
        } catch (Exception ex) {
            log.error("[Cleanup/KumuluzEE] Job '{}' failed {}",
                config.getJobId(), ex.getMessage(), ex);
        }
    }
}
