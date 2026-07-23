package com.mytadika.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 28)
    private String senderId;

    @Column(nullable = false, length = 28)
    private String receiverId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    @Column(nullable = false)
    private boolean read;

    @Column(nullable = false)
    private boolean edited;

    @Column(nullable = false)
    private boolean deleted;

    @Column(columnDefinition = "varchar(10) default 'text'")
    @Builder.Default
    private String messageType = "text";

    @Column(name = "reply_to_id")
    private Long replyToId;
}
