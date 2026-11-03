package com.brahyam.storagemanager.controller;

import com.brahyam.storagemanager.config.JwtService;
import com.brahyam.storagemanager.entity.*;
import com.brahyam.storagemanager.repository.*;
import org.junit.jupiter.api.*;
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
class LocationControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private HouseRepository houses;
    @Autowired private HouseMemberRepository members;
    @Autowired private LocationRepository locations;
    @Autowired private ItemRepository items;
    @Autowired private ItemMovementRepository movements;
    @Autowired private JwtService jwt;
    private House house;
    private House secondHouse;
    private String ownerToken;
    private String memberToken;
    private String outsiderToken;

    @BeforeEach
    void setUp() {
        User owner = users.save(new User(null, "owner", "owner@test.com", "unused", Role.VIEWER));
        User member = users.save(new User(null, "member", "member@test.com", "unused", Role.VIEWER));
        User outsider = users.save(new User(null, "outsider", "outsider@test.com", "unused", Role.ADMIN));
        house = houses.save(new House(null, "Casa", owner));
        secondHouse = houses.save(new House(null, "Segunda", owner));
        members.save(new HouseMember(null, house, member));
        ownerToken = "Bearer " + jwt.generateToken(owner.getEmail());
        memberToken = "Bearer " + jwt.generateToken(member.getEmail());
        outsiderToken = "Bearer " + jwt.generateToken(outsider.getEmail());
    }

    @Test
    void shouldCreateNestedLocationsAndReturnTheirFullPath() throws Exception {
        long room = create("  Despacho  ", null, house.getId(), ownerToken);
        long cabinet = create("Cajonera", room, house.getId(), ownerToken);
        long drawer = create("Cajón 2", cabinet, house.getId(), ownerToken);
        mvc.perform(get("/api/locations/{id}", drawer).header("Authorization", memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("Despacho → Cajonera → Cajón 2"))
                .andExpect(jsonPath("$.houseId").value(house.getId()))
                .andExpect(jsonPath("$.parentId").value(cabinet))
                .andExpect(jsonPath("$.house").doesNotExist())
                .andExpect(jsonPath("$.parent").doesNotExist());
    }

    @Test
    void shouldListOnlyLocationsOfTheSelectedHouse() throws Exception {
        create("Primera", null, house.getId(), ownerToken);
        create("Segunda", null, secondHouse.getId(), ownerToken);
        mvc.perform(get("/api/locations").param("houseId", house.getId().toString())
                        .header("Authorization", memberToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Primera"));
    }

    @Test
    void shouldAllowMembersToOrganizeLocationsAndUpdateDescendantPaths() throws Exception {
        long room = create("Despacho", null, house.getId(), memberToken);
        long otherRoom = create("Salón", null, house.getId(), ownerToken);
        long cabinet = create("Cajonera", room, house.getId(), memberToken);
        long drawer = create("Cajón", cabinet, house.getId(), ownerToken);
        update(cabinet, "Armario", otherRoom, house.getId(), memberToken, 200);
        mvc.perform(get("/api/locations/{id}", drawer).header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.path").value("Salón → Armario → Cajón"));
    }

    @Test
    void shouldRejectAllLocationOperationsForOutsidersEvenAdmins() throws Exception {
        long id = create("Privada", null, house.getId(), ownerToken);
        mvc.perform(get("/api/locations/{id}", id).header("Authorization", outsiderToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/locations").param("houseId", house.getId().toString())
                        .header("Authorization", outsiderToken)).andExpect(status().isNotFound());
        mvc.perform(post("/api/locations").header("Authorization", outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body("Ajena", null, house.getId())))
                .andExpect(status().isNotFound());
        update(id, "Ajena", null, house.getId(), outsiderToken, 404);
        mvc.perform(delete("/api/locations/{id}", id).header("Authorization", outsiderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectCyclesAndCrossHouseParentsWithoutChangingTheTree() throws Exception {
        long root = create("Raíz", null, house.getId(), ownerToken);
        long child = create("Hijo", root, house.getId(), ownerToken);
        long foreign = create("Otra casa", null, secondHouse.getId(), ownerToken);
        update(root, "Raíz", root, house.getId(), ownerToken, 400);
        update(root, "Raíz", child, house.getId(), ownerToken, 400);
        update(child, "Hijo", foreign, house.getId(), ownerToken, 404);
        update(child, "Hijo", null, secondHouse.getId(), ownerToken, 400);
        mvc.perform(get("/api/locations/{id}", child).header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.path").value("Raíz → Hijo"));
        mvc.perform(post("/api/locations").header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body("Incorrecta", foreign, house.getId())))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteEmptyLocationsButKeepThoseWithChildrenOrItems() throws Exception {
        long root = create("Raíz", null, house.getId(), ownerToken);
        long child = create("Hijo", root, house.getId(), ownerToken);
        mvc.perform(delete("/api/locations/{id}", root).header("Authorization", ownerToken))
                .andExpect(status().isConflict());
        Item item = items.saveAndFlush(new Item(null, "Ratón", null, null, 1, locations.findById(child).orElseThrow()));
        mvc.perform(delete("/api/locations/{id}", child).header("Authorization", memberToken))
                .andExpect(status().isConflict());
        items.delete(item);
        items.flush();
        mvc.perform(delete("/api/locations/{id}", child).header("Authorization", memberToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/locations/{id}", child).header("Authorization", ownerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldKeepLocationsReferencedByMovementHistory() throws Exception {
        long source = create("Origen", null, house.getId(), ownerToken);
        long target = create("Destino", null, house.getId(), ownerToken);
        Location from = locations.findById(source).orElseThrow();
        Location to = locations.findById(target).orElseThrow();
        Item item = items.saveAndFlush(new Item(null, "Ratón", null, null, 1, to));
        movements.saveAndFlush(new ItemMovement(null, item, from, to, 1, java.time.LocalDateTime.now()));
        mvc.perform(delete("/api/locations/{id}", source).header("Authorization", ownerToken))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldRequireHouseAndNameAndPreserveUnassignedLegacyLocations() throws Exception {
        Location legacy = locations.saveAndFlush(new Location(null, "Antigua", "Sin casa"));
        mvc.perform(get("/api/locations/{id}", legacy.getId()).header("Authorization", ownerToken))
                .andExpect(status().isNotFound());
        assertTrue(locations.existsById(legacy.getId()));
        for (String invalid : new String[]{"{}", "{\"name\":\"Nombre\"}", body("   ", null, house.getId())}) {
            mvc.perform(post("/api/locations").header("Authorization", ownerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/locations").header("Authorization", ownerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRequireAuthentication() throws Exception {
        mvc.perform(get("/api/locations").param("houseId", house.getId().toString()))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/api/locations").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Sala", null, house.getId()))).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/locations/1")).andExpect(status().is4xxClientError());
        mvc.perform(put("/api/locations/1").contentType(MediaType.APPLICATION_JSON)
                        .content(body("Sala", null, house.getId()))).andExpect(status().is4xxClientError());
        mvc.perform(delete("/api/locations/1")).andExpect(status().is4xxClientError());
    }

    private String body(String name, Long parentId, Long houseId) {
        return "{\"name\":\"" + name + "\",\"houseId\":" + houseId + ",\"parentId\":" + parentId + "}";
    }

    private long create(String name, Long parentId, Long houseId, String token) throws Exception {
        String response = mvc.perform(post("/api/locations").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(name, parentId, houseId)))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.id")).longValue();
    }

    private void update(long id, String name, Long parentId, Long houseId, String token, int expected) throws Exception {
        mvc.perform(put("/api/locations/{id}", id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(name, parentId, houseId)))
                .andExpect(status().is(expected));
    }
}
