package id.ac.ui.cs.advprog.bidmart.wallet.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "01234567890123456789012345678901";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
    }

    @Test
    void extractSubject_returnsSubjectFromValidToken() {
        String token = createToken("wallet-user");

        assertThat(jwtService.extractSubject(token)).isEqualTo("wallet-user");
    }

    @Test
    void isTokenValid_returnsTrueForValidToken() {
        assertThat(jwtService.isTokenValid(createToken("valid-user"))).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseForMalformedToken() {
        assertThat(jwtService.isTokenValid("not-a-jwt")).isFalse();
    }

    private String createToken(String subject) {
        return Jwts.builder()
                .subject(subject)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
