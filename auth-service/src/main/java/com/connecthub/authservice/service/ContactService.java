package com.connecthub.authservice.service;

import com.connecthub.authservice.dto.ContactRequest;
import com.connecthub.authservice.dto.ContactResponse;
import com.connecthub.authservice.entity.Contact;
import com.connecthub.authservice.entity.User;
import com.connecthub.authservice.repository.ContactRepository;
import com.connecthub.authservice.repository.UserRepository;
import com.connecthub.authservice.validation.ValidationSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
/**
 * Encapsulates contact lookups and ownership checks for the auth-service
 * contact endpoints.
 */
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;
    private final UserRepository userRepository;

    public List<ContactResponse> listContacts(String ownerUserId) {
        requireOwner(ownerUserId);
        return contactRepository.findByOwnerUserIdOrderByUpdatedAtDesc(ownerUserId)
                .stream()
                .map(this::resolveAndMap)
                .collect(Collectors.toList());
    }

    public List<ContactResponse> searchContacts(String ownerUserId, String query) {
        String normalizedQuery = normalizeQuery(query);
        if (isBlank(normalizedQuery)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter something to search for.");
        }

        return listContacts(ownerUserId).stream()
                .filter(contact -> matches(contact, normalizedQuery))
                .collect(Collectors.toList());
    }

    public ContactResponse saveContact(String ownerUserId, ContactRequest request) {
        requireOwner(ownerUserId);
        String email = normalizeEmail(request == null ? null : request.getEmail());
        if (!isValidEmail(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please enter a valid email address.");
        }

        User resolvedUser = userRepository.findByEmailIgnoreCase(email).orElse(null);
        Contact contact = contactRepository.findByOwnerUserIdAndContactEmailIgnoreCase(ownerUserId, email)
                .orElse(Contact.builder()
                        .ownerUserId(ownerUserId)
                        .contactEmail(email)
                        .build());

        contact.setNickname(normalizeOptional(request == null ? null : request.getNickname()));
        if (resolvedUser != null) {
            contact.setContactUserId(resolvedUser.getUserId());
            contact.setContactName(displayName(resolvedUser));
        } else if (isBlank(contact.getContactName())) {
            contact.setContactName(email);
        }

        Contact saved = contactRepository.save(contact);
        return toResponse(saved, resolvedUser);
    }

    public void deleteContact(String ownerUserId, String contactId) {
        requireOwner(ownerUserId);
        Contact contact = contactRepository.findByContactIdAndOwnerUserId(contactId, ownerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "We couldn't find that contact."));
        contactRepository.delete(contact);
    }

    private ContactResponse resolveAndMap(Contact contact) {
        User resolvedUser = null;
        if (!isBlank(contact.getContactUserId())) {
            resolvedUser = userRepository.findById(contact.getContactUserId()).orElse(null);
        }

        if (resolvedUser == null) {
            resolvedUser = userRepository.findByEmailIgnoreCase(contact.getContactEmail()).orElse(null);
            if (resolvedUser != null && isBlank(contact.getContactUserId())) {
                contact.setContactUserId(resolvedUser.getUserId());
                contact.setContactName(displayName(resolvedUser));
                contactRepository.save(contact);
            }
        } else if (!displayName(resolvedUser).equals(contact.getContactName())) {
            contact.setContactName(displayName(resolvedUser));
            contactRepository.save(contact);
        }

        return toResponse(contact, resolvedUser);
    }

    private ContactResponse toResponse(Contact contact, User resolvedUser) {
        User user = resolvedUser;
        if (user == null && !isBlank(contact.getContactUserId())) {
            user = userRepository.findById(contact.getContactUserId()).orElse(null);
        }

        return new ContactResponse(
                contact.getContactId(),
                contact.getOwnerUserId(),
                contact.getContactEmail(),
                contact.getNickname(),
                contact.getContactUserId(),
                user == null ? null : user.getUsername(),
                user == null ? contact.getContactName() : displayName(user),
                user == null ? null : user.getAvatarUrl(),
                user == null ? null : user.getBio(),
                user == null ? null : user.getPreferredLanguage(),
                user == null ? null : user.getOnlineStatus(),
                user == null || user.getLastSeenAt() == null ? null : user.getLastSeenAt().toString(),
                user != null
        );
    }

    private boolean matches(ContactResponse contact, String query) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        return contains(contact.getContactEmail(), lowerQuery)
                || contains(contact.getNickname(), lowerQuery)
                || contains(contact.getFullName(), lowerQuery)
                || contains(contact.getUsername(), lowerQuery);
    }

    private boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private String normalizeEmail(String email) {
        return ValidationSupport.normalizeEmail(email);
    }

    private String normalizeQuery(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isValidEmail(String email) {
        return ValidationSupport.isValidEmail(email);
    }

    private String displayName(User user) {
        if (user == null) {
            return null;
        }
        if (!isBlank(user.getFullName())) {
            return user.getFullName();
        }
        if (!isBlank(user.getUsername())) {
            return user.getUsername();
        }
        return user.getEmail();
    }

    private void requireOwner(String ownerUserId) {
        if (isBlank(ownerUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please provide a user ID.");
        }
        if (!userRepository.existsById(ownerUserId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "We couldn't find that user.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
