package id.ac.ui.cs.advprog.bidmart.wallet.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalServiceRequestFilter extends OncePerRequestFilter {

    @Value("${internal.service-token}")
    private String serviceToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/internal/v1/wallet")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader("X-Service-Token");
        if (!serviceToken.equals(token)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        if (HttpMethod.POST.matches(request.getMethod())) {
            String idempotencyKey = request.getHeader("Idempotency-Key");
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
