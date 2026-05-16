package com.connecthub.authservice.service;

import com.connecthub.authservice.dto.ContactRequest;
import com.connecthub.authservice.dto.ContactResponse;
import com.connecthub.authservice.entity.Contact;
import com.connecthub.authservice.entity.User;
import com.connecthub.authservice.repository.ContactRepository;
import com.connecthub.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock
    private ContactRepository contactRepository;

    @Mock
    private UserRepository userRepository;

    private ContactService contactService;

    @BeforeEach
    void setUp() {
        contactService = new ContactService(contactRepository, userRepository);
    }

    @Test
    void listContacts_ValidUser_ReturnsContacts() {
        String ownerUserId = "user-1";
        Contact contact = Contact.builder()
                .contactId("contact-1")
                .ownerUserId(ownerUserId)
                .contactEmail("contact@example.com")
                .contactName("Contact Name")
                .build();

        when(userRepository.existsById(ownerUserId)).thenReturn(true);
        when(contactRepository.findByOwnerUserIdOrderByUpdatedAtDesc(ownerUserId)).thenReturn(List.of(contact));
        when(userRepository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());

        List<ContactResponse> result = contactService.listContacts(ownerUserId);

        assertEquals(1, result.size());
        assertEquals("contact-1", result.get(0).getContactId());
        verify(contactRepository).findByOwnerUserIdOrderByUpdatedAtDesc(ownerUserId);
    }

    @Test
    void listContacts_InvalidUser_ThrowsException() {
        String ownerUserId = "invalid-user";

        when(userRepository.existsById(ownerUserId)).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> contactService.listContacts(ownerUserId));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void searchContacts_ValidQuery_ReturnsFilteredContacts() {
        String ownerUserId = "user-1";
        String query = "contact";
        Contact contact = Contact.builder()
                .contactId("contact-1")
                .ownerUserId(ownerUserId)
                .contactEmail("contact@example.com")
                .contactName("Contact Name")
                .build();

        when(userRepository.existsById(ownerUserId)).thenReturn(true);
        when(contactRepository.findByOwnerUserIdOrderByUpdatedAtDesc(ownerUserId)).thenReturn(List.of(contact));

        List<ContactResponse> result = contactService.searchContacts(ownerUserId, query);

        assertEquals(1, result.size());
    }

    @Test
    void searchContacts_BlankQuery_ThrowsException() {
        String ownerUserId = "user-1";
        String query = "   ";

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> contactService.searchContacts(ownerUserId, query));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void saveContact_ValidRequest_SavesAndReturnsContact() {
        String ownerUserId = "user-1";
        ContactRequest request = new ContactRequest();
        request.setEmail("newcontact@example.com");
        request.setNickname("Nick");

        User resolvedUser = User.builder()
                .userId("contact-user-1")
                .username("contactuser")
                .fullName("Contact User")
                .build();

        when(userRepository.existsById(ownerUserId)).thenReturn(true);
        when(userRepository.findByEmailIgnoreCase("newcontact@example.com")).thenReturn(Optional.of(resolvedUser));
        when(contactRepository.findByOwnerUserIdAndContactEmailIgnoreCase(ownerUserId, "newcontact@example.com"))
                .thenReturn(Optional.empty());
        when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContactResponse result = contactService.saveContact(ownerUserId, request);

        assertNotNull(result);
        assertEquals("newcontact@example.com", result.getContactEmail());
        assertEquals("Nick", result.getNickname());
        verify(contactRepository).save(any(Contact.class));
    }

    @Test
    void saveContact_UsesEmailAsDisplayNameWhenUserCannotBeResolved() {
        String ownerUserId = "user-1";
        ContactRequest request = new ContactRequest();
        request.setEmail("unknown@example.com");
        request.setNickname(" Friend ");

        when(userRepository.existsById(ownerUserId)).thenReturn(true);
        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());
        when(contactRepository.findByOwnerUserIdAndContactEmailIgnoreCase(ownerUserId, "unknown@example.com"))
                .thenReturn(Optional.empty());
        when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContactResponse result = contactService.saveContact(ownerUserId, request);

        assertEquals("unknown@example.com", result.getFullName());
        assertEquals("Friend", result.getNickname());
        assertFalse(result.isRegistered());
    }

    @Test
    void saveContact_InvalidEmail_ThrowsException() {
        String ownerUserId = "user-1";
        ContactRequest request = new ContactRequest();
        request.setEmail("invalid-email");

        when(userRepository.existsById(ownerUserId)).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> contactService.saveContact(ownerUserId, request));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void deleteContact_ValidContact_DeletesSuccessfully() {
        String ownerUserId = "user-1";
        String contactId = "contact-1";
        Contact contact = Contact.builder().contactId(contactId).build();

        when(userRepository.existsById(ownerUserId)).thenReturn(true);
        when(contactRepository.findByContactIdAndOwnerUserId(contactId, ownerUserId)).thenReturn(Optional.of(contact));

        contactService.deleteContact(ownerUserId, contactId);

        verify(contactRepository).delete(contact);
    }

    @Test
    void deleteContact_NotFound_ThrowsException() {
        String ownerUserId = "user-1";
        String contactId = "contact-1";

        when(userRepository.existsById(ownerUserId)).thenReturn(true);
        when(contactRepository.findByContactIdAndOwnerUserId(contactId, ownerUserId)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> contactService.deleteContact(ownerUserId, contactId));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void listContacts_BackfillsResolvedUserMetadataWhenContactWasImportedByEmail() {
        String ownerUserId = "user-1";
        Contact contact = Contact.builder()
                .contactId("contact-2")
                .ownerUserId(ownerUserId)
                .contactEmail("friend@example.com")
                .nickname("Buddy")
                .build();
        User resolvedUser = User.builder()
                .userId("resolved-1")
                .username("frienduser")
                .fullName("Friend User")
                .avatarUrl("/avatar.png")
                .bio("bio")
                .preferredLanguage("en")
                .onlineStatus("ONLINE")
                .lastSeenAt(LocalDateTime.now().minusMinutes(5))
                .build();

        when(userRepository.existsById(ownerUserId)).thenReturn(true);
        when(contactRepository.findByOwnerUserIdOrderByUpdatedAtDesc(ownerUserId)).thenReturn(List.of(contact));
        when(userRepository.findByEmailIgnoreCase("friend@example.com")).thenReturn(Optional.of(resolvedUser));
        when(contactRepository.save(any(Contact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<ContactResponse> result = contactService.listContacts(ownerUserId);

        assertEquals(1, result.size());
        assertEquals("resolved-1", result.get(0).getContactUserId());
        assertEquals("frienduser", result.get(0).getUsername());
        assertTrue(result.get(0).isRegistered());
        verify(contactRepository).save(contact);
    }

    @Test
    void searchContacts_MatchesNicknameAndUsernameCaseInsensitively() {
        String ownerUserId = "user-1";
        Contact contact = Contact.builder()
                .contactId("contact-3")
                .ownerUserId(ownerUserId)
                .contactEmail("friend@example.com")
                .nickname("Design Buddy")
                .contactUserId("friend-1")
                .contactName("Friend User")
                .build();
        User resolvedUser = User.builder()
                .userId("friend-1")
                .username("FriendlyUser")
                .fullName("Friend User")
                .build();

        when(userRepository.existsById(ownerUserId)).thenReturn(true);
        when(contactRepository.findByOwnerUserIdOrderByUpdatedAtDesc(ownerUserId)).thenReturn(List.of(contact));
        when(userRepository.findById("friend-1")).thenReturn(Optional.of(resolvedUser));

        List<ContactResponse> result = contactService.searchContacts(ownerUserId, "buddy");
        List<ContactResponse> byUsername = contactService.searchContacts(ownerUserId, "friendly");

        assertEquals(1, result.size());
        assertEquals(1, byUsername.size());
    }
}
