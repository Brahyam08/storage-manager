package com.brahyam.storagemanager.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "item_consumptions")
public class ItemConsumption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;
    @ManyToOne(optional = false)
    @JoinColumn(name = "consumed_by_id", nullable = false)
    private User consumedBy;
    @ManyToOne
    @JoinColumn(name = "location_id")
    private Location location;
    @Column(nullable = false)
    private Integer quantity;
    @Column(nullable = false)
    private Integer remainingQuantity;
    @Column(nullable = false)
    private LocalDateTime consumedAt;
}
