package top.terapeak.janitor.config;

import top.terapeak.janitor.annotation.Cleanup;

import java.util.Objects;

/**
 * Immutable value object representing a single resolved {@link Cleanup} annotation. Framework adapters convert this to their own
 * scheduler/trigger representation.
 */
public final class CleanupConfig {

    private final Class<?> entity;
    private final String entityName;
    private final String field;
    private final int retentionDays;
    private final String cron;
    private final boolean enabled;
    private final int batchSize;
    private final boolean softDelete;
    private final boolean skipSoftDeleted;

    private CleanupConfig(Builder b) {
        this.entity = Objects.requireNonNull(b.entity, "entity");
        this.entityName = b.entityName != null ? b.entityName : b.entity.getSimpleName();
        this.field = Objects.requireNonNull(b.field, "field");
        this.retentionDays = b.retentionDays;
        this.cron = Objects.requireNonNull(b.cron, "cron");
        this.enabled = b.enabled;
        this.batchSize = b.batchSize;
        this.softDelete = b.softDelete;
        this.skipSoftDeleted = b.skipSoftDeleted;
    }

    /**
     * Build a {@link CleanupConfig} directly from a {@link Cleanup} annotation instance.
     */
    public static CleanupConfig from(Class<?> entity, Cleanup annotation) {
        return new Builder()
            .entity(entity)
            .field(annotation.field())
            .retentionDays(annotation.retentionDays())
            .cron(annotation.cron())
            .enabled(annotation.enabled())
            .batchSize(annotation.batchSize())
            .softDelete(annotation.softDelete())
            .skipSoftDeleted(annotation.skipSoftDeleted())
            .build();
    }

    public Class<?> getEntity() {
        return entity;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getField() {
        return field;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public String getCron() {
        return cron;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public boolean isSoftDelete() {
        return softDelete;
    }

    public boolean isSkipSoftDeleted() {
        return skipSoftDeleted;
    }

    public String getJobId() {
        return "janitor_cleanup_" + entityName + "_" + field + (softDelete ? "_soft-delete" : "");
    }

    @Override
    public String toString() {
        return "CleanupConfig{entity=" + entityName
            + ", field=" + field
            + ", retentionDays=" + retentionDays
            + ", cron='" + cron + "'"
            + ", enabled=" + enabled
            + ", batchSize=" + batchSize
            + ", softDelete=" + softDelete
            + ", skipSoftDeleted=" + skipSoftDeleted + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Class<?> entity;
        private String entityName;
        private String field;
        private int retentionDays;
        private String cron = "0 0 2 * * ?";
        private boolean enabled = true;
        private int batchSize = 0;
        private boolean softDelete = false;
        private boolean skipSoftDeleted = true;

        public Builder entity(Class<?> entity) {
            this.entity = entity;
            return this;
        }

        public Builder entityName(String entityName) {
            this.entityName = entityName;
            return this;
        }

        public Builder field(String field) {
            this.field = field;
            return this;
        }

        public Builder retentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
            return this;
        }

        public Builder cron(String cron) {
            this.cron = cron;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        public Builder softDelete(boolean softDelete) {
            this.softDelete = softDelete;
            return this;
        }

        public Builder skipSoftDeleted(boolean skip) {
            this.skipSoftDeleted = skip;
            return this;
        }

        public CleanupConfig build() {
            return new CleanupConfig(this);
        }
    }
}
