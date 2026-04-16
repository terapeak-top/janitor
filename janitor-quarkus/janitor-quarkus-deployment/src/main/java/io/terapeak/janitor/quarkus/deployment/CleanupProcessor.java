package io.terapeak.janitor.quarkus.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.terapeak.janitor.annotation.Cleanup;
import io.terapeak.janitor.annotation.Cleanups;
import io.terapeak.janitor.quarkus.scheduler.CleanupBootstrap;
import io.terapeak.janitor.quarkus.scheduler.CleanupJobRunner;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Quarkus build-time processor for the janitor extension.
 *
 * <p>Build steps run during the Quarkus augmentation phase (before application
 * startup). This processor:
 * <ol>
 *   <li>Registers the {@code janitor} feature in the Quarkus feature list.</li>
 *   <li>Declares the runtime CDI beans as unremovable so ArC doesn't eliminate them.</li>
 *   <li>Scans the Jandex index for all classes annotated with {@link Cleanup} or
 *       {@link Cleanups} and registers them for native-image reflection.</li>
 * </ol>
 */
public class CleanupProcessor {

    private static final Logger log = LoggerFactory.getLogger(CleanupProcessor.class);

    private static final String FEATURE_NAME = "janitor";

    private static final DotName CLEANUP_NAME = DotName.createSimple(Cleanup.class.getName());
    private static final DotName CLEANUPS_NAME = DotName.createSimple(Cleanups.class.getName());

    // -------------------------------------------------------------------------
    // Feature registration
    // -------------------------------------------------------------------------

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE_NAME);
    }

    // -------------------------------------------------------------------------
    // CDI bean registration — mark runtime beans as unremovable
    // -------------------------------------------------------------------------

    @BuildStep
    AdditionalBeanBuildItem registerRuntimeBeans() {
        return AdditionalBeanBuildItem.builder()
            .setUnremovable()
            .addBeanClasses(
                CleanupJobRunner.class,
                CleanupBootstrap.class)
            .build();
    }

    // -------------------------------------------------------------------------
    // Native image: register annotated classes + entity classes for reflection
    // -------------------------------------------------------------------------

    @BuildStep
    List<ReflectiveClassBuildItem> registerForReflection(CombinedIndexBuildItem indexBuildItem) {
        List<ReflectiveClassBuildItem> items = new ArrayList<>();

        // Classes annotated with @Cleanup (single)
        Collection<AnnotationInstance> singleAnnotations =
            indexBuildItem.getIndex().getAnnotations(CLEANUP_NAME);

        // Classes annotated with @Cleanups (repeatable container)
        Collection<AnnotationInstance> containerAnnotations =
            indexBuildItem.getIndex().getAnnotations(CLEANUPS_NAME);

        List<String> annotatedClassNames = new ArrayList<>();
        singleAnnotations.stream()
            .map(ai -> ai.target().asClass().name().toString())
            .forEach(annotatedClassNames::add);
        containerAnnotations.stream()
            .map(ai -> ai.target().asClass().name().toString())
            .forEach(annotatedClassNames::add);

        if (annotatedClassNames.isEmpty()) {
            log.warn("[Cleanup/Quarkus] No classes annotated with @Cleanup found in Jandex index.");
        }

        for (String className : annotatedClassNames) {
            // Register the config class itself (needs reflection to read annotations)
            items.add(ReflectiveClassBuildItem.builder(className)
                .methods(true)
                .fields(true)
                .build());

            log.debug("[Cleanup/Quarkus] Registered '{}' for reflection.", className);
        }

        // Always register the core library classes for native image
        items.add(ReflectiveClassBuildItem.builder(
                io.terapeak.janitor.config.CleanupConfig.class,
                io.terapeak.janitor.executor.CleanupExecutor.class,
                io.terapeak.janitor.registry.CleanupRegistry.class)
            .methods(true)
            .fields(true)
            .build());

        return items;
    }
}
