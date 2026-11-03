package com.brahyam.storagemanager.config;

import com.brahyam.storagemanager.entity.*;
import com.brahyam.storagemanager.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:auth-rate-limit-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "security.auth-rate-limit.login-limit=2", "security.auth-rate-limit.registration-limit=2"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthRateLimitHttpTest {
    private static final AtomicInteger NEXT_ADDRESS = new AtomicInteger();
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    private String address;

    @BeforeEach
    void setUp() { address = "198.51.100." + NEXT_ADDRESS.incrementAndGet(); }

    private ResultActions login(String remoteAddress) throws Exception {
        return mvc.perform(post("/api/auth/login").with(request -> { request.setRemoteAddr(remoteAddress); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"missing@example.test\",\"password\":\"wrong-password\"}"));
    }

    private ResultActions register(String name) throws Exception {
        return mvc.perform(post("/api/auth/register").with(request -> { request.setRemoteAddr(address); return request; })
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + name
                        + "\",\"email\":\"" + name + "@example.test\",\"password\":\"secret-password\"}"));
    }

    @Test
    void shouldReturn429AndRetryAfterWhenLoginQuotaIsExhausted() throws Exception {
        login(address).andExpect(status().isUnauthorized());
        login(address).andExpect(status().isUnauthorized());
        login(address).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", org.hamcrest.Matchers.matchesPattern("[1-9][0-9]*")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("{\"error\":\"Demasiados intentos. Inténtalo de nuevo más tarde\"}"));
    }

    @Test
    void shouldIgnoreForgedForwardingHeadersAndApplyQuotaWithContextPath() throws Exception {
        login(address).andExpect(status().isUnauthorized());
        login(address).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(request -> { request.setRemoteAddr(address); return request; })
                        .header("X-Forwarded-For", "203.0.113.1").header("Forwarded", "for=203.0.113.2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"different@example.test\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isTooManyRequests());
        mvc.perform(post("/prefix/api/auth/login").contextPath("/prefix")
                        .with(request -> { request.setRemoteAddr(address); return request; })
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests());
        login("203.0.113.99").andExpect(status().isUnauthorized());
    }

    @Test
    void shouldCountSuccessfulRegistrationsAndKeepLoginQuotaSeparate() throws Exception {
        register("first").andExpect(status().isCreated());
        register("second").andExpect(status().isCreated());
        register("third").andExpect(status().isTooManyRequests());
        assertEquals(2, users.count());
        mvc.perform(post("/api/auth/login").with(request -> { request.setRemoteAddr(address); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"first@example.test\",\"password\":\"secret-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void shouldCountMalformedRequestsBeforeParsingTheirBody() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/register").with(request -> { request.setRemoteAddr(address); return request; })
                            .contentType(MediaType.APPLICATION_JSON).content("{broken"))
                    .andExpect(status().is(i < 2 ? 400 : 429));
        }
        assertEquals(0, users.count());
    }

    @Test
    void shouldKeepAuthenticatedInventoryAccessAvailableAfterLoginIsLimited() throws Exception {
        User user = users.saveAndFlush(new User(null, "viewer", "viewer@example.test", "unused", Role.VIEWER));
        String token = "Bearer " + jwt.generateToken(user.getEmail());
        login(address).andExpect(status().isUnauthorized());
        login(address).andExpect(status().isUnauthorized());
        login(address).andExpect(status().isTooManyRequests());
        for (int i = 0; i < 4; i++) {
            mvc.perform(get("/api/users/me").header("Authorization", token)
                            .with(request -> { request.setRemoteAddr(address); return request; }))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(user.getId()));
        }
    }

    @Test
    void shouldNotCountUnsupportedHttpMethods() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/auth/login").with(request -> { request.setRemoteAddr(address); return request; }))
                    .andExpect(status().isMethodNotAllowed());
        }
        login(address).andExpect(status().isUnauthorized());
        login(address).andExpect(status().isUnauthorized());
        login(address).andExpect(status().isTooManyRequests());
    }
}
