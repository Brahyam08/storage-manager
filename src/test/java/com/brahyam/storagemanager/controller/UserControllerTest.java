package com.brahyam.storagemanager.controller;

import com.brahyam.storagemanager.config.JwtService;
import com.brahyam.storagemanager.entity.*;
import com.brahyam.storagemanager.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class UserControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    private User caller;
    private User other;

    @BeforeEach
    void setUp() {
        caller = users.save(new User(null, "caller", "caller@example.com", "unused-test-password", Role.VIEWER));
        other = users.save(new User(null, "other", "other@example.com", "unused-test-password", Role.VIEWER));
    }

    private String token(Role role) {
        caller.setRole(role);
        users.saveAndFlush(caller);
        return "Bearer " + jwt.generateToken(caller.getEmail());
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void shouldReadOnlyOwnProfileWithoutPassword(Role role) throws Exception {
        String token = token(role);
        for (String path : new String[]{"/api/users/me", "/api/users/" + caller.getId()}) {
            mvc.perform(get(path).header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(caller.getId()))
                    .andExpect(jsonPath("$.username").value("caller"))
                    .andExpect(jsonPath("$.email").value("caller@example.com"))
                    .andExpect(jsonPath("$.role").value(role.name()))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void shouldHideOtherAndMissingAccountsEvenFromAdmins(Role role) throws Exception {
        String token = token(role);
        String foreign = mvc.perform(get("/api/users/{id}", other.getId()).header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Usuario no encontrado"))
                .andReturn().getResponse().getContentAsString();
        String missing = mvc.perform(get("/api/users/{id}", Long.MAX_VALUE).header("Authorization", token))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertEquals(foreign, missing);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void shouldForbidListingAccounts(Role role) throws Exception {
        String token = token(role);
        for (String path : new String[]{"/api/users", "/api/users/"}) {
            mvc.perform(get(path).header("Authorization", token))
                    .andExpect(status().isForbidden())
                    .andExpect(content().string(""));
        }
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void shouldForbidDeletingAnyAccountAndPreserveAllUsers(Role role) throws Exception {
        String token = token(role);
        for (Long id : new Long[]{caller.getId(), other.getId(), Long.MAX_VALUE}) {
            mvc.perform(delete("/api/users/{id}", id).header("Authorization", token))
                    .andExpect(status().isForbidden());
        }
        assertTrue(users.existsById(caller.getId()));
        assertTrue(users.existsById(other.getId()));
        assertEquals(2, users.count());
    }

    @Test
    void shouldRejectAnonymousAccountAccess() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/users/{id}", caller.getId())).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/users")).andExpect(status().is4xxClientError());
        mvc.perform(delete("/api/users/{id}", other.getId())).andExpect(status().is4xxClientError());
        assertTrue(users.existsById(other.getId()));
    }
}
