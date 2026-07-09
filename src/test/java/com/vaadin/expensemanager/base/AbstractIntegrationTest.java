package com.vaadin.expensemanager.base;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for the integration-test layer (pyramid layer 2, ADR-0012).
 *
 * <p>Boots the full Spring context on a <strong>singleton</strong>
 * {@link PostgreSQLContainer} — {@code static}, started once per JVM run and
 * shared by every integration-test subclass, never explicitly stopped (Ryuk
 * reaps it). The container is wired into Spring Boot's datasource via
 * {@link ServiceConnection}, so there is no manual {@code @DynamicPropertySource}
 * plumbing; Flyway migrates it once on boot.
 *
 * <p>{@code .withReuse(true)} keeps the container alive between local
 * {@code ./mvnw test} runs for fast TDD loops (honoured only when the developer
 * opts in via {@code ~/.testcontainers.properties}; ignored in CI).
 *
 * <p><strong>State isolation:</strong> {@link Transactional} rolls back each
 * test method by default, so the shared container stays clean without
 * per-class truncation. Tests that need committed state across transactions —
 * notably {@code @Version} / optimistic-lock behaviour (ADR-0011) — are the
 * documented exception and must override this (e.g. drop {@code @Transactional}
 * and truncate explicitly).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest {

    /**
     * Singleton container: started once in the static initializer (not via
     * {@code @Container}/{@code @Testcontainers}, so the context lifecycle never
     * stops it) and detected by Spring Boot on this base class for
     * {@code @ServiceConnection} datasource wiring.
     */
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine").withReuse(true);

    static {
        POSTGRES.start();
    }
}
