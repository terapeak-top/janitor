package io.terapeak.janitor.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container annotation that enables repeatable {@link Cleanup} declarations
 * on a single class.
 *
 * <pre>{@code
 * @Cleanup(entity = Order.class,   field = "createdAt", retentionDays = 90)
 * @Cleanup(entity = Session.class, field = "lastSeen",  retentionDays = 30)
 * public class CleanupConfig {}
 * }</pre>
 */
@Target({ElementType.TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cleanups {
    Cleanup[] value();
}
