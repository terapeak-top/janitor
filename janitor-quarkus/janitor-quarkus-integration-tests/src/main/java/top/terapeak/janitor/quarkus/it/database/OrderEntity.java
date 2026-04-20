package top.terapeak.janitor.quarkus.it.database;

import top.terapeak.janitor.annotation.Cleanup;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Cleanup(field = "createdAt", retentionDays = 1, cron = "*/10 * * * * ?")
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public LocalDateTime createdAt;

    public Boolean deleted;
}
