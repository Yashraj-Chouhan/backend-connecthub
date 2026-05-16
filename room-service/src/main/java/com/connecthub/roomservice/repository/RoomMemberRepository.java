package com.connecthub.roomservice.repository;

import com.connecthub.roomservice.entity.RoomMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomMemberRepository extends JpaRepository<RoomMember, String> {

    List<RoomMember> findByRoomId(String roomId);

    List<RoomMember> findByUserId(String userId);

    Optional<RoomMember> findByRoomIdAndUserId(String roomId, String userId);
}
