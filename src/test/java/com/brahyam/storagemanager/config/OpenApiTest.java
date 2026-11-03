package com.brahyam.storagemanager.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.startsWith;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiTest {
    @Autowired private MockMvc mvc;

    @Test
    void shouldPublishInventorySchemaAndBearerAuthenticationWithoutRequiringLogin() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi", startsWith("3.")))
                .andExpect(jsonPath("$.paths['/api/items/{id}/consume'].post").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.security").isEmpty());
    }

    @Test
    void shouldServeSwaggerUiAndItsConfigurationWithoutRequiringLogin() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"));
    }
}
