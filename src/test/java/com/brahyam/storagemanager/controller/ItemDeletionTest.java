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
class ItemDeletionTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private HouseRepository houses;
    @Autowired private HouseMemberRepository members;
    @Autowired private LocationRepository locations;
    @Autowired private ItemRepository items;
    @Autowired private ItemMovementRepository movements;
    @Autowired private ItemConsumptionRepository consumptions;
    @Autowired private JwtService jwt;
    @Autowired private ItemRestockRepository restocks;
    @Autowired private ItemUseRepository uses;
    private Item item;
    private Location source;
    private Location target;
    private User owner;
    private User creator;
    private User fellow;
    private String ownerToken;
    private String creatorToken;
    private String fellowToken;
    private String outsiderToken;

    @BeforeEach
    void setUp() {
        owner = users.save(new User(null, "owner", "owner@consume.test", "unused", Role.VIEWER));
        creator = users.save(new User(null, "creator", "creator@consume.test", "unused", Role.ADMIN));
        fellow = users.save(new User(null, "fellow", "fellow@consume.test", "unused", Role.VIEWER));
        User outsider = users.save(new User(null, "outsider", "outsider@consume.test", "unused", Role.ADMIN));
        House house = houses.save(new House(null, "Casa", creator));
        members.save(new HouseMember(null, house, owner));
        members.save(new HouseMember(null, house, fellow));
        source = new Location(null, "Cajón", null);
        source.setHouse(house);
        locations.saveAndFlush(source);
        target = new Location(null, "Armario", null);
        target.setHouse(house);
        locations.saveAndFlush(target);
        item = new Item(null, "Tornillos", null, "Ferretería", 10, source);
        item.setOwner(owner);
        items.saveAndFlush(item);
        ownerToken = token(owner);
        creatorToken = token(creator);
        fellowToken = token(fellow);
        outsiderToken = token(outsider);
    }


    @Test
    void shouldDeleteAnArchivedItemAndItsHistoryButPreserveOtherRecords() throws Exception {
        Item other = new Item(null, "Otro", null, null, 4, source);
        other.setOwner(owner);
        items.saveAndFlush(other);
        mvc.perform(post("/api/items/{id}/consume", other.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}")).andExpect(status().isOk());
        mvc.perform(post("/api/items/{id}/consume", item.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}")).andExpect(status().isOk());
        mvc.perform(post("/api/items/{id}/restocks", item.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}")).andExpect(status().isOk());
        mvc.perform(patch("/api/items/{id}/move", item.getId()).header("Authorization", ownerToken)
                .param("toLocationId", target.getId().toString()).param("quantity", "9")).andExpect(status().isOk());
        mvc.perform(patch("/api/items/{id}/archive", item.getId()).header("Authorization", ownerToken)).andExpect(status().isOk());
        remove(ownerToken, 204);
        assertFalse(items.existsById(item.getId()));
        assertTrue(movements.findByItemIdOrderByMovedAtDescIdDesc(item.getId()).isEmpty());
        assertTrue(consumptions.findByItemIdOrderByConsumedAtDescIdDesc(item.getId()).isEmpty());
        assertTrue(restocks.findByItemIdOrderByRestockedAtDescIdDesc(item.getId()).isEmpty());
        assertEquals(1, consumptions.findByItemIdOrderByConsumedAtDescIdDesc(other.getId()).size());
        assertEquals(3, items.findById(other.getId()).orElseThrow().getQuantity());
        assertTrue(locations.existsById(source.getId()));
        assertTrue(users.existsById(owner.getId()));
    }

    @Test
    void shouldDeleteAnArchivedItemWithoutRestoringItsArchivedLocation() throws Exception {
        mvc.perform(patch("/api/items/{id}/archive", item.getId()).header("Authorization", ownerToken)).andExpect(status().isOk());
        mvc.perform(patch("/api/locations/{id}/archive", source.getId()).header("Authorization", ownerToken)).andExpect(status().isOk());
        remove(ownerToken, 204);
        assertTrue(locations.findById(source.getId()).orElseThrow().isArchived());
        remove(ownerToken, 404);
    }

    @Test
    void shouldKeepAnActiveWithdrawalAndAllowDeletionAfterReturn() throws Exception {
        item.setKind(ItemKind.REUSABLE);
        items.saveAndFlush(item);
        String body = mvc.perform(post("/api/items/{id}/uses", item.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number useId = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        remove(ownerToken, 409);
        assertEquals(1, uses.activeQuantity(item.getId()));
        assertTrue(items.existsById(item.getId()));
        mvc.perform(post("/api/items/{id}/uses/{useId}/return", item.getId(), useId).header("Authorization", ownerToken))
                .andExpect(status().isOk());
        remove(ownerToken, 204);
        assertTrue(uses.findByItemIdOrderByTakenAtDescIdDesc(item.getId()).isEmpty());
    }

    @Test
    void shouldRequireOwnershipForDeletingActiveOrArchivedItemsWithHistory() throws Exception {
        mvc.perform(post("/api/items/{id}/restocks", item.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}")).andExpect(status().isOk());
        remove(creatorToken, 404);
        remove(fellowToken, 404);
        mvc.perform(patch("/api/items/{id}/archive", item.getId()).header("Authorization", ownerToken)).andExpect(status().isOk());
        remove(outsiderToken, 404);
        remove(creatorToken, 404);
        item.setVisibility(ItemVisibility.HOUSE_SHARED);
        items.saveAndFlush(item);
        remove(fellowToken, 403);
        remove(creatorToken, 403);
        assertEquals(1, restocks.count());
        assertTrue(items.existsById(item.getId()));
    }

    private String token(User user) { return "Bearer " + jwt.generateToken(user.getEmail()); }
    private void remove(String actor, int expected) throws Exception {
        mvc.perform(delete("/api/items/{id}", item.getId()).header("Authorization", actor))
                .andExpect(status().is(expected));
    }
}
