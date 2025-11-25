package com.nhnacademy.book_server.entity;

import jakarta.persistence.*;
import lombok.*;


@Builder
@Entity
@Table(name = "authors")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "author_id")
    private Long id;

    @Column(name = "author_name", nullable = false, unique = true, length = 500)
    private String name;
}
