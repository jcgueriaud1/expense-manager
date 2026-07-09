package com.vaadin.expensemanager.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static com.vaadin.expensemanager.observability.RequestCorrelationFilter.HEADER;
import static com.vaadin.expensemanager.observability.RequestCorrelationFilter.MDC_KEY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link RequestCorrelationFilter}: the correlation id is present
 * in the MDC while the chain runs, echoed on the response, and cleared
 * afterwards; a safe inbound {@code X-Request-Id} is honoured while a missing or
 * unsafe one is replaced by a generated id (ADR-0013, Phase 0.5).
 */
class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesIdWhenHeaderAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] idDuringChain = new String[1];

        filter.doFilter(request, response, (req, res) -> idDuringChain[0] = MDC.get(MDC_KEY));

        assertThat(idDuringChain[0]).as("id in MDC during the chain").isNotBlank();
        assertThat(response.getHeader(HEADER))
                .as("id echoed on the response").isEqualTo(idDuringChain[0]);
        assertThat(MDC.get(MDC_KEY)).as("MDC cleared after the request").isNull();
    }

    @Test
    void honoursSafeInboundId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HEADER, "abc-123_DEF.45");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] idDuringChain = new String[1];

        filter.doFilter(request, response, (req, res) -> idDuringChain[0] = MDC.get(MDC_KEY));

        assertThat(idDuringChain[0]).isEqualTo("abc-123_DEF.45");
        assertThat(response.getHeader(HEADER)).isEqualTo("abc-123_DEF.45");
    }

    @Test
    void rejectsUnsafeInboundId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // Newline would let a client forge extra lines in the plain console format.
        request.addHeader(HEADER, "evil\nINFO forged log line");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] idDuringChain = new String[1];

        filter.doFilter(request, response, (req, res) -> idDuringChain[0] = MDC.get(MDC_KEY));

        assertThat(idDuringChain[0])
                .as("unsafe inbound id is dropped in favour of a generated one")
                .doesNotContain("\n")
                .isNotEqualTo("evil\nINFO forged log line");
    }

    @Test
    void clearsMdcEvenWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, (req, res) -> {
                throw new RuntimeException("boom");
            });
        } catch (Exception expected) {
            // ignored — we only care that the MDC is still cleaned up
        }

        assertThat(MDC.get(MDC_KEY)).as("MDC cleared even on failure").isNull();
    }
}
