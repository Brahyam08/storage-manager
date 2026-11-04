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
class IndividualItemTest {
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
    void shouldCreateAnIndividualButNotInferItFromQuantityOne() throws Exception {
        mvc.perform(post("/api/items").header("Authorization", ownerToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Teclado\",\"kind\":\"REUSABLE\",\"individual\":true,\"quantity\":1,\"locationId\":" + source.getId() + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.individual").value(true));
        item.setKind(ItemKind.REUSABLE);
        item.setQuantity(1);
        items.saveAndFlush(item);
        mvc.perform(get("/api/items/{id}", item.getId()).header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.individual").value(false));
    }

    @Test
    void shouldRejectIndividualConsumablesAndMoreThanOnePiece() throws Exception {
        for (String fields : new String[]{"\"kind\":\"CONSUMABLE\",\"quantity\":1", "\"kind\":\"REUSABLE\",\"quantity\":2"}) {
            mvc.perform(post("/api/items").header("Authorization", ownerToken).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Objeto\",\"individual\":true," + fields + ",\"locationId\":" + source.getId() + "}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void shouldUseAndReturnOneIndividualButRejectAddingUnits() throws Exception {
        item.setKind(ItemKind.REUSABLE);
        item.setQuantity(1);
        item.setIndividual(true);
        items.saveAndFlush(item);
        mvc.perform(post("/api/items/{id}/restocks", item.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isConflict());
        assertEquals(0, restocks.count());
        String body = mvc.perform(post("/api/items/{id}/uses", item.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number useId = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        mvc.perform(get("/api/items/{id}", item.getId()).header("Authorization", ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.inUseQuantity").value(1))
                .andExpect(jsonPath("$.quantity").value(1));
        mvc.perform(post("/api/items/{id}/uses/{useId}/return", item.getId(), useId).header("Authorization", ownerToken))
                .andExpect(status().isOk());
        mvc.perform(put("/api/items/{id}", item.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(request(2, "true")))
                .andExpect(status().isBadRequest());
        assertEquals(1, items.findById(item.getId()).orElseThrow().getQuantity());
    }

    @Test
    void shouldNotDiscardAGroupWhenConvertingToIndividual() throws Exception {
        item.setKind(ItemKind.REUSABLE);
        items.saveAndFlush(item);
        mvc.perform(put("/api/items/{id}", item.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(request(1, "true")))
                .andExpect(status().isConflict());
        assertEquals(10, items.findById(item.getId()).orElseThrow().getQuantity());
        item.setQuantity(1);
        items.saveAndFlush(item);
        mvc.perform(put("/api/items/{id}", item.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(request(1, "true")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.individual").value(true));
        mvc.perform(put("/api/items/{id}", item.getId()).header("Authorization", ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(request(1, "null")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.individual").value(true));
    }

    @Test
    void shouldNotLetAnotherMemberChangeTheTrackingMode() throws Exception {
        item.setKind(ItemKind.REUSABLE);
        item.setVisibility(ItemVisibility.HOUSE_SHARED);
        item.setQuantity(1);
        items.saveAndFlush(item);
        mvc.perform(put("/api/items/{id}", item.getId()).header("Authorization", fellowToken)
                .contentType(MediaType.APPLICATION_JSON).content(request(1, "true")))
                .andExpect(status().isForbidden());
        assertFalse(items.findById(item.getId()).orElseThrow().isIndividual());
    }

    private String request(int quantity, String individual) {
        return "{\"name\":\"Teclado\",\"kind\":\"REUSABLE\",\"quantity\":" + quantity
                + ",\"individual\":" + individual + ",\"locationId\":" + source.getId() + "}";
    }
    private String token(User user) { return "Bearer " + jwt.generateToken(user.getEmail()); }
}
