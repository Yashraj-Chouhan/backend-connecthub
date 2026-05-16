package com.connecthub.authservice.controller;

import com.connecthub.authservice.dto.ContactRequest;
import com.connecthub.authservice.dto.ContactResponse;
import com.connecthub.authservice.service.ContactService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
/**
 * Handles CRUD operations for a user's saved contacts inside ConnectHub.
 */
@RequestMapping("/auth/users/{userId}/contacts")
@Validated
@RequiredArgsConstructor
public class ContactController {

    private final ContactService contactService;

    @GetMapping
    public ResponseEntity<List<ContactResponse>> listContacts(@PathVariable String userId) {
        return ResponseEntity.ok(contactService.listContacts(userId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<ContactResponse>> searchContacts(@PathVariable String userId,
                                                                @RequestParam @NotBlank(message = "Please enter something to search for.")
                                                                String query) {
        return ResponseEntity.ok(contactService.searchContacts(userId, query));
    }

    @PostMapping
    public ResponseEntity<ContactResponse> saveContact(@PathVariable String userId,
                                                        @Valid @RequestBody ContactRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contactService.saveContact(userId, request));
    }

    @DeleteMapping("/{contactId}")
    public ResponseEntity<Map<String, String>> deleteContact(@PathVariable String userId,
                                                             @PathVariable String contactId) {
        contactService.deleteContact(userId, contactId);
        return ResponseEntity.ok(Map.of("message", "Contact deleted successfully"));
    }
}
