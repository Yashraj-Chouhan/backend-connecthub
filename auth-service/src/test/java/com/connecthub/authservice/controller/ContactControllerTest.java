package com.connecthub.authservice.controller;

import com.connecthub.authservice.dto.ContactRequest;
import com.connecthub.authservice.dto.ContactResponse;
import com.connecthub.authservice.service.ContactService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ContactController.class)
@AutoConfigureMockMvc(addFilters = false)
class ContactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContactService contactService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listContacts_ReturnsContacts() throws Exception {
        String userId = "user-1";
        ContactResponse response = new ContactResponse("contact-1", userId, "contact@example.com", "Nick", null, null, "Contact Name", null, null, null, null, null, true);
        when(contactService.listContacts(userId)).thenReturn(List.of(response));

        mockMvc.perform(get("/auth/users/{userId}/contacts", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contactId").value("contact-1"));
    }

    @Test
    void searchContacts_ValidQuery_ReturnsResults() throws Exception {
        String userId = "user-1";
        String query = "contact";
        ContactResponse response = new ContactResponse("contact-1", userId, "contact@example.com", "Nick", null, null, "Contact Name", null, null, null, null, null, true);
        when(contactService.searchContacts(userId, query)).thenReturn(List.of(response));

        mockMvc.perform(get("/auth/users/{userId}/contacts/search", userId)
                        .param("query", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contactId").value("contact-1"));
    }

    @Test
    void searchContacts_BlankQuery_ReturnsBadRequest() throws Exception {
        String userId = "user-1";

        mockMvc.perform(get("/auth/users/{userId}/contacts/search", userId)
                        .param("query", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void saveContact_ValidRequest_ReturnsCreated() throws Exception {
        String userId = "user-1";
        ContactRequest request = new ContactRequest();
        request.setEmail("newcontact@example.com");
        request.setNickname("Nick");
        ContactResponse response = new ContactResponse("contact-1", userId, "newcontact@example.com", "Nick", null, null, "New Contact", null, null, null, null, null, true);
        when(contactService.saveContact(eq(userId), any(ContactRequest.class))).thenReturn(response);

        mockMvc.perform(post("/auth/users/{userId}/contacts", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contactId").value("contact-1"));
    }

    @Test
    void deleteContact_ValidRequest_ReturnsOk() throws Exception {
        String userId = "user-1";
        String contactId = "contact-1";

        mockMvc.perform(delete("/auth/users/{userId}/contacts/{contactId}", userId, contactId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Contact deleted successfully"));
    }
}
