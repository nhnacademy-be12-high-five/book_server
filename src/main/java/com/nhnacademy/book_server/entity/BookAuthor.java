package com.nhnacademy.book_server.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BookAuthor {

    @Embeddable // JPA에게 임베디드 가능한 클래스임을 알림
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pk implements java.io.Serializable {
        private Long book;   // Book 엔티티의 bookId와 매핑
        private Long author; // Author 엔티티의 id와 매핑
    }

    @EmbeddedId // 이 필드가 복합 키임을 명시
    private Pk id;

    @MapsId("book")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bookId")
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "authorId")
    @MapsId("author")
    private Author author;
}
