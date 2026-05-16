package com.connecthub.roomservice.service;

import com.connecthub.roomservice.dto.RoomCreateRequest;
import com.connecthub.roomservice.dto.RoomUpdateRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceManagementTest {

    @Mock
    private RoomRepository roomRepo;

    @Mock
    private RoomMemberRepository memberRepo;

    @InjectMocks
    private RoomService service;

    @Test
    void createRoomBuildsGroupMetadataAndAddsCreatorMembership() {
        when(roomRepo.save(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            room.setRoomId("room-100");
            return room;
        });
        when(memberRepo.findByRoomIdAndUserId(anyString(), anyString())).thenReturn(Optional.empty());

        RoomCreateRequest request = RoomCreateRequest.builder()
                .createdBy(" owner ")
                .name(" Product Squad ")
                .roomType("group")
                .memberUserIds(List.of("owner", " bob ", "bob", "   "))
                .description(" Sprint planning ")
                .avatarUrl(" /images/room.png ")
                .maxMembers(1)
                .isPrivate(true)
                .build();

        Room created = service.createRoom(request);

        assertThat(created.getRoomId()).isEqualTo("room-100");
        assertThat(created.getName()).isEqualTo("Product Squad");
        assertThat(created.getRoomType()).isEqualTo("GROUP");
        assertThat(created.getCreatedBy()).isEqualTo("owner");
        assertThat(created.isPrivate()).isTrue();
        assertThat(created.getDescription()).isEqualTo("Sprint planning");
        assertThat(created.getAvatarUrl()).isEqualTo("/images/room.png");
        assertThat(created.getMaxMembers()).isEqualTo(2);
        assertThat(created.getInviteCode()).hasSize(8);
        assertThat(created.getCreatedAt()).isNotNull();

        ArgumentCaptor<RoomMember> membershipCaptor = ArgumentCaptor.forClass(RoomMember.class);
        verify(memberRepo, times(2)).save(membershipCaptor.capture());
        assertThat(membershipCaptor.getAllValues())
                .extracting(RoomMember::getUserId, RoomMember::getRole)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("owner", "ADMIN"),
                        org.assertj.core.groups.Tuple.tuple("bob", "MEMBER")
                );
    }

    @Test
    void addMembersAddsOnlyNewMembers() {
        Room room = room("room-1", "owner", "GROUP");
        RoomMember owner = member("room-1", "owner", "ADMIN");
        RoomMember bob = member("room-1", "bob", "MEMBER");
        RoomMember cara = member("room-1", "cara", "MEMBER");

        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoomId("room-1"))
                .thenReturn(List.of(owner, bob))
                .thenReturn(List.of(owner, bob, cara));
        when(memberRepo.findByRoomIdAndUserId("room-1", "bob")).thenReturn(Optional.of(bob));
        when(memberRepo.findByRoomIdAndUserId("room-1", "cara")).thenReturn(Optional.empty());

        List<RoomMember> result = service.addMembers("room-1", "owner", List.of("bob", "cara"));

        assertThat(result).extracting(RoomMember::getUserId).containsExactly("owner", "bob", "cara");

        ArgumentCaptor<RoomMember> membershipCaptor = ArgumentCaptor.forClass(RoomMember.class);
        verify(memberRepo).save(membershipCaptor.capture());
        assertThat(membershipCaptor.getValue().getUserId()).isEqualTo("cara");
        assertThat(membershipCaptor.getValue().getRole()).isEqualTo("MEMBER");
    }

    @Test
    void addMembersRejectsWhenRoomLimitWouldBeExceeded() {
        Room room = room("room-1", "owner", "GROUP");
        room.setMaxMembers(2);
        RoomMember owner = member("room-1", "owner", "ADMIN");
        RoomMember bob = member("room-1", "bob", "MEMBER");

        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoomId("room-1")).thenReturn(List.of(owner, bob));

        assertThatThrownBy(() -> service.addMembers("room-1", "owner", List.of("cara")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("limit");

        verify(memberRepo, never()).save(any(RoomMember.class));
    }

    @Test
    void removeMemberPromotesAnotherMemberWhenAdminListBecomesEmpty() {
        Room room = room("room-1", "moderator", "GROUP");
        RoomMember alice = member("room-1", "alice", "ADMIN");
        RoomMember bob = member("room-1", "bob", "MEMBER");

        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoomIdAndUserId("room-1", "alice")).thenReturn(Optional.of(alice));
        when(memberRepo.findByRoomId("room-1"))
                .thenReturn(List.of(bob))
                .thenReturn(List.of(bob));

        service.removeMember("room-1", "moderator", "alice");

        assertThat(bob.getRole()).isEqualTo("ADMIN");
        verify(memberRepo).delete(alice);
        verify(memberRepo).save(bob);
    }

    @Test
    void updateRoomTrimsOptionalFieldsAndNormalizesDirectRoomType() {
        Room room = room("room-1", "owner", "GROUP");
        room.setDescription("old");
        room.setAvatarUrl("/old.png");
        room.setMaxMembers(6);
        room.setPrivate(true);

        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(roomRepo.save(room)).thenReturn(room);

        RoomUpdateRequest request = RoomUpdateRequest.builder()
                .name(" Team DM ")
                .roomType("dm")
                .description("   ")
                .avatarUrl(" ")
                .maxMembers(0)
                .isPrivate(false)
                .build();

        Room updated = service.updateRoom("room-1", "owner", request);

        assertThat(updated.getName()).isEqualTo("Team DM");
        assertThat(updated.getRoomType()).isEqualTo("DIRECT");
        assertThat(updated.getDescription()).isNull();
        assertThat(updated.getAvatarUrl()).isNull();
        assertThat(updated.getMaxMembers()).isEqualTo(6);
        assertThat(updated.isPrivate()).isFalse();
    }

    @Test
    void updateLastMessageAtFallsBackToCurrentTimeForInvalidTimestamp() {
        Room room = room("room-1", "owner", "GROUP");
        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(roomRepo.save(room)).thenReturn(room);

        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        Room updated = service.updateLastMessageAt("room-1", "not-a-date");
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        assertThat(updated.getLastMessageAt()).isNotNull();
        assertThat(updated.getLastMessageAt()).isAfter(before);
        assertThat(updated.getLastMessageAt()).isBefore(after);
    }

    @Test
    void deleteDirectRoomRemovesOnlyRequestersMembershipWhenOthersRemain() {
        Room room = room("room-1", "alice", "DIRECT");
        RoomMember alice = member("room-1", "alice", "ADMIN");
        RoomMember bob = member("room-1", "bob", "MEMBER");

        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoomIdAndUserId("room-1", "alice")).thenReturn(Optional.of(alice));
        when(memberRepo.findByRoomId("room-1")).thenReturn(List.of(bob));

        Room deleted = service.deleteRoom("room-1", "alice");

        assertThat(deleted).isSameAs(room);
        verify(memberRepo).delete(alice);
        verify(roomRepo, never()).delete(room);
    }

    @Test
    void deleteGroupRoomRemovesAllMembershipsAndRoom() {
        Room room = room("room-1", "owner", "GROUP");
        RoomMember owner = member("room-1", "owner", "ADMIN");
        RoomMember bob = member("room-1", "bob", "MEMBER");

        when(roomRepo.findById("room-1")).thenReturn(Optional.of(room));
        when(memberRepo.findByRoomId("room-1")).thenReturn(List.of(owner, bob));

        Room deleted = service.deleteRoom("room-1", "owner");

        assertThat(deleted).isSameAs(room);
        verify(memberRepo).deleteAll(List.of(owner, bob));
        verify(roomRepo).delete(room);
    }

    @Test
    void createDirectRoomRejectsMissingOtherParticipant() {
        RoomCreateRequest request = RoomCreateRequest.builder()
                .createdBy("owner")
                .roomType("DIRECT")
                .memberUserIds(List.of("owner"))
                .build();

        assertThatThrownBy(() -> service.createDirectRoom(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("another participant");
    }

    private Room room(String roomId, String createdBy, String roomType) {
        return Room.builder()
                .roomId(roomId)
                .createdBy(createdBy)
                .roomType(roomType)
                .name("Room")
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();
    }

    private RoomMember member(String roomId, String userId, String role) {
        RoomMember roomMember = new RoomMember();
        roomMember.setRoomId(roomId);
        roomMember.setUserId(userId);
        roomMember.setRole(role);
        roomMember.setJoinedAt(LocalDateTime.now().minusMinutes(10));
        return roomMember;
    }
}
