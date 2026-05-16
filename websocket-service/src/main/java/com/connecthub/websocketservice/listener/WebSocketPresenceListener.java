package com.connecthub.websocketservice.listener;

import com.connecthub.websocketservice.client.AuthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
@Slf4j
/**
 * Updates user presence when WebSocket sessions connect and disconnect.
 */
public class WebSocketPresenceListener {
    private static final String USER_ID_HEADER = "userId";

    private final AuthClient authClient;

    @EventListener
    public void handleWebSocketConnect(SessionConnectedEvent event) {
        try {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
            String userId = accessor.getFirstNativeHeader(USER_ID_HEADER);
            if (!StringUtils.hasText(userId)) {
                return;
            }

            java.util.Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes != null) {
                sessionAttributes.put(USER_ID_HEADER, userId);
            }

            authClient.updateStatus(userId, "ONLINE");
        } catch (Exception ex) {
            log.debug("Failed to mark websocket session online", ex);
        }
    }

    @EventListener
    public void handleWebSocketDisconnect(SessionDisconnectEvent event) {
        try {
            StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
            String userId = null;
            java.util.Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes != null) {
                Object storedUserId = sessionAttributes.get(USER_ID_HEADER);
                if (storedUserId != null) {
                    userId = storedUserId.toString();
                }
            }

            if (!StringUtils.hasText(userId)) {
                return;
            }

            authClient.updateStatus(userId, "OFFLINE");
        } catch (Exception ex) {
            log.debug("Failed to mark websocket session offline", ex);
        }
    }
}
