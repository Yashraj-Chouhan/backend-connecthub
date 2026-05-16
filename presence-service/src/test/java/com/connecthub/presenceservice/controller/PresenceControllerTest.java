package com.connecthub.presenceservice.controller;

import com.connecthub.presenceservice.service.PresenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceControllerTest {

    @Mock
    private PresenceService presenceService;

    @InjectMocks
    private PresenceController controller;

    @Test
    void onlineMarksUserOnline() {
        assertThat(controller.online("alice")).isEqualTo("User is online");
        verify(presenceService).setOnline("alice");
    }

    @Test
    void offlineMarksUserOffline() {
        assertThat(controller.offline("alice")).isEqualTo("User is offline");
        verify(presenceService).setOffline("alice");
    }

    @Test
    void statusReturnsCurrentPresence() {
        when(presenceService.getStatus("alice")).thenReturn("ONLINE");

        assertThat(controller.status("alice")).isEqualTo("ONLINE");
        verify(presenceService).getStatus("alice");
    }
}
