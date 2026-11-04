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
class ItemConsumptionTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private HouseRepository houses;
    @Autowired private HouseMemberRepository members;
    @Autowired private LocationRepository locations;
    @Autowired private ItemRepository items;
    @Autowired private ItemMovementRepository movements;
    @Autowired private ItemConsumptionRepository consumptions;
    @Autowired private JwtService jwt;
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
    void shouldReturnConsumptionTimestampWithUtcOffset() throws Exception {
        java.time.Instant before = java.time.Instant.now().minusSeconds(1);
        consume(1, ownerToken, 200);
        String history = mvc.perform(get("/api/items/{id}/consumptions", item.getId()).header("Authorization", ownerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String value = com.jayway.jsonpath.JsonPath.read(history, "$[0].consumedAt");
        java.time.Instant recorded = java.time.Instant.parse(value);
        assertFalse(recorded.isBefore(before));
        assertFalse(recorded.isAfter(java.time.Instant.now().plusSeconds(1)));
    }

    @Test
    void shouldCreateReusableItemsAndRejectConsumptionWithoutChangingHistory() throws Exception {
        item.setKind(ItemKind.REUSABLE);
        item.setQuantity(1);
        items.saveAndFlush(item);
        mvc.perform(get("/api/items/{id}", item.getId()).header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("REUSABLE"))
                .andExpect(jsonPath("$.outOfStock").value(false));
        consume(1, ownerToken, 409);
        assertEquals(1, items.findById(item.getId()).orElseThrow().getQuantity());
        assertEquals(0, consumptions.count());
        mvc.perform(put("/api/items/{id}", item.getId()).header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ratón\",\"quantity\":2,\"locationId\":" + source.getId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("REUSABLE"));
    }

    @Test
    void shouldNotInferKindFromQuantityOneAndDefaultOldRequestsToConsumable() throws Exception {
        mvc.perform(post("/api/items").header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Último tornillo\",\"quantity\":1,\"locationId\":" + source.getId() + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.kind").value("CONSUMABLE"));
        item.setQuantity(1);
        items.saveAndFlush(item);
        consume(1, ownerToken, 200);
        mvc.perform(get("/api/items/{id}", item.getId()).header("Authorization", ownerToken))
                .andExpect(jsonPath("$.kind").value("CONSUMABLE"))
                .andExpect(jsonPath("$.outOfStock").value(true));
    }

    @Test
    void shouldReclassifyExistingItemsWithoutConsumptionButPreserveRecordedConsumptionMeaning() throws Exception {
        String request = "{\"name\":\"Ratón\",\"quantity\":1,\"kind\":\"REUSABLE\",\"locationId\":" + source.getId() + "}";
        mvc.perform(put("/api/items/{id}", item.getId()).header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("REUSABLE"));
        item.setKind(ItemKind.CONSUMABLE);
        item.setQuantity(10);
        items.saveAndFlush(item);
        consume(5, ownerToken, 200);
        mvc.perform(put("/api/items/{id}", item.getId()).header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isConflict());
        assertEquals(ItemKind.CONSUMABLE, items.findById(item.getId()).orElseThrow().getKind());
        assertEquals(5, items.findById(item.getId()).orElseThrow().getQuantity());
        assertEquals(1, consumptions.count());
    }

    @Test
    void shouldValidateReusableQuantitiesAndKindOnCreate() throws Exception {
        for (String kind : new String[]{"REUSABLE", "UNKNOWN"}) {
            mvc.perform(post("/api/items").header("Authorization", ownerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Ratón\",\"quantity\":0,\"kind\":\"" + kind + "\",\"locationId\":" + source.getId() + "}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/items").header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ratón\",\"quantity\":1,\"kind\":\"REUSABLE\",\"locationId\":" + source.getId() + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.kind").value("REUSABLE"));
    }

    @Test
    void shouldRejectZeroReusableAdjustmentAndKeepOtherMembersFromChangingKind() throws Exception {
        item.setVisibility(ItemVisibility.HOUSE_SHARED);
        item.setKind(ItemKind.REUSABLE);
        items.saveAndFlush(item);
        mvc.perform(put("/api/items/{id}", item.getId()).header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ratón\",\"quantity\":0,\"locationId\":" + source.getId() + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/items/{id}", item.getId()).header("Authorization", fellowToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ratón\",\"quantity\":1,\"kind\":\"CONSUMABLE\",\"locationId\":" + source.getId() + "}"))
                .andExpect(status().isForbidden());
        consume(1, fellowToken, 409);
        assertEquals(ItemKind.REUSABLE, items.findById(item.getId()).orElseThrow().getKind());
        assertEquals(10, items.findById(item.getId()).orElseThrow().getQuantity());
    }

    private String token(User user) { return "Bearer " + jwt.generateToken(user.getEmail()); }

    private void consume(int quantity, String token, int expected) throws Exception {
        mvc.perform(post("/api/items/{id}/consume", item.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":" + quantity + "}"))
                .andExpect(status().is(expected));
    }

    @Test
    void shouldConsumeFiveOfTenWithoutMovingAndRecordSessionActor() throws Exception {
        mvc.perform(post("/api/items/{id}/consume", item.getId()).header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":5,\"consumedById\":" + creator.getId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.outOfStock").value(false))
                .andExpect(jsonPath("$.location.id").value(source.getId()));
        mvc.perform(get("/api/items/{id}/consumptions", item.getId()).header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].quantity").value(5))
                .andExpect(jsonPath("$[0].remainingQuantity").value(5))
                .andExpect(jsonPath("$[0].consumedById").value(owner.getId()))
                .andExpect(jsonPath("$[0].consumedAt").exists())
                .andExpect(jsonPath("$[0].location.path").value("Cajón"))
                .andExpect(jsonPath("$[0].consumedBy").doesNotExist())
                .andExpect(jsonPath("$[0].item").doesNotExist());
        assertEquals(5, items.findById(item.getId()).orElseThrow().getQuantity());
        assertEquals(0, movements.count());
    }

    @Test
    void shouldKeepOutOfStockItemSearchableAndItsConsumptionHistory() throws Exception {
        consume(3, ownerToken, 200);
        consume(7, ownerToken, 200);
        mvc.perform(get("/api/items/{id}", item.getId()).header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(0))
                .andExpect(jsonPath("$.outOfStock").value(true));
        mvc.perform(get("/api/items").param("search", "Tornillos").header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].quantity").value(0));
        mvc.perform(get("/api/items/{id}/consumptions", item.getId()).header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].quantity").value(7)).andExpect(jsonPath("$[0].remainingQuantity").value(0))
                .andExpect(jsonPath("$[1].quantity").value(3)).andExpect(jsonPath("$[1].remainingQuantity").value(7));
        consume(1, ownerToken, 409);
        assertTrue(items.existsById(item.getId()));
        assertEquals(2, consumptions.count());
        mvc.perform(put("/api/items/{id}", item.getId()).header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Tornillos agotados\",\"quantity\":0,\"locationId\":" + source.getId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outOfStock").value(true));
    }

    @Test
    void shouldRejectInvalidQuantitiesWithoutChangingStockOrHistory() throws Exception {
        for (String body : new String[]{"{}", "{\"quantity\":null}", "{\"quantity\":0}", "{\"quantity\":-1}",
                "{\"quantity\":1.5}", "{\"quantity\":2147483648}", "{\"quantity\":\"five\"}", "{bad-json"}) {
            mvc.perform(post("/api/items/{id}/consume", item.getId()).header("Authorization", ownerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        consume(11, ownerToken, 409);
        assertEquals(10, items.findById(item.getId()).orElseThrow().getQuantity());
        assertEquals(0, consumptions.count());
    }

    @Test
    void shouldHidePrivateConsumptionAndItsHistoryEvenFromCreatorAdmin() throws Exception {
        for (String token : new String[]{creatorToken, fellowToken, outsiderToken}) {
            consume(1, token, 404);
            for (long id : new long[]{item.getId(), Long.MAX_VALUE}) {
                mvc.perform(get("/api/items/{id}/consumptions", id).header("Authorization", token))
                        .andExpect(status().isNotFound()).andExpect(content().json("{\"error\":\"Objeto no encontrado\"}"));
            }
        }
        assertEquals(10, items.findById(item.getId()).orElseThrow().getQuantity());
        assertEquals(0, consumptions.count());
    }

    @Test
    void shouldAllowHouseMembersToConsumeSharedStockAndHideHistoryWhenPrivateAgain() throws Exception {
        item.setVisibility(ItemVisibility.HOUSE_SHARED);
        items.saveAndFlush(item);
        consume(2, fellowToken, 200);
        consume(1, creatorToken, 200);
        consume(1, outsiderToken, 404);
        mvc.perform(get("/api/items/{id}/consumptions", item.getId()).header("Authorization", fellowToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].consumedById").value(creator.getId()))
                .andExpect(jsonPath("$[1].consumedById").value(fellow.getId()));
        mvc.perform(get("/api/items/{id}/consumptions", item.getId()).header("Authorization", outsiderToken))
                .andExpect(status().isNotFound());
        item.setVisibility(ItemVisibility.PRIVATE);
        items.saveAndFlush(item);
        mvc.perform(get("/api/items/{id}/consumptions", item.getId()).header("Authorization", fellowToken))
                .andExpect(status().isNotFound());
        assertEquals(7, items.findById(item.getId()).orElseThrow().getQuantity());
    }

    @Test
    void shouldPreserveConsumptionLocationReferenceAfterLocationDeletion() throws Exception {
        consume(5, ownerToken, 200);
        mvc.perform(patch("/api/items/{id}/move", item.getId()).header("Authorization", ownerToken)
                        .param("toLocationId", target.getId().toString()).param("quantity", "5"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/locations/{id}", source.getId()).header("Authorization", ownerToken))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/items/{id}/consumptions", item.getId()).header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].location.id").value(source.getId()));
    }

    @Test
    void shouldAllowTrackingNewItemsWithZeroStock() throws Exception {
        mvc.perform(post("/api/items").header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Arandelas\",\"quantity\":0,\"locationId\":" + source.getId() + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.quantity").value(0))
                .andExpect(jsonPath("$.outOfStock").value(true));
    }

    @Test
    void shouldRequireAuthenticationForConsumptionAndItsHistory() throws Exception {
        mvc.perform(post("/api/items/{id}/consume", item.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/items/{id}/consumptions", item.getId())).andExpect(status().isUnauthorized());
        assertEquals(10, items.findById(item.getId()).orElseThrow().getQuantity());
        assertEquals(0, consumptions.count());
    }
}
