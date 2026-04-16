# Janitor

A multi-framework JPA entity cleanup library for **Spring Boot**, **Quarkus**, and **KumuluzEE**.
Annotate any class with `@Cleanup` and the library schedules a cron job that deletes (or soft-deletes)
old rows from your entity table automatically.

---

## Modules

| Artifact                      | Purpose |
|-------------------------------|---|
| `janitor-core`                | Annotation, config model, executor, registry, annotation processor |
| `janitor-spring-boot-starter` | Spring Boot 3.x auto-configuration |
| `janitor-quarkus-runtime`     | Quarkus CDI beans and scheduler integration |
| `janitor-quarkus-deployment`  | Quarkus build-time processor (Jandex, reflection registration) |
| `janitor-kumuluzee`           | KumuluzEE CDI + kumuluzee-cron integration |

---

## Quick start

### 1. Add the dependency

**Spring Boot 3.x**
```xml
<dependency>
    <groupId>io.terapeak</groupId>
    <artifactId>janitor-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Quarkus**
```xml
<dependency>
    <groupId>io.terapeak</groupId>
    <artifactId>janitor-quarkus-runtime</artifactId>
    <version>1.0.0</version>
</dependency>
```
> The deployment artifact is picked up automatically via `quarkus-extension.properties`.

**KumuluzEE**
```xml
<dependency>
    <groupId>io.terapeak</groupId>
    <artifactId>janitor-kumuluzee</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

### 2. Annotate a configuration class

Create any class (a dedicated config class, an existing service, or even a `package-info.java`)
and add one or more `@Cleanup` annotations:

```java
package com.example.config;

import io.terapeak.cleanup.annotation.Cleanup;

@Cleanup(
    field         = "createdAt",       // java.time.LocalDateTime field on the entity
    retentionDays = 90,                // delete rows older than 90 days
    cron          = "0 0 2 * * ?",     // 2 AM every day (Quartz format)
    batchSize     = 500,               // delete 500 rows per transaction batch
    softDelete    = false,
    skipSoftDeleted = true
)
@Cleanup(
    field         = "lastActiveAt",
    retentionDays = 30,
    softDelete    = true               // sets deleted=true instead of hard DELETE
)
public class CleanupConfiguration {}
```

The annotation processor generates `META-INF/janitor/cleanups.index` at compile time.
No runtime classpath scanning is needed.

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
import io.terapeak.cleanup.spi.SoftDeletable;

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
