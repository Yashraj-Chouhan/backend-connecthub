package com.connecthub.messageservice.controller;

import com.connecthub.messageservice.client.AuthClient;
import com.connecthub.messageservice.client.SpeechTranscriptionClient;
import com.connecthub.messageservice.client.TranslationClient;
import com.connecthub.messageservice.dto.MessageTranslationResponse;
import com.connecthub.messageservice.dto.TranscriptionResponse;
import com.connecthub.messageservice.dto.TranslationRequest;
import com.connecthub.messageservice.dto.TranslationResponse;
import com.connecthub.messageservice.dto.UserSummaryResponse;
import com.connecthub.messageservice.entity.Message;
import com.connecthub.messageservice.service.MessageService;
import com.connecthub.messageservice.service.TranscriptEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageControllerTest {

    @Mock
    private MessageService service;

    @Mock
    private TranslationClient translationClient;

    @Mock
    private AuthClient authClient;

    @Mock
    private SpeechTranscriptionClient speechTranscriptionClient;

    @Mock
    private TranscriptEventPublisher transcriptEventPublisher;

    private MessageController controller;

    @BeforeEach
    void setUp() {
        controller = new MessageController(service, translationClient, authClient, speechTranscriptionClient, transcriptEventPublisher);
        ReflectionTestUtils.setField(controller, "authServiceTopupSecret", "test-topup-secret");
    }

    @Test
    void translateMessageDefaultsToUsersPreferredHindiLanguage() {
        Message message = Message.builder()
                .id(7L)
                .roomId("room-1")
                .content("hello")
                .build();

        when(service.getMessage(7L, "room-1")).thenReturn(message);
        when(service.isTranslatable(message)).thenReturn(true);
        when(authClient.consumeTranslationCredit("user-1")).thenReturn(summaryWithLanguage("hindi", 49));
        when(translationClient.translate(any(TranslationRequest.class))).thenReturn(new TranslationResponse(
                "hello",
                "\u0928\u092E\u0938\u094D\u0924\u0947",
                "en",
                "hi",
                "libretranslate",
                true,
                null
        ));

        ResponseEntity<MessageTranslationResponse> response =
                controller.translateMessage("room-1", 7L, null, "user-1");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getTranslatedContent()).isEqualTo("\u0928\u092E\u0938\u094D\u0924\u0947");
        assertThat(response.getBody().getTargetLanguage()).isEqualTo("hi");

        ArgumentCaptor<TranslationRequest> requestCaptor = ArgumentCaptor.forClass(TranslationRequest.class);
        verify(translationClient).translate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getTargetLang()).isEqualTo("hi");
    }

    @Test
    void translateMessageDefaultsToUsersPreferredJapaneseLanguage() {
        Message message = Message.builder()
                .id(8L)
                .roomId("room-1")
                .content("hello")
                .build();

        when(service.getMessage(8L, "room-1")).thenReturn(message);
        when(service.isTranslatable(message)).thenReturn(true);
        when(authClient.consumeTranslationCredit("user-2")).thenReturn(summaryWithLanguage("Japanese", 49));
        when(translationClient.translate(any(TranslationRequest.class))).thenReturn(new TranslationResponse(
                "hello",
                "\u3053\u3093\u306b\u3061\u306f",
                "en",
                "ja",
                "libretranslate",
                true,
                null
        ));

        ResponseEntity<MessageTranslationResponse> response =
                controller.translateMessage("room-1", 8L, null, "user-2");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getTranslatedContent()).isEqualTo("\u3053\u3093\u306b\u3061\u306f");
        assertThat(response.getBody().getTargetLanguage()).isEqualTo("ja");

        ArgumentCaptor<TranslationRequest> requestCaptor = ArgumentCaptor.forClass(TranslationRequest.class);
        verify(translationClient).translate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getTargetLang()).isEqualTo("ja");
    }

    @Test
    void translateVoiceNoteTranscribesAudioBeforeTranslating() {
        Message message = Message.builder()
                .id(9L)
                .roomId("room-voice")
                .messageType("VOICE_NOTE")
                .content("Voice note")
                .attachmentContentType("audio/webm")
                .attachmentPath("uploads/voice-note.webm")
                .build();
        Message updatedMessage = Message.builder()
                .id(9L)
                .roomId("room-voice")
                .messageType("VOICE_NOTE")
                .content("Voice note")
                .attachmentContentType("audio/webm")
                .attachmentPath("uploads/voice-note.webm")
                .transcript("namaste duniya")
                .transcriptSourceLanguage("hi")
                .build();

        when(service.getMessage(9L, "room-voice")).thenReturn(message);
        when(service.isTranslatable(message)).thenReturn(true);
        when(service.isVoiceNote(message)).thenReturn(true);
        when(authClient.consumeTranslationCredit("user-3")).thenReturn(summaryWithLanguage("english", 49));
        when(speechTranscriptionClient.transcribe(message, "auto")).thenReturn(TranscriptionResponse.builder()
                .transcript("namaste duniya")
                .sourceLanguage("hi")
                .provider("openai-audio-transcriptions")
                .success(true)
                .build());
        when(service.updateTranscript(9L, "room-voice", "namaste duniya", "hi")).thenReturn(updatedMessage);
        when(translationClient.translate(any(TranslationRequest.class))).thenReturn(new TranslationResponse(
                "namaste duniya",
                "hello world",
                "hi",
                "en",
                "libretranslate",
                true,
                null
        ));

        ResponseEntity<MessageTranslationResponse> response =
                controller.translateMessage("room-voice", 9L, "en", "user-3");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessageType()).isEqualTo("VOICE_NOTE");
        assertThat(response.getBody().getOriginalContent()).isEqualTo("namaste duniya");
        assertThat(response.getBody().getTranslatedContent()).isEqualTo("hello world");
        assertThat(response.getBody().getDetectedSourceLanguage()).isEqualTo("hi");

        ArgumentCaptor<TranslationRequest> requestCaptor = ArgumentCaptor.forClass(TranslationRequest.class);
        verify(translationClient).translate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getText()).isEqualTo("namaste duniya");
        assertThat(requestCaptor.getValue().getSourceLang()).isEqualTo("auto");
        assertThat(requestCaptor.getValue().getTargetLang()).isEqualTo("en");
        verify(transcriptEventPublisher).publish(updatedMessage);
    }

    @Test
    void translateVoiceNoteReusesCachedTranscriptWithoutRetranscribing() {
        Message message = Message.builder()
                .id(10L)
                .roomId("room-voice")
                .messageType("VOICE_NOTE")
                .content("Voice note")
                .attachmentContentType("audio/webm")
                .attachmentPath("uploads/voice-note.webm")
                .transcript("bonjour le monde")
                .transcriptSourceLanguage("fr")
                .build();

        when(service.getMessage(10L, "room-voice")).thenReturn(message);
        when(service.isTranslatable(message)).thenReturn(true);
        when(service.isVoiceNote(message)).thenReturn(true);
        when(authClient.consumeTranslationCredit("user-4")).thenReturn(summaryWithLanguage("english", 49));
        when(translationClient.translate(any(TranslationRequest.class))).thenReturn(new TranslationResponse(
                "bonjour le monde",
                "hello world",
                "fr",
                "en",
                "libretranslate",
                true,
                null
        ));

        ResponseEntity<MessageTranslationResponse> response =
                controller.translateMessage("room-voice", 10L, "en", "user-4");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getOriginalContent()).isEqualTo("bonjour le monde");
        ArgumentCaptor<TranslationRequest> requestCaptor = ArgumentCaptor.forClass(TranslationRequest.class);
        verify(translationClient).translate(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getSourceLang()).isEqualTo("auto");
        verifyNoInteractions(speechTranscriptionClient);
        verifyNoInteractions(transcriptEventPublisher);
    }

    @Test
    void translateDeletedMessageReturnsGracefulError() {
        Message deletedMessage = Message.builder()
                .id(11L)
                .roomId("room-voice")
                .messageType("VOICE_NOTE")
                .deleted(true)
                .build();

        when(service.getMessage(11L, "room-voice")).thenReturn(deletedMessage);
        when(service.isTranslatable(deletedMessage)).thenReturn(false);

        ResponseEntity<MessageTranslationResponse> response =
                controller.translateMessage("room-voice", 11L, "en", "user-5");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getError()).isEqualTo("Deleted messages cannot be translated");
        verifyNoInteractions(authClient, translationClient, speechTranscriptionClient, transcriptEventPublisher);
    }

    @Test
    void translateMessageRefundsCreditsWithInternalSecretWhenTranslationFails() {
        Message message = Message.builder()
                .id(12L)
                .roomId("room-1")
                .content("hello")
                .messageType("TEXT")
                .build();

        when(service.getMessage(12L, "room-1")).thenReturn(message);
        when(service.isTranslatable(message)).thenReturn(true);
        when(authClient.consumeTranslationCredit("user-6")).thenReturn(summaryWithLanguage("english", 49));
        when(translationClient.translate(any(TranslationRequest.class))).thenReturn(new TranslationResponse(
                "hello",
                null,
                "en",
                "es",
                "libretranslate",
                false,
                "Provider unavailable"
        ));
        when(authClient.topUpTranslationCredits("user-6", "test-topup-secret", 1))
                .thenReturn(summaryWithLanguage("english", 50));

        ResponseEntity<MessageTranslationResponse> response =
                controller.translateMessage("room-1", 12L, "es", "user-6");

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getTranslationCreditsRemaining()).isEqualTo(50);
        assertThat(response.getBody().getError()).isEqualTo("Provider unavailable");
        verify(authClient).topUpTranslationCredits("user-6", "test-topup-secret", 1);
    }

    private UserSummaryResponse summaryWithLanguage(String preferredLanguage, int remainingCredits) {
        return new UserSummaryResponse(
                "user-1",
                "receiver",
                "Receiver",
                "receiver@example.com",
                null,
                null,
                null,
                preferredLanguage == null ? null : preferredLanguage.toLowerCase(Locale.ROOT),
                remainingCredits,
                "ONLINE",
                null,
                "USER"
        );
    }
}
