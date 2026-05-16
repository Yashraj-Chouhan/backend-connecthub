package com.connecthub.roomservice.repository;

import com.connecthub.roomservice.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, String> {
}