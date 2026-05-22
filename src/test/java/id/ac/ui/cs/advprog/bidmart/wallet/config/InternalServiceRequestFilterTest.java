package id.ac.ui.cs.advprog.bidmart.wallet.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class InternalServiceRequestFilterTest {

    private InternalServiceRequestFilter filter;

    @BeforeEach
    void setUp() {
        filter = new InternalServiceRequestFilter();
        ReflectionTestUtils.setField(filter, "serviceToken", "service-secret");
    }

    @Test
    void doFilterInternal_allowsInternalGetWithoutIdempotencyKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/wallet/holds");
        request.addHeader("X-Service-Token", "service-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        verify(filterChain).doFilter(any(), any());
    }

    @Test
    void doFilterInternal_rejectsInternalPostWithBlankIdempotencyKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/v1/wallet/holds");
        request.addHeader("X-Service-Token", "service-secret");
        request.addHeader("Idempotency-Key", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        verifyNoInteractions(filterChain);
    }
}
