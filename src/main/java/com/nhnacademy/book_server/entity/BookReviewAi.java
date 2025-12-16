package com.nhnacademy.book_server.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
public class BookReviewAi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String summary;

    private Long lastReviewCount;
    private Double lastAvgRating;

    private LocalDateTime updatedAt;

    public BookReviewAi(Book book, String summary, Long lastReviewCount, Double lastAvgRating) {
        this.book = book;
        this.summary = summary;
        this.lastReviewCount = lastReviewCount;
        this.lastAvgRating = lastAvgRating;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateSummary(String newSummary, Long currentCount, Double currentRating) {
        this.summary = newSummary;
        this.lastReviewCount = currentCount;
        this.lastAvgRating = currentRating;
        this.updatedAt = LocalDateTime.now();
    }
}