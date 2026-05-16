package com.connecthub.roomservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class RoomExceptionHandlerTest {

    private final RoomExceptionHandler handler = new RoomExceptionHandler();

    @Test
    void handlesIllegalArgumentWithContextualStatus() {
        assertThat(handler.handleIllegalArgument(new IllegalArgumentException("Room not found")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(handler.handleIllegalArgument(new IllegalArgumentException("Only admins can manage this room")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleIllegalArgument(new IllegalArgumentException((String) null)).getBody())
                .containsEntry("error", "Invalid room request");
    }

    @Test
    void handlesIllegalStateAndGenericErrors() {
        assertThat(handler.handleIllegalState(new IllegalStateException("limit reached")).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(handler.handleGeneric(new RuntimeException("boom")).getBody())
                .containsEntry("error", "boom");
    }
}
