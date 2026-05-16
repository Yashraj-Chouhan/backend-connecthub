package com.connecthub.websocketservice.listener;

import com.connecthub.websocketservice.client.AuthClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class WebSocketPresenceListenerTest {

    @Mock
    private AuthClient authClient;

    @InjectMocks
    private WebSocketPresenceListener listener;

    @Test
    void marksUserOnlineAndStoresSessionUserId() {
        Map<String, Object> sessionAttributes = new HashMap<>();
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0])
                .setHeader("nativeHeaders", Map.of("userId", java.util.List.of("alice")))
                .setHeader("simpSessionAttributes", sessionAttributes)
                .build();

        listener.handleWebSocketConnect(new SessionConnectedEvent(this, message));

        verify(authClient).updateStatus("alice", "ONLINE");
        org.assertj.core.api.Assertions.assertThat(sessionAttributes).containsEntry("userId", "alice");
    }

    @Test
    void marksUserOnlineEvenWhenSessionAttributesAreMissing() {
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0])
                .setHeader("nativeHeaders", Map.of("userId", java.util.List.of("alice")))
                .build();

        listener.handleWebSocketConnect(new SessionConnectedEvent(this, message));

        verify(authClient).updateStatus("alice", "ONLINE");
    }

    @Test
    void ignoresConnectWithoutUserId() {
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).build();
        listener.handleWebSocketConnect(new SessionConnectedEvent(this, message));
        verifyNoInteractions(authClient);
    }

    @Test
    void swallowsConnectStatusUpdateFailures() {
        doThrow(new RuntimeException("down")).when(authClient).updateStatus("alice", "ONLINE");
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0])
                .setHeader("nativeHeaders", Map.of("userId", java.util.List.of("alice")))
                .build();

        assertThatNoException().isThrownBy(() -> listener.handleWebSocketConnect(new SessionConnectedEvent(this, message)));

        verify(authClient).updateStatus("alice", "ONLINE");
    }

    @Test
    void marksUserOfflineFromStoredSessionAttribute() {
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0])
                .setHeader("simpSessionAttributes", Map.of("userId", "alice"))
                .build();

        listener.handleWebSocketDisconnect(new SessionDisconnectEvent(this, message, "1", null));

        verify(authClient).updateStatus("alice", "OFFLINE");
    }

    @Test
    void ignoresDisconnectWithoutStoredUserId() {
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).build();
        listener.handleWebSocketDisconnect(new SessionDisconnectEvent(this, message, "1", null));
        verifyNoInteractions(authClient);
    }

    @Test
    void ignoresDisconnectWithEmptySessionAttributes() {
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0])
                .setHeader("simpSessionAttributes", new HashMap<String, Object>())
                .build();

        listener.handleWebSocketDisconnect(new SessionDisconnectEvent(this, message, "1", null));

        verifyNoInteractions(authClient);
    }

    @Test
    void swallowsDisconnectStatusUpdateFailures() {
        doThrow(new RuntimeException("down")).when(authClient).updateStatus("alice", "OFFLINE");
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0])
                .setHeader("simpSessionAttributes", Map.of("userId", "alice"))
                .build();

        assertThatNoException().isThrownBy(() -> listener.handleWebSocketDisconnect(new SessionDisconnectEvent(this, message, "1", null)));

        verify(authClient).updateStatus("alice", "OFFLINE");
    }
}
