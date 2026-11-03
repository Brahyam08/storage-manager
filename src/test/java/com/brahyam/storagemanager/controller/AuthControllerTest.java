package com.brahyam.storagemanager.controller;

import com.brahyam.storagemanager.config.JwtService;
import com.brahyam.storagemanager.entity.*;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private PasswordEncoder passwords;
    @Autowired private JwtService jwt;

    private String registration(String name, String email, String password) {
        return "{\"username\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
    }

    private void register(String name, String email, String password) throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registration(name, email, password))).andExpect(status().isCreated());
    }

    private String login(String email, String password, int expected) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }

    @Test
    void shouldRegisterViewerWithHashedPasswordAndLoginUsingCanonicalEmail() throws Exception {
        String request = registration("  Alice  ", " Alice@Example.test ", "correct-password")
                .replace("}", ",\"role\":\"ADMIN\"}");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.username").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@example.test"))
                .andExpect(jsonPath("$.role").value("VIEWER"))
                .andExpect(jsonPath("$.password").doesNotExist());
        User saved = users.findByEmail("alice@example.test").orElseThrow();
        assertNotEquals("correct-password", saved.getPassword());
        assertTrue(passwords.matches("correct-password", saved.getPassword()));
        String response = login(" ALICE@EXAMPLE.TEST ", "correct-password", 200);
        String token = com.jayway.jsonpath.JsonPath.read(response, "$.token");
        assertEquals(saved.getEmail(), jwt.extractEmail(token));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(saved.getId()));
    }

    @Test
    void shouldRejectInvalidRegistrationWithoutSavingAccounts() throws Exception {
        for (String body : new String[]{"{}", "{\"username\":null,\"email\":null,\"password\":null}",
                registration(" ", "a@example.test", "correct-password"),
                registration("a".repeat(256), "a@example.test", "correct-password"),
                registration("alice", "not-email", "correct-password"),
                registration("alice", " ", "correct-password"),
                registration("alice", "a".repeat(256) + "@example.test", "correct-password"),
                registration("alice", "a@example.test", ""), registration("alice", "a@example.test", "       "),
                registration("alice", "a@example.test", "short"), registration("alice", "a@example.test", "x".repeat(73)),
                registration("alice", "a@example.test", "é".repeat(36) + "a")}) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertEquals(0, users.count());
    }

    @Test
    void shouldRejectDuplicateUsernameOrCaseInsensitiveEmailWithoutDatabaseDetails() throws Exception {
        register("Alice", "Alice@Example.test", "correct-password");
        for (String body : new String[]{registration("Alice", "different@example.test", "other-password"),
                registration("Different", " ALICE@example.test ", "other-password")}) {
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isConflict())
                    .andExpect(content().json("{\"error\":\"No se puede registrar esa cuenta\"}"));
        }
        assertEquals(1, users.count());
    }

    @Test
    void shouldReturnSameUnauthorizedErrorForWrongPasswordAndUnknownAccount() throws Exception {
        register("Alice", "alice@example.test", "correct-password");
        String wrong = login("alice@example.test", "wrong-password", 401);
        String missing = login("missing@example.test", "wrong-password", 401);
        assertEquals(wrong, missing);
        assertEquals("Credenciales incorrectas", com.jayway.jsonpath.JsonPath.read(wrong, "$.error"));
        assertFalse(wrong.contains("alice"));
        assertFalse(wrong.contains("wrong-password"));
    }

    @Test
    void shouldValidateLoginBeforeAuthenticating() throws Exception {
        for (String body : new String[]{"{}", "{\"email\":null,\"password\":null}",
                "{\"email\":\"invalid\",\"password\":\"whatever\"}",
                "{\"email\":\"valid@example.test\",\"password\":\" \"}",
                registration("ignored", "valid@example.test", "x".repeat(73)),
                registration("ignored", "valid@example.test", "é".repeat(36) + "a")}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void shouldSupportExactly72Utf8BytesWithoutTruncatingOrTrimmingPasswords() throws Exception {
        String password = "é".repeat(36);
        register("Unicode", "unicode@example.test", password);
        login("unicode@example.test", password, 200);
        login("unicode@example.test", "é".repeat(35) + "a", 401);
        login("unicode@example.test", password + "a", 400);
        register("Spaces", "spaces@example.test", "  secret-password  ");
        login("spaces@example.test", "  secret-password  ", 200);
        login("spaces@example.test", "secret-password", 401);
    }

    @Test
    void shouldPreserveLegacyEmailCaseAndRejectAmbiguousLegacyAccounts() throws Exception {
        users.saveAndFlush(new User(null, "Legacy", "Legacy@Example.test", passwords.encode("old"), Role.VIEWER));
        String response = login("legacy@example.test", "old", 200);
        String token = com.jayway.jsonpath.JsonPath.read(response, "$.token");
        assertEquals("Legacy@Example.test", jwt.extractEmail(token));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        users.saveAndFlush(new User(null, "Other", "legacy@example.test", passwords.encode("old"), Role.VIEWER));
        login("legacy@example.test", "old", 401);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnGenericBadRequestForMalformedJson() throws Exception {
        for (String path : new String[]{"/api/auth/register", "/api/auth/login"}) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{broken"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().json("{\"error\":\"El cuerpo de la petición no es válido\"}"));
        }
    }
}
