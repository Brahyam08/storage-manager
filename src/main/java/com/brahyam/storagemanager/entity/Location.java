package com.brahyam.storagemanager.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "locations")
public class Location {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    private String description;

    // Nullable while legacy inventory awaits explicit assignment to a house.
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "house_id")
    private House house;
    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "parent_id")
    private Location parent;

    public Location(Long id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }
}
