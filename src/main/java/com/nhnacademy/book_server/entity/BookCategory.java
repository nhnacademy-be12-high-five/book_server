package com.nhnacademy.book_server.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "book_category")
@NoArgsConstructor
@Getter
public class BookCategory {

    @EmbeddedId
    private Pk id;

    public BookCategory(Pk category1, Book book, Category category) {
        this.id=category1;
        this.book=book;
        this.category=category;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Pk implements Serializable {
        @Column(name = "book_id")
        private Long bookId;

        @Column(name = "category_id")
        private Integer categoryId;
    }

    @MapsId("bookId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Book book;

    @MapsId("categoryId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;
}
