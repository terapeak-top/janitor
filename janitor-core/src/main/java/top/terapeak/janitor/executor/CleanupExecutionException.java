package top.terapeak.janitor.executor;

/**
 * Thrown when a cleanup job fails during execution.
 */
public class CleanupExecutionException extends RuntimeException {

    public CleanupExecutionException(String message) {
        super(message);
    }

    public CleanupExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
