package com.nhnacademy.book_server.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
@AllArgsConstructor
@Table(uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_review_like_member_review",
                columnNames = {"member_id", "review_id"}
        )
})
public class ReviewLike {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_id", nullable = false)
    private Review review;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    public ReviewLike(Review review, Long memberId) {
        this.review = review;
        this.memberId = memberId;
    }
}
