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
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class LocationContentsTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired HouseRepository houses;
    @Autowired HouseMemberRepository members;
    @Autowired LocationRepository locations;
    @Autowired ItemRepository items;
    @Autowired ItemMovementRepository movements;
    @Autowired ItemUseRepository uses;
    @Autowired JwtService jwt;
    User owner, member, outsider;
    House house;
    Location source, target;
    Item first, second;
    String ownerToken, memberToken;

    @BeforeEach void setup() {
        owner = users.save(new User(null,"owner","owner@test.com","unused",Role.VIEWER));
        member = users.save(new User(null,"member","member@test.com","unused",Role.VIEWER));
        outsider = users.save(new User(null,"outside","outside@test.com","unused",Role.ADMIN));
        house = houses.save(new House(null,"Casa",owner));
        members.save(new HouseMember(null,house,member));
        source = location("Cajón"); target = location("Estantería");
        first = item("Tornillos",10,source); second = item("Agotado",0,source);
        ownerToken = token(owner); memberToken = token(member);
    }
    String token(User user) { return "Bearer " + jwt.generateToken(user.getEmail()); }
    Location location(String name) {
        Location location = new Location(null,name,null); location.setHouse(house);
        return locations.saveAndFlush(location);
    }
    Item item(String name,int quantity,Location location) {
        Item item = new Item(null,name,null,null,quantity,location);
        item.setOwner(owner); item.setVisibility(ItemVisibility.HOUSE_SHARED);
        return items.saveAndFlush(item);
    }
    String snapshot() { return "[{\"id\":"+first.getId()+",\"quantity\":10},{\"id\":"+second.getId()+",\"quantity\":0}]"; }
    void move(String token, Location destination, String snapshot, int status) throws Exception {
        mvc.perform(post("/api/locations/{id}/move-contents",source.getId()).header("Authorization",token)
            .contentType(MediaType.APPLICATION_JSON).content("{\"toLocationId\":"+destination.getId()+",\"items\":"+snapshot+"}"))
            .andExpect(status().is(status));
    }
    void unchanged() {
        assertEquals(source.getId(),items.findById(first.getId()).orElseThrow().getLocation().getId());
        assertEquals(source.getId(),items.findById(second.getId()).orElseThrow().getLocation().getId());
        assertFalse(movements.existsByItemId(first.getId()));
        assertFalse(movements.existsByItemId(second.getId()));
    }
    @Test void movesSharedContentsIncludingZeroButLeavesArchivedAndChildren() throws Exception {
        Item archived=item("Archivado",2,source); archived.setArchived(true); items.saveAndFlush(archived);
        Location child=location("Interior"); child.setParent(source); locations.saveAndFlush(child);
        Item nested=item("Dentro",3,child);
        mvc.perform(get("/api/locations/{id}/contents",source.getId()).header("Authorization",memberToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        move(memberToken,target,snapshot(),200);
        assertEquals(target.getId(),first.getLocation().getId());
        assertEquals(target.getId(),second.getLocation().getId());
        assertEquals(source.getId(),archived.getLocation().getId());
        assertEquals(child.getId(),nested.getLocation().getId());
        assertEquals(0,movements.findByItemIdOrderByMovedAtDescIdDesc(second.getId()).getFirst().getQuantity());
        move(memberToken,target,snapshot(),409);
        assertEquals(1,movements.findByItemIdOrderByMovedAtDescIdDesc(first.getId()).size());
    }
    @Test void rejectsPrivateOtherContentsWithoutPartialTransferOrNames() throws Exception {
        second.setVisibility(ItemVisibility.PRIVATE); items.saveAndFlush(second);
        mvc.perform(get("/api/locations/{id}/contents",source.getId()).header("Authorization",memberToken))
            .andExpect(status().isConflict()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Agotado"))));
        move(memberToken,target,snapshot(),409); unchanged();
        move(ownerToken,target,snapshot(),200);
    }
    @Test void rejectsStaleMissingExtraAndDuplicateSnapshots() throws Exception {
        move(ownerToken,target,snapshot().replace(":10",":9"),409);
        move(ownerToken,target,"[{\"id\":"+first.getId()+",\"quantity\":10}]",409);
        move(ownerToken,target,snapshot().replace("]",",{\"id\":999999,\"quantity\":1}]"),409);
        move(ownerToken,target,snapshot().replace("]",",{\"id\":"+first.getId()+",\"quantity\":10}]"),400);
        unchanged();
    }
    @Test void rejectsInUseAndArchivedDestinations() throws Exception {
        first.setKind(ItemKind.REUSABLE); items.saveAndFlush(first);
        ItemUse use=new ItemUse(); use.setItem(first); use.setLocation(source); use.setUsedBy(owner);
        use.setQuantity(1); use.setTakenAt(LocalDateTime.now()); uses.saveAndFlush(use);
        move(ownerToken,target,snapshot(),409); unchanged();
        use.setReturnedAt(LocalDateTime.now()); uses.saveAndFlush(use);
        target.setArchived(true); locations.saveAndFlush(target);
        move(ownerToken,target,snapshot(),409); unchanged();
    }
    @Test void rejectsOutsideHouseAndInvalidDestinations() throws Exception {
        move(token(outsider),target,snapshot(),404);
        move(ownerToken,source,snapshot(),400);
        House other=houses.save(new House(null,"Otra",owner));
        target.setHouse(other); locations.saveAndFlush(target);
        move(ownerToken,target,snapshot(),400); unchanged();
    }
    @Test void rejectsEmptyOrInvalidRequests() throws Exception {
        move(ownerToken,target,"[]",400);
        move(ownerToken,target,"[null]",400);
        move(ownerToken,target,snapshot().replace(":10",":-1"),400);
        unchanged();
    }
    @Test void deletingLocationPreservesInventoryPrivacyAndAllowsRelocation() throws Exception {
        second.setVisibility(ItemVisibility.PRIVATE); items.saveAndFlush(second);
        mvc.perform(post("/api/items/{id}/consume",first.getId()).header("Authorization",memberToken)
            .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}" )).andExpect(status().isOk());
        mvc.perform(delete("/api/locations/{id}",source.getId()).header("Authorization",memberToken))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/items").param("houseId",house.getId().toString()).header("Authorization",ownerToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].houseId").value(house.getId()))
            .andExpect(jsonPath("$[0].location").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/items/{id}",second.getId()).header("Authorization",memberToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/items/{id}",first.getId()).header("Authorization",token(outsider))).andExpect(status().isNotFound());
        mvc.perform(get("/api/items/{id}/consumptions",first.getId()).header("Authorization",ownerToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].location.deleted").value(true));
        mvc.perform(post("/api/items/{id}/restocks",first.getId()).header("Authorization",ownerToken)
            .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}" )).andExpect(status().isOk());
        mvc.perform(put("/api/items/{id}",first.getId()).header("Authorization",ownerToken)
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Sin cajón\",\"quantity\":10,\"locationId\":null}" ))
            .andExpect(status().isOk());
        mvc.perform(patch("/api/items/{id}/move",first.getId()).header("Authorization",memberToken)
            .param("toLocationId",target.getId().toString()).param("quantity","10")).andExpect(status().isOk());
        mvc.perform(get("/api/items/{id}/history",first.getId()).header("Authorization",ownerToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].fromLocation").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/locations").param("houseId",house.getId().toString()).param("includeArchived","true")
            .header("Authorization",ownerToken)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(patch("/api/locations/{id}/restore",source.getId()).header("Authorization",ownerToken)).andExpect(status().isNotFound());
    }
    @Test void deletingArchivedLocationKeepsArchivedItemsAndActiveUses() throws Exception {
        source.setArchived(true); locations.saveAndFlush(source);
        first.setArchived(true); items.saveAndFlush(first);
        second.setKind(ItemKind.REUSABLE); second.setQuantity(1); items.saveAndFlush(second);
        ItemUse use=new ItemUse(); use.setItem(second); use.setLocation(source); use.setUsedBy(owner);
        use.setQuantity(1); use.setTakenAt(LocalDateTime.now()); uses.saveAndFlush(use);
        mvc.perform(delete("/api/locations/{id}",source.getId()).header("Authorization",ownerToken)).andExpect(status().isNoContent());
        assertTrue(first.isArchived()); assertNull(first.getLocation());
        mvc.perform(patch("/api/items/{id}/restore",first.getId()).header("Authorization",ownerToken)).andExpect(status().isOk());
        mvc.perform(post("/api/items/{id}/uses/{use}/return",second.getId(),use.getId()).header("Authorization",ownerToken))
            .andExpect(status().isOk());
        mvc.perform(post("/api/items/{id}/uses",second.getId()).header("Authorization",ownerToken)
            .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}" )).andExpect(status().isCreated());
    }

}
