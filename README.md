# Janitor

A multi-framework JPA entity cleanup library for **Spring Boot**, **Quarkus**, and **KumuluzEE**.
Annotate any class with `@Cleanup` and the library schedules a cron job that deletes (or soft-deletes)
old rows from your entity table automatically.

---

## Modules

| Artifact                    | Purpose |
|-----------------------------|---|
| `janitor-core`              | Annotation, config model, executor, registry, annotation processor |
| `janitor-spring-boot` | Spring Boot 3.x auto-configuration |
| `janitor-quarkus`     | Quarkus CDI beans and scheduler integration |
| `janitor-kumuluzee`         | KumuluzEE CDI + kumuluzee-cron integration |

---

## Demos
- [Spring boot demo](https://github.com/terapeak-top/janitor-spring-boot-demo)
- [Quarkus demo](https://github.com/terapeak-top/janitor-quarkus-demo)
- [KumuluzEE demo](https://github.com/terapeak-top/janitor-kumuluzee-demo)

## Quick start

### Add the dependency

**Spring Boot 3.x**
```xml
<dependency>
    <groupId>top.terapeak</groupId>
    <artifactId>janitor-spring-boot</artifactId>
    <version>1.1.0</version>
</dependency>
```

**Quarkus**
```xml
<dependency>
    <groupId>top.terapeak</groupId>
    <artifactId>janitor-quarkus</artifactId>
    <version>1.1.0</version>
</dependency>
```
> The deployment artifact is picked up automatically via `quarkus-extension.properties`.

**KumuluzEE**
```xml
<dependency>
    <groupId>top.terapeak</groupId>
    <artifactId>janitor-kumuluzee</artifactId>
    <version>1.1.0</version>
</dependency>
```

---

### Annotate any entity class

#### Simplest form all defaults

```java
@Cleanup(field = "createdAt", retentionDays = 90)
@Entity(table="customer_profile")
public class Customer {}
```
The scheduler for this entity will run a cleanup job every night at 2 AM and will clean all Customers that are older than 90 days, considering createdAt contains the creation `LocalDateTime`
> [!TIP]
> What will be executed: `DELETE FROM Customer c WHERE c.createdAt < '2026-01-20T14:30:05.080028410'` 

#### Advanced parameters

```java
@Cleanup(field = "createdAt", retentionDays = 20, cron = "0 0 5 * * ?", batchSize = 500, skipSoftDeleted = true)
@Entity(table="customer_profile")
public class Customer {}
```
The scheduler for this entity will run a cleanup job every night at 5 AM and will clean all Customers that are older than 20 days, considering createdAt contains the creation `LocalDateTime` and skipping all already soft deleted rows
> [!TIP]
> What will be executed: `DELETE FROM Customer c WHERE c.createdAt < '2026-01-20T14:30:05.080028410' and deleted = false`

> [!WARNING]  
> Using the `skipSoftDeleted` parameter assumes the target entity contains a boolean field named `deleted`

#### Using soft cleanup
```java
@Cleanup(field = "lastActiveAt", retentionDays = 10, softDelete = true)
@Entity(table="customer_profile")
public class Customer {}
```
The scheduler for this entity will run a cleanup job every night at 2 AM and will soft delete all Customers that have not been active more than 10 days, considering `lastActiveAt` contains the last login time. Using this option does not actually delete the rows, instead it executes an update, setting `deleted = true` on all target entities

> [!TIP]
> What will be executed: `UPDATE Customer c SET c.deleted = true WHERE c.lastActiveAt < '2026-01-20T14:30:05.080028410' and deleted = false`

> [!WARNING]  
> Using the `softDelete` parameter assumes the target entity contains a boolean field named `deleted`

#### Multiple usage
The same annotation can be used multiple times on the same entity as long as a different field and soft/hard delete are distinguishing them from one another. For example this is a legitimate usage:

```java
@Cleanup(field = "createdAt", retentionDays = 90, skipSoftDeleted = true)
@Cleanup(field = "lastActiveAt", retentionDays = 10, softDelete = true)
@Entity(table="customer_profile")
public class Customer {}
```

### Class scanning

This library does not scan classes at runtime. In order for the entity classes to be detected the host project need to integrate the compile time processor in its build setup. For example for Maven here is an example that needs to be added to the build:

```xml
<plugin>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <annotationProcessorPaths>
      <path>
        <groupId>top.terapeak</groupId>
        <artifactId>janitor-core</artifactId>
        <version>1.1.0</version>
      </path>
    </annotationProcessorPaths>
  </configuration>
</plugin>
```
The annotation processor then generates `META-INF/janitor/cleanups.index` at compile time in the host project. This file is then used at runtime to schedule all the executors for the annotated classes.

---

## `@Cleanup` reference

| Attribute | Type | Default | Description |
|---|---|---|---|
| `field` | `String` | — | **Required.** Date/time field name used to compute row age. |
| `retentionDays` | `int` | — | **Required.** Rows older than this many days are processed. |
| `cron` | `String` | `"0 0 2 * * ?"` | Quartz cron expression (6-part: s m h d M dow). |
| `enabled` | `boolean` | `true` | Set to `false` to disable without removing the annotation. |
| `batchSize` | `int` | `0` | Rows per batch. `0` = single bulk DELETE. |
| `softDelete` | `boolean` | `false` | When `true`, sets `deleted=true` / `deletedAt=now()` instead of hard DELETE. |
| `skipSoftDeleted` | `boolean` | `true` | Exclude already-soft-deleted rows from hard DELETE jobs. |

---

## Soft delete support

The executor supports two soft-delete mechanisms:

**Option A — implement `SoftDeletable`:**
```java
import top.terapeak.cleanup.spi.SoftDeletable;

@Entity
public class UserSession implements SoftDeletable {
    private boolean deleted;
    private LocalDateTime deletedAt;

    @Override public boolean isDeleted()          { return deleted; }
    @Override public LocalDateTime getDeletedAt() { return deletedAt; }
    @Override public void markDeleted() {
        this.deleted   = true;
        this.deletedAt = LocalDateTime.now();
    }
}
```

**Option B — reflection fallback:**  
Expose `boolean deleted` and (optionally) `LocalDateTime deletedAt` fields on the entity.
The executor sets them via reflection if `SoftDeletable` is not implemented.

---

## Spring Boot configuration

Override any default via `application.properties` / `application.yml`:

```properties
# Disable ALL cleanup jobs (master switch)
janitor.enabled=true

# Override the default batch size for all jobs that have batchSize=0
janitor.default-batch-size=1000
```

All beans are `@ConditionalOnMissingBean`, so any bean you declare replaces the default.

---

## Architecture

```
Host project
    │  declares @Cleanup on config classes
    ▼
janitor-core
    ├── @Cleanup / @Cleanups annotations
    ├── CleanupIndexProcessor   (compile-time: writes cleanups.index)
    ├── CleanupRegistry         (startup: reads cleanups.index → CleanupConfig list)
    ├── CleanupConfig           (immutable value object per annotation)
    └── CleanupExecutor         (stateless: bulk delete / batch delete / soft delete)

Framework adapter
    ├── reads CleanupConfig list from CleanupRegistry
    ├── registers one scheduled job per config (using framework scheduler)
    └── calls CleanupExecutor.execute(config, entityManager) inside a transaction
```

---

## Building

```bash
mvn clean install
```

The annotation processor in `janitor-core` runs automatically during compilation.
Check `target/classes/META-INF/janitor/cleanups.index` in your host project to
verify it was generated correctly.

---

## Compatibility

| Framework | Tested version | JPA namespace |
|---|---|---|
| Spring Boot | 3.2+ | `jakarta.*` |
| Quarkus | 3.6+ | `jakarta.*` |
| KumuluzEE | 4.1+ | `jakarta.*` |

For Spring Boot 2.x (`javax.*` namespace), use a `2.x` release line of this library
(replace `jakarta.persistence` imports with `javax.persistence` in the Spring adapter).
