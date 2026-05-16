package com.connecthub.authservice.repository;

import com.connecthub.authservice.entity.Contact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContactRepository extends JpaRepository<Contact, String> {
    List<Contact> findByOwnerUserIdOrderByUpdatedAtDesc(String ownerUserId);
    Optional<Contact> findByContactIdAndOwnerUserId(String contactId, String ownerUserId);
    Optional<Contact> findByOwnerUserIdAndContactEmailIgnoreCase(String ownerUserId, String contactEmail);
    boolean existsByOwnerUserIdAndContactEmailIgnoreCase(String ownerUserId, String contactEmail);
}
