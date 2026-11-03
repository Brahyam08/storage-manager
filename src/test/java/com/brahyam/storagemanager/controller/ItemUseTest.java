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
class ItemUseTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private HouseRepository houses;
    @Autowired private HouseMemberRepository members;
    @Autowired private LocationRepository locations;
    @Autowired private ItemRepository items;
    @Autowired private ItemMovementRepository movements;
    @Autowired private ItemConsumptionRepository consumptions;
    @Autowired private JwtService jwt;
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
        item.setKind(ItemKind.REUSABLE);
        items.saveAndFlush(item);
        ownerToken = token(owner);
        creatorToken = token(creator);
        fellowToken = token(fellow);
        outsiderToken = token(outsider);
    }



    private String token(User user) { return "Bearer " + jwt.generateToken(user.getEmail()); }
    private long take(int quantity, String token) throws Exception {
        String body = mvc.perform(post("/api/items/{id}/uses", item.getId()).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":" + quantity + ",\"usedById\":" + owner.getId() + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.id")).longValue();
    }
    private void giveBack(long id, String token, int expected) throws Exception {
        mvc.perform(post("/api/items/{id}/uses/{useId}/return", item.getId(), id).header("Authorization", token))
                .andExpect(status().is(expected));
    }

    @Test
    void shouldKeepTotalAndRecordAvailabilityAndReturnOnce() throws Exception {
        long id = take(2, ownerToken);
        mvc.perform(get("/api/items/{id}", item.getId()).header("Authorization", ownerToken))
                .andExpect(jsonPath("$.quantity").value(10)).andExpect(jsonPath("$.inUseQuantity").value(2))
                .andExpect(jsonPath("$.availableQuantity").value(8));
        giveBack(id, ownerToken, 200);
        giveBack(id, ownerToken, 409);
        mvc.perform(get("/api/items/{id}", item.getId()).header("Authorization", ownerToken))
                .andExpect(jsonPath("$.quantity").value(10)).andExpect(jsonPath("$.availableQuantity").value(10));
        mvc.perform(get("/api/items/{id}/uses", item.getId()).header("Authorization", ownerToken))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].usedById").value(owner.getId()))
                .andExpect(jsonPath("$[0].returnedAt").isNotEmpty()).andExpect(jsonPath("$[0].location.id").value(source.getId()))
                .andExpect(jsonPath("$[0].usedBy").doesNotExist());
        assertEquals(0, consumptions.count());
    }

    @Test
    void shouldUseSessionActorAndAllowBorrowerOrOwnerToReturnSharedItems() throws Exception {
        item.setVisibility(ItemVisibility.HOUSE_SHARED); items.saveAndFlush(item);
        long id = take(1, fellowToken);
        assertEquals(fellow.getId(), uses.findById(id).orElseThrow().getUsedBy().getId());
        giveBack(id, creatorToken, 403);
        giveBack(id, fellowToken, 200);
        giveBack(take(1, fellowToken), ownerToken, 200);
    }

    @Test
    void shouldHidePrivateUsesFromMembersCreatorAdminAndOutsiders() throws Exception {
        long id = take(1, ownerToken);
        for (String token : new String[]{creatorToken, fellowToken, outsiderToken}) {
            mvc.perform(get("/api/items/{id}/uses", item.getId()).header("Authorization", token)).andExpect(status().isNotFound());
            mvc.perform(post("/api/items/{id}/uses", item.getId()).header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}")).andExpect(status().isNotFound());
            giveBack(id, token, 404);
        }
        assertNull(uses.findById(id).orElseThrow().getReturnedAt());
    }

    @Test
    void shouldRejectInvalidQuantitiesAndOverspendingWithoutNewUses() throws Exception {
        for (String body : new String[]{"{}", "{\"quantity\":0}", "{\"quantity\":-1}", "{\"quantity\":1.5}", "{\"quantity\":2147483648}"}) {
            mvc.perform(post("/api/items/{id}/uses", item.getId()).header("Authorization", ownerToken)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        take(10, ownerToken);
        mvc.perform(post("/api/items/{id}/uses", item.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}")).andExpect(status().isConflict());
        assertEquals(1, uses.count());
    }

    @Test
    void shouldRejectUseOfConsumablesAndUnknownUseIds() throws Exception {
        item.setKind(ItemKind.CONSUMABLE); items.saveAndFlush(item);
        mvc.perform(post("/api/items/{id}/uses", item.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}")).andExpect(status().isConflict());
        giveBack(Long.MAX_VALUE, ownerToken, 404);
        assertEquals(0, uses.count());
    }

    @Test
    void shouldBlockMovesAndVisibilityOrTypeChangesWhileInUse() throws Exception {
        take(2, ownerToken);
        mvc.perform(patch("/api/items/{id}/move", item.getId()).header("Authorization", ownerToken)
                .param("toLocationId", target.getId().toString()).param("quantity", "10")).andExpect(status().isConflict());
        for (String extra : new String[]{",\"kind\":\"CONSUMABLE\"", ",\"visibility\":\"HOUSE_SHARED\"", ""}) {
            mvc.perform(put("/api/items/{id}", item.getId()).header("Authorization", ownerToken)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ratón\",\"quantity\":" + (extra.isEmpty() ? 1 : 10) + ",\"locationId\":" + source.getId() + extra + "}"))
                    .andExpect(status().isConflict());
        }
        mvc.perform(delete("/api/items/{id}", item.getId()).header("Authorization", ownerToken)).andExpect(status().isConflict());
        assertEquals(2, uses.activeQuantity(item.getId()));
    }

    @Test
    void shouldPreserveHistoryAfterReturnAndAllowMovementAgain() throws Exception {
        giveBack(take(1, ownerToken), ownerToken, 200);
        mvc.perform(put("/api/items/{id}", item.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ratón\",\"quantity\":10,\"kind\":\"CONSUMABLE\",\"locationId\":" + source.getId() + "}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/items/{id}/move", item.getId()).header("Authorization", ownerToken)
                .param("toLocationId", target.getId().toString()).param("quantity", "10")).andExpect(status().isOk());
        mvc.perform(delete("/api/items/{id}", item.getId()).header("Authorization", ownerToken)).andExpect(status().isConflict());
        assertEquals(source.getId(), uses.findByItemIdOrderByTakenAtDescIdDesc(item.getId()).getFirst().getLocation().getId());
    }
}
