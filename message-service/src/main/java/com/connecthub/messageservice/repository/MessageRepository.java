package com.connecthub.messageservice.repository;

import com.connecthub.messageservice.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByRoomIdOrderByTimestampAsc(String roomId);
    java.util.Optional<Message> findByIdAndRoomId(Long id, String roomId);
    Page<Message> findByRoomIdOrderByTimestampDesc(String roomId, Pageable pageable);
    Page<Message> findByRoomIdAndContentContainingIgnoreCaseOrderByTimestampDesc(String roomId, String content, Pageable pageable);
}
