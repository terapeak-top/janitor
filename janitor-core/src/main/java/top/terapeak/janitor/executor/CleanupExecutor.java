package top.terapeak.janitor.executor;

import top.terapeak.janitor.config.CleanupConfig;
import top.terapeak.janitor.spi.SoftDeletable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Framework-agnostic executor that carries out a single cleanup job.
 *
 * <p>Framework adapters (Spring, Quarkus, KumuluzEE) obtain an {@link EntityManager}
 * from their own context and call {@link #execute(CleanupConfig, EntityManager)}. The executor itself is stateless and thread-safe.
 */
public class CleanupExecutor {

    private static final Logger log = LoggerFactory.getLogger(CleanupExecutor.class);

    private static final CleanupExecutor INSTANCE = new CleanupExecutor();

    public static CleanupExecutor getInstance() {
        return INSTANCE;
    }

    /**
     * Execute the cleanup defined by {@code config} using the supplied {@code em}.
     *
     * <p>The caller is responsible for transaction demarcation. Each batch is
     * executed inside a separate transaction if {@code batchSize > 0}.
     *
     * @param config fully resolved cleanup configuration
     * @param em     active {@link EntityManager}; must be in an open transaction when {@code batchSize == 0}
     */
    public void execute(CleanupConfig config, EntityManager em) {
        if (!config.isEnabled()) {
            log.debug("[Cleanup] Job '{}' is disabled, skipping.", config.getJobId());
            return;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusDays(config.getRetentionDays());
        log.info("[Cleanup] Starting job '{}' — deleting records where {} < {}",
            config.getJobId(), config.getField(), cutoff);

        try {
            if (config.isSoftDelete()) {
                executeSoftDelete(config, em, cutoff);
            } else if (config.getBatchSize() > 0) {
                executeBatchHardDelete(config, em, cutoff);
            } else {
                executeBulkHardDelete(config, em, cutoff);
            }
        } catch (Exception ex) {
            log.error("[Cleanup] Job '{}' failed: {}", config.getJobId(), ex.getMessage(), ex);
            throw new CleanupExecutionException("Cleanup job '" + config.getJobId() + "' failed", ex);
        }
    }

    private void executeBulkHardDelete(CleanupConfig config, EntityManager em, LocalDateTime cutoff) {
        String jpql = buildHardDeleteJpql(config);
        log.debug("[Cleanup] JPQL: {}", jpql);
        int deleted = em.createQuery(jpql)
            .setParameter("cutoff", cutoff)
            .executeUpdate();
        log.info("[Cleanup] Job '{}' deleted {} rows (bulk).", config.getJobId(), deleted);
    }


    private void executeBatchHardDelete(CleanupConfig config, EntityManager em, LocalDateTime cutoff) {
        String selectJpql = buildBatchSelectJpql(config);
        String deleteJpql = "DELETE FROM " + config.getEntityName()
            + " e WHERE e IN :batch";

        log.debug("[Cleanup] Batch SELECT JPQL: {}", selectJpql);

        int totalDeleted = 0;
        int batchSize = config.getBatchSize();
        while (true) {
            List<?> batch = em.createQuery(selectJpql)
                .setParameter("cutoff", cutoff)
                .setMaxResults(batchSize)
                .getResultList();

            if (batch.isEmpty()) {
                break;
            }
            int count = em.createQuery(deleteJpql)
                .setParameter("batch", batch)
                .executeUpdate();

            totalDeleted += count;
            em.flush();
            em.clear();
            log.debug("[Cleanup] Job '{}' batch deleted {} rows (running total: {}).",
                config.getJobId(), count, totalDeleted);

            if (batch.size() < batchSize) {
                break;
            }
        }

        log.info("[Cleanup] Job '{}' deleted {} rows (batched, size={}).",
            config.getJobId(), totalDeleted, batchSize);
    }

    private void executeSoftDelete(CleanupConfig config, EntityManager em, LocalDateTime cutoff) {
        String selectJpql = buildSoftDeleteSelectJpql(config);
        log.debug("[Cleanup] Soft-delete SELECT JPQL: {}", selectJpql);

        Query query = em.createQuery(selectJpql)
            .setParameter("cutoff", cutoff);

        if (config.getBatchSize() > 0) {
            query.setMaxResults(config.getBatchSize());
        }

        List<?> entities = query.getResultList();
        int count = 0;

        for (Object entity : entities) {
            markSoftDeleted(entity, config);
            count++;
            if (config.getBatchSize() > 0 && count % config.getBatchSize() == 0) {
                em.flush();
                em.clear();
            }
        }

        em.flush();
        log.info("[Cleanup] Job '{}' soft-deleted {} rows.", config.getJobId(), count);
    }

    private void markSoftDeleted(Object entity, CleanupConfig config) {
        if (entity instanceof SoftDeletable) {
            ((SoftDeletable) entity).markDeleted();
            return;
        }
        try {
            java.lang.reflect.Field deletedField = findField(entity.getClass(), "deleted");
            deletedField.setAccessible(true);
            deletedField.set(entity, true);

            try {
                java.lang.reflect.Field deletedAtField = findField(entity.getClass(), "deletedAt");
                deletedAtField.setAccessible(true);
                deletedAtField.set(entity, LocalDateTime.now());
            } catch (NoSuchFieldException ignored) {
                // we are not complaining deletedAt is optional
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new CleanupExecutionException(
                "Entity '" + config.getEntityName() + "' does not implement SoftDeletable "
                    + "and has no 'deleted' field. Cannot soft-delete.", e);
        }
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("No field '" + name + "' in hierarchy of " + clazz.getName());
    }
    private String buildHardDeleteJpql(CleanupConfig config) {
        StringBuilder sb = new StringBuilder()
            .append("DELETE FROM ").append(config.getEntityName()).append(" e")
            .append(" WHERE e.").append(config.getField()).append(" < :cutoff");

        if (config.isSkipSoftDeleted()) {
            appendSkipSoftDeletedClause(sb);
        }
        return sb.toString();
    }

    private String buildBatchSelectJpql(CleanupConfig config) {
        StringBuilder sb = new StringBuilder()
            .append("SELECT e FROM ").append(config.getEntityName()).append(" e")
            .append(" WHERE e.").append(config.getField()).append(" < :cutoff");

        if (config.isSkipSoftDeleted()) {
            appendSkipSoftDeletedClause(sb);
        }
        return sb.toString();
    }

    private String buildSoftDeleteSelectJpql(CleanupConfig config) {
        return "SELECT e FROM " + config.getEntityName() + " e"
            + " WHERE e." + config.getField() + " < :cutoff"
            + " AND (e.deleted IS NULL OR e.deleted = false)";
    }

    private void appendSkipSoftDeletedClause(StringBuilder sb) {
        sb.append(" AND (e.deleted IS NULL OR e.deleted = false)");
    }
}
