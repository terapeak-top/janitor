package io.terapeak.janitor.quarkus.it.database;

import io.terapeak.janitor.annotation.Cleanup;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "orders")
@Cleanup(entity = OrderEntity.class, field = "createdAt", retentionDays = 1)
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public Instant createdAt;
}
