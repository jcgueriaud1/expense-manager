package com.vaadin.expensemanager.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stamps every HTTP request with a correlation id so all log lines emitted while
 * handling a single user action can be traced together (ADR-0013, Phase 0.5).
 *
 * <p>The id is taken from an inbound {@value #HEADER} header when the client
 * supplies a safe-looking one, otherwise a fresh {@link UUID} is generated. It
 * is placed in the SLF4J {@link MDC} under {@value #MDC_KEY} for the duration of
 * the request and echoed back on the response {@value #HEADER} header so callers
 * (and upstream proxies) can correlate too. The MDC entry surfaces in
 * human-readable console logs via {@code logging.pattern.correlation} ({@code
 * local}) and is included automatically in the ecs structured output ({@code
 * staging}/{@code prod}).
 *
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} so the id is in scope before any
 * other filter (including Spring Security and the Vaadin servlet) logs, and the
 * MDC is always cleared in a {@code finally} block to keep it from leaking onto
 * the pooled worker thread's next request.
 *
 * <p><strong>Accepted V1 limitation:</strong> this is a servlet filter, so it
 * covers ordinary HTTP and Vaadin UIDL requests but <em>not</em> {@code @Push}
 * messages — those are handled on Atmosphere websocket threads that never pass
 * through the servlet chain and so inherit no MDC. Push-triggered log lines lack
 * a correlation id in V1; the upgrade path is Micrometer Tracing, deliberately
 * out of scope here (no tracing stack in V1). See {@code docs/findings.md}
 * (F-009) and {@link com.vaadin.expensemanager.observability}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    /** Request/response header carrying the correlation id. */
    public static final String HEADER = "X-Request-Id";

    /** MDC key under which the id is stored; referenced by the log patterns. */
    public static final String MDC_KEY = "requestId";

    /**
     * Guards against log injection: an inbound id is only trusted if it is a
     * short run of URL-safe characters. Anything else (notably newlines that
     * could forge extra log lines in the plain-text console format) is dropped
     * in favour of a generated id.
     */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolveRequestId(String inbound) {
        if (inbound != null && SAFE_ID.matcher(inbound).matches()) {
            return inbound;
        }
        return UUID.randomUUID().toString();
    }
}
