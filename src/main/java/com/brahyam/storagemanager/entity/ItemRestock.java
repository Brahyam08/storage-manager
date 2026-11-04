package com.brahyam.storagemanager.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "item_restocks")
public class ItemRestock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;
    @ManyToOne(optional = false)
    @JoinColumn(name = "restocked_by_id", nullable = false)
    private User restockedBy;
    @ManyToOne(optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;
    @Column(nullable = false)
    private Integer quantity;
    @Column(nullable = false)
    private Integer resultingQuantity;
    @Column(nullable = false)
    private LocalDateTime restockedAt;
}
