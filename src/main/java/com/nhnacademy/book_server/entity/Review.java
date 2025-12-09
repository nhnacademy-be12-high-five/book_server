package com.nhnacademy.book_server.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
@Entity
// 동시성 제어
@Table(
        name = "review",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_review_member_book",
                        columnNames = {"member_id", "book_id"}
                )
        }
)

public class Review {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    private int rating;

    @NotNull
    @Column(length = 3000)
    private String reviewContent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    @Column(name = "member_id")
    private Long memberId;

    private String loginId;

    /**
     * @Id
     * private Long id;
     *
     * private String loginId;
     *
     * @Entity
     * ReviewDetail
     * - Review review (FK)
     * - review.rating
     * - review.getMemberId() -- 1 (= 사용자 ID를) -> Select m.loginId Member m where id = 1; = adada
     */



    @OneToMany(mappedBy = "review", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<ReviewImage> reviewImages = new ArrayList<>();

    public Review(int rating, String reviewContent, Book book, Long memberId){
        this.rating = rating;
        this.reviewContent = reviewContent;
        this.book = book;
        this.memberId = memberId;
    }

    public void update(int rating, String reviewContent){
        this.rating = rating;
        this.reviewContent = reviewContent;
    }

}
