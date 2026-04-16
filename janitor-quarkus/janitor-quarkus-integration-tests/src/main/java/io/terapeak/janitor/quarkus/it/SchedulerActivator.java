package io.terapeak.janitor.quarkus.it;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SchedulerActivator {

    @Scheduled(every = "10h")
    public void noop() {

    }
}