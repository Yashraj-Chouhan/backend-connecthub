package com.connecthub.websocketservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsBadRequestAndGenericExceptions() {
        assertThat(handler.handleBadRequest(new IllegalArgumentException("bad")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handle(new RuntimeException()).getBody())
                .containsEntry("error", "Unexpected error");
    }

    @Test
    void fallsBackWhenExceptionMessagesAreMissing() {
        assertThat(handler.handleBadRequest(new IllegalArgumentException()).getBody())
                .containsEntry("error", "Bad request");
        assertThat(handler.handle(new RuntimeException()).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
