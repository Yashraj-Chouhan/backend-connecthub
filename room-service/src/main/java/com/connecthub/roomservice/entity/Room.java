package com.connecthub.roomservice.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JsonProperty("id")
    @JsonAlias("roomId")
    private String roomId;

    private String name;

    @Column(name = "room_type")
    @JsonProperty("roomType")
    @JsonAlias({"type", "roomType"})
    private String roomType; // GROUP / DIRECT

    private String createdBy;

    private boolean isPrivate;

    private Integer maxMembers;

    private String description;

    private String avatarUrl;

    private String inviteCode;

    private LocalDateTime createdAt;

    private LocalDateTime lastMessageAt;
}
