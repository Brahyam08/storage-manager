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
class ItemRestockTest {
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
    void shouldAddUnitsAndRecordActorLocationTotalAndUtcDate() throws Exception {
        restock("{\"quantity\":5}", ownerToken, 200);
        assertEquals(15, items.findById(item.getId()).orElseThrow().getQuantity());
        mvc.perform(get("/api/items/{id}/restocks", item.getId()).header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].quantity").value(5))
                .andExpect(jsonPath("$[0].resultingQuantity").value(15))
                .andExpect(jsonPath("$[0].restockedById").value(owner.getId()))
                .andExpect(jsonPath("$[0].location.id").value(source.getId()))
                .andExpect(jsonPath("$[0].restockedAt").value(org.hamcrest.Matchers.endsWith("Z")));
    }

    @Test
    void shouldRejectInvalidQuantitiesWithoutChangingStockOrHistory() throws Exception {
        for (String body : new String[]{"{}", "{\"quantity\":null}", "{\"quantity\":0}",
                "{\"quantity\":-1}", "{\"quantity\":1.5}", "{\"quantity\":2147483648}"}) {
            restock(body, ownerToken, 400);
        }
        assertEquals(10, items.findById(item.getId()).orElseThrow().getQuantity());
        assertEquals(0, restocks.count());
    }

    @Test
    void shouldKeepPrivateRestocksHiddenEvenFromHouseCreatorAndAdmin() throws Exception {
        for (String actor : new String[]{creatorToken, fellowToken, outsiderToken}) {
            restock("{\"quantity\":2}", actor, 404);
            mvc.perform(get("/api/items/{id}/restocks", item.getId()).header("Authorization", actor))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/items/{id}/restocks", item.getId())
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isUnauthorized());
        assertEquals(0, restocks.count());
    }

    @Test
    void shouldAllowSharedHistoryButOnlyOwnerCanAddUnits() throws Exception {
        item.setVisibility(ItemVisibility.HOUSE_SHARED);
        items.saveAndFlush(item);
        for (String actor : new String[]{creatorToken, fellowToken}) {
            restock("{\"quantity\":2}", actor, 403);
        }
        restock("{\"quantity\":2}", ownerToken, 200);
        mvc.perform(get("/api/items/{id}/restocks", item.getId()).header("Authorization", fellowToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        restock("{\"quantity\":2}", outsiderToken, 404);
    }

    @Test
    void shouldReplenishAnOutOfStockItem() throws Exception {
        item.setQuantity(0);
        items.saveAndFlush(item);
        mvc.perform(post("/api/items/{id}/restocks", item.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.availableQuantity").value(5))
                .andExpect(jsonPath("$.outOfStock").value(false));
    }

    @Test
    void shouldRejectOverflowAndAcceptExactlyTheSupportedBoundary() throws Exception {
        restock("{\"quantity\":2147483647}", ownerToken, 409);
        assertEquals(10, items.findById(item.getId()).orElseThrow().getQuantity());
        assertEquals(0, restocks.count());
        item.setQuantity(Integer.MAX_VALUE - 1);
        items.saveAndFlush(item);
        restock("{\"quantity\":1}", ownerToken, 200);
        restock("{\"quantity\":1}", ownerToken, 409);
        assertEquals(Integer.MAX_VALUE, items.findById(item.getId()).orElseThrow().getQuantity());
        assertEquals(1, restocks.count());
    }

    @Test
    void shouldAddReusableObjectsWithoutReleasingThoseInUse() throws Exception {
        item.setKind(ItemKind.REUSABLE);
        item.setQuantity(1);
        items.saveAndFlush(item);
        mvc.perform(post("/api/items/{id}/uses", item.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/items/{id}/restocks", item.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(3))
                .andExpect(jsonPath("$.inUseQuantity").value(1))
                .andExpect(jsonPath("$.availableQuantity").value(2));
    }

    @Test
    void shouldPreserveItemTypeAndLocationReferencedByRestockHistory() throws Exception {
        restock("{\"quantity\":2}", ownerToken, 200);
        mvc.perform(put("/api/items/{id}", item.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Tornillos\",\"kind\":\"REUSABLE\",\"quantity\":12,\"locationId\":" + source.getId() + "}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/items/{id}", item.getId()).header("Authorization", ownerToken))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/items/{id}/move", item.getId()).header("Authorization", ownerToken)
                .param("toLocationId", target.getId().toString()).param("quantity", "12"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/locations/{id}", source.getId()).header("Authorization", ownerToken))
                .andExpect(status().isConflict());
        assertEquals(1, restocks.count());
    }

    private String token(User user) { return "Bearer " + jwt.generateToken(user.getEmail()); }

    private void restock(String body, String actor, int expected) throws Exception {
        mvc.perform(post("/api/items/{id}/restocks", item.getId()).header("Authorization", actor)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected));
    }
}
