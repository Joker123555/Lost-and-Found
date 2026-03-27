package com.campus.lostfound.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "claims")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "claimant_id", nullable = false)
    private Long claimantId;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private Integer status;

    @Column(name = "reject_reason", length = 512)
    private String rejectReason;

    @Column(name = "processed_by")
    private Long processedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "chat_session_id")
    private Long chatSessionId;

    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (version == null) version = 0;
        if (isDeleted == null) isDeleted = 0;
        if (status == null) status = 0;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
