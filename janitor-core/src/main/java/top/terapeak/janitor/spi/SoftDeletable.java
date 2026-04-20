package top.terapeak.janitor.spi;

import top.terapeak.janitor.annotation.Cleanup;
import java.time.LocalDateTime;

/**
 * Optional marker interface for entities that support soft deletion.
 *
 * <p>If your entity implements this interface, the {@code CleanupExecutor} will
 * call {@link #markDeleted()} instead of issuing a hard DELETE when
 * {@code softDelete = true} on the {@link Cleanup}
 * annotation.
 *
 * <p>Alternatively, if your entity does <em>not</em> implement this interface but
 * exposes {@code boolean deleted} and {@code LocalDateTime deletedAt} fields,
 * the executor will set them via reflection.
 */
public interface SoftDeletable {

    /**
     * Returns whether this entity has been soft-deleted.
     */
    boolean isDeleted();

    /**
     * Returns the timestamp at which this entity was soft-deleted, or {@code null}
     * if it has not been deleted.
     */
    LocalDateTime getDeletedAt();

    /**
     * Marks the entity as deleted, setting the deletion timestamp to now.
     * Implementations should set both a {@code deleted = true} flag and a
     * {@code deletedAt} timestamp.
     */
    void markDeleted();
}
