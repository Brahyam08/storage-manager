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
class ItemControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private HouseRepository houses;
    @Autowired private HouseMemberRepository members;
    @Autowired private LocationRepository locations;
    @Autowired private ItemRepository items;
    @Autowired private ItemMovementRepository movements;
    @Autowired private JwtService jwt;
    private User owner;
    private User creator;
    private House house;
    private Location source;
    private Location target;
    private Location secondHouseLocation;
    private Location foreignLocation;
    private String ownerToken;
    private String creatorToken;
    private String fellowToken;
    private String outsiderToken;

    @BeforeEach
    void setUp() {
        creator = users.save(new User(null, "creator", "creator@test.com", "unused", Role.ADMIN));
        owner = users.save(new User(null, "owner", "owner@test.com", "unused", Role.VIEWER));
        User fellow = users.save(new User(null, "fellow", "fellow@test.com", "unused", Role.VIEWER));
        User outsider = users.save(new User(null, "outsider", "outsider@test.com", "unused", Role.ADMIN));
        house = houses.save(new House(null, "Compartida", creator));
        members.save(new HouseMember(null, house, owner));
        members.save(new HouseMember(null, house, fellow));
        House second = houses.save(new House(null, "Segunda", owner));
        House foreign = houses.save(new House(null, "Ajena", outsider));
        Location room = location("Despacho", house, null);
        source = location("Cajón 1", house, room);
        target = location("Cajón 2", house, room);
        secondHouseLocation = location("Armario", second, null);
        foreignLocation = location("Secreta", foreign, null);
        ownerToken = token(owner);
        creatorToken = token(creator);
        fellowToken = token(fellow);
        outsiderToken = token(outsider);
    }

    private String token(User user) { return "Bearer " + jwt.generateToken(user.getEmail()); }

    private Location location(String name, House house, Location parent) {
        Location location = new Location(null, name, null);
        location.setHouse(house);
        location.setParent(parent);
        return locations.saveAndFlush(location);
    }

    private String body(String name, Long locationId, String visibility) {
        return "{\"name\":\"" + name + "\",\"category\":\"Gear\",\"quantity\":3,\"locationId\":"
                + locationId + (visibility == null ? "" : ",\"visibility\":\"" + visibility + "\"") + "}";
    }

    private long create(String name, Location location, String visibility, String token) throws Exception {
        String response = mvc.perform(post("/api/items").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(name, location.getId(), visibility)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.id")).longValue();
    }

    private void move(long id, Location destination, int quantity, String token, int expected) throws Exception {
        mvc.perform(patch("/api/items/{id}/move", id).header("Authorization", token)
                        .param("toLocationId", destination.getId().toString()).param("quantity", String.valueOf(quantity)))
                .andExpect(status().is(expected));
    }


    @Test
    void shouldReturnMovementTimestampWithUtcOffset() throws Exception {
        long id = create("Timestamp fixture", source, null, ownerToken);
        java.time.Instant before = java.time.Instant.now().minusSeconds(1);
        move(id, target, 3, ownerToken, 200);
        String history = mvc.perform(get("/api/items/{id}/history", id).header("Authorization", ownerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String value = com.jayway.jsonpath.JsonPath.read(history, "$[0].movedAt");
        java.time.Instant recorded = java.time.Instant.parse(value);
        assertFalse(recorded.isBefore(before));
        assertFalse(recorded.isAfter(java.time.Instant.now().plusSeconds(1)));
    }

    @Test
    void shouldTreatSqlPayloadsAsBoundSearchValuesWithoutLeakingOtherOwners() throws Exception {
        create("Private diamonds", source, null, fellowToken);
        for (String payload : new String[]{"' OR 1=1 --", "'; DROP TABLE items; --"}) {
            create(payload, source, null, ownerToken);
            mvc.perform(get("/api/items").param("search", payload).header("Authorization", ownerToken))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].name").value(payload))
                    .andExpect(jsonPath("$[0].ownerId").value(owner.getId()));
            mvc.perform(get("/api/items").param("category", payload).header("Authorization", ownerToken))
                    .andExpect(status().isOk()).andExpect(content().json("[]"));
        }
        mvc.perform(get("/api/items").param("houseId", "1 OR 1=1").header("Authorization", ownerToken))
                .andExpect(status().isBadRequest());
        assertEquals(3, items.count());
        mvc.perform(get("/api/items").param("search", "%").header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void shouldPreventBrowserCachingForPrivateInventoryAndDeniedResponses() throws Exception {
        long id = create("Private inventory", source, null, ownerToken);
        for (String token : new String[]{ownerToken, creatorToken}) {
            mvc.perform(get("/api/items/{id}", id).header("Authorization", token))
                    .andExpect(status().is(token.equals(ownerToken) ? 200 : 404))
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }
    }

    @Test
    void shouldDefaultToPrivateUseSessionOwnerAndReturnFullLocationWithoutUserEntity() throws Exception {
        String request = body("  Ratón  ", source.getId(), null).replace("}", ",\"ownerId\":" + creator.getId() + "}");
        mvc.perform(post("/api/items").header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Ratón"))
                .andExpect(jsonPath("$.ownerId").value(owner.getId()))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.location.path").value("Despacho → Cajón 1"))
                .andExpect(jsonPath("$.owner").doesNotExist())
                .andExpect(jsonPath("$.location.house").doesNotExist());
    }

    @Test
    void shouldApplyPrivacyToGlobalListsSearchAndCombinedFilters() throws Exception {
        create("Mouse own", source, null, ownerToken);
        create("MOUSE shared", source, "HOUSE_SHARED", fellowToken);
        create("Mouse hidden", source, null, fellowToken);
        create("Mouse other house", secondHouseLocation, null, ownerToken);
        create("Mouse foreign", foreignLocation, "HOUSE_SHARED", outsiderToken);
        mvc.perform(get("/api/items").header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
        for (String[] filters : new String[][]{{null, null}, {"mOuSe", null}, {null, "Gear"}, {"mOuSe", "Gear"}, {"%", null}}) {
            var request = get("/api/items").param("houseId", house.getId().toString())
                    .param("locationId", source.getId().toString()).header("Authorization", ownerToken);
            if (filters[0] != null) request.param("search", filters[0]);
            if (filters[1] != null) request.param("category", filters[1]);
            mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].name").value("Mouse own"))
                    .andExpect(jsonPath("$[1].name").value("MOUSE shared"));
        }
        mvc.perform(get("/api/items").param("houseId", house.getId().toString()).header("Authorization", creatorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("MOUSE shared"));
        mvc.perform(get("/api/items").param("locationId", target.getId().toString()).header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void shouldHidePrivateItemsFromCreatorMembersAndOutsidersOnEveryRoute() throws Exception {
        long id = create("Privado", source, null, ownerToken);
        for (String token : new String[]{creatorToken, fellowToken, outsiderToken}) {
            for (long candidate : new long[]{id, Long.MAX_VALUE}) {
                for (String suffix : new String[]{"", "/history"}) {
                    mvc.perform(get("/api/items/" + candidate + suffix).header("Authorization", token))
                            .andExpect(status().isNotFound()).andExpect(content().json("{\"error\":\"Objeto no encontrado\"}"));
                }
                mvc.perform(put("/api/items/{id}", candidate).header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON).content(body("Intrusión", source.getId(), "HOUSE_SHARED")))
                        .andExpect(status().isNotFound());
                mvc.perform(delete("/api/items/{id}", candidate).header("Authorization", token))
                        .andExpect(status().isNotFound());
                move(candidate, target, 3, token, 404);
            }
        }
        assertEquals(source.getId(), items.findById(id).orElseThrow().getLocation().getId());
        assertEquals("Privado", items.findById(id).orElseThrow().getName());
        assertEquals(0, movements.count());
        // Locations stay visible even when their contents are private.
        mvc.perform(get("/api/locations/{id}", source.getId()).header("Authorization", fellowToken))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowMembersToReadMoveAndSeeSharedHistory() throws Exception {
        long id = create("Compartido", source, "HOUSE_SHARED", ownerToken);
        move(id, target, 3, fellowToken, 200);
        mvc.perform(get("/api/items/{id}", id).header("Authorization", creatorToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.location.path").value("Despacho → Cajón 2"))
                .andExpect(jsonPath("$.quantity").value(3)).andExpect(jsonPath("$.ownerId").value(owner.getId()));
        move(id, source, 3, creatorToken, 200);
        mvc.perform(get("/api/items/{id}/history", id).header("Authorization", fellowToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fromLocation.id").value(target.getId()))
                .andExpect(jsonPath("$[0].toLocation.path").value("Despacho → Cajón 1"))
                .andExpect(jsonPath("$[0].quantity").value(3))
                .andExpect(jsonPath("$[0].movedAt").exists())
                .andExpect(jsonPath("$[0].item").doesNotExist());
    }

    @Test
    void shouldHideSharedItemsFromNonMembersOnEveryRoute() throws Exception {
        long id = create("Compartido", source, "HOUSE_SHARED", ownerToken);
        mvc.perform(get("/api/items/{id}", id).header("Authorization", outsiderToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/items/{id}/history", id).header("Authorization", outsiderToken)).andExpect(status().isNotFound());
        mvc.perform(put("/api/items/{id}", id).header("Authorization", outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body("Intrusión", foreignLocation.getId(), null)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/items/{id}", id).header("Authorization", outsiderToken)).andExpect(status().isNotFound());
        move(id, foreignLocation, 3, outsiderToken, 404);
        mvc.perform(get("/api/items").header("Authorization", outsiderToken)).andExpect(content().json("[]"));
    }

    @Test
    void shouldReserveEditsAndVisibilityForOwnerAndHideHistoryAfterMakingPrivate() throws Exception {
        long id = create("Compartido", source, "HOUSE_SHARED", ownerToken);
        move(id, target, 3, fellowToken, 200);
        for (String token : new String[]{creatorToken, fellowToken}) {
            mvc.perform(put("/api/items/{id}", id).header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(body("Ajeno", target.getId(), "PRIVATE")))
                    .andExpect(status().isForbidden());
            mvc.perform(delete("/api/items/{id}", id).header("Authorization", token)).andExpect(status().isForbidden());
        }
        mvc.perform(put("/api/items/{id}", id).header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body("Renombrado", target.getId(), null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("HOUSE_SHARED"));
        String request = body("Privado", target.getId(), "PRIVATE").replace("}", ",\"ownerId\":" + creator.getId() + "}");
        mvc.perform(put("/api/items/{id}", id).header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.ownerId").value(owner.getId()));
        mvc.perform(get("/api/items/{id}/history", id).header("Authorization", fellowToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/items/{id}/history", id).header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/items").header("Authorization", fellowToken)).andExpect(content().json("[]"));
    }

    @Test
    void shouldRequireMovementEndpointInsteadOfChangingLocationViaEdit() throws Exception {
        long id = create("Ratón", source, null, ownerToken);
        mvc.perform(put("/api/items/{id}", id).header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body("Ratón", target.getId(), null)))
                .andExpect(status().isBadRequest());
        assertEquals(source.getId(), items.findById(id).orElseThrow().getLocation().getId());
        assertEquals(0, movements.count());
    }

    @Test
    void shouldRejectPartialInvalidCrossHouseAndRedundantMovesWithoutHistory() throws Exception {
        long id = create("Ratón", source, null, ownerToken);
        for (int quantity : new int[]{-1, 0, 1, 4}) move(id, target, quantity, ownerToken, 400);
        move(id, source, 3, ownerToken, 400);
        move(id, secondHouseLocation, 3, ownerToken, 400);
        move(id, foreignLocation, 3, ownerToken, 404);
        mvc.perform(patch("/api/items/{id}/move", id).header("Authorization", ownerToken)
                        .param("toLocationId", String.valueOf(Long.MAX_VALUE)).param("quantity", "3"))
                .andExpect(status().isNotFound());
        assertEquals(0, movements.count());
        assertEquals(source.getId(), items.findById(id).orElseThrow().getLocation().getId());
        assertEquals(3, items.findById(id).orElseThrow().getQuantity());
    }

    @Test
    void shouldLetOwnerDeleteItemsAndTheirOwnMovementHistory() throws Exception {
        long empty = create("Sin historial", source, null, ownerToken);
        mvc.perform(delete("/api/items/{id}", empty).header("Authorization", ownerToken)).andExpect(status().isNoContent());
        assertFalse(items.existsById(empty));
        long moved = create("Con historial", source, null, ownerToken);
        move(moved, target, 3, ownerToken, 200);
        mvc.perform(delete("/api/items/{id}", moved).header("Authorization", ownerToken)).andExpect(status().isNoContent());
        assertFalse(items.existsById(moved));
        assertEquals(0, movements.count());
    }

    @Test
    void shouldRejectInvalidInputAndInaccessibleLocationsOrFilters() throws Exception {
        for (String request : new String[]{"{}", body("   ", source.getId(), null),
                body("Ratón", source.getId(), null).replace("\"quantity\":3", "\"quantity\":-1"),
                body("Ratón", source.getId(), "INVALID"), "{not-json"}) {
            mvc.perform(post("/api/items").header("Authorization", ownerToken)
                            .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/items").header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body("Ajeno", foreignLocation.getId(), null)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/items").param("houseId", foreignLocation.getHouse().getId().toString())
                        .header("Authorization", ownerToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/items").param("locationId", foreignLocation.getId().toString())
                        .header("Authorization", ownerToken)).andExpect(status().isNotFound());
        assertEquals(0, items.count());
    }

    @Test
    void shouldPreserveButHideLegacyItemsWithoutOwnerOrHouse() throws Exception {
        Item noOwner = items.saveAndFlush(new Item(null, "Antiguo", null, null, 1, source));
        Location legacy = locations.saveAndFlush(new Location(null, "Sin casa", null));
        Item noHouse = items.saveAndFlush(new Item(null, "Sin casa", null, null, 1, legacy));
        for (long id : new long[]{noOwner.getId(), noHouse.getId()}) {
            mvc.perform(get("/api/items/{id}", id).header("Authorization", ownerToken)).andExpect(status().isNotFound());
            mvc.perform(get("/api/items/{id}/history", id).header("Authorization", ownerToken)).andExpect(status().isNotFound());
            assertTrue(items.existsById(id));
        }
        mvc.perform(get("/api/items").header("Authorization", ownerToken)).andExpect(content().json("[]"));
    }

    @Test
    void shouldRequireAuthenticationOnEveryInventoryRoute() throws Exception {
        long id = create("Privado", source, null, ownerToken);
        mvc.perform(get("/api/items")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/items/{id}", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/items/{id}/history", id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/items").contentType(MediaType.APPLICATION_JSON).content(body("Otro", source.getId(), null)))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/items/{id}", id).contentType(MediaType.APPLICATION_JSON).content(body("Otro", source.getId(), null)))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/items/{id}", id)).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/items/{id}/move", id).param("toLocationId", target.getId().toString()).param("quantity", "3"))
                .andExpect(status().isUnauthorized());
        assertTrue(items.existsById(id));
    }
}
