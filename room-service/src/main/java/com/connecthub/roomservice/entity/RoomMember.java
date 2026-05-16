package com.connecthub.roomservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class RoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String roomId;
    private String userId;

    private String role; // ADMIN / MEMBER

    private LocalDateTime joinedAt;

    private LocalDateTime lastReadAt;

    @Column(name = "is_muted", nullable = false)
    private boolean muted;
}
