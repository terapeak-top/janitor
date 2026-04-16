package io.terapeak.janitor.quarkus.it;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.terapeak.janitor.quarkus.it.database.OrderService;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@QuarkusMain
public class Janitor {

    public static void main(String... args) {
        Quarkus.run(TestApplication.class, args);
    }

    public static class TestApplication implements QuarkusApplication {
    private static final Logger log = LoggerFactory.getLogger(TestApplication.class);

        @Inject
        OrderService orderService;

        @Override
        public int run(String... args) throws Exception {
            orderService.createOrder();
            log.info("Order created");
            Quarkus.waitForExit();
            return 0;
        }
    }
}