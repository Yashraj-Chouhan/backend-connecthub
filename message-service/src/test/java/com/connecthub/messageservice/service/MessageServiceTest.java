package com.connecthub.messageservice.service;

import com.connecthub.messageservice.dto.EditMessageRequest;
import com.connecthub.messageservice.dto.MessageRequest;
import com.connecthub.messageservice.dto.ReactionRequest;
import com.connecthub.messageservice.entity.Message;
import com.connecthub.messageservice.entity.MessageReaction;
import com.connecthub.messageservice.repository.MessageReactionRepository;
import com.connecthub.messageservice.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository repository;

    @Mock
    private MessageReactionRepository reactionRepository;

    @TempDir
    Path tempDir;

    private MessageService service;

    @BeforeEach
    void setUp() {
        service = new MessageService(repository, reactionRepository, null, tempDir.toString());
    }

    @Test
    void saveMessageDefaultsBlankMessageTypeToText() {
        MessageRequest request = new MessageRequest();
        request.setSender("alice");
        request.setRoomId("room-1");
        request.setContent("hello");
        request.setMessageType("   ");

        when(repository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message saved = service.saveMessage(request);

        assertThat(saved.getMessageType()).isEqualTo("TEXT");
        assertThat(saved.getSender()).isEqualTo("alice");
        assertThat(saved.getRoomId()).isEqualTo("room-1");
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    void saveAttachmentMessageInfersVoiceNoteForAudioUploads() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.webm",
                "audio/webm",
                "voice".getBytes()
        );
        when(repository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message saved = service.saveAttachmentMessage("alice", "room-1", null, null, null, null, file);

        assertThat(saved.getMessageType()).isEqualTo("VOICE_NOTE");
        assertThat(saved.getContent()).isEqualTo("Voice note");
        assertThat(saved.getAttachmentName()).isEqualTo("note.webm");
        assertThat(saved.getTranscript()).isNull();
        verify(repository).save(any(Message.class));
    }

    @Test
    void saveAttachmentMessageRejectsUnsupportedVoiceNoteFormats() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.txt",
                "audio/plain",
                "voice".getBytes()
        );

        assertThatThrownBy(() -> service.saveAttachmentMessage("alice", "room-1", null, "VOICE_NOTE", null, null, file))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unsupported voice note format");

        verify(repository, never()).save(any(Message.class));
    }

    @Test
    void saveAttachmentMessageStoresTranscriptWhenProvided() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "note.webm",
                "audio/webm",
                "voice".getBytes()
        );
        when(repository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message saved = service.saveAttachmentMessage(
                "alice",
                "room-1",
                null,
                "VOICE_NOTE",
                "hello from browser transcript",
                "en-US",
                file
        );

        assertThat(saved.getTranscript()).isEqualTo("hello from browser transcript");
        assertThat(saved.getTranscriptSourceLanguage()).isEqualTo("en-US");
        assertThat(saved.getTranscriptUpdatedAt()).isNotNull();
    }

    @Test
    void saveAttachmentMessageAcceptsBlobVoiceNoteAndInfersWebmExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "blob",
                "audio/webm",
                "voice".getBytes()
        );
        when(repository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message saved = service.saveAttachmentMessage("alice", "room-1", null, "VOICE_NOTE", null, null, file);

        assertThat(saved.getMessageType()).isEqualTo("VOICE_NOTE");
        assertThat(saved.getAttachmentName()).isEqualTo("voice-note.webm");
        assertThat(saved.getAttachmentPath()).endsWith("voice-note.webm");
        assertThat(saved.getAttachmentContentType()).isEqualTo("audio/webm");
    }

    @Test
    void saveAttachmentMessageRejectsMissingFile() {
        assertThatThrownBy(() -> service.saveAttachmentMessage("alice", "room-1", null, null, null, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("File is required");
    }

    @Test
    void saveAttachmentMessageStoresRegularFilesAsFileMessages() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "design.pdf",
                "application/pdf",
                "pdf".getBytes()
        );
        when(repository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message saved = service.saveAttachmentMessage("alice", "room-1", "Specification", null, null, null, file);

        assertThat(saved.getMessageType()).isEqualTo("FILE");
        assertThat(saved.getContent()).isEqualTo("Specification");
        assertThat(saved.getAttachmentName()).isEqualTo("design.pdf");
        assertThat(saved.getAttachmentPath()).contains("design.pdf");
    }

    @Test
    void getMessageThrowsWhenMessageDoesNotExist() {
        when(repository.findByIdAndRoomId(99L, "room-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMessage(99L, "room-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Message not found");
    }

    @Test
    void getMessageHistoryDelegatesToRepository() {
        PageImpl<Message> page = new PageImpl<>(List.of(Message.builder().id(7L).roomId("room-1").build()));
        when(repository.findByRoomIdOrderByTimestampDesc(eq("room-1"), any())).thenReturn(page);

        assertThat(service.getMessageHistory("room-1", 1, 20).getContent())
                .extracting(Message::getId)
                .containsExactly(7L);
    }

    @Test
    void searchMessagesDelegatesToRepository() {
        PageImpl<Message> page = new PageImpl<>(List.of(Message.builder().id(8L).content("hello").build()));
        when(repository.findByRoomIdAndContentContainingIgnoreCaseOrderByTimestampDesc(eq("room-1"), eq("hello"), any()))
                .thenReturn(page);

        assertThat(service.searchMessages("room-1", "hello", 0, 10).getContent())
                .extracting(Message::getId)
                .containsExactly(8L);
    }

    @Test
    void editMessageRejectsDeletedMessages() {
        Message message = Message.builder()
                .id(11L)
                .roomId("room-1")
                .deleted(true)
                .build();
        EditMessageRequest request = new EditMessageRequest();
        request.setContent("updated");

        when(repository.findByIdAndRoomId(11L, "room-1")).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.editMessage(11L, "room-1", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Deleted message cannot be edited");
    }

    @Test
    void editMessageRejectsBlankContent() {
        Message message = Message.builder()
                .id(11L)
                .roomId("room-1")
                .deleted(false)
                .build();
        EditMessageRequest request = new EditMessageRequest();
        request.setContent("   ");

        when(repository.findByIdAndRoomId(11L, "room-1")).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.editMessage(11L, "room-1", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Content is required");
    }

    @Test
    void editMessageUpdatesContentAndEditedAt() {
        Message message = Message.builder()
                .id(11L)
                .roomId("room-1")
                .content("old")
                .timestamp(LocalDateTime.now().minusMinutes(3))
                .deleted(false)
                .build();
        EditMessageRequest request = new EditMessageRequest();
        request.setContent("updated");

        when(repository.findByIdAndRoomId(11L, "room-1")).thenReturn(Optional.of(message));
        when(repository.save(message)).thenReturn(message);

        Message saved = service.editMessage(11L, "room-1", request);

        assertThat(saved.getContent()).isEqualTo("updated");
        assertThat(saved.getEditedAt()).isNotNull();
    }

    @Test
    void deleteMessageMarksMessageDeletedAndClearsContent() {
        Message message = Message.builder()
                .id(12L)
                .roomId("room-1")
                .content("secret")
                .timestamp(LocalDateTime.now().minusMinutes(1))
                .build();

        when(repository.findByIdAndRoomId(12L, "room-1")).thenReturn(Optional.of(message));
        when(repository.save(message)).thenReturn(message);

        Message deleted = service.deleteMessage(12L, "room-1");

        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getDeletedAt()).isNotNull();
        assertThat(deleted.getContent()).isNull();
    }

    @Test
    void addReactionRejectsMissingUserOrEmoji() {
        ReactionRequest request = new ReactionRequest();
        request.setUserId("alice");
        request.setEmoji(" ");

        assertThatThrownBy(() -> service.addReaction(15L, "room-1", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("userId and emoji are required");
    }

    @Test
    void addReactionCreatesOrUpdatesReactionForExistingMessage() {
        Message message = Message.builder().id(15L).roomId("room-1").build();
        MessageReaction reaction = MessageReaction.builder()
                .messageId(15L)
                .roomId("room-1")
                .userId("alice")
                .build();
        ReactionRequest request = new ReactionRequest();
        request.setUserId("alice");
        request.setEmoji("fire");

        when(repository.findByIdAndRoomId(15L, "room-1")).thenReturn(Optional.of(message));
        when(reactionRepository.findByMessageIdAndUserId(15L, "alice")).thenReturn(Optional.of(reaction));
        when(reactionRepository.save(reaction)).thenReturn(reaction);

        MessageReaction saved = service.addReaction(15L, "room-1", request);

        assertThat(saved.getEmoji()).isEqualTo("fire");
        assertThat(saved.getTimestamp()).isNotNull();
    }

    @Test
    void getAttachmentRejectsMessagesWithoutAttachment() {
        Message message = Message.builder()
                .id(22L)
                .roomId("room-1")
                .build();

        when(repository.findByIdAndRoomId(22L, "room-1")).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.getAttachment(22L, "room-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no attachment");
    }

    @Test
    void getAttachmentRejectsMissingAttachmentFiles() {
        Message message = Message.builder()
                .id(22L)
                .roomId("room-1")
                .attachmentPath(tempDir.resolve("missing.webm").toString())
                .build();

        when(repository.findByIdAndRoomId(22L, "room-1")).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> service.getAttachment(22L, "room-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Attachment file missing");
    }

    @Test
    void getAttachmentReturnsReadableResource() throws Exception {
        Path attachment = tempDir.resolve("note.webm");
        Files.writeString(attachment, "voice");
        Message message = Message.builder()
                .id(22L)
                .roomId("room-1")
                .attachmentPath(attachment.toString())
                .build();

        when(repository.findByIdAndRoomId(22L, "room-1")).thenReturn(Optional.of(message));

        Resource resource = service.getAttachment(22L, "room-1");

        assertThat(resource.exists()).isTrue();
        assertThat(resource.getFilename()).isEqualTo("note.webm");
    }

    @Test
    void updateTranscriptTrimsValuesAndStoresTimestamp() {
        Message message = Message.builder()
                .id(30L)
                .roomId("room-1")
                .build();

        when(repository.findByIdAndRoomId(30L, "room-1")).thenReturn(Optional.of(message));
        when(repository.save(message)).thenReturn(message);

        Message updated = service.updateTranscript(30L, "room-1", "  hello transcript  ", " en-US ");

        assertThat(updated.getTranscript()).isEqualTo("hello transcript");
        assertThat(updated.getTranscriptSourceLanguage()).isEqualTo("en-US");
        assertThat(updated.getTranscriptUpdatedAt()).isNotNull();
    }

    @Test
    void voiceNoteAndTranslationHelpersReflectMessageState() {
        Message audioAttachment = Message.builder()
                .attachmentContentType("audio/mpeg")
                .messageType("FILE")
                .build();
        Message deletedMessage = Message.builder()
                .deleted(true)
                .build();

        assertThat(service.isVoiceNote(audioAttachment)).isTrue();
        assertThat(service.isVoiceNote(null)).isFalse();
        assertThat(service.isTranslatable(audioAttachment)).isTrue();
        assertThat(service.isTranslatable(deletedMessage)).isFalse();
        assertThat(service.isTranslatable(null)).isFalse();
    }
}
