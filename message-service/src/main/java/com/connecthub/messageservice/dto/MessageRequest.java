package com.connecthub.messageservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MessageRequest {

    @NotBlank(message = "Sender is required")
    private String sender;

    @Size(max = 4000, message = "Message content cannot exceed 4000 characters")
    private String content;

    @NotBlank(message = "Room ID is required")
    private String roomId;

    @NotBlank(message = "Message type is required")
    private String messageType;
    private String attachmentName;
    private String attachmentPath;
    private String attachmentContentType;
    private Long attachmentSize;
    private Long replyToMessageId;
}
