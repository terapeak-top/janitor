package io.terapeak.janitor.quarkus.it.database;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@ApplicationScoped
public class OrderService {

    private final EntityManager em;

    public OrderService(EntityManager em) {
        this.em = em;
    }

    @Transactional
    public OrderEntity createOrder() {
        OrderEntity order = new OrderEntity();
        order.createdAt = LocalDateTime.now().minusDays(5);
        em.persist(order);
        return order;
    }
}