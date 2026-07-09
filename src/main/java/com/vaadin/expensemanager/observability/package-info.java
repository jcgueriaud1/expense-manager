/**
 * Cross-cutting observability plumbing: structured logging and request
 * correlation (ADR-0013, Phase 0.5).
 *
 * <p>Deliberately thin — no Micrometer/OTel tracing in V1. Structured JSON
 * logging is configured declaratively via {@code logging.structured.format.*}
 * in the {@code staging}/{@code prod} profiles; the only code here is the
 * {@link com.vaadin.expensemanager.observability.RequestCorrelationFilter} that
 * stamps a request id into the MDC.
 */
package com.vaadin.expensemanager.observability;
