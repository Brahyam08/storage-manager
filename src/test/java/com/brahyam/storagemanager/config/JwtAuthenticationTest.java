package com.brahyam.storagemanager.config;

import com.brahyam.storagemanager.entity.*;
import com.brahyam.storagemanager.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.stream.Stream;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtAuthenticationTest {
    private static final String SECRET = "storage-manager-test-secret-key-which-is-long-enough";
    private static final String EMAIL = "jwt-user@example.com";
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    @Autowired private PasswordEncoder passwords;

    @BeforeEach
    void setUp() {
        users.saveAndFlush(new User(null, "jwt-user", EMAIL, passwords.encode("test-password"), Role.VIEWER));
    }

    static Stream<String> invalidTokens() {
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date future = new Date(System.currentTimeMillis() + 600_000);
        return Stream.of(
                "", "   ", "not-a-jwt",
                new JwtService(SECRET, -60_000).generateToken(EMAIL),
                new JwtService("different-test-signing-secret-at-least-32-characters", 600_000).generateToken(EMAIL),
                new JwtService(SECRET, 600_000).generateToken("missing@example.com"),
                Jwts.builder().expiration(future).signWith(key).compact(),
                Jwts.builder().subject("   ").expiration(future).signWith(key).compact(),
                Jwts.builder().subject(EMAIL).signWith(key).compact(),
                Jwts.builder().subject(EMAIL).notBefore(future)
                        .expiration(new Date(future.getTime() + 600_000)).signWith(key).compact());
    }

    @ParameterizedTest
    @MethodSource("invalidTokens")
    void shouldRejectInvalidCredentialsWithoutLeakingDetails(String token) throws Exception {
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"error\":\"Autenticación requerida\"}"))
                .andExpect(cookie().doesNotExist("JSESSIONID"));
    }

    @Test
    void shouldRequireAuthenticationWhenTokenIsMissing() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void shouldAcceptValidTokenWithoutReusingAuthenticationInLaterRequests() throws Exception {
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + jwt.generateToken(EMAIL)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(cookie().doesNotExist("JSESSIONID"));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowPublicLoginEvenWhenAnOldBearerTokenIsInvalid() throws Exception {
        mvc.perform(post("/api/auth/login").header("Authorization", "Bearer invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"jwt-user@example.com\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }
}
