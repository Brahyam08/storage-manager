package com.brahyam.storagemanager.config;

import com.brahyam.storagemanager.entity.Role;
import com.brahyam.storagemanager.entity.User;
import com.brahyam.storagemanager.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "beta"})
@Transactional
class BetaSecurityTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwords;
    @Autowired private JwtService jwt;

    @Test
    void browserPoliciesAlsoProtectUnauthenticatedResponses() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("script-src 'self'"),
                                org.hamcrest.Matchers.containsString("style-src 'self'"),
                                org.hamcrest.Matchers.containsString("frame-ancestors 'none'"))))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void cachesOnlySuccessfulVersionedPublicAssets() throws Exception {
        mvc.perform(get("/chunk-BETATEST1.js").servletPath("/chunk-BETATEST1.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"));
        mvc.perform(get("/chunk-MISSING1.js").servletPath("/chunk-MISSING1.js"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void closedRegistrationCannotCreateAccountsEvenWhenClientSendsAdminRole() throws Exception {
        long before = users.count();
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"username":"Intruder","email":"intruder@example.test",
                     "password":"correct-password","role":"ADMIN"}
                    """))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"El registro está cerrado en esta beta\"}"));
        assertEquals(before, users.count());
    }

    @Test
    void provisionedTesterCanLoginButCannotReadAnotherAccount() throws Exception {
        User tester = users.save(new User(null, "Tester", "tester@example.test",
                passwords.encode("correct-password"), Role.VIEWER));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"tester@example.test\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
        String token = jwt.generateToken(tester.getEmail());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(get("/api/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        for (String path : new String[]{"/v3/api-docs", "/swagger-ui/index.html"}) {
            mvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());
        }
    }
}
