package com.vaadin.expensemanager.base;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 0 acceptance test (tracer bullet): the full Spring context boots on real
 * Postgres via {@link AbstractIntegrationTest}, Flyway {@code V1} is confirmed
 * applied, and both health probes respond — the seam every later integration
 * test reuses (ADR-0005, ADR-0012, ADR-0013).
 *
 * <p>The health-probe assertions run through {@code MockMvc} with the Spring
 * Security filter chain wired in, so they also verify
 * {@link com.vaadin.expensemanager.security.HealthProbeSecurityConfig} opens the
 * probes to unauthenticated access.
 */
class FoundationAcceptanceTest extends AbstractIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void flywayV1IsApplied() {
        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied)
                .as("Flyway V1 baseline should be applied on startup")
                .anyMatch(info -> "1".equals(info.getVersion().getVersion()));
        assertThat(applied)
                .as("no applied migration is in a failed state")
                .noneMatch(info -> info.getState().isFailed());
    }

    @Test
    void livenessProbeResponds() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void readinessProbeResponds() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
