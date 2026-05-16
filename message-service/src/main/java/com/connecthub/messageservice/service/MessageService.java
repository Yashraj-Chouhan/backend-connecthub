package com.connecthub.messageservice.service;

import com.connecthub.messageservice.dto.EditMessageRequest;
import com.connecthub.messageservice.dto.MessageRequest;
import com.connecthub.messageservice.dto.ReactionRequest;
import com.connecthub.messageservice.client.RoomClient;
import com.connecthub.messageservice.entity.Message;
import com.connecthub.messageservice.entity.MessageReaction;
import com.connecthub.messageservice.repository.MessageReactionRepository;
import com.connecthub.messageservice.repository.MessageRepository;
import com.connecthub.messageservice.util.VoiceNoteFilenameSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
/**
 * Persists chat content and attachments, maintains message history, and updates
 * room activity metadata after message changes.
 */
public class MessageService {

    private static final String DEFAULT_MESSAGE_TYPE = "TEXT";
    private static final String FILE_MESSAGE_TYPE = "FILE";
    private static final String VOICE_NOTE_MESSAGE_TYPE = "VOICE_NOTE";

    private final MessageRepository repository;
    private final MessageReactionRepository reactionRepository;
    private final RoomClient roomClient;
    private final Path uploadDirectory;

    public MessageService(MessageRepository repository,
                          MessageReactionRepository reactionRepository,
                          @Nullable RoomClient roomClient,
                          @Value("${app.upload-dir:uploads}") String uploadDir) {
        this.repository = repository;
        this.reactionRepository = reactionRepository;
        this.roomClient = roomClient;
        this.uploadDirectory = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload directory", e);
        }
    }

    /**
     * Saves a text-style message and nudges room-service so the conversation is
     * sorted as recently active.
     */
    public Message saveMessage(MessageRequest request) {
        Message message = Message.builder()
                .sender(request.getSender())
                .content(request.getContent())
                .roomId(request.getRoomId())
                .timestamp(LocalDateTime.now())
                .messageType(normalizeMessageType(request.getMessageType()))
                .attachmentName(request.getAttachmentName())
                .attachmentPath(request.getAttachmentPath())
                .attachmentContentType(request.getAttachmentContentType())
                .attachmentSize(request.getAttachmentSize())
                .replyToMessageId(request.getReplyToMessageId())
                .deleted(false)
                .build();

        Message saved = repository.save(message);
        touchRoom(saved.getRoomId(), saved.getTimestamp());
        return saved;
    }

    /**
     * Stores the uploaded file on disk and creates a matching FILE message row
     * so attachments behave like normal chat messages in history.
     */
    public Message saveAttachmentMessage(String sender,
                                         String roomId,
                                         String content,
                                         @Nullable String requestedMessageType,
                                         @Nullable String transcript,
                                         @Nullable String transcriptSourceLanguage,
                                         MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is required");
        }

        String resolvedMessageType = resolveAttachmentMessageType(requestedMessageType, file.getContentType());
        String originalFilename = normalizeAttachmentFilename(
                StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename()),
                resolvedMessageType,
                file.getContentType()
        );
        validateAttachmentType(originalFilename, resolvedMessageType, file.getContentType());
        String storedFilename = UUID.randomUUID() + "_" + originalFilename;
        Path targetPath = uploadDirectory.resolve(storedFilename);
        String defaultContent = VOICE_NOTE_MESSAGE_TYPE.equals(resolvedMessageType) ? "Voice note" : originalFilename;

        try {
            Files.copy(file.getInputStream(), targetPath);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store attachment");
        }

        Message message = Message.builder()
                .sender(sender)
                .content(StringUtils.hasText(content) ? content.trim() : defaultContent)
                .roomId(roomId)
                .timestamp(LocalDateTime.now())
                .messageType(resolvedMessageType)
                .attachmentName(originalFilename)
                .attachmentPath(targetPath.toString())
                .attachmentContentType(file.getContentType())
                .attachmentSize(file.getSize())
                .transcript(StringUtils.hasText(transcript) ? transcript.trim() : null)
                .transcriptSourceLanguage(StringUtils.hasText(transcriptSourceLanguage) ? transcriptSourceLanguage.trim() : null)
                .transcriptUpdatedAt(StringUtils.hasText(transcript) ? LocalDateTime.now() : null)
                .deleted(false)
                .build();

        Message saved = repository.save(message);
        touchRoom(saved.getRoomId(), saved.getTimestamp());
        return saved;
    }

    public List<Message> getMessages(String roomId) {
        return repository.findByRoomIdOrderByTimestampAsc(roomId);
    }

    public Message getMessage(Long messageId, String roomId) {
        return repository.findByIdAndRoomId(messageId, roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
    }

    public Page<Message> getMessageHistory(String roomId, int page, int size) {
        return repository.findByRoomIdOrderByTimestampDesc(roomId, PageRequest.of(page, size, Sort.by("timestamp").descending()));
    }

    public Page<Message> searchMessages(String roomId, String query, int page, int size) {
        return repository.findByRoomIdAndContentContainingIgnoreCaseOrderByTimestampDesc(
                roomId,
                query,
                PageRequest.of(page, size, Sort.by("timestamp").descending())
        );
    }

    public Message editMessage(Long messageId, String roomId, EditMessageRequest request) {
        Message message = repository.findByIdAndRoomId(messageId, roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        if (message.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deleted message cannot be edited");
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Content is required");
        }
        message.setContent(request.getContent());
        message.setEditedAt(LocalDateTime.now());
        Message saved = repository.save(message);
        touchRoom(saved.getRoomId(), saved.getTimestamp());
        return saved;
    }

    public Message deleteMessage(Long messageId, String roomId) {
        Message message = repository.findByIdAndRoomId(messageId, roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        message.setDeleted(true);
        message.setDeletedAt(LocalDateTime.now());
        message.setContent(null);
        Message saved = repository.save(message);
        touchRoom(saved.getRoomId(), saved.getTimestamp());
        return saved;
    }

    public MessageReaction addReaction(Long messageId, String roomId, ReactionRequest request) {
        if (request.getUserId() == null || request.getUserId().isBlank() || request.getEmoji() == null || request.getEmoji().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId and emoji are required");
        }

        Message message = repository.findByIdAndRoomId(messageId, roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        MessageReaction reaction = reactionRepository.findByMessageIdAndUserId(message.getId(), request.getUserId())
                .orElse(MessageReaction.builder()
                        .messageId(message.getId())
                        .roomId(message.getRoomId())
                        .userId(request.getUserId())
                        .build());
        reaction.setEmoji(request.getEmoji());
        reaction.setTimestamp(LocalDateTime.now());
        return reactionRepository.save(reaction);
    }

    public Resource getAttachment(Long messageId, String roomId) {
        Message message = repository.findByIdAndRoomId(messageId, roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));

        if (message.getAttachmentPath() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This message has no attachment");
        }

        try {
            Path filePath = Paths.get(message.getAttachmentPath()).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment file missing");
            }
            return resource;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not load attachment");
        }
    }

    public Message getAttachmentMetadata(Long messageId, String roomId) {
        return repository.findByIdAndRoomId(messageId, roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
    }

    public Message updateTranscript(Long messageId, String roomId, String transcript, String sourceLanguage) {
        Message message = repository.findByIdAndRoomId(messageId, roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        message.setTranscript(StringUtils.hasText(transcript) ? transcript.trim() : null);
        message.setTranscriptSourceLanguage(StringUtils.hasText(sourceLanguage) ? sourceLanguage.trim() : null);
        message.setTranscriptUpdatedAt(LocalDateTime.now());
        return repository.save(message);
    }

    public boolean isVoiceNote(Message message) {
        if (message == null) {
            return false;
        }

        if (VOICE_NOTE_MESSAGE_TYPE.equalsIgnoreCase(message.getMessageType())) {
            return true;
        }

        return StringUtils.hasText(message.getAttachmentContentType())
                && message.getAttachmentContentType().toLowerCase(Locale.ROOT).startsWith("audio/");
    }

    public boolean isTranslatable(Message message) {
        return message != null && !message.isDeleted();
    }

    private String resolveAttachmentMessageType(String requestedMessageType, String contentType) {
        String normalizedRequestedType = normalizeMessageType(requestedMessageType);
        if (VOICE_NOTE_MESSAGE_TYPE.equals(normalizedRequestedType)) {
            return VOICE_NOTE_MESSAGE_TYPE;
        }

        if (StringUtils.hasText(contentType) && contentType.toLowerCase(Locale.ROOT).startsWith("audio/")) {
            return VOICE_NOTE_MESSAGE_TYPE;
        }

        return FILE_MESSAGE_TYPE;
    }

    private void validateAttachmentType(String originalFilename, String resolvedMessageType, String contentType) {
        if (!VOICE_NOTE_MESSAGE_TYPE.equals(resolvedMessageType)) {
            return;
        }

        if (!VoiceNoteFilenameSupport.isSupportedVoiceNote(originalFilename, contentType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported voice note format. Supported formats: flac, m4a, mp3, mp4, mpeg, mpga, ogg, wav, webm"
            );
        }
    }

    private String normalizeAttachmentFilename(String originalFilename, String resolvedMessageType, String contentType) {
        if (!VOICE_NOTE_MESSAGE_TYPE.equals(resolvedMessageType)) {
            return originalFilename;
        }

        return VoiceNoteFilenameSupport.normalizeVoiceNoteFilename(originalFilename, contentType);
    }

    private String normalizeMessageType(String messageType) {
        if (!StringUtils.hasText(messageType)) {
            return DEFAULT_MESSAGE_TYPE;
        }

        return messageType.trim().toUpperCase(Locale.ROOT);
    }

    private void touchRoom(String roomId, LocalDateTime timestamp) {
        if (roomClient == null) {
            return;
        }
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                roomClient.updateLastMessageAt(roomId, timestamp == null ? null : timestamp.toString());
            } catch (Exception ignored) {
                // Room activity updates should not block message persistence.
            }
        });
    }
}
