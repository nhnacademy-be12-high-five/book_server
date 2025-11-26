package com.nhnacademy.book_server.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "book_author")
public class BookAuthor implements Persistable<BookAuthor.Pk> {

    @EmbeddedId // 이 필드가 복합 키임을 명시
    @Builder.Default
    private Pk id = new Pk();

    @MapsId("book")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    @JsonIgnore
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    @MapsId("author")
    private Author author;

    @Override
    public boolean isNew() {
        return true;
    }

    @Embeddable
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        @Column(name = "book_id")
        private Long book;   // Book 엔티티의 bookId와 매핑

        @Column(name = "author_id")
        private Long author; // Author 엔티티의 id와 매핑
    }

}
