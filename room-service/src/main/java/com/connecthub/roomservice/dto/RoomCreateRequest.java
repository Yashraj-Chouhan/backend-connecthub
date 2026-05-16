package com.connecthub.roomservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomCreateRequest {

    private String name;

    @NotBlank
    private String createdBy;

    private String roomType;

    private List<String> memberUserIds;

    private String description;

    private String avatarUrl;

    private Integer maxMembers;

    private Boolean isPrivate;
}
