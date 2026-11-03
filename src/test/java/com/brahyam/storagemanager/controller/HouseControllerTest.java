package com.brahyam.storagemanager.controller;

import com.brahyam.storagemanager.config.JwtService;
import com.brahyam.storagemanager.entity.House;
import com.brahyam.storagemanager.entity.Role;
import com.brahyam.storagemanager.entity.User;
import com.brahyam.storagemanager.repository.HouseRepository;
import com.brahyam.storagemanager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
class HouseControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private HouseRepository houses;
    @Autowired private JwtService jwtService;
    private User owner;
    private User other;
    private String ownerToken;
    private String otherToken;

    @BeforeEach
    void setUp() {
        owner = users.save(new User(null, "owner", "owner@example.com", "unused-test-password", Role.VIEWER));
        other = users.save(new User(null, "other", "other@example.com", "unused-test-password", Role.ADMIN));
        ownerToken = "Bearer " + jwtService.generateToken(owner.getEmail());
        otherToken = "Bearer " + jwtService.generateToken(other.getEmail());
    }

    @Test
    void shouldCreateHouseForAuthenticatedUserAndAllowReadingIt() throws Exception {
        mvc.perform(post("/api/houses").header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  Casa Valencia  \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Casa Valencia"))
                .andExpect(jsonPath("$.ownerId").value(owner.getId().intValue()))
                .andExpect(jsonPath("$.owner").doesNotExist());
        House house = houses.findAllByOwnerIdOrderByIdAsc(owner.getId()).getFirst();
        assertEquals(owner.getId(), house.getOwner().getId());
        mvc.perform(get("/api/houses/{id}", house.getId()).header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Casa Valencia"));
    }

    @Test
    void shouldRejectMissingEmptyAndBlankName() throws Exception {
        for (String body : new String[]{"{}", "{\"name\":null}", "{\"name\":\"\"}", "{\"name\":\"   \"}"}) {
            mvc.perform(post("/api/houses").header("Authorization", ownerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.name").exists());
        }
        assertEquals(0, houses.count());
    }

    @Test
    void shouldNotAllowSelectingAnotherOwnerInRequest() throws Exception {
        mvc.perform(post("/api/houses").header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mi casa\",\"ownerId\":" + other.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownerId").value(owner.getId().intValue()));
        assertTrue(houses.findAllByOwnerIdOrderByIdAsc(other.getId()).isEmpty());
    }

    @Test
    void shouldHideHouseFromAnotherUserEvenWithAdminRole() throws Exception {
        House house = houses.saveAndFlush(new House(null, "Privada", owner));
        mvc.perform(get("/api/houses/{id}", house.getId()).header("Authorization", otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Casa no encontrada"));
        mvc.perform(get("/api/houses").header("Authorization", otherToken))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void shouldListOnlyOwnedHouses() throws Exception {
        houses.save(new House(null, "Mi casa", owner));
        houses.save(new House(null, "Otra casa", other));
        mvc.perform(get("/api/houses").header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Mi casa"));
    }

    @Test
    void shouldReturnNotFoundForMissingHouse() throws Exception {
        mvc.perform(get("/api/houses/{id}", Long.MAX_VALUE).header("Authorization", ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldJoinHouseAndKeepMembershipWhenInvitationIsRenewed() throws Exception {
        House house = houses.saveAndFlush(new House(null, "Compartida", owner));
        String code = invitationCode(house.getId());
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/houses/join").header("Authorization", otherToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"code\":\"" + code + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(house.getId()));
        }
        mvc.perform(get("/api/houses/{id}", house.getId()).header("Authorization", otherToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/houses").header("Authorization", otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/houses/{id}/invitation", house.getId()).header("Authorization", otherToken))
                .andExpect(status().isNotFound());
        String renewedCode = invitationCode(house.getId());
        assertNotEquals(code, renewedCode);
        mvc.perform(post("/api/houses/join").header("Authorization", otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/houses/{id}", house.getId()).header("Authorization", otherToken))
                .andExpect(status().isOk());
        String token = "Bearer " + jwtService.generateToken(
                users.save(new User(null, "third", "third@example.com", "unused", Role.VIEWER)).getEmail());
        mvc.perform(post("/api/houses/join").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + renewedCode + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectInvalidInvitationsAndAnonymousMembershipRequests() throws Exception {
        mvc.perform(post("/api/houses/join").header("Authorization", otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"invalid\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/houses/join").header("Authorization", otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"  \"}"))
                .andExpect(status().isBadRequest());
        House house = houses.saveAndFlush(new House(null, "Privada", owner));
        mvc.perform(post("/api/houses/{id}/invitation", house.getId()).header("Authorization", otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/houses/{id}/invitation", house.getId()))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/api/houses/join").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"invalid\"}"))
                .andExpect(status().is4xxClientError());
    }

    private String invitationCode(Long houseId) throws Exception {
        String body = mvc.perform(post("/api/houses/{id}/invitation", houseId)
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.code");
    }

    @Test
    void shouldRejectAnonymousRequests() throws Exception {
        mvc.perform(post("/api/houses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Casa\"}"))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/api/houses")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/houses/1")).andExpect(status().is4xxClientError());
        assertEquals(0, houses.count());
    }
}
