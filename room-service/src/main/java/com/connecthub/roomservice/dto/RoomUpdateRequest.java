package com.connecthub.roomservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomUpdateRequest {

    private String name;
    private String roomType;
    private String description;
    private String avatarUrl;
    private Integer maxMembers;
    private Boolean isPrivate;
}
