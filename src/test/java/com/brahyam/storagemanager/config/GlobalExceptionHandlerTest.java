package com.brahyam.storagemanager.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ErrorFixture())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @RestController
    static class ErrorFixture {
        @GetMapping("/internal")
        public String internal() { throw new IllegalStateException("private internal details"); }
        @GetMapping("/conflict")
        public String conflict() { throw new DataIntegrityViolationException("private SQL details"); }
        @GetMapping("/credentials")
        public String credentials() { throw new BadCredentialsException("private credentials"); }
        @GetMapping("/forbidden")
        public String forbidden() { throw new AccessDeniedException("private role details"); }
        @GetMapping("/parameter")
        public Long parameter(@RequestParam Long id) { return id; }
    }

    @Test
    void shouldReturnGeneric500ForUnexpectedErrors() throws Exception {
        mvc.perform(get("/internal")).andExpect(status().isInternalServerError())
                .andExpect(content().json("{\"error\":\"Error interno del servidor\"}"));
    }

    @Test
    void shouldHideDatabaseConflictDetails() throws Exception {
        mvc.perform(get("/conflict")).andExpect(status().isConflict())
                .andExpect(content().json("{\"error\":\"La operación entra en conflicto con los datos existentes\"}"));
    }

    @Test
    void shouldUseGenericAuthenticationAndAuthorizationErrors() throws Exception {
        mvc.perform(get("/credentials")).andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"Credenciales incorrectas\"}"));
        mvc.perform(get("/forbidden")).andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"Acceso denegado\"}"));
    }

    @Test
    void shouldReturn400ForMissingOrInvalidParameters() throws Exception {
        mvc.perform(get("/parameter")).andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"Los parámetros de la petición no son válidos\"}"));
        mvc.perform(get("/parameter").param("id", "invalid")).andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"Los parámetros de la petición no son válidos\"}"));
    }

    @Test
    void shouldKeepFrameworkMethodNotAllowedStatus() throws Exception {
        mvc.perform(post("/internal")).andExpect(status().isMethodNotAllowed());
    }
}
