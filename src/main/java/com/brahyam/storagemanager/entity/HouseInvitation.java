package com.brahyam.storagemanager.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "house_invitations")
public class HouseInvitation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(optional = false)
    @JoinColumn(name = "house_id", nullable = false, unique = true)
    private House house;
    @Column(nullable = false, unique = true, length = 64)
    private String codeHash;
}
