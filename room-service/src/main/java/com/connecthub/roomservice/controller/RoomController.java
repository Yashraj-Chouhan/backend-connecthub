package com.connecthub.roomservice.controller;

import com.connecthub.roomservice.dto.RoomCreateRequest;
import com.connecthub.roomservice.dto.RoomUpdateRequest;
import com.connecthub.roomservice.entity.Room;
import com.connecthub.roomservice.entity.RoomMember;
import com.connecthub.roomservice.service.RoomService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
/**
 * Exposes room creation, membership, admin-role, and room metadata endpoints.
 */
@RequestMapping("/rooms")
@RequiredArgsConstructor
@Validated
public class RoomController {

    private final RoomService service;

    @PostMapping("/create")
    public Room create(@RequestParam String userId,
                       @RequestParam String name) {
        return service.createRoom(userId, name);
    }

    @PostMapping
    public Room create(@Valid @RequestBody RoomCreateRequest request) {
        return service.createRoom(request);
    }

    @PostMapping("/direct")
    public Room createDirect(@Valid @RequestBody RoomCreateRequest request) {
        return service.createDirectRoom(request);
    }

    @GetMapping("/users/{userId}")
    public List<Room> roomsForUser(@PathVariable String userId) {
        return service.getRoomsForUser(userId);
    }

    @GetMapping("/{roomId}")
    public Room getRoom(@PathVariable String roomId) {
        return service.getRoom(roomId);
    }

    @GetMapping("/{roomId}/members")
    public List<RoomMember> members(@PathVariable String roomId) {
        return service.getMembers(roomId);
    }

    @PostMapping("/add")
    public void add(@RequestParam String roomId,
                    @RequestParam String adminId,
                    @RequestParam String userId) {
        service.addMember(roomId, adminId, userId);
    }

    @PostMapping("/{roomId}/members")
    public List<RoomMember> addMembers(@PathVariable String roomId,
                                       @RequestParam String requestedBy,
                                       @RequestBody List<String> userIds) {
        return service.addMembers(roomId, requestedBy, userIds);
    }

    @PostMapping("/remove")
    public void remove(@RequestParam String roomId,
                       @RequestParam String adminId,
                       @RequestParam String userId) {
        service.removeMember(roomId, adminId, userId);
    }

    @DeleteMapping("/{roomId}/members/{userId}")
    public void removeMember(@PathVariable String roomId,
                             @PathVariable String userId,
                             @RequestParam String requestedBy) {
        service.removeMember(roomId, requestedBy, userId);
    }

    @PostMapping("/promote")
    public void promote(@RequestParam String roomId,
                        @RequestParam String adminId,
                        @RequestParam String userId) {
        service.promoteToAdmin(roomId, adminId, userId);
    }

    @PutMapping("/{roomId}/admins/{userId}")
    public void promoteAdmin(@PathVariable String roomId,
                             @PathVariable String userId,
                             @RequestParam String requestedBy) {
        service.promoteToAdmin(roomId, requestedBy, userId);
    }

    @DeleteMapping("/{roomId}/admins/{userId}")
    public void demoteAdmin(@PathVariable String roomId,
                            @PathVariable String userId,
                            @RequestParam String requestedBy) {
        service.demoteFromAdmin(roomId, requestedBy, userId);
    }

    @PostMapping("/{roomId}/leave/{userId}")
    public void leaveRoom(@PathVariable String roomId,
                          @PathVariable String userId) {
        service.leaveRoom(roomId, userId);
    }

    @PutMapping("/{roomId}")
    public Room updateRoom(@PathVariable String roomId,
                           @RequestParam String requestedBy,
                           @RequestBody RoomUpdateRequest request) {
        return service.updateRoom(roomId, requestedBy, request);
    }

    @DeleteMapping("/{roomId}")
    public Room deleteRoom(@PathVariable String roomId,
                           @RequestParam String requestedBy) {
        return service.deleteRoom(roomId, requestedBy);
    }

    @PutMapping("/{roomId}/last-message")
    public Room updateLastMessageAt(@PathVariable String roomId,
                                    @RequestParam(required = false) String timestamp) {
        return service.updateLastMessageAt(roomId, timestamp);
    }
}
