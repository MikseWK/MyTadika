package com.mytadika.model;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "memory_images")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MemoryImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "memory_post_id", nullable = false) private Long memoryPostId;
    @Column(name = "image_url", nullable = false, length = 500) private String imageUrl;
    @Column(name = "media_type", length = 10) private String mediaType;
}
