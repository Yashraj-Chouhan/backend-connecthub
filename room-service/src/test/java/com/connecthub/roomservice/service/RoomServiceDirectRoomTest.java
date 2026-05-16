package com.connecthub.roomservice.service;

import com.connecthub.roomservice.dto.RoomCreateRequest;
import com.connecthub.roomservice.entity.Room;
import com.connecthub.roomservice.entity.RoomMember;
import com.connecthub.roomservice.repository.RoomMemberRepository;
import com.connecthub.roomservice.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceDirectRoomTest {

    @Mock
    private RoomRepository roomRepo;

    @Mock
    private RoomMemberRepository memberRepo;

    @InjectMocks
    private RoomService service;

    @Test
    void createDirectRoom_reusesExistingRoomForTheSameParticipants() {
        Room existingRoom = Room.builder()
                .roomId("room-1")
                .name("Direct Chat")
                .roomType("DIRECT")
                .createdBy("user-a")
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();

        RoomMember memberA = member("room-1", "user-a", "ADMIN");
        RoomMember memberB = member("room-1", "user-b", "MEMBER");

        when(memberRepo.findByUserId("user-a")).thenReturn(List.of(memberA));
        when(roomRepo.findAllById(any())).thenReturn(List.of(existingRoom));
        when(memberRepo.findByRoomId("room-1")).thenReturn(List.of(memberA, memberB));

        RoomCreateRequest request = RoomCreateRequest.builder()
                .createdBy("user-a")
                .name("Direct Chat")
                .roomType("DIRECT")
                .memberUserIds(List.of("user-b"))
                .build();

        Room result = service.createDirectRoom(request);

        assertSame(existingRoom, result);
        verify(roomRepo, never()).save(any());
        verify(memberRepo, never()).save(any());
    }

    @Test
    void getRoomsForUser_ignoresMembershipsWithoutRoomIds() {
        Room room = Room.builder()
                .roomId("room-1")
                .name("General")
                .roomType("GROUP")
                .createdAt(LocalDateTime.now().minusMinutes(10))
                .build();

        RoomMember orphanMembership = member(null, "user-a", "MEMBER");
        RoomMember validMembership = member("room-1", "user-a", "ADMIN");

        when(memberRepo.findByUserId("user-a")).thenReturn(List.of(orphanMembership, validMembership));
        when(roomRepo.findAllById(any())).thenReturn(List.of(room));

        List<Room> result = service.getRoomsForUser("user-a");

        assertEquals(List.of(room), result);
        verify(roomRepo).findAllById(any());
    }

    @Test
    void createDirectRoom_createsNewRoomWhenNoneExists() {
        when(memberRepo.findByUserId("user-a")).thenReturn(List.of());
        when(roomRepo.save(any())).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            room.setRoomId("room-2");
            return room;
        });
        when(memberRepo.findByRoomIdAndUserId(anyString(), anyString())).thenReturn(Optional.empty());

        RoomCreateRequest request = RoomCreateRequest.builder()
                .createdBy("user-a")
                .name("   ")
                .roomType("DIRECT")
                .memberUserIds(List.of("user-b"))
                .build();

        Room result = service.createDirectRoom(request);

        assertNotNull(result.getRoomId());
        assertEquals("room-2", result.getRoomId());
        assertEquals("Direct Chat", result.getName());
        assertEquals("DIRECT", result.getRoomType());
        assertEquals(2, result.getMaxMembers());

        ArgumentCaptor<Room> savedRoomCaptor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepo).save(savedRoomCaptor.capture());
        assertEquals("Direct Chat", savedRoomCaptor.getValue().getName());
        assertEquals("DIRECT", savedRoomCaptor.getValue().getRoomType());
        assertEquals("user-a", savedRoomCaptor.getValue().getCreatedBy());
        assertEquals(2, savedRoomCaptor.getValue().getMaxMembers());
        verify(memberRepo, times(2)).save(any());
    }

    private RoomMember member(String roomId, String userId, String role) {
        RoomMember roomMember = new RoomMember();
        roomMember.setRoomId(roomId);
        roomMember.setUserId(userId);
        roomMember.setRole(role);
        roomMember.setJoinedAt(LocalDateTime.now().minusMinutes(5));
        return roomMember;
    }
}
