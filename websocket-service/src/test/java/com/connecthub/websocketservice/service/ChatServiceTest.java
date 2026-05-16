package com.connecthub.websocketservice.service;

import com.connecthub.websocketservice.client.AuthClient;
import com.connecthub.websocketservice.client.NotificationClient;
import com.connecthub.websocketservice.client.RoomClient;
import com.connecthub.websocketservice.client.MessageClient;
import com.connecthub.websocketservice.client.TranslationClient;
import com.connecthub.websocketservice.config.WebSocketProperties;
import com.connecthub.websocketservice.dto.ChatMessage;
import com.connecthub.websocketservice.dto.NotificationRequest;
import com.connecthub.websocketservice.dto.TranslationResponse;
import com.connecthub.websocketservice.dto.UserSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private MessageClient messageClient;

    @Mock
    private RoomClient roomClient;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private AuthClient authClient;

    @Mock
    private TranslationClient translationClient;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        WebSocketProperties properties = new WebSocketProperties();
        chatService = new ChatService(
                messagingTemplate,
                messageClient,
                roomClient,
                notificationClient,
                authClient,
                translationClient,
                properties,
                kafkaTemplate
        );
        lenient().when(roomClient.getRoom("room-1")).thenReturn(Map.of("name", "Design Room"));
        lenient().when(roomClient.getMembers("room-1")).thenReturn(List.of(
                Map.of("userId", "alice"),
                Map.of("userId", "bob")
        ));
    }

    @Test
    void persistsTextMessagesAndBroadcastsToRoomAndSender() {
        ChatMessage incoming = ChatMessage.builder()
                .sender("alice")
                .roomId("room-1")
                .content("Hello there")
                .messageType("TEXT")
                .build();

        chatService.handleMessage(incoming);

        verify(kafkaTemplate).send(eq("chat.message.incoming"), any(ChatMessage.class));
        verifyNoInteractions(messageClient, notificationClient);
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/room/room-1"), any(ChatMessage.class));
    }

    @Test
    void typingEventsOnlyBroadcastToRoom() {
        ChatMessage typing = ChatMessage.builder()
                .sender("alice")
                .roomId("room-1")
                .eventType("TYPING_INDICATOR")
                .build();

        chatService.handleTyping(typing);

        verifyNoInteractions(messageClient);
        verify(messagingTemplate).convertAndSend(eq("/topic/room/room-1"), any(ChatMessage.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/user/alice"), any(ChatMessage.class));
    }

    @Test
    void typingEventsRoutedThroughHandleMessagePreserveExistingTimestamp() {
        LocalDateTime timestamp = LocalDateTime.now().minusMinutes(1);
        ChatMessage typing = ChatMessage.builder()
                .sender(" alice ")
                .roomId(" room-1 ")
                .eventType(" typing_indicator ")
                .timestamp(timestamp)
                .build();

        chatService.handleMessage(typing);

        ArgumentCaptor<ChatMessage> chatCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/room/room-1"), chatCaptor.capture());
        assertThat(chatCaptor.getValue().getEventType()).isEqualTo("TYPING_INDICATOR");
        assertThat(chatCaptor.getValue().getTimestamp()).isEqualTo(timestamp);
    }

    @Test
    void malformedTypingEventsAreIgnored() {
        chatService.handleTyping(ChatMessage.builder().roomId("room-1").build());

        verifyNoInteractions(messagingTemplate, messageClient, notificationClient, kafkaTemplate);
    }

    @Test
    void reactionsOnlyBroadcastToRoomWithoutPersisting() {
        ChatMessage reaction = ChatMessage.builder()
                .sender("alice")
                .roomId("room-1")
                .messageId(42L)
                .emoji("fire")
                .eventType("REACTION")
                .build();

        chatService.handleMessage(reaction);

        verifyNoInteractions(messageClient);
        verify(messagingTemplate).convertAndSend(eq("/topic/room/room-1"), any(ChatMessage.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/user/alice"), any(ChatMessage.class));
        verifyNoInteractions(notificationClient);
    }

    @Test
    void malformedReactionEventsWithoutRoutingAreIgnored() {
        chatService.handleMessage(ChatMessage.builder()
                .eventType("REACTION")
                .messageId(42L)
                .emoji("fire")
                .build());

        verifyNoInteractions(messagingTemplate, notificationClient, kafkaTemplate);
    }

    @Test
    void reactionEventsWithoutMessageIdAreIgnored() {
        chatService.handleMessage(ChatMessage.builder()
                .sender("alice")
                .roomId("room-1")
                .eventType("REACTION")
                .emoji("fire")
                .build());

        verifyNoInteractions(messagingTemplate, notificationClient, kafkaTemplate);
    }

    @Test
    void reactionEventsPreserveExistingTimestamp() {
        LocalDateTime timestamp = LocalDateTime.now().minusMinutes(2);
        ChatMessage reaction = ChatMessage.builder()
                .sender("alice")
                .roomId("room-1")
                .messageId(42L)
                .emoji("fire")
                .eventType("REACTION")
                .timestamp(timestamp)
                .build();

        chatService.handleMessage(reaction);

        ArgumentCaptor<ChatMessage> chatCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/room/room-1"), chatCaptor.capture());
        assertThat(chatCaptor.getValue().getTimestamp()).isEqualTo(timestamp);
    }

    @Test
    void transcriptEventsBroadcastWithoutPersistingOrNotifying() {
        ChatMessage transcriptUpdate = ChatMessage.builder()
                .messageId(73L)
                .sender("alice")
                .roomId("room-1")
                .messageType("VOICE_NOTE")
                .content("Voice note")
                .eventType("MESSAGE_TRANSCRIBED")
                .transcript("hello from the voice note")
                .transcriptSourceLanguage("en")
                .build();
        when(authClient.getUserById("alice")).thenReturn(userSummary("alice", "en"));
        when(authClient.getUserById("bob")).thenReturn(userSummary("bob", "es"));
        when(translationClient.translate(any())).thenReturn(new TranslationResponse(
                "hello from the voice note",
                null,
                "hola desde la nota de voz",
                "en",
                "es",
                "libretranslate",
                true,
                null
        ));

        chatService.handleTranscriptMessage(transcriptUpdate);

        verifyNoInteractions(messageClient, notificationClient);
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ChatMessage> chatCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), chatCaptor.capture());
        assertThat(destinationCaptor.getAllValues())
                .containsExactly("/topic/user/bob");
        assertThat(chatCaptor.getAllValues())
                .extracting(ChatMessage::getTranscript)
                .containsExactly("hola desde la nota de voz");
        assertThat(chatCaptor.getAllValues())
                .extracting(ChatMessage::getTargetLanguage)
                .containsExactly("es");
    }

    @Test
    void transcribedEventsRoutedThroughHandleMessageBroadcastWithoutNotifications() {
        ChatMessage transcriptUpdate = ChatMessage.builder()
                .messageId(73L)
                .sender("alice")
                .roomId("room-1")
                .content("Voice note")
                .eventType("message_transcribed")
                .transcript("hello from the voice note")
                .transcriptSourceLanguage("en")
                .build();
        when(authClient.getUserById("alice")).thenReturn(userSummary("alice", "en"));
        when(authClient.getUserById("bob")).thenReturn(userSummary("bob", "fr"));
        when(translationClient.translate(any())).thenReturn(new TranslationResponse(
                "hello from the voice note",
                null,
                "bonjour depuis la note vocale",
                "en",
                "fr",
                "libretranslate",
                true,
                null
        ));

        chatService.handleMessage(transcriptUpdate);

        ArgumentCaptor<ChatMessage> chatCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), chatCaptor.capture());
        assertThat(destinationCaptor.getValue()).isEqualTo("/topic/user/bob");
        assertThat(chatCaptor.getAllValues())
                .extracting(ChatMessage::getEventType)
                .containsOnly("MESSAGE_TRANSCRIBED");
        assertThat(chatCaptor.getAllValues())
                .extracting(ChatMessage::getTranscript)
                .containsExactly("bonjour depuis la note vocale");
        verifyNoInteractions(messageClient, notificationClient, kafkaTemplate);
    }

    @Test
    void transcribedEventsWithoutMessageIdAreIgnored() {
        chatService.handleMessage(ChatMessage.builder()
                .sender("alice")
                .roomId("room-1")
                .eventType("MESSAGE_TRANSCRIBED")
                .content("Voice note")
                .build());

        verifyNoInteractions(messagingTemplate, notificationClient, kafkaTemplate, roomClient);
    }

    @Test
    void transcriptEventsWithoutRoomIdAreIgnored() {
        chatService.handleTranscriptMessage(ChatMessage.builder()
                .messageId(73L)
                .sender("alice")
                .content("Voice note")
                .build());

        verifyNoInteractions(messagingTemplate, notificationClient, roomClient);
    }

    @Test
    void transcriptLocalizationFallsBackToOriginalTranscriptWhenTranslationFails() {
        ChatMessage transcriptUpdate = ChatMessage.builder()
                .messageId(73L)
                .sender("alice")
                .roomId("room-1")
                .messageType("VOICE_NOTE")
                .content("Voice note")
                .eventType("MESSAGE_TRANSCRIBED")
                .transcript("hello from the voice note")
                .transcriptSourceLanguage("en")
                .build();
        when(authClient.getUserById("alice")).thenReturn(userSummary("alice", "en"));
        when(authClient.getUserById("bob")).thenReturn(userSummary("bob", "es"));
        when(translationClient.translate(any())).thenThrow(new RuntimeException("translation-service down"));

        chatService.handleTranscriptMessage(transcriptUpdate);

        ArgumentCaptor<ChatMessage> chatCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messagingTemplate).convertAndSend(any(String.class), chatCaptor.capture());
        assertThat(chatCaptor.getAllValues())
                .extracting(ChatMessage::getTranscript)
                .containsOnly("hello from the voice note");
    }

    @Test
    void ignoresNullTranscriptEventsFromKafka() {
        chatService.handleTranscriptMessage(null);

        verifyNoInteractions(messageClient, notificationClient, kafkaTemplate, roomClient, messagingTemplate);
    }

    @Test
    void ignoresMessagesWithoutRoutingInformation() {
        chatService.handleMessage(ChatMessage.builder().content("hello").build());

        verifyNoInteractions(messageClient, notificationClient, kafkaTemplate, roomClient);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void ignoresNullMessagesWithoutRoutingInformation() {
        chatService.handleMessage(null);

        verifyNoInteractions(messageClient, notificationClient, kafkaTemplate, roomClient, messagingTemplate);
    }

    @Test
    void ignoresTranslatedEventsWithoutMessageId() {
        ChatMessage translated = ChatMessage.builder()
                .sender("alice")
                .roomId("room-1")
                .eventType("MESSAGE_TRANSLATED")
                .content("hola")
                .build();

        chatService.handleMessage(translated);

        verifyNoInteractions(kafkaTemplate, notificationClient);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void ignoresPersistedFileMessagesWithoutMessageId() {
        ChatMessage fileOnly = ChatMessage.builder()
                .sender("alice")
                .roomId("room-1")
                .messageType("FILE")
                .content("document.pdf")
                .build();

        chatService.handleMessage(fileOnly);

        verifyNoInteractions(kafkaTemplate, notificationClient);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void ignoresEmptyTextMessagesBeforePublishing() {
        ChatMessage empty = ChatMessage.builder()
                .sender("alice")
                .roomId("room-1")
                .content("   ")
                .build();

        chatService.handleMessage(empty);

        verifyNoInteractions(kafkaTemplate, notificationClient);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void kafkaPublishFailuresAreSwallowed() {
        doThrow(new RuntimeException("kafka down")).when(kafkaTemplate).send(eq("chat.message.incoming"), any(ChatMessage.class));
        ChatMessage incoming = ChatMessage.builder()
                .sender("alice")
                .roomId("room-1")
                .content("Hello there")
                .build();

        assertThatNoException().isThrownBy(() -> chatService.handleMessage(incoming));

        verify(kafkaTemplate).send(eq("chat.message.incoming"), any(ChatMessage.class));
        verifyNoInteractions(messagingTemplate, notificationClient);
    }

    @Test
    void persistedMessagesBroadcastToRoomSenderAndOtherMembers() {
        ChatMessage persisted = ChatMessage.builder()
                .messageId(99L)
                .sender("alice")
                .roomId("room-1")
                .content("New design ready")
                .build();

        chatService.handleMessage(persisted);

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ChatMessage> chatCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messagingTemplate, times(3)).convertAndSend(destinationCaptor.capture(), chatCaptor.capture());
        assertThat(destinationCaptor.getAllValues())
                .containsExactlyInAnyOrder("/topic/room/room-1", "/topic/user/alice", "/topic/user/bob");
        assertThat(chatCaptor.getAllValues())
                .extracting(ChatMessage::getContent)
                .contains("New design ready", "New design ready", "Design Room: New design ready");

        ArgumentCaptor<NotificationRequest> notificationCaptor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationClient).createNotification(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getUserId()).isEqualTo("bob");
        assertThat(notificationCaptor.getValue().getMessage()).isEqualTo("Design Room: New design ready");
        verifyNoInteractions(messageClient);
    }

    @Test
    void broadcastMessagesWithoutSenderOnlyBroadcastToTheRoom() {
        ChatMessage persisted = ChatMessage.builder()
                .messageId(100L)
                .roomId("room-1")
                .content("System update")
                .build();

        chatService.handleBroadcastMessage(persisted);

        verify(messagingTemplate).convertAndSend(eq("/topic/room/room-1"), any(ChatMessage.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/user/alice"), any(ChatMessage.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/user/bob"), any(ChatMessage.class));
    }

    @Test
    void ignoresNullBroadcastMessagesFromKafka() {
        chatService.handleBroadcastMessage(null);

        verifyNoInteractions(messageClient, notificationClient, kafkaTemplate, roomClient, messagingTemplate);
    }

    @Test
    void ignoresBroadcastMessagesWithoutRoomId() {
        ChatMessage persisted = ChatMessage.builder()
                .messageId(98L)
                .sender("alice")
                .content("hello")
                .build();

        chatService.handleBroadcastMessage(persisted);

        verifyNoInteractions(notificationClient, roomClient);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void translatedMessagesWithIdsBroadcastWithoutTriggeringNotifications() {
        ChatMessage translated = ChatMessage.builder()
                .messageId(101L)
                .sender("alice")
                .roomId("room-1")
                .content("hola")
                .translatedContent("hello")
                .eventType("MESSAGE_TRANSLATED")
                .build();

        chatService.handleMessage(translated);

        verify(messagingTemplate).convertAndSend(eq("/topic/room/room-1"), any(ChatMessage.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/user/alice"), any(ChatMessage.class));
        verifyNoInteractions(notificationClient, kafkaTemplate, messageClient);
    }

    @Test
    void callSignalsWithoutContentBroadcastDirectlyToCallerAndRecipient() {
        ChatMessage callOffer = ChatMessage.builder()
                .sender("alice")
                .recipientId("bob")
                .roomId("room-1")
                .eventType(" call_offer ")
                .callId("call-1")
                .signalType("offer")
                .sdp("v=0")
                .build();

        chatService.handleMessage(callOffer);

        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ChatMessage> chatCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messagingTemplate, times(2)).convertAndSend(destinationCaptor.capture(), chatCaptor.capture());
        assertThat(destinationCaptor.getAllValues())
                .containsExactlyInAnyOrder("/topic/user/alice", "/topic/user/bob");
        assertThat(chatCaptor.getAllValues())
                .extracting(ChatMessage::getEventType)
                .containsOnly("CALL_OFFER");
        verifyNoInteractions(kafkaTemplate, notificationClient, messageClient, roomClient);
    }

    @Test
    void notificationPersistenceFailuresDoNotBreakFanOut() {
        doThrow(new RuntimeException("down")).when(notificationClient).createNotification(any(NotificationRequest.class));
        ChatMessage persisted = ChatMessage.builder()
                .messageId(102L)
                .sender("alice")
                .roomId("room-1")
                .content("Hello again")
                .build();

        assertThatNoException().isThrownBy(() -> chatService.handleBroadcastMessage(persisted));

        verify(messagingTemplate).convertAndSend(eq("/topic/user/bob"), any(ChatMessage.class));
        verify(notificationClient).createNotification(any(NotificationRequest.class));
    }

    @Test
    void malformedReactionEventsAreIgnored() {
        ChatMessage reaction = ChatMessage.builder()
                .sender("alice")
                .roomId("room-1")
                .eventType("REACTION")
                .messageId(42L)
                .emoji(" ")
                .build();

        chatService.handleMessage(reaction);

        verifyNoInteractions(kafkaTemplate, notificationClient);
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/room/room-1"), any(ChatMessage.class));
    }

    @Test
    void readReceiptsBroadcastOnlyToTheRoom() {
        ChatMessage receipt = ChatMessage.builder()
                .sender("alice")
                .roomId("room-1")
                .eventType("READ_RECEIPT")
                .build();

        chatService.handleReadReceipt(receipt);

        verify(messagingTemplate).convertAndSend(eq("/topic/room/room-1"), any(ChatMessage.class));
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/user/alice"), any(ChatMessage.class));
    }

    @Test
    void readReceiptsRoutedThroughHandleMessagePreserveExistingTimestamp() {
        LocalDateTime timestamp = LocalDateTime.now().minusMinutes(3);
        ChatMessage receipt = ChatMessage.builder()
                .sender("alice")
                .roomId("room-1")
                .eventType(" read_receipt ")
                .timestamp(timestamp)
                .build();

        chatService.handleMessage(receipt);

        ArgumentCaptor<ChatMessage> chatCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/room/room-1"), chatCaptor.capture());
        assertThat(chatCaptor.getValue().getEventType()).isEqualTo("READ_RECEIPT");
        assertThat(chatCaptor.getValue().getTimestamp()).isEqualTo(timestamp);
    }

    @Test
    void malformedReadReceiptsAreIgnored() {
        chatService.handleReadReceipt(ChatMessage.builder().sender("alice").build());

        verifyNoInteractions(messagingTemplate, messageClient, notificationClient, kafkaTemplate);
    }

    @Test
    void broadcastMessagesUseAttachmentFallbackPreviewForNotifications() {
        ChatMessage persisted = ChatMessage.builder()
                .messageId(103L)
                .sender("alice")
                .roomId("room-1")
                .attachmentName("design.pdf")
                .messageType("FILE")
                .build();

        chatService.handleBroadcastMessage(persisted);

        ArgumentCaptor<ChatMessage> notificationCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/bob"), notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getContent()).isEqualTo("Design Room: sent an attachment");
    }

    @Test
    void broadcastMessagesUseGenericRoomNameAndTruncateLongPreview() {
        when(roomClient.getRoom("room-2")).thenReturn(null);
        when(roomClient.getMembers("room-2")).thenReturn(List.of(Map.of("userId", "bob")));
        ChatMessage persisted = ChatMessage.builder()
                .messageId(104L)
                .sender("alice")
                .roomId("room-2")
                .content("x".repeat(140))
                .build();

        chatService.handleBroadcastMessage(persisted);

        ArgumentCaptor<ChatMessage> notificationCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/bob"), notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getContent())
                .startsWith("room room-2: ")
                .endsWith("...");
    }

    @Test
    void broadcastMessagesWithoutPreviewUseGenericFallbackText() {
        when(roomClient.getMembers("room-2")).thenReturn(List.of(Map.of("userId", "bob")));
        when(roomClient.getRoom("room-2")).thenReturn(Map.of("name", "Design Room"));
        ChatMessage persisted = ChatMessage.builder()
                .messageId(106L)
                .sender("alice")
                .roomId("room-2")
                .build();

        chatService.handleBroadcastMessage(persisted);

        ArgumentCaptor<ChatMessage> notificationCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/bob"), notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getContent()).isEqualTo("Design Room: New message");
    }

    @Test
    void roomLookupFailuresDoNotPreventRoomAndSenderBroadcasts() {
        when(roomClient.getMembers("room-1")).thenThrow(new RuntimeException("room-service down"));
        ChatMessage persisted = ChatMessage.builder()
                .messageId(105L)
                .sender("alice")
                .roomId("room-1")
                .content("Heads up")
                .build();

        assertThatNoException().isThrownBy(() -> chatService.handleBroadcastMessage(persisted));

        verify(messagingTemplate).convertAndSend(eq("/topic/room/room-1"), any(ChatMessage.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/user/alice"), any(ChatMessage.class));
    }

    @Test
    void broadcastMessagesSkipNullBlankAndSelfRecipients() {
        when(roomClient.getMembers("room-3")).thenReturn(java.util.Arrays.asList(
                null,
                Map.of("userId", " "),
                Map.of("userId", "alice"),
                Map.of("userId", "bob")
        ));
        when(roomClient.getRoom("room-3")).thenReturn(Map.of("name", "General"));
        ChatMessage persisted = ChatMessage.builder()
                .messageId(107L)
                .sender("alice")
                .roomId("room-3")
                .content("Heads up")
                .build();

        chatService.handleBroadcastMessage(persisted);

        verify(messagingTemplate, times(3)).convertAndSend(any(String.class), any(ChatMessage.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/user/bob"), any(ChatMessage.class));
        verify(notificationClient).createNotification(any(NotificationRequest.class));
    }

    @Test
    void privateHelpersHandleNullAndFallbackInputs() throws Exception {
        ChatMessage fallback = ChatMessage.builder()
                .roomId("room-9")
                .originalContent("Original")
                .translatedContent("Translated")
                .messageType(" file ")
                .eventType(" reaction ")
                .timestamp(LocalDateTime.now().minusMinutes(5))
                .deleted(Boolean.TRUE)
                .build();

        assertThat(invokePrivate("firstNonBlank", new Class[]{String[].class}, (Object) null)).isNull();
        assertThat(invokePrivate("normalizeBroadcastMessage", new Class[]{ChatMessage.class, ChatMessage.class}, null, fallback))
                .isSameAs(fallback);

        ChatMessage normalized = (ChatMessage) invokePrivate(
                "normalizeBroadcastMessage",
                new Class[]{ChatMessage.class, ChatMessage.class},
                ChatMessage.builder().deleted(Boolean.TRUE).build(),
                fallback
        );

        assertThat(normalized.getOriginalContent()).isEqualTo("Original");
        assertThat(normalized.getTranslatedContent()).isEqualTo("Translated");
        assertThat(normalized.getContent()).isEqualTo("Translated");
        assertThat(normalized.getMessageType()).isEqualTo("FILE");
        assertThat(normalized.getEventType()).isEqualTo("REACTION");
        assertThat(normalized.getTimestamp()).isEqualTo(fallback.getTimestamp());
        assertThat(normalized.getDeleted()).isTrue();

        assertThatNoException().isThrownBy(() ->
                invokePrivate("broadcastToRoom", new Class[]{ChatMessage.class}, new Object[]{null}));
        assertThatNoException().isThrownBy(() ->
                invokePrivate("broadcastToRoomAndSender", new Class[]{ChatMessage.class}, new Object[]{null}));
        verifyNoInteractions(messagingTemplate);
    }

    private Object invokePrivate(String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = ChatService.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(chatService, args);
    }

    private UserSummaryResponse userSummary(String userId, String preferredLanguage) {
        return new UserSummaryResponse(
                userId,
                userId,
                userId,
                userId + "@example.com",
                null,
                null,
                null,
                preferredLanguage,
                50,
                "ONLINE",
                null,
                "USER",
                false
        );
    }
}
