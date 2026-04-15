package io.terapeak.janitor.annotation;

import io.terapeak.janitor.spi.SoftDeletable;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class (or package-info) as a source of entity cleanup configuration.
 *
 * <p>Place this annotation on any class visible to the annotation processor.
 * The library will schedule a periodic job that deletes (or soft-deletes) rows
 * from the target entity table whose {@code field} value is older than
 * {@code retentionDays} days.
 *
 * <pre>{@code
 * @Cleanup(
 *     entity       = Order.class,
 *     field        = "createdAt",
 *     retentionDays = 90,
 *     cron         = "0 0 3 * * ?",
 *     batchSize    = 500,
 *     softDelete   = false
 * )
 * public class OrderCleanupConfig {}
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(Cleanups.class)
public @interface Cleanup {

    /**
     * The JPA entity class whose table will be cleaned up.
     */
    Class<?> entity();

    /**
     * Name of the date/time field on the entity used to evaluate row age.
     * Must be of type {@link java.time.LocalDateTime}, {@link java.time.Instant},
     * or {@link java.util.Date}.
     */
    String field();

    /**
     * Rows older than this many days (relative to the field value) will be processed.
     */
    int retentionDays();

    /**
     * Cron expression (Quartz/Spring format: {@code s m h d M dow}).
     * Defaults to {@code 0 0 2 * * ?} (2 AM every day).
     */
    String cron() default "0 0 2 * * ?";

    /**
     * Whether this cleanup job is active. Set to {@code false} to disable without
     * removing the annotation.
     */
    boolean enabled() default true;

    /**
     * Number of rows to delete per transaction batch. {@code 0} means delete all
     * matching rows in a single transaction (use with caution on large tables).
     */
    int batchSize() default 0;

    /**
     * When {@code true}, the executor sets a {@code deleted} / {@code deletedAt} field
     * instead of issuing a hard DELETE. The entity must implement
     * {@link SoftDeletable} or expose a boolean {@code deleted}
     * field and an {@link java.time.LocalDateTime} {@code deletedAt} field.
     */
    boolean softDelete() default false;

    /**
     * When {@code true} (default), rows that have already been soft-deleted are excluded
     * from hard-delete jobs. Ignored when {@code softDelete = true}.
     */
    boolean skipSoftDeleted() default true;
}
