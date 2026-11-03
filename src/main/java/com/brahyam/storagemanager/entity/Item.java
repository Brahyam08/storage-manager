package com.brahyam.storagemanager.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "items")
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    private String description;
    private String category;
    @Column(nullable = false)
    private Integer quantity;
    @ManyToOne
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    // Nullable to preserve legacy rows without assigning them to an arbitrary user.
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ItemVisibility visibility = ItemVisibility.PRIVATE;

    public Item(Long id, String name, String description, String category, Integer quantity, Location location) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.quantity = quantity;
        this.location = location;
    }
}
