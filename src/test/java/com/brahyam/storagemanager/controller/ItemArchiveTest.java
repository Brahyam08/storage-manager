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
class ItemArchiveTest {
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
    void shouldArchiveAnItemWithHistoryWithoutLosingItsStockOrRecords() throws Exception {
        mvc.perform(post("/api/items/{id}/consume", item.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}"))
                .andExpect(status().isOk());
        archive(ownerToken, 200);
        mvc.perform(get("/api/items").header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/items").param("includeArchived", "true").header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].archived").value(true))
                .andExpect(jsonPath("$[0].quantity").value(7));
        mvc.perform(get("/api/items/{id}/consumptions", item.getId()).header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        restore(ownerToken, 200);
        assertEquals(7, items.findById(item.getId()).orElseThrow().getQuantity());
    }

    @Test
    void shouldRequireTheOwnerAndNotExposePrivateArchivedItems() throws Exception {
        archive(creatorToken, 404);
        archive(fellowToken, 404);
        archive(outsiderToken, 404);
        archive(ownerToken, 200);
        mvc.perform(get("/api/items").param("includeArchived", "true").header("Authorization", creatorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/items/{id}", item.getId()).header("Authorization", creatorToken))
                .andExpect(status().isNotFound());
        restore(fellowToken, 404);
        item.setVisibility(ItemVisibility.HOUSE_SHARED);
        items.saveAndFlush(item);
        restore(fellowToken, 403);
        archive(creatorToken, 403);
    }

    @Test
    void shouldNotHideItemsThatAreStillInUse() throws Exception {
        item.setKind(ItemKind.REUSABLE);
        item.setQuantity(1);
        items.saveAndFlush(item);
        mvc.perform(post("/api/items/{id}/uses", item.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isCreated());
        archive(ownerToken, 409);
        assertFalse(items.findById(item.getId()).orElseThrow().isArchived());
    }

    @Test
    void shouldPreventChangesAndUsageUntilTheItemIsRestored() throws Exception {
        archive(ownerToken, 200);
        for (String action : new String[]{"consume", "restocks", "uses"}) {
            mvc.perform(post("/api/items/{id}/" + action, item.getId()).header("Authorization", ownerToken)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                    .andExpect(status().isConflict());
        }
        mvc.perform(patch("/api/items/{id}/move", item.getId()).header("Authorization", ownerToken)
                .param("toLocationId", target.getId().toString()).param("quantity", "10"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/items/{id}", item.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Otro\",\"quantity\":10,\"locationId\":" + source.getId() + "}"))
                .andExpect(status().isConflict());
        assertEquals(10, items.findById(item.getId()).orElseThrow().getQuantity());
    }

    @Test
    void shouldAllowArchivingItsLocationButRestoreThatLocationFirst() throws Exception {
        archive(ownerToken, 200);
        mvc.perform(patch("/api/locations/{id}/archive", source.getId()).header("Authorization", fellowToken))
                .andExpect(status().isOk());
        restore(ownerToken, 409);
        assertTrue(items.findById(item.getId()).orElseThrow().isArchived());
        mvc.perform(patch("/api/locations/{id}/restore", source.getId()).header("Authorization", fellowToken))
                .andExpect(status().isOk());
        restore(ownerToken, 200);
        assertFalse(items.findById(item.getId()).orElseThrow().isArchived());
    }

    @Test
    void shouldKeepArchivalIdempotentAndRequireAuthentication() throws Exception {
        mvc.perform(patch("/api/items/{id}/archive", item.getId())).andExpect(status().isUnauthorized());
        archive(ownerToken, 200);
        archive(ownerToken, 200);
        restore(ownerToken, 200);
        restore(ownerToken, 200);
        assertEquals(10, items.findById(item.getId()).orElseThrow().getQuantity());
    }

    private String token(User user) { return "Bearer " + jwt.generateToken(user.getEmail()); }
    private void archive(String token, int status) throws Exception {
        mvc.perform(patch("/api/items/{id}/archive", item.getId()).header("Authorization", token))
                .andExpect(status().is(status));
    }
    private void restore(String token, int status) throws Exception {
        mvc.perform(patch("/api/items/{id}/restore", item.getId()).header("Authorization", token))
                .andExpect(status().is(status));
    }
}
