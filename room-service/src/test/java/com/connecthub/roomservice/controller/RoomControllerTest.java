package com.connecthub.roomservice.controller;

import com.connecthub.roomservice.dto.RoomCreateRequest;
import com.connecthub.roomservice.dto.RoomUpdateRequest;
import com.connecthub.roomservice.entity.Room;
import com.connecthub.roomservice.entity.RoomMember;
import com.connecthub.roomservice.service.RoomService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {

    @Mock
    private RoomService roomService;

    @InjectMocks
    private RoomController controller;

    @Test
    void delegatesCreateEndpoints() {
        RoomCreateRequest request = RoomCreateRequest.builder().createdBy("owner").name("Team").build();
        Room room = Room.builder().roomId("room-1").name("Team").build();

        when(roomService.createRoom("owner", "Team")).thenReturn(room);
        when(roomService.createRoom(request)).thenReturn(room);
        when(roomService.createDirectRoom(request)).thenReturn(room);

        assertThat(controller.create("owner", "Team")).isSameAs(room);
        assertThat(controller.create(request)).isSameAs(room);
        assertThat(controller.createDirect(request)).isSameAs(room);
    }

    @Test
    void delegatesQueryEndpoints() {
        Room room = Room.builder().roomId("room-1").build();
        RoomMember member = new RoomMember();
        member.setUserId("alice");

        when(roomService.getRoomsForUser("alice")).thenReturn(List.of(room));
        when(roomService.getRoom("room-1")).thenReturn(room);
        when(roomService.getMembers("room-1")).thenReturn(List.of(member));

        assertThat(controller.roomsForUser("alice")).containsExactly(room);
        assertThat(controller.getRoom("room-1")).isSameAs(room);
        assertThat(controller.members("room-1")).containsExactly(member);
    }

    @Test
    void delegatesMembershipAndAdminEndpoints() {
        RoomMember member = new RoomMember();
        member.setUserId("bob");
        when(roomService.addMembers("room-1", "owner", List.of("bob"))).thenReturn(List.of(member));

        controller.add("room-1", "owner", "bob");
        assertThat(controller.addMembers("room-1", "owner", List.of("bob"))).containsExactly(member);
        controller.remove("room-1", "owner", "bob");
        controller.removeMember("room-1", "bob", "owner");
        controller.promote("room-1", "owner", "bob");
        controller.promoteAdmin("room-1", "bob", "owner");
        controller.demoteAdmin("room-1", "bob", "owner");
        controller.leaveRoom("room-1", "bob");

        verify(roomService).addMember("room-1", "owner", "bob");
        verify(roomService).addMembers("room-1", "owner", List.of("bob"));
        verify(roomService, times(2)).removeMember("room-1", "owner", "bob");
        verify(roomService, times(2)).promoteToAdmin("room-1", "owner", "bob");
        verify(roomService).demoteFromAdmin("room-1", "owner", "bob");
        verify(roomService).leaveRoom("room-1", "bob");
    }

    @Test
    void delegatesRoomMutationEndpoints() {
        Room room = Room.builder().roomId("room-1").name("Updated").build();
        RoomUpdateRequest request = RoomUpdateRequest.builder().name("Updated").build();

        when(roomService.updateRoom("room-1", "owner", request)).thenReturn(room);
        when(roomService.deleteRoom("room-1", "owner")).thenReturn(room);
        when(roomService.updateLastMessageAt("room-1", "2026-01-01T10:00:00")).thenReturn(room);

        assertThat(controller.updateRoom("room-1", "owner", request)).isSameAs(room);
        assertThat(controller.deleteRoom("room-1", "owner")).isSameAs(room);
        assertThat(controller.updateLastMessageAt("room-1", "2026-01-01T10:00:00")).isSameAs(room);
    }
}
