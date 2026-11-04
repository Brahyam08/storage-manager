package com.brahyam.storagemanager.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebRoutingTest {
    @Autowired private MockMvc mvc;

    @Test
    void shouldServeOnlyKnownClientRoutesWithoutExposingInventory() throws Exception {
        for (String path : new String[]{"/", "/acceso", "/registro", "/inventario", "/perfil", "/objetos/4"}) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(forwardedUrl("/index.html"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }
        mvc.perform(head("/inventario")).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        mvc.perform(get("/api/items")).andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"Autenticación requerida\"}"));
    }

    @Test
    void shouldNotForwardUnknownApiPathsOrAssetLookalikesToTheApp() throws Exception {
        for (String path : new String[]{"/api/unknown", "/api/missing.js", "/api/items/missing.css", "/api/users", "/objetos/missing.js", "/objetos/4/history", "/api/missing.webmanifest"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/missing-bundle.js")).andExpect(status().isNotFound());
        mvc.perform(get("/media/missing-image.png")).andExpect(status().isNotFound());
        mvc.perform(get("/icons/missing.png")).andExpect(status().isNotFound());
    }

    @Test
    void shouldNotAllowMutationRequestsToPublicUiPaths() throws Exception {
        mvc.perform(post("/inventario")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/index.html")).andExpect(status().isUnauthorized());
        mvc.perform(post("/manifest.webmanifest")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/icons/icon-192.png")).andExpect(status().isUnauthorized());
    }
}
