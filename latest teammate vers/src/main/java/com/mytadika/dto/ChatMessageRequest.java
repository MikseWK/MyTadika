package com.mytadika.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatMessageRequest {
    private String senderId;
    private String receiverId;
    private String content;
    private String messageType = "text";
    private Long replyToId;
}
