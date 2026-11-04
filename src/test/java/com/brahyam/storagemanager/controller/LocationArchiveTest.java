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
class LocationArchiveTest {
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
    void shouldArchiveAnEmptyLocationWithHistoryAndRestoreTheSameRecord() throws Exception {
        long source = create("Cajón", null, house.getId(), ownerToken);
        long target = create("Estantería", null, house.getId(), ownerToken);
        Item item = new Item(null, "Teclado", null, null, 1, locations.findById(source).orElseThrow());
        item.setOwner(house.getOwner());
        items.saveAndFlush(item);
        mvc.perform(patch("/api/items/{id}/move", item.getId()).header("Authorization", ownerToken)
                .param("toLocationId", Long.toString(target)).param("quantity", "1"))
                .andExpect(status().isOk());
        archive(source, memberToken, 200);
        mvc.perform(get("/api/locations").param("houseId", house.getId().toString()).header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/locations").param("houseId", house.getId().toString()).param("includeArchived", "true").header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/items/{id}/history", item.getId()).header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].fromLocation.id").value(source))
                .andExpect(jsonPath("$[0].fromLocation.archived").value(true));
        restore(source, memberToken, 200);
        assertFalse(locations.findById(source).orElseThrow().isArchived());
        assertEquals(1, movements.count());
    }

    @Test
    void shouldRejectArchivingOccupiedLocationsEvenWithZeroStock() throws Exception {
        long source = create("Cajón", null, house.getId(), ownerToken);
        Item item = new Item(null, "Tornillos", null, null, 0, locations.findById(source).orElseThrow());
        item.setOwner(house.getOwner());
        items.saveAndFlush(item);
        archive(source, ownerToken, 409);
        assertFalse(locations.findById(source).orElseThrow().isArchived());
        assertEquals(source, item.getLocation().getId());
    }

    @Test
    void shouldRequireActiveChildrenToBeReorganizedAndParentsRestoredFirst() throws Exception {
        long parent = create("Cajonera", null, house.getId(), ownerToken);
        long child = create("Cajón", parent, house.getId(), ownerToken);
        archive(parent, ownerToken, 409);
        archive(child, ownerToken, 200);
        archive(parent, ownerToken, 200);
        restore(child, ownerToken, 409);
        restore(parent, ownerToken, 200);
        restore(child, ownerToken, 200);
        assertFalse(locations.findById(child).orElseThrow().isArchived());
    }

    @Test
    void shouldRejectNewItemsAndChildrenInsideAnArchivedLocation() throws Exception {
        long source = create("Cajón", null, house.getId(), ownerToken);
        archive(source, ownerToken, 200);
        mvc.perform(post("/api/items").header("Authorization", ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Objeto\",\"quantity\":1,\"locationId\":" + source + "}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/locations").header("Authorization", ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content(body("Interior", source, house.getId()))).andExpect(status().isConflict());
        assertEquals(0, items.count());
    }

    @Test
    void shouldRejectMovingAnItemOrLocationIntoAnArchivedDestination() throws Exception {
        long source = create("Cajón", null, house.getId(), ownerToken);
        long target = create("Antigua", null, house.getId(), ownerToken);
        Item item = new Item(null, "Objeto", null, null, 1, locations.findById(source).orElseThrow());
        item.setOwner(house.getOwner());
        items.saveAndFlush(item);
        archive(target, ownerToken, 200);
        mvc.perform(patch("/api/items/{id}/move", item.getId()).header("Authorization", ownerToken)
                .param("toLocationId", Long.toString(target)).param("quantity", "1"))
                .andExpect(status().isConflict());
        update(source, "Cajón", target, house.getId(), ownerToken, 409);
        assertEquals(source, items.findById(item.getId()).orElseThrow().getLocation().getId());
        assertEquals(0, movements.count());
    }

    @Test
    void shouldKeepArchivedLocationsReadOnlyUntilRestored() throws Exception {
        long source = create("Cajón", null, house.getId(), ownerToken);
        archive(source, ownerToken, 200);
        archive(source, ownerToken, 200);
        update(source, "Otro", null, house.getId(), ownerToken, 409);
        assertEquals("Cajón", locations.findById(source).orElseThrow().getName());
        restore(source, ownerToken, 200);
        restore(source, ownerToken, 200);
    }

    @Test
    void shouldKeepArchiveAndRestoreRestrictedToHouseMembers() throws Exception {
        long source = create("Cajón", null, house.getId(), ownerToken);
        archive(source, outsiderToken, 404);
        restore(source, outsiderToken, 404);
        mvc.perform(get("/api/locations").header("Authorization", outsiderToken)
                .param("houseId", house.getId().toString()).param("includeArchived", "true"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/locations/{id}/archive", source)).andExpect(status().isUnauthorized());
        assertFalse(locations.findById(source).orElseThrow().isArchived());
    }

    private void archive(long id, String token, int expected) throws Exception {
        mvc.perform(patch("/api/locations/{id}/archive", id).header("Authorization", token))
                .andExpect(status().is(expected));
    }
    private void restore(long id, String token, int expected) throws Exception {
        mvc.perform(patch("/api/locations/{id}/restore", id).header("Authorization", token))
                .andExpect(status().is(expected));
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
