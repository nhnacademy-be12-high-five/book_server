package com.nhnacademy.book_server.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "member_book_user_tag",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_book_user_tag",
                columnNames = {"member_id", "book_id", "tag_code"}
        )
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberBookUserTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="member_id", nullable=false)
    private Long memberId;

    @Column(name="book_id", nullable=false)
    private Long bookId;

    @Column(name="tag_code", nullable=false, length=40)
    private String tagCode;

    @Column(name="created_at", nullable=false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
