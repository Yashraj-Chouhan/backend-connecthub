package com.connecthub.roomservice.service;

import com.connecthub.roomservice.dto.RoomCreateRequest;
import com.connecthub.roomservice.dto.RoomUpdateRequest;
import com.connecthub.roomservice.entity.Room;
import com.connecthub.roomservice.entity.RoomMember;
import com.connecthub.roomservice.repository.RoomMemberRepository;
import com.connecthub.roomservice.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
/**
 * Implements the rules around direct chats, group membership, admin privileges,
 * and room ordering based on the latest activity.
 */
@RequiredArgsConstructor
@Transactional
public class RoomService {

    private final RoomRepository roomRepo;
    private final RoomMemberRepository memberRepo;

    public Room createRoom(String userId, String name) {
        return createRoom(RoomCreateRequest.builder()
                .name(name)
                .createdBy(userId)
                .roomType("GROUP")
                .build());
    }

    /**
     * Creates either a group room or a direct room after applying the service's
     * membership, naming, and duplicate-conversation rules.
     */
    public Room createRoom(RoomCreateRequest request) {
        String createdBy = requireText(request.getCreatedBy(), "createdBy is required");
        String roomType = normalizeRoomType(request.getRoomType());
        List<String> memberIds = normalizeMemberIds(request.getMemberUserIds());
        boolean directRoom = isDirectRoom(roomType);
        String roomName = directRoom ? trimToNull(request.getName()) : requireText(request.getName(), "name is required");

        if (directRoom) {
            String otherMember = memberIds.stream()
                    .filter(memberId -> !createdBy.equals(memberId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Direct rooms require another participant"));
            Room existingRoom = findExistingDirectRoom(createdBy, otherMember);
            if (existingRoom != null) {
                return existingRoom;
            }
            if (!StringUtils.hasText(roomName)) {
                roomName = "Direct Chat";
            }
            memberIds = List.of(createdBy, otherMember);
            roomType = "DIRECT";
        } else {
            if (!memberIds.contains(createdBy)) {
                memberIds.add(createdBy);
            }
            roomType = "GROUP";
        }

        Room room = Room.builder()
                .name(roomName)
                .roomType(roomType)
                .createdBy(createdBy)
                .isPrivate(Boolean.TRUE.equals(request.getIsPrivate()))
                .maxMembers(resolveMaxMembers(request.getMaxMembers(), roomType, memberIds.size()))
                .description(trimToNull(request.getDescription()))
                .avatarUrl(trimToNull(request.getAvatarUrl()))
                .inviteCode(generateInviteCode())
                .createdAt(LocalDateTime.now())
                .build();

        Room saved = roomRepo.save(room);
        saveMembership(saved.getRoomId(), createdBy, "ADMIN");
        memberIds.stream()
                .filter(memberId -> !createdBy.equals(memberId))
                .forEach(memberId -> saveMembership(saved.getRoomId(), memberId, "MEMBER"));
        return saved;
    }

    /**
     * Convenience wrapper for forcing direct-message room creation rules.
     */
    public Room createDirectRoom(RoomCreateRequest request) {
        RoomCreateRequest directRequest = RoomCreateRequest.builder()
                .name(request.getName())
                .createdBy(request.getCreatedBy())
                .roomType("DIRECT")
                .memberUserIds(request.getMemberUserIds())
                .description(request.getDescription())
                .avatarUrl(request.getAvatarUrl())
                .maxMembers(2)
                .isPrivate(request.getIsPrivate())
                .build();
        return createRoom(directRequest);
    }

    public List<Room> getRoomsForUser(String userId) {
        List<RoomMember> memberships = memberRepo.findByUserId(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }

        List<Room> rooms = loadRoomsByIds(memberships.stream()
                .map(RoomMember::getRoomId)
                .collect(Collectors.toList()));
        rooms.sort(roomComparator());
        return rooms;
    }

    public Room getRoom(String roomId) {
        return roomRepo.findById(roomId).orElseThrow(() -> new IllegalArgumentException("Room not found"));
    }

    public List<RoomMember> getMembers(String roomId) {
        return memberRepo.findByRoomId(roomId);
    }

    public List<RoomMember> addMember(String roomId, String adminId, String userId) {
        return addMembers(roomId, adminId, List.of(userId));
    }

    /**
     * Adds one or more members to an existing room while enforcing admin access
     * and maximum member limits.
     */
    public List<RoomMember> addMembers(String roomId, String requestedBy, List<String> userIds) {
        Room room = getRoom(roomId);
        ensureCanManageRoom(room, requestedBy);

        List<String> newMemberIds = normalizeMemberIds(userIds);
        if (newMemberIds.isEmpty()) {
            return memberRepo.findByRoomId(roomId);
        }

        List<RoomMember> currentMembers = memberRepo.findByRoomId(roomId);
        int allowedMax = room.getMaxMembers() == null ? Integer.MAX_VALUE : room.getMaxMembers();
        if (currentMembers.size() + newMemberIds.size() > allowedMax) {
            throw new IllegalStateException("Room member limit reached");
        }

        newMemberIds.stream()
                .filter(memberId -> memberRepo.findByRoomIdAndUserId(roomId, memberId).isEmpty())
                .forEach(memberId -> saveMembership(roomId, memberId, "MEMBER"));

        return memberRepo.findByRoomId(roomId);
    }

    public void removeMember(String roomId, String adminId, String userId) {
        Room room = getRoom(roomId);
        ensureCanManageRoom(room, adminId);

        RoomMember member = memberRepo.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        memberRepo.delete(member);
        ensureRoomHasAdmin(roomId);
        removeRoomIfEmpty(roomId);
    }

    public void promoteToAdmin(String roomId, String adminId, String userId) {
        Room room = getRoom(roomId);
        ensureCanManageRoom(room, adminId);

        RoomMember member = memberRepo.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        member.setRole("ADMIN");
        memberRepo.save(member);
    }

    public void demoteFromAdmin(String roomId, String adminId, String userId) {
        Room room = getRoom(roomId);
        ensureCanManageRoom(room, adminId);

        RoomMember member = memberRepo.findByRoomIdAndUserId(roomId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        member.setRole("MEMBER");
        memberRepo.save(member);
    }

    public void leaveRoom(String roomId, String userId) {
        memberRepo.findByRoomIdAndUserId(roomId, userId).ifPresent(memberRepo::delete);
        ensureRoomHasAdmin(roomId);
        removeRoomIfEmpty(roomId);
    }

    public Room updateRoom(String roomId, String requestedBy, RoomUpdateRequest request) {
        Room room = getRoom(roomId);
        ensureCanManageRoom(room, requestedBy);

        if (StringUtils.hasText(request.getName())) {
            room.setName(request.getName().trim());
        }
        if (StringUtils.hasText(request.getRoomType())) {
            room.setRoomType(normalizeRoomType(request.getRoomType()));
        }
        if (request.getDescription() != null) {
            room.setDescription(trimToNull(request.getDescription()));
        }
        if (request.getAvatarUrl() != null) {
            room.setAvatarUrl(trimToNull(request.getAvatarUrl()));
        }
        if (request.getMaxMembers() != null && request.getMaxMembers() > 0) {
            room.setMaxMembers(request.getMaxMembers());
        }
        if (request.getIsPrivate() != null) {
            room.setPrivate(request.getIsPrivate());
        }

        return roomRepo.save(room);
    }

    public Room updateLastMessageAt(String roomId, String timestamp) {
        Room room = getRoom(roomId);
        room.setLastMessageAt(parseTimestamp(timestamp));
        return roomRepo.save(room);
    }

    /**
     * Deletes a group room for admins, or removes only the caller's membership
     * when the room is a direct conversation.
     */
    public Room deleteRoom(String roomId, String requestedBy) {
        Room room = getRoom(roomId);

        // For direct (1-on-1) chats, any member may remove the conversation from their
        // view – we simply remove their membership.  If the room becomes empty
        // afterwards it is cleaned up automatically.
        if ("DIRECT".equalsIgnoreCase(room.getRoomType())) {
            memberRepo.findByRoomIdAndUserId(roomId, requestedBy)
                    .ifPresent(memberRepo::delete);
            removeRoomIfEmpty(roomId);
            return room;
        }

        // For group rooms the requester must be the creator or an admin.
        ensureCanManageRoom(room, requestedBy);
        memberRepo.deleteAll(memberRepo.findByRoomId(roomId));
        roomRepo.delete(room);
        return room;
    }

    private void ensureCanManageRoom(Room room, String userId) {
        if (room == null) {
            throw new IllegalArgumentException("Room not found");
        }
        if (Objects.equals(room.getCreatedBy(), userId)) {
            return;
        }
        RoomMember membership = memberRepo.findByRoomIdAndUserId(room.getRoomId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Only room members can manage this room"));
        if (!"ADMIN".equalsIgnoreCase(membership.getRole())) {
            throw new IllegalArgumentException("Only admins can manage this room");
        }
    }

    private void saveMembership(String roomId, String userId, String role) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        RoomMember member = memberRepo.findByRoomIdAndUserId(roomId, userId)
                .orElseGet(RoomMember::new);
        member.setRoomId(roomId);
        member.setUserId(userId);
        member.setRole(role);
        if (member.getJoinedAt() == null) {
            member.setJoinedAt(LocalDateTime.now());
        }
        memberRepo.save(member);
    }

    private void removeRoomIfEmpty(String roomId) {
        if (memberRepo.findByRoomId(roomId).isEmpty()) {
            roomRepo.findById(roomId).ifPresent(roomRepo::delete);
        }
    }

    private void ensureRoomHasAdmin(String roomId) {
        List<RoomMember> members = memberRepo.findByRoomId(roomId);
        if (members.isEmpty()) {
            return;
        }

        boolean hasAdmin = members.stream()
                .anyMatch(member -> "ADMIN".equalsIgnoreCase(member.getRole()));
        if (hasAdmin) {
            return;
        }

        RoomMember firstMember = members.get(0);
        firstMember.setRole("ADMIN");
        memberRepo.save(firstMember);
    }

    private List<String> normalizeMemberIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }
        return userIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private boolean isDirectRoom(String roomType) {
        String normalized = normalizeRoomType(roomType);
        return "DIRECT".equals(normalized) || "DM".equals(normalized);
    }

    private Room findExistingDirectRoom(String createdBy, String otherMember) {
        Set<String> expectedMembers = Set.of(createdBy, otherMember);

        return loadRoomsByIds(memberRepo.findByUserId(createdBy).stream()
                .map(RoomMember::getRoomId)
                .collect(Collectors.toList()))
                .stream()
                .filter(room -> "DIRECT".equalsIgnoreCase(room.getRoomType()))
                .filter(room -> roomHasExactMembers(room.getRoomId(), expectedMembers))
                .sorted(roomComparator())
                .findFirst()
                .orElse(null);
    }

    private boolean roomHasExactMembers(String roomId, Set<String> expectedMembers) {
        Set<String> actualMembers = memberRepo.findByRoomId(roomId).stream()
                .map(RoomMember::getUserId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        return actualMembers.size() == expectedMembers.size() && actualMembers.equals(expectedMembers);
    }

    private String normalizeRoomType(String roomType) {
        if (!StringUtils.hasText(roomType)) {
            return "GROUP";
        }
        String normalized = roomType.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DM", "DIRECT" -> "DIRECT";
            case "GROUP" -> "GROUP";
            default -> normalized;
        };
    }

    private int resolveMaxMembers(Integer maxMembers, String roomType, int memberCount) {
        if ("DIRECT".equals(roomType)) {
            return 2;
        }
        if (maxMembers != null && maxMembers > 0) {
            return Math.max(maxMembers, memberCount);
        }
        return Math.max(memberCount, 2);
    }

    private String generateInviteCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        if (!StringUtils.hasText(timestamp)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(timestamp.trim());
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }

    private Comparator<Room> roomComparator() {
        return Comparator
                .comparing(Room::getLastMessageAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Room::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private List<Room> loadRoomsByIds(Collection<String> roomIds) {
        List<Room> rooms = new ArrayList<>();
        if (roomIds == null || roomIds.isEmpty()) {
            return rooms;
        }

        Set<String> validRoomIds = roomIds.stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (validRoomIds.isEmpty()) {
            return rooms;
        }

        roomRepo.findAllById(validRoomIds).forEach(rooms::add);
        return rooms;
    }
}
