package com.brahyam.storagemanager.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "item_uses", indexes = @Index(name = "idx_item_uses_item", columnList = "item_id"))
public class ItemUse {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "item_id", nullable = false)
    private Item item;
    @ManyToOne(optional = false) @JoinColumn(name = "used_by_id", nullable = false)
    private User usedBy;
    @ManyToOne(optional = false) @JoinColumn(name = "location_id", nullable = false)
    private Location location;
    @Column(nullable = false) private Integer quantity;
    @Column(nullable = false) private LocalDateTime takenAt;
    private LocalDateTime returnedAt;
}
